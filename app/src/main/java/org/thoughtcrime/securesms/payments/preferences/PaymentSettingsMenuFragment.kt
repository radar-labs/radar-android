/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.preferences

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.signal.core.util.concurrent.SimpleTask
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.help.HelpFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.PaymentSnippetRefresher
import org.thoughtcrime.securesms.payments.backup.RecoveryPhraseStates
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.whispersystems.signalservice.api.payments.Money

/**
 * Mirrors iOS `PaymentSettingsMenuViewController` — the settings page reached from
 * App Settings → Payments. Lists the same rows the iOS menu shows (currency,
 * bitcoin unit, payments username, recovery phrase, help, lightning logs,
 * deactivate, delete wallet). Distinct from [PaymentsHomeFragment], which is the
 * wallet UI (balance, send/receive, transactions) reached from the bottom-nav tab.
 */
class PaymentSettingsMenuFragment : DSLSettingsFragment(R.string.preferences__payments) {

  private lateinit var viewModel: PaymentsHomeViewModel
  private var lastBalanceAmount: Money? = null

  override fun bindAdapter(adapter: MappingAdapter) {
    viewModel = ViewModelProvider(this, PaymentsHomeViewModel.Factory())[PaymentsHomeViewModel::class.java]

    viewModel.balance.observe(viewLifecycleOwner) { balance ->
      lastBalanceAmount = balance
    }

    viewModel.paymentStateEvents.observe(viewLifecycleOwner) { event ->
      handlePaymentStateEvent(event)
    }

    adapter.submitList(buildConfiguration().toMappingModelList())
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    // Refresh the bitcoin-unit / currency-code accessory rows whenever the user
    // returns from one of the sub-screens (Set currency, Bitcoin unit picker).
    // Re-binding the adapter here is cheap and keeps the rows in sync.
  }

  override fun onResume() {
    super.onResume()
    if (::viewModel.isInitialized) {
      // Re-render so the accessory summaries (currency code, sats/BTC) reflect
      // any change made on a child screen.
      recyclerView?.adapter?.let { adapter ->
        (adapter as? MappingAdapter)?.submitList(buildConfiguration().toMappingModelList())
      }
    }
  }

  private fun buildConfiguration(): DSLConfiguration {
    val currencyCode = SignalStore.payments.currentCurrency().currencyCode
    val bitcoinUnit = if (SignalStore.payments.showInSats) "sats" else "BTC"
    val destructiveColor = ContextCompat.getColor(requireContext(), R.color.signal_alert_primary)

    return configure {
      clickPref(
        title = DSLSettingsText.from(R.string.PaymentsHomeFragment__currency_conversion),
        summary = DSLSettingsText.from(currencyCode),
        onClick = {
          NavHostFragment.findNavController(this@PaymentSettingsMenuFragment)
            .safeNavigate(R.id.action_paymentSettingsMenu_to_setCurrency)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.PaymentsHomeFragment__bitcoin_unit),
        summary = DSLSettingsText.from(bitcoinUnit),
        onClick = { showBitcoinUnitPicker() }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.PaymentsHomeFragment__payments_username),
        onClick = {
          NavHostFragment.findNavController(this@PaymentSettingsMenuFragment)
            .safeNavigate(R.id.action_paymentSettingsMenu_to_editLightningUsername)
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.PaymentsHomeFragment__recovery_phrase),
        onClick = {
          val state = if (SignalStore.payments.isMnemonicConfirmed()) {
            RecoveryPhraseStates.FROM_PAYMENTS_MENU_WITH_MNEMONIC_CONFIRMED
          } else {
            RecoveryPhraseStates.FROM_PAYMENTS_MENU_WITH_MNEMONIC_NOT_CONFIRMED
          }
          NavHostFragment.findNavController(this@PaymentSettingsMenuFragment)
            .safeNavigate(
              PaymentSettingsMenuFragmentDirections.actionPaymentSettingsMenuToPaymentsBackup()
                .setRecoveryPhraseState(state)
            )
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.PaymentsHomeFragment__help),
        onClick = {
          startActivity(AppSettingsActivity.help(requireContext(), HelpFragment.PAYMENT_INDEX))
        }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.PaymentsHomeFragment__lightning_logs),
        onClick = {
          NavHostFragment.findNavController(this@PaymentSettingsMenuFragment)
            .safeNavigate(R.id.action_paymentSettingsMenu_to_lightningLogs)
        }
      )

