/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.preferences.addmoney

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Lets the user pick / edit their Radar lightning-address username (e.g. `alice@radar.cash`).
 *
 * Mirrors iOS RadarUsernameViewController:
 *  - Centered radar-logo header + Title2/subtitle copy.
 *  - Gray-fill pill EditText with a "@radar.cash" accent suffix and a clear (X) button.
 *  - Debounced (500ms) availability check; ✓/✗ status label in green/accent-red.
 *  - Accent-blue Confirm capsule + borderless accent-blue Cancel.
 *  - Auto-focuses the field on appear (keyboard slides over the content).
 *  - On successful register: posts a FragmentResult so the Add Funds screen refreshes
 *    (mirrors iOS `walletAddressDidLoad`), then pops back.
 */
class EditLightningUsernameFragment : LoggingFragment(R.layout.fragment_edit_lightning_username) {

  companion object {
    private val TAG = Log.tag(EditLightningUsernameFragment::class.java)
    private const val DEBOUNCE_MS = 500L
    private const val AVAILABLE_COLOR = 0xFF46B827.toInt()
    private const val UNAVAILABLE_COLOR = 0xFFFF3B30.toInt()
  }

  private var checkJob: Job? = null
  private var available = false

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar = view.findViewById<Toolbar>(R.id.edit_lightning_username_toolbar)
    val field = view.findViewById<EditText>(R.id.edit_lightning_username_field)
    val clear = view.findViewById<ImageButton>(R.id.edit_lightning_username_clear)
    val status = view.findViewById<TextView>(R.id.edit_lightning_username_status)
    val confirm = view.findViewById<MaterialButton>(R.id.edit_lightning_username_confirm)
    val skip = view.findViewById<TextView>(R.id.edit_lightning_username_skip)

    toolbar.setNavigationOnClickListener { NavHostFragment.findNavController(this).popBackStack() }

    confirm.isEnabled = false
    status.visibility = View.GONE

    // Prefill with the current username, if any.
    viewLifecycleOwner.lifecycleScope.launch {
      val current = withContext(Dispatchers.IO) {
        runCatching { SignalStore.payments.breezSdkWrapperLatest().getUsername() }.getOrNull()
      }
      if (!current.isNullOrEmpty() && field.text.isNullOrEmpty()) {
        field.setText(current)
        field.setSelection(field.text.length)
      }
    }

    field.doAfterTextChanged {
      val text = it?.toString().orEmpty()
      clear.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
      onUsernameChanged(text.trim(), status, confirm)
    }

    clear.setOnClickListener { field.setText("") }

    confirm.setOnClickListener { onConfirm(field.text.toString().trim(), confirm) }
    skip.setOnClickListener { NavHostFragment.findNavController(this).popBackStack() }

    // Auto-focus the field and present the soft keyboard (parity with iOS `becomeFirstResponder`).
    field.requestFocus()
    field.post {
      val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
      imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }
  }

  private fun onUsernameChanged(username: String, status: TextView, confirm: MaterialButton) {
    checkJob?.cancel()
    available = false
    confirm.isEnabled = false
    status.visibility = View.GONE

    if (username.isBlank()) return

    checkJob = viewLifecycleOwner.lifecycleScope.launch {
      delay(DEBOUNCE_MS)
      val result = withContext(Dispatchers.IO) {
        runCatching { SignalStore.payments.breezSdkWrapperLatest().isUsernameAvailable(username) }.getOrNull()
      } ?: return@launch // network/SDK error: leave confirm disabled, no status shown

      available = result
      status.visibility = View.VISIBLE
      if (result) {
        status.text = "✓ " + getString(R.string.EditLightningUsernameFragment__username_available)
        status.setTextColor(AVAILABLE_COLOR)
        confirm.isEnabled = true
      } else {
        status.text = "✗ " + getString(R.string.EditLightningUsernameFragment__username_unavailable)
        status.setTextColor(UNAVAILABLE_COLOR)
        confirm.isEnabled = false
      }
    }
  }

  private fun onConfirm(username: String, confirm: MaterialButton) {
    if (!available || username.isBlank()) return

    confirm.isEnabled = false
    viewLifecycleOwner.lifecycleScope.launch {
      val success = withContext(Dispatchers.IO) {
        runCatching { SignalStore.payments.breezSdkWrapperLatest().registerUsername(username) }
          .onFailure { Log.w(TAG, "Failed to register username", it) }
          .isSuccess
      }
      if (success) {
        // Tell the Add Funds screen to refresh its address/QR (mirrors iOS walletAddressDidLoad).
        parentFragmentManager.setFragmentResult(PaymentsAddMoneyFragment.REQUEST_KEY_USERNAME_CHANGED, Bundle())
        NavHostFragment.findNavController(this@EditLightningUsernameFragment).popBackStack()
      } else {
        confirm.isEnabled = true
        Toast.makeText(requireContext(), R.string.EditLightningUsernameFragment__could_not_register, Toast.LENGTH_SHORT).show()
      }
    }
  }
}
