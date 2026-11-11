/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.preferences

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.button.MaterialButton
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.payments.LightningLogger

/** Shows the buffered Breez/Lightning SDK logs for troubleshooting. Mirrors iOS LightningLogsViewController. */
class LightningLogsFragment : LoggingFragment(R.layout.fragment_lightning_logs) {

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar = view.findViewById<Toolbar>(R.id.lightning_logs_toolbar)
    val text = view.findViewById<TextView>(R.id.lightning_logs_text)
    val copy = view.findViewById<MaterialButton>(R.id.lightning_logs_copy)

    toolbar.setNavigationOnClickListener { NavHostFragment.findNavController(this).popBackStack() }

    val logs = LightningLogger.currentText()
    text.text = logs.ifEmpty { getString(R.string.LightningLogs__no_logs) }

    copy.setOnClickListener {
      val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      clipboard.setPrimaryClip(ClipData.newPlainText("Lightning logs", LightningLogger.currentText()))
      Toast.makeText(requireContext(), R.string.LightningLogs__copied, Toast.LENGTH_SHORT).show()
    }
  }
}
