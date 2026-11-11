/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.onboarding

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.button.MaterialButton
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R

/** Shown when a deposit arrives during onboarding. Mirrors iOS DepositReceivedViewController. */
class PaymentsOnboardingDepositReceivedFragment : LoggingFragment(R.layout.fragment_payments_onboarding_deposit_received) {

  companion object {
    const val ARG_AMOUNT = "amount"
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val amount = arguments?.getString(ARG_AMOUNT) ?: "0"
    view.findViewById<TextView>(R.id.onboarding_deposit_amount).text = amount

    view.findViewById<MaterialButton>(R.id.onboarding_deposit_continue).setOnClickListener {
      NavHostFragment.findNavController(this).navigate(R.id.action_depositReceived_to_setupComplete)
    }
    view.findViewById<MaterialButton>(R.id.onboarding_deposit_more).setOnClickListener {
      NavHostFragment.findNavController(this).popBackStack()
    }
  }
}
