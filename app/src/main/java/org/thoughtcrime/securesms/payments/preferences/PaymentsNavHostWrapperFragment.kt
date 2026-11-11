/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.preferences

import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R

/**
 * Hosts the `payments_preferences` navigation graph inside the bottom-nav Payments tab in
 * [org.thoughtcrime.securesms.MainActivity]. Pure container — all behavior lives in the
 * nav graph's destinations (`PaymentsHomeFragment` etc.).
 *
 * The existing [org.thoughtcrime.securesms.payments.preferences.PaymentsActivity] continues
 * to host the same graph for the Settings → Payments entry, so that surface is unchanged.
 *
 * Mirrors iOS `HomeTabBarController.walletViewController = PaymentsSettingsViewController(...)`.
 */
class PaymentsNavHostWrapperFragment : LoggingFragment(R.layout.payments_nav_host_wrapper)
