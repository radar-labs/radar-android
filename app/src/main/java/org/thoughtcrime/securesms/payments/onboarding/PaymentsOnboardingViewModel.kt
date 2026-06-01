/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.onboarding

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.BreezSdkWrapper

/**
 * Activity-scoped state for [PaymentsOnboardingActivity]. Holds the wallet-readiness gate so the
 * onboarding flow can be shown immediately after the PIN step while the restored payments seed is
 * still landing, instead of stalling its launch in MainActivity (see
 * [org.thoughtcrime.securesms.MainActivity.maybeLaunchDeferredPaymentsOnboarding]).
 *
 * The Intro screen needs no wallet state and shows right away; [walletReady] only gates the Add
 * Funds intro, which activates payments (and would mint a fresh entropy/username if it ran before
 * the restored seed arrived — the race the deferral exists to prevent).
 */
class PaymentsOnboardingViewModel : ViewModel() {

  private val _walletReady = MutableLiveData(false)
  val walletReady: LiveData<Boolean> = _walletReady

  private var gateStarted = false

  /**
   * Starts the (idempotent) wallet-readiness gate.
   *
   * @param awaitRestoredSeed true when onboarding was deferred behind a restore, so a restored
   *   payments seed is expected to arrive asynchronously. When false (fresh registration) there is
   *   no seed to wait for and minting a new wallet on activation is correct, so the gate opens
   *   immediately.
   */
  fun beginWalletReadyGate(awaitRestoredSeed: Boolean) {
    if (gateStarted) {
      return
    }
    gateStarted = true

    if (!awaitRestoredSeed) {
      _walletReady.value = true
      return
    }

    viewModelScope.launch {
      val arrived = withContext(Dispatchers.IO) {
        var waitedMs = 0L
        while (SignalStore.payments.paymentsEntropy == null && waitedMs < SEED_MAX_WAIT_MS) {
          delay(SEED_POLL_MS)
          waitedMs += SEED_POLL_MS
        }
        SignalStore.payments.paymentsEntropy != null
      }

      if (arrived) {
        // Drop any Breez SDK connection / wallet that may have been built against a pre-restore
        // (or briefly null) entropy, so onboarding connects with the restored Spark identity.
        // BreezSdkWrapper.reset() alone is not enough — Payments caches the entropy-bound Wallet.
        BreezSdkWrapper.reset()
        AppDependencies.payments.closeWallet()
      } else {
        Log.w(TAG, "Restored payments seed did not arrive within ${SEED_MAX_WAIT_MS}ms; proceeding (may mint a fresh wallet).")
      }

      _walletReady.value = true
    }
  }

  companion object {
    private val TAG = Log.tag(PaymentsOnboardingViewModel::class.java)

    /** Bounded wait for the restore to deliver the payments seed before opening the onboarding gate. */
    private const val SEED_MAX_WAIT_MS = 8000L
    private const val SEED_POLL_MS = 200L
  }
}
