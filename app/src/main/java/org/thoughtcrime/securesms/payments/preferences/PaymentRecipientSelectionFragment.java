package org.thoughtcrime.securesms.payments.preferences;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

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
import org.thoughtcrime.securesms.payments.preferences.model.PayeeParcelable;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.whispersystems.signalservice.api.util.ExpiringProfileCredentialUtil;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;


public class PaymentRecipientSelectionFragment extends LoggingFragment implements ContactSelectionListFragment.OnContactSelectedListener, ContactSelectionListFragment.ScrollCallback {

  private static final String TAG = Log.tag(PaymentRecipientSelectionFragment.class);

  /** Loose "looks like a lightning address" gate (e.g. {@code name@radar.cash}) before we attempt to parse it. */
  private static final Pattern LIGHTNING_ADDRESS_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private Toolbar                      toolbar;
  private ContactFilterView            contactFilterView;
  private TextView                     sendToAddressRow;
  private ContactSelectionListFragment contactsFragment;

  public PaymentRecipientSelectionFragment() {
    super(R.layout.payment_recipient_selection_fragment);
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
      SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                     () -> Recipient.resolved(recipientId.get()),
                     this::createPaymentOrShowWarningDialog);
    }

    callback.accept(false);
  }

  @Override
  public void onContactDeselected(@NonNull Optional<RecipientId> recipientId, @Nullable String number, @NonNull Optional<ChatType> chatType) {}

  @Override
  public void onSelectionChanged() {
  }

  @Override
  public void onBeginScroll() {
    hideKeyboard();
  }

  private void hideKeyboard() {
    ViewUtil.hideKeyboard(requireContext(), toolbar);
    toolbar.clearFocus();
  }

  private void createPaymentOrShowWarningDialog(@NonNull Recipient recipient) {
    if (ExpiringProfileCredentialUtil.isValid(recipient.getExpiringProfileKeyCredential())) {
      createPayment(recipient.getId());
    } else {
      showWarningDialog(recipient.getId());
    }
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
