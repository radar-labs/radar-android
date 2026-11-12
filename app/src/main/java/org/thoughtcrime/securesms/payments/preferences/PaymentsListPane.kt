/*
 * Copyright 2026 Radar Chat, Inc.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.preferences

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState

/**
 * Radar: list pane content for the Payments tab. Hosts the payments_preferences nav graph through
 * [PaymentsNavHostWrapperFragment]. The shared MainToolbar is hidden on this tab (PaymentsHomeFragment
 * has its own toolbar), so the pane pads itself below the status bar once, for every screen in the graph.
 */
@Composable
fun PaymentsListPane(modifier: Modifier = Modifier) {
  AndroidFragment(
    clazz = PaymentsNavHostWrapperFragment::class.java,
    fragmentState = rememberFragmentState(),
    modifier = modifier.statusBarsPadding()
  )
}