      dividerPref()

      clickPref(
        title = DSLSettingsText.from(R.string.PaymentsHomeFragment__deactivate_payments, destructiveColor),
        onClick = { viewModel.deactivatePayments() }
      )

      clickPref(
        title = DSLSettingsText.from(R.string.PaymentsHomeFragment__delete_wallet, destructiveColor),
        onClick = { confirmDeleteWallet() }
      )
    }
  }

  private fun showBitcoinUnitPicker() {
    val options = arrayOf("BTC", "sats")
    val checked = if (SignalStore.payments.showInSats) 1 else 0
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PaymentsHomeFragment__bitcoin_unit)
      .setSingleChoiceItems(options, checked) { dialog, which ->
        SignalStore.payments.showInSats = which == 1
        PaymentSnippetRefresher.refreshAsync()
        // Re-render so the accessory text updates immediately.
        (recyclerView?.adapter as? MappingAdapter)?.submitList(buildConfiguration().toMappingModelList())
        dialog.dismiss()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun confirmDeleteWallet() {
    val balance = lastBalanceAmount
    if (balance == null) {
      MaterialAlertDialogBuilder(requireContext())
        .setMessage(R.string.PaymentsHomeFragment__delete_wallet_balance_unavailable)
        .setPositiveButton(android.R.string.ok, null)
        .show()
      return
    }
    if (balance.isPositive) {
      MaterialAlertDialogBuilder(requireContext())
        .setMessage(R.string.PaymentsHomeFragment__delete_wallet_requires_zero_balance)
        .setPositiveButton(android.R.string.ok, null)
        .show()
      return
    }
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PaymentsHomeFragment__delete_wallet_title)
      .setMessage(R.string.PaymentsHomeFragment__delete_wallet_description)
      .setPositiveButton(R.string.PaymentsHomeFragment__delete_wallet) { _, _ -> doDeleteWallet() }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun doDeleteWallet() {
    SimpleTask.run(
      viewLifecycleOwner.lifecycle,
      {
        SignalStore.payments.breezSdkWrapperLatest().deleteLightningAddress()
        SignalStore.payments.deleteWallet()
        true
      },
      { requireActivity().finish() }
    )
  }

  private fun handlePaymentStateEvent(event: PaymentStateEvent) {
    when (event) {
      PaymentStateEvent.NO_BALANCE -> {
        android.widget.Toast.makeText(
          requireContext(),
          R.string.PaymentsHomeFragment__balance_is_not_currently_available,
          android.widget.Toast.LENGTH_SHORT
        ).show()
      }
      PaymentStateEvent.DEACTIVATED -> {
        Snackbar.make(requireView(), R.string.PaymentsHomeFragment__payments_deactivated, Snackbar.LENGTH_SHORT).show()
      }
      PaymentStateEvent.DEACTIVATE_WITHOUT_BALANCE -> {
        val destructiveColor = ContextCompat.getColor(requireContext(), R.color.signal_alert_primary)
        MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.PaymentsHomeFragment__deactivate_payments_question)
          .setMessage(R.string.PaymentsHomeFragment__you_will_not_be_able_to_send)
          .setPositiveButton(
            SpanUtil.color(destructiveColor, getString(R.string.PaymentsHomeFragment__deactivate))
          ) { dialog, _ ->
            viewModel.confirmDeactivatePayments()
            dialog.dismiss()
          }
          .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
          .show()
      }
      PaymentStateEvent.DEACTIVATE_WITH_BALANCE -> {
        MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.PaymentsHomeFragment__deactivate_payments_question)
          .setMessage(R.string.PaymentsHomeFragment__you_will_not_be_able_to_send)
          .setPositiveButton(R.string.PaymentsHomeFragment__continue) { dialog, _ ->
            dialog.dismiss()
            NavHostFragment.findNavController(this)
              .safeNavigate(R.id.action_paymentSettingsMenu_to_deactivateWallet)
          }
          .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
          .show()
      }
      else -> Unit
    }
  }
}
