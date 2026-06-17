package org.thoughtcrime.securesms.payments.preferences;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ContactFilterView;
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode;
import org.thoughtcrime.securesms.contacts.paged.ChatType;
import org.thoughtcrime.securesms.contacts.selection.ContactSelectionArguments;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.payments.CanNotSendPaymentDialog;
import org.thoughtcrime.securesms.payments.LightningAddress;
import org.thoughtcrime.securesms.payments.PaymentsAddressException;
import org.thoughtcrime.securesms.payments.preferences.model.PayeeParcelable;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.ui.findby.FindByActivity;
import org.thoughtcrime.securesms.recipients.ui.findby.FindByMode;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;


public class PaymentRecipientSelectionFragment extends LoggingFragment implements ContactSelectionListFragment.OnContactSelectedListener, ContactSelectionListFragment.ScrollCallback, ContactSelectionListFragment.FindByCallback {

  private static final String TAG = Log.tag(PaymentRecipientSelectionFragment.class);

  /** Loose "looks like a lightning address" gate (e.g. {@code name@radar.cash}) before we attempt to parse it. */
  private static final Pattern LIGHTNING_ADDRESS_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private Toolbar                      toolbar;
  private ContactFilterView            contactFilterView;
  private TextView                     sendToAddressRow;
  private ContactSelectionListFragment contactsFragment;

  private ActivityResultLauncher<FindByMode> findByUsernameLauncher;

  public PaymentRecipientSelectionFragment() {
    super(R.layout.payment_recipient_selection_fragment);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Registered here (not in onViewCreated) because ActivityResult launchers must be registered
    // before the fragment reaches STARTED. The found recipient is routed through the same funnel as
    // a contact tap.
    findByUsernameLauncher = registerForActivityResult(new FindByActivity.Contract(), recipientId -> {
      if (recipientId != null) {
        createPaymentOrShowWarningDialog(recipientId);
      }
    });
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    toolbar = view.findViewById(R.id.payment_recipient_selection_fragment_toolbar);
    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

    contactFilterView = view.findViewById(R.id.contact_filter_edit_text);
    contactFilterView.enablePaste();
    sendToAddressRow  = view.findViewById(R.id.payment_recipient_selection_fragment_send_to_address);

    Bundle arguments = new Bundle();
    arguments.putBoolean(ContactSelectionArguments.REFRESHABLE, false);
    arguments.putInt(ContactSelectionArguments.DISPLAY_MODE, ContactSelectionDisplayMode.FLAG_PUSH | ContactSelectionDisplayMode.FLAG_HIDE_NEW);
    arguments.putBoolean(ContactSelectionArguments.CAN_SELECT_SELF, false);
    arguments.putBoolean(ContactSelectionArguments.ENABLE_FIND_BY_USERNAME, true);

    Fragment child = getChildFragmentManager().findFragmentById(R.id.contact_selection_list_fragment_holder);
    if (child == null) {
      FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
      contactsFragment = new ContactSelectionListFragment();
      contactsFragment.setArguments(arguments);
      transaction.add(R.id.contact_selection_list_fragment_holder, contactsFragment);
      transaction.commit();
    } else {
      contactsFragment = (ContactSelectionListFragment) child;
    }

    initializeSearch();
  }

  private void initializeSearch() {
    contactFilterView.setOnFilterChangedListener(filter -> {
      contactsFragment.setQueryFilter(filter);
      updateSendToAddressRow(filter);
    });
  }

  /**
   * Mirrors iOS's inline "find by …" section: when the typed query looks like a lightning address
   * (e.g. {@code name@radar.cash}), surface a tappable row that sends directly to that address.
   */
  private void updateSendToAddressRow(@Nullable String filter) {
    String query = filter != null ? filter.trim() : "";

    if (LIGHTNING_ADDRESS_SHAPE.matcher(query).matches()) {
      sendToAddressRow.setText(getString(R.string.PaymentRecipientSelectionFragment__send_to_s, query));
      sendToAddressRow.setVisibility(View.VISIBLE);
      sendToAddressRow.setOnClickListener(v -> onSendToAddressClicked(query));
    } else {
      sendToAddressRow.setVisibility(View.GONE);
      sendToAddressRow.setOnClickListener(null);
    }
  }

