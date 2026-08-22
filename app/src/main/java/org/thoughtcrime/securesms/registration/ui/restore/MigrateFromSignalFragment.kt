/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.phonenumber.EnterPhoneNumberMode
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Explains what the user has to do in Signal before their account and messages can be brought over,
 * then hands off to the Signal Backups restore flow.
 *
 * This screen used to attempt a same-device account transfer: it opened a provisioning socket, minted
 * a `sgnl://rereg` URL and handed that to the installed Signal app by intent. That cannot work.
 * Signal's deep-link handler (CommunicationActions.handlePotentialQuickRestoreUrl) reads only the
 * scheme and host, shows a confirmation dialog, and then opens its *camera* — the `uuid` and
 * `pub_key` in the URL are never read. The only code in Signal that consumes them filters the live
 * camera QR stream, so the payload can physically only arrive by pointing one device at another. On
 * a single device the user just landed on a scanner with nothing to scan.
 *
 * Restoring from the user's Signal backup does work on one device, needs no cooperation from the
 * Signal app, and reuses the flow that already exists behind "I don't have my old phone". The
 * two-device transfer is untouched and still reachable via "Set up my new phone".
 */
class MigrateFromSignalFragment : ComposeFragment() {

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()

  @Composable
  override fun FragmentContent() {
    MigrateFromSignalScreen(
      onContinue = ::onContinue,
      onCancel = { findNavController().popBackStack() }
    )
  }

  private fun onContinue() {
    // hasOldDevice must be false here, and not just cosmetically: isWantingManualRemoteRestore is
    // defined as `fromRemote && !hasOldDevice`, and RegistrationViewModel gates the
    // post-registration remote restore on it. Getting this wrong would walk the user through
    // entering their Recovery Key and then quietly restore nothing.
    sharedViewModel.intendToRestore(hasOldDevice = false, fromRemote = true)
    findNavController().safeNavigate(
      MigrateFromSignalFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.COLLECT_FOR_MANUAL_SIGNAL_BACKUPS_RESTORE)
    )
  }
}

@Composable
private fun MigrateFromSignalScreen(
  onContinue: () -> Unit = {},
  onCancel: () -> Unit = {}
) {
  RegistrationScreen(
    title = stringResource(R.string.MigrateFromSignal_title),
    subtitle = stringResource(R.string.MigrateFromSignal_subtitle),
    bottomContent = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .align(Alignment.Center)
          .fillMaxWidth()
      ) {
        Buttons.LargeTonal(
          onClick = onContinue,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(text = stringResource(R.string.MigrateFromSignal_continue))
        }

        TextButton(onClick = onCancel) {
          Text(text = stringResource(android.R.string.cancel))
        }
      }
    }
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth()
    ) {
      Image(
        painter = painterResource(R.drawable.welcome_signal_app_icon),
        contentDescription = null,
        modifier = Modifier
          .padding(top = 8.dp, bottom = 24.dp)
          .size(64.dp)
      )

      Column(modifier = Modifier.widthIn(max = 320.dp)) {
        MigrateInstructionRow(
          icon = painterResource(R.drawable.symbol_settings_android_24),
          instruction = stringResource(R.string.MigrateFromSignal_instruction_1)
        )

        MigrateInstructionRow(
          icon = painterResource(R.drawable.symbol_key_24),
          instruction = stringResource(R.string.MigrateFromSignal_instruction_2)
        )

        MigrateInstructionRow(
          icon = painterResource(R.drawable.symbol_arrow_right_24),
          instruction = stringResource(R.string.MigrateFromSignal_instruction_3)
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Registering Radar on this number takes the number over on Signal's servers, which signs the
      // Signal app out. That is inherent to any migration — one number has one primary device — but
      // it cannot be undone by backing out later, so it is said before the user commits.
      Text(
        text = stringResource(R.string.MigrateFromSignal_signal_will_be_signed_out),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 320.dp)
      )
    }
  }
}

@Composable
private fun MigrateInstructionRow(
  icon: Painter,
  instruction: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp)
  ) {
    Icon(
      painter = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary
    )

    Spacer(modifier = Modifier.width(16.dp))

    Text(
      text = instruction,
      style = MaterialTheme.typography.bodyLarge
    )
  }
}

@DayNightPreviews
@Composable
private fun MigrateFromSignalScreenPreview() {
  Previews.Preview {
    MigrateFromSignalScreen()
  }
}
