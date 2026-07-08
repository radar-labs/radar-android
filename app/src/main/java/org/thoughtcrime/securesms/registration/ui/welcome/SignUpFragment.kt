/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.welcome

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.util.getSerializableCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationSignUpBinding
import org.thoughtcrime.securesms.registration.fragments.WelcomePermissions
import org.thoughtcrime.securesms.registration.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.permissions.GrantPermissionsFragment
import org.thoughtcrime.securesms.registration.ui.phonenumber.EnterPhoneNumberMode
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * "Sign up" screen reached from the welcome screen's "I'm new to Radar" button. Lets the user either
 * create a brand new account or restore/transfer an existing Signal account.
 */
class SignUpFragment : LoggingFragment(R.layout.fragment_registration_sign_up) {
  companion object {
    private val TAG = Log.tag(SignUpFragment::class.java)
  }

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val binding: FragmentRegistrationSignUpBinding by ViewBinderDelegate(FragmentRegistrationSignUpBinding::bind)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.description.text = buildDescription()

    binding.createAccountButton.setOnClickListener { onCreateAccountClicked() }
    binding.useSignalAccountButton.setOnClickListener { onUseSignalAccountClicked() }

    childFragmentManager.setFragmentResultListener(RestoreWelcomeBottomSheet.REQUEST_KEY, viewLifecycleOwner) { requestKey, bundle ->
      if (requestKey == RestoreWelcomeBottomSheet.REQUEST_KEY) {
        when (val userSelection = bundle.getSerializableCompat(RestoreWelcomeBottomSheet.REQUEST_KEY, WelcomeUserSelection::class.java)) {
          WelcomeUserSelection.RESTORE_WITH_OLD_PHONE,
          WelcomeUserSelection.RESTORE_WITH_NO_PHONE,
          WelcomeUserSelection.MIGRATE_FROM_SIGNAL -> afterRestoreOrTransferClicked(userSelection)
          WelcomeUserSelection.LINK -> onLinkDeviceClicked()
          else -> Unit
        }
      }
    }

    parentFragmentManager.setFragmentResultListener(GrantPermissionsFragment.REQUEST_KEY, viewLifecycleOwner) { requestKey, bundle ->
      if (requestKey == GrantPermissionsFragment.REQUEST_KEY) {
        when (val userSelection = bundle.getSerializableCompat(GrantPermissionsFragment.REQUEST_KEY, WelcomeUserSelection::class.java)) {
          WelcomeUserSelection.RESTORE_WITH_OLD_PHONE,
          WelcomeUserSelection.RESTORE_WITH_NO_PHONE,
          WelcomeUserSelection.MIGRATE_FROM_SIGNAL -> navigateToNextScreenViaRestore(userSelection)
          WelcomeUserSelection.CONTINUE -> navigateToNextScreenViaContinue()
          WelcomeUserSelection.LINK -> navigateToLinkDevice()
          null -> Unit
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    sharedViewModel.resetRestoreDecision()
  }

  /** Builds the body copy, highlighting the "Radar is based on Signal" phrase in the brand accent color. */
  private fun buildDescription(): CharSequence {
    val accent = getString(R.string.RegistrationActivity_radar_is_based_on_signal)
    val secondParagraph = getString(R.string.RegistrationActivity_and_compatible_with_it, accent)

    val body = SpannableStringBuilder()
    body.append(getString(R.string.RegistrationActivity_create_a_new_account_or_restore))
    body.append("\n\n")

    val accentStart = body.length + secondParagraph.indexOf(accent)
    body.append(secondParagraph)

    if (accentStart >= body.length - secondParagraph.length) {
      body.setSpan(
        ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.radar_accent_blue)),
        accentStart,
        accentStart + accent.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
      )
    }

    return body
  }

  private fun onCreateAccountClicked() {
    if (!hasAllPermissions()) {
      findNavController().safeNavigate(SignUpFragmentDirections.actionSignUpFragmentToGrantPermissionsFragment(WelcomeUserSelection.CONTINUE))
    } else {
      navigateToNextScreenViaContinue()
    }
  }

  private fun navigateToNextScreenViaContinue() {
    sharedViewModel.maybePrefillE164(requireContext())
    findNavController().safeNavigate(SignUpFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.NORMAL))
  }

  private fun onUseSignalAccountClicked() {
    RestoreWelcomeBottomSheet().show(childFragmentManager, null)
  }

  private fun onLinkDeviceClicked() {
    if (!hasAllPermissions()) {
      findNavController().safeNavigate(SignUpFragmentDirections.actionSignUpFragmentToGrantPermissionsFragment(WelcomeUserSelection.LINK))
    } else {
      navigateToLinkDevice()
    }
  }

  private fun navigateToLinkDevice() {
    findNavController().safeNavigate(SignUpFragmentDirections.goToLinkViaQr())
  }

  private fun afterRestoreOrTransferClicked(userSelection: WelcomeUserSelection) {
    if (!hasAllPermissions()) {
      findNavController().safeNavigate(SignUpFragmentDirections.actionSignUpFragmentToGrantPermissionsFragment(userSelection))
    } else {
      navigateToNextScreenViaRestore(userSelection)
    }
  }

  private fun navigateToNextScreenViaRestore(userSelection: WelcomeUserSelection) {
    sharedViewModel.maybePrefillE164(requireContext())
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PERMISSIONS_GRANTED)

    when (userSelection) {
      WelcomeUserSelection.LINK,
      WelcomeUserSelection.CONTINUE -> throw IllegalArgumentException()
      WelcomeUserSelection.RESTORE_WITH_OLD_PHONE -> {
        sharedViewModel.intendToRestore(hasOldDevice = true, fromRemote = true)
        findNavController().safeNavigate(SignUpFragmentDirections.goToRestoreViaQr())
      }
      WelcomeUserSelection.RESTORE_WITH_NO_PHONE -> {
        sharedViewModel.intendToRestore(hasOldDevice = false, fromRemote = true)
        findNavController().safeNavigate(SignUpFragmentDirections.goToSelectRestoreMethod(userSelection))
      }
      WelcomeUserSelection.MIGRATE_FROM_SIGNAL -> {
        sharedViewModel.intendToRestore(hasOldDevice = true, fromRemote = true)
        findNavController().safeNavigate(SignUpFragmentDirections.goToMigrateFromSignal())
      }
    }
  }

  private fun hasAllPermissions(): Boolean {
    val isUserSelectionRequired = BackupUtil.isUserSelectionRequired(requireContext())
    return WelcomePermissions.getWelcomePermissions(isUserSelectionRequired).all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }
  }
}
