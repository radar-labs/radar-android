/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.onboarding

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.button.MaterialButton
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R

/** First step of the payments onboarding flow: introduces Radar payments. Mirrors iOS PaymentsIntroViewController. */
class PaymentsOnboardingIntroFragment : LoggingFragment(R.layout.fragment_payments_onboarding_intro) {
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    view.findViewById<MaterialButton>(R.id.onboarding_intro_continue).setOnClickListener {
      NavHostFragment.findNavController(this).navigate(R.id.action_intro_to_addFundsIntro)
    }
  }
}
