/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.onboarding

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.button.MaterialButton
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.qr.QrView
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.preferences.addmoney.PaymentsAddMoneyViewModel

/**
 * Add-funds step of onboarding: shows the wallet's Lightning address + QR. If a deposit arrives,
 * advances to the deposit-received screen. Mirrors iOS PaymentsTransferInViewController(isOnboarding:).
 */
class PaymentsOnboardingAddFundsFragment : LoggingFragment(R.layout.fragment_payments_onboarding_add_funds) {

  private var navigatedToDeposit = false
  private var sawInitialBalance = false
  private var initialBalancePositive = false

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val viewModel = ViewModelProvider(this, PaymentsAddMoneyViewModel.Factory()).get(PaymentsAddMoneyViewModel::class.java)

    val qr = view.findViewById<QrView>(R.id.onboarding_add_funds_qr)
    val addressView = view.findViewById<TextView>(R.id.onboarding_add_funds_address)

    viewModel.selfAddressB58.observe(viewLifecycleOwner) { address ->
      addressView.text = address
      qr.setQrText(address)
    }

    view.findViewById<MaterialButton>(R.id.onboarding_add_funds_continue).setOnClickListener {
      NavHostFragment.findNavController(this).navigate(R.id.action_addFunds_to_setupComplete)
    }
    view.findViewById<TextView>(R.id.onboarding_add_funds_skip).setOnClickListener {
      NavHostFragment.findNavController(this).navigate(R.id.action_addFunds_to_setupComplete)
    }

    // Watch for an incoming deposit while on this screen; advance once on the 0 -> positive transition.
    SignalStore.payments.liveMobileCoinBalance().observe(viewLifecycleOwner) { balance ->
      val positive = balance != null && balance.fullAmount.isPositive
      if (!sawInitialBalance) {
        sawInitialBalance = true
        initialBalancePositive = positive
        return@observe
      }
      if (!navigatedToDeposit && positive && !initialBalancePositive) {
        navigatedToDeposit = true
        val args = Bundle().apply {
          putString(PaymentsOnboardingDepositReceivedFragment.ARG_AMOUNT, balance!!.fullAmount.serializeAmountString())
        }
        NavHostFragment.findNavController(this).navigate(R.id.action_addFunds_to_depositReceived, args)
      }
    }
  }
}