  private void onSendToAddressClicked(@NonNull String query) {
    final LightningAddress address;
    try {
      address = LightningAddress.Companion.fromLightningAddress(query);
    } catch (LightningAddress.AddressException | RuntimeException e) {
      Log.w(TAG, "Address is not valid", e);
      new MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.PaymentsTransferFragment__invalid_address)
          .setMessage(R.string.PaymentsTransferFragment__check_the_wallet_address)
          .setPositiveButton(android.R.string.ok, null)
          .show();
      return;
    }

    LightningAddress ownAddress = AppDependencies.getPayments().getWallet().getLightningAddress();
    if (ownAddress != null && ownAddress.equals(address)) {
      new MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.PaymentsTransferFragment__invalid_address)
          .setMessage(R.string.PaymentsTransferFragment__you_cant_transfer_to_your_own_signal_wallet_address)
          .setPositiveButton(android.R.string.ok, null)
          .show();
      return;
    }

    hideKeyboard();
    SafeNavigation.safeNavigate(Navigation.findNavController(requireView()),
                                PaymentRecipientSelectionFragmentDirections.actionPaymentRecipientSelectionToCreatePayment(new PayeeParcelable(address)));
  }

  @Override
  public void onBeforeContactSelected(boolean isFromUnknownSearchKey, @NonNull Optional<RecipientId> recipientId, @Nullable String number, @NonNull Optional<ChatType> chatType, @NonNull Consumer<Boolean> callback) {
    if (recipientId.isPresent()) {
      createPaymentOrShowWarningDialog(recipientId.get());
    }

    callback.accept(false);
  }

  @Override
  public void onContactDeselected(@NonNull Optional<RecipientId> recipientId, @Nullable String number, @NonNull Optional<ChatType> chatType) {}

  @Override
  public void onSelectionChanged() {
  }

  @Override
  public void onFindByUsername() {
    findByUsernameLauncher.launch(FindByMode.USERNAME);
  }

  @Override
  public void onFindByPhoneNumber() {
    // No-op: only the find-by-username row is enabled in the payment recipient picker.
  }

  @Override
  public void onBeginScroll() {
    hideKeyboard();
  }

  private void hideKeyboard() {
    ViewUtil.hideKeyboard(requireContext(), toolbar);
    toolbar.clearFocus();
  }

  /**
   * Resolves whether we can send a payment to {@code recipientId} and routes accordingly.
   *
   * <p>Mirrors {@link org.thoughtcrime.securesms.mms.AttachmentManager#selectPayment}: we
   * intentionally do not gate on an expiring profile key credential (this fork does not use one and
   * the server does not issue one for the fixed payment profile version). Instead we require the
   * recipient's profile key (delivered in their messages once they share their profile with us) and
   * then attempt to fetch their payment address from their versioned profile. Both the resolve and
   * the network fetch run off the main thread.
   */
  private void createPaymentOrShowWarningDialog(@NonNull RecipientId recipientId) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                   () -> {
                     Recipient recipient = Recipient.resolved(recipientId);
                     if (recipient.getProfileKey() == null) {
                       return PaymentEligibility.NO_PROFILE_KEY;
                     }
                     try {
                       ProfileUtil.getAddressForRecipient(recipient);
                       return PaymentEligibility.CAN_SEND;
                     } catch (IOException | PaymentsAddressException e) {
                       Log.w(TAG, "Could not get address for recipient: ", e);
                       return PaymentEligibility.NOT_ENABLED;
                     }
                   },
                   eligibility -> {
                     switch (eligibility) {
                       case CAN_SEND:
                         createPayment(recipientId);
                         break;
                       case NO_PROFILE_KEY:
                         showWarningDialog(recipientId);
                         break;
                       case NOT_ENABLED:
                         RecipientHasNotEnabledPaymentsDialog.show(requireContext());
                         break;
                     }
                   });
  }

  private enum PaymentEligibility {
    CAN_SEND,
    NO_PROFILE_KEY,
    NOT_ENABLED
  }

  private void createPayment(@NonNull RecipientId recipientId) {
    hideKeyboard();
    SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), PaymentRecipientSelectionFragmentDirections.actionPaymentRecipientSelectionToCreatePayment(new PayeeParcelable(recipientId)));
  }

  private void showWarningDialog(@NonNull RecipientId recipientId) {
    CanNotSendPaymentDialog.show(requireContext(),
                                 () -> openConversation(recipientId));
  }

  private void openConversation(@NonNull RecipientId recipientId) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                   () -> SignalDatabase.threads().getOrCreateThreadIdFor(Recipient.resolved(recipientId)),
                   threadId -> startActivity(ConversationIntents.createBuilderSync(requireContext(), recipientId, threadId).build()));
  }
}
