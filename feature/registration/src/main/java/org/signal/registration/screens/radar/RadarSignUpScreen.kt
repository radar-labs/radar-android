/*
 * Copyright 2026 Radar Chat, Inc.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.radar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.dismissWithAnimation
import org.signal.registration.R
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.welcome.RadarRestoreOptions
import org.signal.registration.screens.welcome.WelcomeScreenEvents

/**
 * Radar: the "Sign up" screen reached from "I'm new to Radar" on the welcome screen (as in Radar 1.0.9).
 * Lets the user create a brand new account, or bring an existing Signal account over (the same options
 * as "I already have an account").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarSignUpScreen(
  onEvent: (WelcomeScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  var showRestoreOptions by remember { mutableStateOf(false) }

  RegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    content = {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier.widthIn(max = 360.dp)
        ) {
          Icon(
            painter = painterResource(R.drawable.symbol_person_fill_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp)
          )

          Spacer(modifier = Modifier.height(40.dp))

          Text(
            text = stringResource(R.string.RegistrationActivity_sign_up),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
          )

          Spacer(modifier = Modifier.height(22.dp))

          Text(
            text = rememberDescription(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
          )
        }
      }
    },
    footer = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 32.dp)
      ) {
        Buttons.LargePrimary(
          onClick = { onEvent(WelcomeScreenEvents.Continue) },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(stringResource(R.string.RegistrationActivity_create_new_account))
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { showRestoreOptions = true }) {
          Text(stringResource(R.string.RegistrationActivity_use_my_signal_account))
        }

        Spacer(modifier = Modifier.height(40.dp))
      }
    }
  )

  if (showRestoreOptions) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    BottomSheets.BottomSheet(
      onDismissRequest = { sheetState.dismissWithAnimation(scope, onComplete = { showRestoreOptions = false }) },
      sheetState = sheetState
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 26.dp, bottom = 54.dp)
      ) {
        RadarRestoreOptions(
          onOptionClick = { event ->
            sheetState.dismissWithAnimation(scope) {
              showRestoreOptions = false
              onEvent(event)
            }
          }
        )
      }
    }
  }
}

/** The body copy, with "Radar is based on Signal" in the brand accent colour. */
@Composable
private fun rememberDescription() = run {
  val accent = stringResource(R.string.RegistrationActivity_radar_is_based_on_signal)
  val first = stringResource(R.string.RegistrationActivity_create_a_new_account_or_restore)
  val second = stringResource(R.string.RegistrationActivity_and_compatible_with_it, accent)
  val accentColor = MaterialTheme.colorScheme.primary
  remember(accent, first, second, accentColor) {
    buildAnnotatedString {
      append(first)
      append("\n\n")
      val start = second.indexOf(accent)
      if (start >= 0) {
        append(second.substring(0, start))
        withStyle(SpanStyle(color = accentColor)) { append(accent) }
        append(second.substring(start + accent.length))
      } else {
        append(second)
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun RadarSignUpScreenPreview() {
  Previews.Preview {
    RadarSignUpScreen(onEvent = {})
  }
}
