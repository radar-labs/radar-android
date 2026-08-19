/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.permissions

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

/**
 * Prominent disclosure shown before the app requests the contacts permission and uploads the
 * device address book to the contact discovery service. Required by the Google Play User Data
 * policy: it comprehensively describes that contacts are collected and transmitted to a server,
 * and requires an affirmative accept/decline choice before any collection begins.
 */
@Composable
fun ContactDiscoveryConsentScreen(
  onAllowClicked: () -> Unit = {},
  onNotNowClicked: () -> Unit = {}
) {
  RegistrationScreen(
    title = stringResource(id = R.string.ContactDiscoveryConsent__title),
    subtitle = stringResource(id = R.string.ContactDiscoveryConsent__body),
    bottomContent = {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        TextButton(
          modifier = Modifier.weight(weight = 1f, fill = false),
          onClick = onNotNowClicked
        ) {
          Text(
            text = stringResource(id = R.string.GrantPermissionsFragment__not_now)
          )
        }

        Spacer(modifier = Modifier.size(24.dp))

        Buttons.LargeTonal(
          onClick = onAllowClicked
        ) {
          Text(
            text = stringResource(id = R.string.ContactDiscoveryConsent__allow_access)
          )
        }
      }
    }
  ) {
    Image(
      imageVector = ImageVector.vectorResource(id = R.drawable.permission_contact),
      contentDescription = null,
      modifier = Modifier
        .padding(bottom = 24.dp)
        .size(48.dp)
    )

    Text(
      text = stringResource(id = R.string.ContactDiscoveryConsent__details),
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(bottom = 16.dp)
    )

    Text(
      text = stringResource(id = R.string.ContactDiscoveryConsent__optional),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@DayNightPreviews
@Composable
private fun ContactDiscoveryConsentScreenPreview() {
  Previews.Preview {
    ContactDiscoveryConsentScreen()
  }
}
