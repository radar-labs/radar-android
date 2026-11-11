package org.thoughtcrime.securesms.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Publishes the wallet's lightning address to the account's Signal profile (at the fixed lightning
 * profile version) so that other users can send payments to it.
 * <p>
 * Enabling payments does not, by itself, put the lightning address into the profile: historically
 * that only happened lazily, as a side effect of opening the Receive/Send screens (which read
 * {@code wallet.lightningAddress}). That left a window where a freshly-activated account had
 * payments enabled locally but still showed "hasn't activated payments" to senders, because their
 * profile carried no lightning address yet. It was also best-effort — a slow or failed Breez
 * registration silently produced no upload and was never retried.
 * <p>
 * Enqueue this whenever payments become enabled (or a restored wallet seed lands) to publish the
 * address eagerly and, on failure (slow/failed Breez registration, transient network), retry it in
 * the background. The job is idempotent and self-guards on registration/enablement/seed state.
 */
public final class PublishLightningAddressJob extends BaseJob {

  private static final String TAG = Log.tag(PublishLightningAddressJob.class);

  public static final String KEY = "PublishLightningAddressJob";

  private static final String QUEUE = "PublishLightningAddress";

  public PublishLightningAddressJob() {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue(QUEUE)
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .setMaxInstancesForFactory(2)
                           .build());
  }

  private PublishLightningAddressJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  /**
   * Enqueues a publish when payments are enabled; a safe no-op otherwise. Call this after any
   * default-version profile upload (which becomes the server's "current" version and would
   * otherwise hide the lightning address) and on app startup (to heal accounts whose lightning
   * version was already clobbered before this fix).
   */
  public static void enqueueIfPaymentsEnabled() {
    if (SignalStore.payments().mobileCoinPaymentsEnabled()) {
      AppDependencies.getJobManager().add(new PublishLightningAddressJob());
    }
  }

  @Override
  protected void onRun() throws Exception {
    if (!SignalStore.account().isRegistered()) {
      Log.w(TAG, "Not registered. Skipping.");
      return;
    }

    if (!SignalStore.payments().mobileCoinPaymentsEnabled()) {
      Log.w(TAG, "Payments not enabled. Skipping lightning address publish.");
      return;
    }

    if (!SignalStore.payments().hasPaymentsEntropy()) {
      Log.w(TAG, "No payments entropy available yet. Skipping; will be re-enqueued once the wallet is ready.");
      return;
    }

    SignalStore.payments().breezSdkWrapperLatest().publishLightningAddress();
    Log.i(TAG, "Published lightning address to profile.");
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof IOException;
  }

  @Override
  public @Nullable byte[] serialize() {
    return null;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to publish lightning address to profile; senders may see \"hasn't activated payments\" until this succeeds.");
  }

  public static class Factory implements Job.Factory<PublishLightningAddressJob> {
    @Override
    public @NonNull PublishLightningAddressJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      return new PublishLightningAddressJob(parameters);
    }
  }
}
