/*
 * Copyright 2026 Radar Chat, Inc.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.radar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.registration.R
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.test.TestTags

/**
 * Radar: explains what the user has to do in Signal before their account and messages can be brought
 * over, then hands off to the Signal Secure Backups restore flow (as in Radar 1.0.9). Restoring from the
 * user's Signal backup works on a single phone and needs no cooperation from the Signal app; the
 * two-phone transfer stays reachable through "Set up my new phone".
 */
@Composable
fun MigrateFromSignalScreen(
  onContinue: () -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier
) {
  RegistrationScaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.MIGRATE_FROM_SIGNAL_SCREEN),
    content = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp)
      ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
          text = stringResource(R.string.MigrateFromSignal_title),
          style = MaterialTheme.typography.headlineMedium,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = stringResource(R.string.MigrateFromSignal_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.widthIn(max = 360.dp)
        )

        Image(
          painter = painterResource(R.drawable.welcome_signal_app_icon),
          contentDescription = null,
          modifier = Modifier
            .padding(top = 32.dp, bottom = 24.dp)
            .size(64.dp)
        )

        Column(modifier = Modifier.widthIn(max = 320.dp)) {
          InstructionRow(SignalIcons.Settings.painter, stringResource(R.string.MigrateFromSignal_instruction_1))
          InstructionRow(SignalIcons.Key.painter, stringResource(R.string.MigrateFromSignal_instruction_2))
          InstructionRow(SignalIcons.ArrowEnd.painter, stringResource(R.string.MigrateFromSignal_instruction_3))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Registering Radar on this number takes the number over on Signal's servers, which signs the Signal
        // app out. That is inherent to any migration, but it cannot be undone by backing out later, so it is
        // said before the user commits.
        Text(
          text = stringResource(R.string.MigrateFromSignal_signal_will_be_signed_out),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.widthIn(max = 320.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
      }
    },
    footer = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 32.dp)
      ) {
        Buttons.LargeTonal(
          onClick = onContinue,
          modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.MIGRATE_FROM_SIGNAL_CONTINUE_BUTTON)
        ) {
          Text(stringResource(R.string.MigrateFromSignal_continue))
        }

        TextButton(onClick = onCancel) {
          Text(stringResource(android.R.string.cancel))
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  )
}

@Composable
private fun InstructionRow(icon: Painter, instruction: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp)
  ) {
    Icon(
      painter = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(24.dp)
    )

    Text(
      text = instruction,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(start = 16.dp)
    )
  }
}

@DayNightPreviews
@Composable
private fun MigrateFromSignalScreenPreview() {
  Previews.Preview {
    MigrateFromSignalScreen(onContinue = {}, onCancel = {})
  }
}
