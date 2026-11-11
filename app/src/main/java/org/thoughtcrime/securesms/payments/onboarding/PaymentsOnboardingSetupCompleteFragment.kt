/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.onboarding

import android.os.Bundle
import android.view.View
import com.google.android.material.button.MaterialButton
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R

/** Final step of the payments onboarding flow. Mirrors iOS RegistrationSetupCompleteViewController. */
class PaymentsOnboardingSetupCompleteFragment : LoggingFragment(R.layout.fragment_payments_onboarding_setup_complete) {
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    view.findViewById<MaterialButton>(R.id.onboarding_setup_complete_continue).setOnClickListener {
      (requireActivity() as PaymentsOnboardingActivity).finishToMain()
    }
  }
}
