/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.ActivityNavigator
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.isDecisionPending
import org.thoughtcrime.securesms.payments.onboarding.PaymentsOnboardingActivity
import org.thoughtcrime.securesms.registration.sms.SmsRetrieverReceiver
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme

/**
 * Activity to hold the entire registration process.
 */
class RegistrationActivity : BaseActivity() {

  private val TAG = Log.tag(RegistrationActivity::class.java)

  private val dynamicTheme = DynamicNoActionBarTheme()
  val sharedViewModel: RegistrationViewModel by viewModels()

  private var smsRetrieverReceiver: SmsRetrieverReceiver? = null

  init {
    lifecycle.addObserver(SmsRetrieverObserver())
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    dynamicTheme.onCreate(this)

    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_registration_navigation_v3)

    sharedViewModel.isReregister = intent.getBooleanExtra(RE_REGISTRATION_EXTRA, false)

    sharedViewModel.checkpoint.observe(this) {
      if (it >= RegistrationCheckpoint.LOCAL_REGISTRATION_COMPLETE) {
        RegistrationUtil.maybeMarkRegistrationComplete()
        handleSuccessfulVerify()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  private fun handleSuccessfulVerify() {
    if (SignalStore.account.isPrimaryDevice && SignalStore.account.isMultiDevice) {
      SignalStore.misc.shouldShowLinkedDevicesReminder = sharedViewModel.isReregister
    }

    // New registrations enter the payments onboarding flow once (mirrors iOS PaymentsOnboardingCoordinator).
    // Re-registration goes straight to the app.
    if (!sharedViewModel.isReregister && !SignalStore.payments.paymentsOnboardingShown) {
      if (isRestoreOrPinStepPending()) {
        // A restore / "Enter your PIN" step still runs after this — it's driven by the
        // PassphraseRequiredActivity router on the way to MainActivity, which PaymentsOnboardingActivity
        // (a plain BaseActivity) would otherwise skip in front of. Defer onboarding so it appears AFTER
        // the restore delivers the wallet seed, instead of registering/showing a username against a
        // not-yet-restored Spark identity. MainActivity launches it once the restore finishes. (Fix A.)
        SignalStore.payments.paymentsOnboardingPendingAfterRestore = true
        startActivity(MainActivity.clearTop(this))
      } else {
        SignalStore.payments.paymentsOnboardingShown = true
        startActivity(PaymentsOnboardingActivity.createIntent(this, awaitRestoredSeed = false))
      }
    } else {
      startActivity(MainActivity.clearTop(this))
    }
    finish()
    ActivityNavigator.applyPopAnimationsToPendingTransition(this)
  }

  /**
   * Whether a restore or "Enter your PIN" step still runs before the app reaches its normal state
   * (see [org.thoughtcrime.securesms.PassphraseRequiredActivity]'s STATE_TRANSFER_OR_RESTORE /
   * STATE_ENTER_SIGNAL_PIN routing). While true, the payments onboarding must be deferred.
   */
  private fun isRestoreOrPinStepPending(): Boolean {
    return SignalStore.storageService.needsAccountRestore ||
      SignalStore.registration.restoreDecisionState.isDecisionPending
  }

  private inner class SmsRetrieverObserver : DefaultLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
      smsRetrieverReceiver = SmsRetrieverReceiver(application)
      smsRetrieverReceiver?.registerReceiver()
    }

    override fun onDestroy(owner: LifecycleOwner) {
      smsRetrieverReceiver?.unregisterReceiver()
      smsRetrieverReceiver = null
    }
  }

  companion object {
    const val RE_REGISTRATION_EXTRA: String = "re_registration"

    @JvmStatic
    fun newIntentForNewRegistration(context: Context, originalIntent: Intent): Intent {
      return Intent(context, RegistrationActivity::class.java).apply {
        putExtra(RE_REGISTRATION_EXTRA, false)
        setData(originalIntent.data)
      }
    }

    @JvmStatic
    fun newIntentForReRegistration(context: Context): Intent {
      return Intent(context, RegistrationActivity::class.java).apply {
        putExtra(RE_REGISTRATION_EXTRA, true)
      }
    }
  }
}
