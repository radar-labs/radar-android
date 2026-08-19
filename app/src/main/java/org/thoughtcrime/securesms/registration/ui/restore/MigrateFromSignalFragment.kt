/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.util.logging.Log
import org.signal.registration.proto.RegistrationProvisionMessage
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.registration.ui.welcome.WelcomeUserSelection
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Same-device migration from the official Signal app. Instead of displaying a QR code for another
 * device to scan (see [RestoreViaQrFragment]), this hands the registration provisioning URI
 * directly to the Signal app installed on this device and waits for the account transfer to come
 * back over the provisioning socket.
 */
class MigrateFromSignalFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(MigrateFromSignalFragment::class.java)

    private const val SIGNAL_PACKAGE_NAME = "org.thoughtcrime.securesms"
  }

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val viewModel: RestoreViaQrViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
        viewModel
          .state
          .mapNotNull { it.provisioningMessage }
          .distinctUntilChanged()
          .collect { message ->
            if (message.platform == RegistrationProvisionMessage.Platform.ANDROID || message.tier != null) {
              sharedViewModel.registerWithBackupKey(requireContext(), message.accountEntropyPool, message.e164, message.pin, message.aciIdentityKeyPair, message.pniIdentityKeyPair)
            } else {
              sharedViewModel.registrationProvisioningMessage = message
              findNavController().safeNavigate(MigrateFromSignalFragmentDirections.goToNoBackupToRestore())
            }
          }
      }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
        viewModel
          .state
          .mapNotNull { it.registerAccountResult }
          .filter { it !is RegisterAccountResult.Success }
          .distinctUntilChanged()
          .collect { result ->
            when (result) {
              is RegisterAccountResult.AttemptsExhausted -> {
                findNavController().safeNavigate(MigrateFromSignalFragmentDirections.goToAccountLocked())
              }

              else -> Unit
            }
          }
      }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
        sharedViewModel
          .state
          .map { it.registerAccountError }
          .filterNotNull()
          .collect {
            sharedViewModel.registerAccountErrorShown()
            viewModel.handleRegistrationFailure(it)
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()
    var showSignalNotInstalled by remember { mutableStateOf(false) }

    MigrateFromSignalScreen(
      state = state,
      onOpenSignal = { url ->
        if (!openSignalApp(url)) {
          showSignalNotInstalled = true
        }
      },
      onRetry = viewModel::restart,
      onRegistrationErrorDismiss = viewModel::clearRegistrationError,
      onNoOldPhone = ::onNoOldPhone,
      onCancel = { findNavController().popBackStack() }
    )

    if (showSignalNotInstalled) {
      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.MigrateFromSignal_signal_not_installed),
        onDismiss = { showSignalNotInstalled = false },
        dismiss = stringResource(android.R.string.ok)
      )
    }
  }

  private fun openSignalApp(provisioningUrl: String): Boolean {
    return try {
      startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(provisioningUrl)).apply {
          setPackage(SIGNAL_PACKAGE_NAME)
        }
      )
      true
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "Signal app not found on this device, unable to hand off migration", e)
      false
    }
  }

  private fun onNoOldPhone() {
    sharedViewModel.intendToRestore(hasOldDevice = false, fromRemote = true)
    findNavController().safeNavigate(MigrateFromSignalFragmentDirections.goToSelectRestoreMethod(WelcomeUserSelection.RESTORE_WITH_NO_PHONE))
  }
}

