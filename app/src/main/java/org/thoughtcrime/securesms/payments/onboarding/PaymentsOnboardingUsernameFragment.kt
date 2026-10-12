/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments.onboarding

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.button.MaterialButton
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * First step of payments onboarding: settle the user's Signal/Radar username, which is how other
 * people find them. Mirrors iOS UsernameOnboardingViewController, which its onboarding coordinator
 * presents before the payments intro.
 *
 * The screen adapts to what is already set. With a username it reads as a confirmation and offers
 * to edit; without one it invites the user to pick. Either way it is skippable — a username is not
 * required to send or receive, so blocking onboarding on it would be wrong.
 *
 * Editing delegates to the existing [org.thoughtcrime.securesms.profiles.manage.UsernameEditFragment]
 * rather than reimplementing availability checks and validation. That screen pops when it is done,
 * which lands back here, so [onResume] re-reads the state.
 */
class PaymentsOnboardingUsernameFragment : LoggingFragment(R.layout.fragment_payments_onboarding_username) {

  private lateinit var subtitle: TextView
  private lateinit var usernameView: TextView
  private lateinit var primary: MaterialButton

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    subtitle = view.findViewById(R.id.onboarding_username_subtitle)
    usernameView = view.findViewById(R.id.onboarding_username_value)
    primary = view.findViewById(R.id.onboarding_username_primary)

    view.findViewById<MaterialButton>(R.id.onboarding_username_skip).setOnClickListener { continueToIntro() }
  }

  override fun onResume() {
    super.onResume()
    render()
  }

  private fun render() {
    val username = SignalStore.account.username

    if (username.isNullOrBlank()) {
      subtitle.setText(R.string.PaymentsOnboarding__username_subtitle)
      usernameView.visibility = View.GONE
      primary.setText(R.string.PaymentsOnboarding__username_set)
      primary.setOnClickListener { editUsername() }
    } else {
      subtitle.setText(R.string.PaymentsOnboarding__username_existing_subtitle)
      usernameView.visibility = View.VISIBLE
      usernameView.text = username
      primary.setText(R.string.PaymentsOnboarding__username_confirm)
      primary.setOnClickListener { continueToIntro() }
    }
  }

  private fun editUsername() {
    NavHostFragment.findNavController(this).navigate(R.id.action_username_to_editUsername)
  }

  private fun continueToIntro() {
    NavHostFragment.findNavController(this).navigate(R.id.action_username_to_intro)
  }
}
