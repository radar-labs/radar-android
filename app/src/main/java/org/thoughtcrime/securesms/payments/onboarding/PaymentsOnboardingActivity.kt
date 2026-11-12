/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.SystemWindowInsetsSetter

/**
 * Hosts the post-registration payments onboarding flow (intro → add funds → deposit received →
 * setup complete). Every exit routes to [MainActivity] so the user can never get stranded.
 * Mirrors iOS PaymentsOnboardingCoordinator.
 */
class PaymentsOnboardingActivity : BaseActivity() {

  private val dynamicTheme = DynamicNoActionBarTheme()
  val viewModel: PaymentsOnboardingViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    dynamicTheme.onCreate(this)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_payments_onboarding)

    // The fitsSystemWindows nav host clears the system bars and the keyboard for every onboarding screen, so the
    // ones that also pad for them themselves (Add Funds) leave it to the host instead of adding a second gap.
    SystemWindowInsetsSetter.markInsetByHost(
      findViewById(R.id.payments_onboarding_nav_host),
      WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
    )

    // Open the wallet-readiness gate. When deferred behind a restore, this waits for the restored
    // seed before the Add Funds intro activates payments. The gate is idempotent across recreation.
    viewModel.beginWalletReadyGate(intent.getBooleanExtra(EXTRA_AWAIT_RESTORED_SEED, false))
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  /** Leaves onboarding for the main app. */
  fun finishToMain() {
    startActivity(MainActivity.clearTop(this))
    finish()
  }

  companion object {
    /** Whether onboarding was deferred behind a restore and should wait for the restored seed. */
    private const val EXTRA_AWAIT_RESTORED_SEED = "await_restored_seed"

    @JvmStatic
    fun createIntent(context: Context, awaitRestoredSeed: Boolean = false): Intent =
      Intent(context, PaymentsOnboardingActivity::class.java)
        .putExtra(EXTRA_AWAIT_RESTORED_SEED, awaitRestoredSeed)
  }
}
