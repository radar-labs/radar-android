/*
 * Copyright 2026 Radar Chat, Inc.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.radar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.dismissWithAnimation
import org.signal.registration.R
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.welcome.RadarRestoreOptions
import org.signal.registration.screens.welcome.WelcomeScreenEvents
import org.signal.registration.test.TestTags

/**
 * Radar: the "Sign up" screen reached from "I'm new to Radar" on the welcome screen (as in Radar 1.0.9).
 * Lets the user create a brand new account, or bring an existing Signal account over (the same options
 * as "I already have an account").
 *
 * Looks like 1.0.9's fragment_registration_sign_up.xml: the icon, title and description are centred on the
 * whole screen, the two buttons sit at the bottom, and the icon, accent text and buttons are Radar orange.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarSignUpScreen(
  onEvent: (WelcomeScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  var showRestoreOptions by remember { mutableStateOf(false) }
  val radarOrange = colorResource(R.color.radar_orange)

  RegistrationScaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.RADAR_SIGN_UP_SCREEN),
    content = {
      CenteredHeroWithFooter(
        modifier = Modifier.fillMaxSize(),
        hero = {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 32.dp)
          ) {
            Icon(
              painter = painterResource(R.drawable.symbol_person_fill_24),
              contentDescription = null,
              tint = radarOrange,
              modifier = Modifier.size(width = 96.dp, height = 100.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            Text(
              text = stringResource(R.string.RegistrationActivity_sign_up),
              color = MaterialTheme.colorScheme.onBackground,
              fontSize = 28.sp,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            Text(
              text = rememberDescription(radarOrange),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 17.sp,
              lineHeight = 24.sp,
              textAlign = TextAlign.Center
            )
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
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = radarOrange,
                contentColor = colorResource(R.color.radar_on_orange)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(TestTags.RADAR_SIGN_UP_CREATE_ACCOUNT_BUTTON)
            ) {
              Text(
                text = stringResource(R.string.RegistrationActivity_create_new_account),
                fontWeight = FontWeight.Bold
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
              onClick = { showRestoreOptions = true },
              colors = ButtonDefaults.textButtonColors(contentColor = radarOrange)
            ) {
              Text(stringResource(R.string.RegistrationActivity_use_my_signal_account))
            }

            Spacer(modifier = Modifier.height(40.dp))
          }
        }
      )
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

/**
 * Places [footer] at the bottom and [hero] centred on the whole area, as 1.0.9's ConstraintLayout did. When the
 * hero would run into the footer (short screens, landscape), it moves up instead, and it scrolls if it is taller
 * than the space above the footer.
 */
@Composable
private fun CenteredHeroWithFooter(
  hero: @Composable () -> Unit,
  footer: @Composable () -> Unit,
  modifier: Modifier = Modifier
) {
  Layout(
    contents = listOf(hero, footer),
    modifier = modifier
  ) { (heroMeasurables, footerMeasurables), constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val loose = constraints.copy(minWidth = 0, minHeight = 0)

    val footerPlaceables = footerMeasurables.map { it.measure(loose) }
    val footerHeight = footerPlaceables.maxOfOrNull { it.height } ?: 0

    val heroPlaceables = heroMeasurables.map { it.measure(loose.copy(maxHeight = (height - footerHeight).coerceAtLeast(0))) }
    val heroHeight = heroPlaceables.maxOfOrNull { it.height } ?: 0
    val heroTop = ((height - heroHeight) / 2)
      .coerceAtMost(height - footerHeight - heroHeight)
      .coerceAtLeast(0)

    layout(width, height) {
      heroPlaceables.forEach { it.placeRelative((width - it.width) / 2, heroTop) }
      footerPlaceables.forEach { it.placeRelative((width - it.width) / 2, height - footerHeight) }
    }
  }
}

/** The body copy, with "Radar is based on Signal" in the brand accent colour. */
@Composable
private fun rememberDescription(accentColor: Color) = run {
  val accent = stringResource(R.string.RegistrationActivity_radar_is_based_on_signal)
  val first = stringResource(R.string.RegistrationActivity_create_a_new_account_or_restore)
  val second = stringResource(R.string.RegistrationActivity_and_compatible_with_it, accent)
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
