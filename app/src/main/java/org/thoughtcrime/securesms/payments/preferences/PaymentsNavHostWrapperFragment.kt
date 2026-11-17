/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.preferences

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import org.signal.core.ui.logging.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.SystemWindowInsetsSetter

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
class PaymentsNavHostWrapperFragment : LoggingFragment(R.layout.payments_nav_host_wrapper) {

  private var navController: NavController? = null
  private var backStackListener: NavController.OnDestinationChangedListener? = null

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // The tab already clears the system bars: PaymentsListPane pads the status bar and the bottom navigation sits
    // over the navigation bar. Screens of the graph that pad for them themselves (they do in PaymentsActivity)
    // would otherwise add a second status-bar gap and extra space above the bottom navigation.
    SystemWindowInsetsSetter.markInsetByHost(view, WindowInsetsCompat.Type.systemBars())

    // System back inside the tab. The embedded NavHostFragment isn't the activity's primary navigation fragment, so
    // its own back handling stays off and back on a pushed screen (e.g. payment settings from the gear) fell through
    // to MainActivity, which closed the app. Pop the payments graph instead. At the wallet itself nothing is left to
    // pop, so PaymentsHomeFragment's own back handling applies, as in 1.0.9.
    val controller = (childFragmentManager.findFragmentById(R.id.payments_nav_host) as NavHostFragment).navController
    val popPaymentsScreen = object : OnBackPressedCallback(false) {
      override fun handleOnBackPressed() {
        controller.popBackStack()
      }
    }
    val listener = NavController.OnDestinationChangedListener { changed, _, _ ->
      popPaymentsScreen.isEnabled = changed.previousBackStackEntry != null
    }

    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, popPaymentsScreen)
    controller.addOnDestinationChangedListener(listener)
    navController = controller
    backStackListener = listener
  }

  override fun onDestroyView() {
    backStackListener?.let { navController?.removeOnDestinationChangedListener(it) }
    backStackListener = null
    navController = null
    super.onDestroyView()
  }
}
