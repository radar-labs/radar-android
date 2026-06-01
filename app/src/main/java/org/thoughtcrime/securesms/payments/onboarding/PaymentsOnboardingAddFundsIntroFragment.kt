/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.onboarding

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.button.MaterialButton
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.preferences.PaymentsHomeRepository
import org.thoughtcrime.securesms.util.AsynchronousCallback

/** Add-funds intro step, shown between the payments intro and the QR/address screen. Mirrors iOS AddFundsIntroViewController. */
class PaymentsOnboardingAddFundsIntroFragment : LoggingFragment(R.layout.fragment_payments_onboarding_add_funds_intro) {

  companion object {
    private val TAG = Log.tag(PaymentsOnboardingAddFundsIntroFragment::class.java)
  }

  private val onboardingViewModel: PaymentsOnboardingViewModel by activityViewModels()

  private var paymentsActivated = false

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val spinner = view.findViewById<ProgressBar>(R.id.onboarding_add_funds_intro_spinner)
    val continueButton = view.findViewById<MaterialButton>(R.id.onboarding_add_funds_intro_continue)

    continueButton.setOnClickListener {
      NavHostFragment.findNavController(this).navigate(R.id.action_addFundsIntro_to_addFunds)
    }
    view.findViewById<TextView>(R.id.onboarding_add_funds_intro_skip).setOnClickListener {
      NavHostFragment.findNavController(this).navigate(R.id.action_addFundsIntro_to_setupComplete)
    }

    // Gate activation and Continue on the wallet being ready. When onboarding was deferred behind a
    // restore, the restored seed may still be landing; activating payments before it arrives would
    // mint a fresh entropy/username. Until ready we show a spinner and keep Continue disabled.
    onboardingViewModel.walletReady.observe(viewLifecycleOwner) { ready ->
      spinner.visibility = if (ready) View.GONE else View.VISIBLE
      continueButton.isEnabled = ready
      if (ready) {
        activatePaymentsOnce()
      }
    }
  }

  /**
   * Enables payments on arrival so the downstream Add Funds (QR/address) screen has a wallet.
   * Mirrors iOS PaymentsOnboardingCoordinator.showAddFundsIntro().
   */
  private fun activatePaymentsOnce() {
    if (paymentsActivated || SignalStore.payments.mobileCoinPaymentsEnabled()) {
      return
    }
    paymentsActivated = true
    PaymentsHomeRepository().activatePayments(object : AsynchronousCallback.WorkerThread<Void, PaymentsHomeRepository.Error> {
      override fun onComplete(result: Void?) = Unit
      override fun onError(error: PaymentsHomeRepository.Error?) {
        Log.w(TAG, "Failed to enable payments during onboarding: $error")
      }
    })
  }
}
