/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.welcome

import android.content.DialogInterface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment

/**
 * Restore flow starting bottom sheet that allows user to progress through quick restore or manual restore flows
 * from the Welcome screen.
 */
class RestoreWelcomeBottomSheet : ComposeBottomSheetDialogFragment() {

  private var result: WelcomeUserSelection = WelcomeUserSelection.CONTINUE

  companion object {
    const val REQUEST_KEY = "RestoreWelcomeBottomSheet"
  }

  @Composable
  override fun SheetContent() {
    Sheet(
      showLinkDevice = BuildConfig.LINK_DEVICE_UX_ENABLED,
      onMigrateFromSignal = {
        result = WelcomeUserSelection.MIGRATE_FROM_SIGNAL
        dismissAllowingStateLoss()
      },
      onHasOldPhone = {
        result = WelcomeUserSelection.RESTORE_WITH_OLD_PHONE
        dismissAllowingStateLoss()
      },
      onLinkDevice = {
        result = WelcomeUserSelection.LINK
        dismissAllowingStateLoss()
      }
    )
  }

  override fun onDismiss(dialog: DialogInterface) {
    setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to result))

    super.onDismiss(dialog)
  }
}

@Composable
private fun Sheet(
  showLinkDevice: Boolean = true,
  onMigrateFromSignal: () -> Unit = {},
  onHasOldPhone: () -> Unit = {},
  onLinkDevice: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
      .padding(bottom = 54.dp)
  ) {
    BottomSheets.Handle()

    Spacer(modifier = Modifier.size(26.dp))

    RestoreActionRow(
      icon = painterResource(R.drawable.welcome_signal_app_icon),
      iconTint = Color.Unspecified,
      title = stringResource(R.string.WelcomeFragment_restore_action_migrate_from_signal),
      subtitle = stringResource(R.string.WelcomeFragment_restore_action_migrate_from_signal_subtitle),
      onRowClick = onMigrateFromSignal
    )

    RestoreActionRow(
      icon = painterResource(R.drawable.welcome_transfer_phone_44),
      title = stringResource(R.string.WelcomeFragment_restore_action_set_up_new_phone),
      subtitle = stringResource(R.string.WelcomeFragment_restore_action_set_up_new_phone_subtitle),
      onRowClick = onHasOldPhone
    )

    if (showLinkDevice) {
      RestoreActionRow(
        icon = painterResource(R.drawable.welcome_link_44),
        title = stringResource(R.string.WelcomeFragment_restore_action_link_device),
        subtitle = stringResource(R.string.WelcomeFragment_restore_action_link_device_subtitle),
        onRowClick = onLinkDevice
      )
    }
  }
}

@Composable
@DayNightPreviews
private fun SheetPreview() {
  Previews.BottomSheetContentPreview {
    Sheet()
  }
}

@Composable
fun RestoreActionRow(
  icon: Painter,
  title: String,
  subtitle: String,
  iconTint: Color = MaterialTheme.colorScheme.primary,
  onRowClick: () -> Unit = {}
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .horizontalGutters()
      .padding(vertical = 8.dp)
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(MaterialTheme.colorScheme.background)
      .clickable(enabled = true, onClick = onRowClick)
      .padding(horizontal = 24.dp, vertical = 16.dp)
  ) {
    Icon(
      painter = icon,
      tint = iconTint,
      contentDescription = null,
      modifier = Modifier.size(44.dp)
    )

    Column(
      modifier = Modifier
        .padding(start = 16.dp)
        .weight(1f)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge
      )

      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Icon(
      painter = painterResource(R.drawable.symbol_chevron_right_24),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      contentDescription = null,
      modifier = Modifier.padding(start = 8.dp)
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreActionRowPreview() {
  Previews.Preview {
    RestoreActionRow(
      icon = painterResource(R.drawable.welcome_transfer_phone_44),
      title = stringResource(R.string.WelcomeFragment_restore_action_set_up_new_phone),
      subtitle = stringResource(R.string.WelcomeFragment_restore_action_set_up_new_phone_subtitle)
    )
  }
}