@Composable
private fun MigrateFromSignalScreen(
  state: RestoreViaQrViewModel.RestoreViaQrState,
  onOpenSignal: (String) -> Unit = {},
  onRetry: () -> Unit = {},
  onRegistrationErrorDismiss: () -> Unit = {},
  onNoOldPhone: () -> Unit = {},
  onCancel: () -> Unit = {}
) {
  RegistrationScreen(
    title = stringResource(R.string.MigrateFromSignal_title),
    subtitle = stringResource(R.string.MigrateFromSignal_subtitle),
    bottomContent = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .align(Alignment.Center)
          .fillMaxWidth()
      ) {
        Buttons.LargeTonal(
          onClick = { state.provisioningUrl?.let(onOpenSignal) },
          enabled = state.provisioningUrl != null,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(text = stringResource(R.string.MigrateFromSignal_open_signal))
        }

        TextButton(onClick = onNoOldPhone) {
          Text(text = stringResource(R.string.WelcomeFragment_restore_action_i_dont_have_my_old_phone))
        }

        TextButton(onClick = onCancel) {
          Text(text = stringResource(android.R.string.cancel))
        }
      }
    }
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth()
    ) {
      Image(
        painter = painterResource(R.drawable.welcome_signal_app_icon),
        contentDescription = null,
        modifier = Modifier
          .padding(top = 8.dp, bottom = 20.dp)
          .size(64.dp)
      )

      // Message history comes across from the user's Signal backup, not from the live handoff, so
      // say so before they leave for Signal — afterwards is too late, and the only alternative is
      // the "nothing to restore" dead end further down this flow. Mirrors the disclaimer iOS shows
      // on its own migrate path (b4f557f2be), adapted to the fact that Android hands off live.
      Column(
        modifier = Modifier
          .widthIn(max = 320.dp)
          .padding(bottom = 24.dp)
      ) {
        Text(
          text = stringResource(R.string.MigrateFromSignal_before_you_start),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = stringResource(R.string.MigrateFromSignal_backup_step_1),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = stringResource(R.string.MigrateFromSignal_backup_step_2),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Column(modifier = Modifier.widthIn(max = 320.dp)) {
        MigrateInstructionRow(
          icon = painterResource(R.drawable.symbol_device_phone_24),
          instruction = stringResource(R.string.MigrateFromSignal_instruction_1)
        )

        MigrateInstructionRow(
          icon = painterResource(R.drawable.symbol_check_24),
          instruction = stringResource(R.string.MigrateFromSignal_instruction_2)
        )

        MigrateInstructionRow(
          icon = painterResource(R.drawable.symbol_arrow_right_24),
          instruction = stringResource(R.string.MigrateFromSignal_instruction_3)
        )
      }

      if (state.qrState is RestoreViaQrViewModel.QrState.Failed) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
          text = stringResource(R.string.MigrateFromSignal_unable_to_prepare),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Buttons.Small(onClick = onRetry) {
          Text(text = stringResource(R.string.RestoreViaQr_retry))
        }
      }
    }

    if (state.isRegistering) {
      Dialogs.IndeterminateProgressDialog()
    } else if (state.showRegistrationError) {
      val message = when (state.registerAccountResult) {
        is RegisterAccountResult.IncorrectRecoveryPassword -> stringResource(R.string.RestoreViaQr_registration_error)
        is RegisterAccountResult.RateLimited -> stringResource(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
        else -> stringResource(R.string.RegistrationActivity_error_connecting_to_service)
      }

      Dialogs.SimpleMessageDialog(
        message = message,
        onDismiss = onRegistrationErrorDismiss,
        dismiss = stringResource(android.R.string.ok)
      )
    }
  }
}

@Composable
private fun MigrateInstructionRow(
  icon: Painter,
  instruction: String
) {
  Row(
    modifier = Modifier.padding(vertical = 12.dp)
  ) {
    Icon(
      painter = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.width(16.dp))

    Text(
      text = instruction,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@DayNightPreviews
@Composable
private fun MigrateFromSignalScreenPreview() {
  Previews.Preview {
    MigrateFromSignalScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(provisioningUrl = "sgnl://rereg?uuid=asdf&pub_key=asdf")
    )
  }
}

@DayNightPreviews
@Composable
private fun MigrateFromSignalScreenLoadingPreview() {
  Previews.Preview {
    MigrateFromSignalScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState()
    )
  }
}

@DayNightPreviews
@Composable
private fun MigrateFromSignalScreenFailedPreview() {
  Previews.Preview {
    MigrateFromSignalScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(qrState = RestoreViaQrViewModel.QrState.Failed)
    )
  }
}
