/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme

/**
 * Hosts the post-registration payments onboarding flow (intro → add funds → deposit received →
 * setup complete). Every exit routes to [MainActivity] so the user can never get stranded.
 * Mirrors iOS PaymentsOnboardingCoordinator.
 */
class PaymentsOnboardingActivity : BaseActivity() {

  private val dynamicTheme = DynamicNoActionBarTheme()

  override fun onCreate(savedInstanceState: Bundle?) {
    dynamicTheme.onCreate(this)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_payments_onboarding)
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
    @JvmStatic
    fun createIntent(context: Context): Intent = Intent(context, PaymentsOnboardingActivity::class.java)
  }
}
