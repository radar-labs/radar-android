package org.thoughtcrime.securesms.payments.preferences;

import android.Manifest;
import android.widget.Toast;
import org.thoughtcrime.securesms.payments.preferences.transfer.PaymentsTransferQrScanFragment;
import org.thoughtcrime.securesms.permissions.Permissions;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import androidx.annotation.WorkerThread;
import org.thoughtcrime.securesms.database.PaymentTable;
import org.thoughtcrime.securesms.payments.Direction;
import org.thoughtcrime.securesms.payments.Payee;
import org.thoughtcrime.securesms.payments.Payment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.thoughtcrime.securesms.mms.AttachmentManager;
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
  private static final int MAX_RECENT_RECIPIENTS = 5;

  private static final Pattern LIGHTNING_ADDRESS_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private Toolbar                      toolbar;
  private TextView                     recentHeader;
  private LinearLayout                 recentContainer;
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
    toolbar.inflateMenu(R.menu.payment_recipient_selection_fragment_menu);
    toolbar.setOnMenuItemClickListener(item -> {
      if (item.getItemId() == R.id.payment_recipient_selection_scan_qr) {
        scanQrCode();
        return true;
      }
      return false;
    });

    // The scanner reports back as a fragment result rather than through the transfer graph's view
    // model, because this screen is not part of that graph. Dropping the scanned destination into
    // the filter field reuses the existing "Send to <destination>" row rather than adding a second
    // way to start a payment.
    getParentFragmentManager().setFragmentResultListener(
        PaymentsTransferQrScanFragment.REQUEST_KEY_SCANNED_DESTINATION,
        getViewLifecycleOwner(),
        (key, bundle) -> {
          String destination = bundle.getString(PaymentsTransferQrScanFragment.RESULT_DESTINATION);
          if (destination != null) {
            contactFilterView.setQuery(destination);
          }
        });

    recentHeader    = view.findViewById(R.id.payment_recipient_selection_fragment_recent_header);
    recentContainer = view.findViewById(R.id.payment_recipient_selection_fragment_recents);
    loadRecentRecipients();

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
      setRecentsVisible(TextUtils.isEmpty(filter) && recentContainer.getChildCount() > 0);
    });
  }

  /**
   * Mirrors iOS's inline "find by …" section: when the typed query looks like a payable
   * destination, surface a tappable row that sends straight to it.
   *
   * <p>Two shapes are accepted: a lightning address ({@code name@radar.cash}) and a BOLT11 invoice
   * ({@code lnbc…}), the latter typically arriving via the paste button. An invoice is far too long
   * to show in full, so it is abbreviated for display while the full string is what gets sent.
   */
  private void updateSendToAddressRow(@Nullable String filter) {
    String query = filter != null ? filter.trim() : "";

    boolean isAddress = LIGHTNING_ADDRESS_SHAPE.matcher(query).matches();
    boolean isInvoice = LightningAddress.looksLikeBolt11Invoice(query);

    if (isAddress || isInvoice) {
      String display = isInvoice ? abbreviate(query) : query;
      sendToAddressRow.setText(getString(R.string.PaymentRecipientSelectionFragment__send_to_s, display));
      sendToAddressRow.setVisibility(View.VISIBLE);
      sendToAddressRow.setOnClickListener(v -> onSendToAddressClicked(query));
    } else {
      sendToAddressRow.setVisibility(View.GONE);
      sendToAddressRow.setOnClickListener(null);
    }
  }

  /** Shortens a long destination to head…tail for display. Matches iOS's 10/9 split. */
  private static @NonNull String abbreviate(@NonNull String value) {
    final int prefix = 10;
    final int suffix = 9;
    if (value.length() <= prefix + suffix + 1) {
      return value;
    }
    return value.substring(0, prefix) + "\u2026" + value.substring(value.length() - suffix);
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
                         // Offer to ask them to turn payments on, rather than the OK-only dead end
                         // this used to show. Same dialog the conversation attachment menu uses.
                         AttachmentManager.showRequestToActivatePayments(requireContext(), Recipient.resolved(recipientId));
                         break;
                     }
                   });
  }

  private enum PaymentEligibility {
    CAN_SEND,
    NO_PROFILE_KEY,
    NOT_ENABLED
  }

  private void scanQrCode() {
    Permissions.with(this)
               .request(Manifest.permission.CAMERA)
               .ifNecessary()
               .withRationaleDialog(getString(R.string.CameraXFragment_allow_access_camera), getString(R.string.PaymentsTransferFragment__to_scan_a_qr_code_signal_needs), R.drawable.ic_camera_24)
               .withPermanentDenialDialog(getString(R.string.PaymentsTransferFragment__to_scan_a_qr_code_signal_needs_access_to_the_camera), null, R.string.CameraXFragment_allow_access_camera, R.string.CameraXFragment_to_scan_qr_codes, getParentFragmentManager())
               .onAllGranted(() -> SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), R.id.action_paymentRecipientSelection_to_scanQr))
               .onAnyDenied(() -> Toast.makeText(requireContext(), R.string.PaymentsTransferFragment__to_scan_a_qr_code_signal_needs_access_to_the_camera, Toast.LENGTH_LONG).show())
               .execute();
  }

  /**
   * Fills the "Recent" section with the last few destinations this wallet paid, newest first.
   * Mirrors the recent-recipients section on iOS's send screen, which is backed by the same
   * outgoing-payment history.
   */
  private void loadRecentRecipients() {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                   PaymentRecipientSelectionFragment::queryRecentPayees,
                   this::bindRecentRecipients);
  }

  @WorkerThread
  private static @NonNull List<Payee> queryRecentPayees() {
    List<Payee>  payees = new ArrayList<>();
    Set<String>  seen   = new HashSet<>();

    List<PaymentTable.PaymentTransaction> all = new ArrayList<>(SignalDatabase.payments().getAll());
    Collections.sort(all, Payment.DESCENDING_TIMESTAMP);

    for (PaymentTable.PaymentTransaction transaction : all) {
      if (transaction.getDirection() != Direction.SENT) {
        continue;
      }

      Payee  payee = transaction.getPayee();
      String key   = payeeKey(payee);
      if (key == null || !seen.add(key)) {
        continue;
      }

      payees.add(payee);
      if (payees.size() == MAX_RECENT_RECIPIENTS) {
        break;
      }
    }

    return payees;
  }

  /** Identity for de-duplication: a contact by id, an external destination by its address. */
  private static @Nullable String payeeKey(@NonNull Payee payee) {
    if (payee.hasRecipientId()) {
      return "id:" + payee.requireRecipientId().serialize();
    } else if (payee.hasPublicAddress()) {
      return "addr:" + payee.requireLightningAddress().getPaymentAddress();
    } else {
      return null;
    }
  }

  private void bindRecentRecipients(@NonNull List<Payee> payees) {
    recentContainer.removeAllViews();

    for (Payee payee : payees) {
      TextView row = (TextView) LayoutInflater.from(requireContext())
                                              .inflate(R.layout.payment_recipient_selection_recent_item, recentContainer, false);

      if (payee.hasRecipientId()) {
        RecipientId recipientId = payee.requireRecipientId();
        row.setText(Recipient.resolved(recipientId).getDisplayName(requireContext()));
        row.setOnClickListener(v -> createPaymentOrShowWarningDialog(recipientId));
      } else {
        String address = payee.requireLightningAddress().getPaymentAddress();
        row.setText(address);
        row.setOnClickListener(v -> onSendToAddressClicked(address));
      }

      recentContainer.addView(row);
    }

    setRecentsVisible(!payees.isEmpty() && TextUtils.isEmpty(contactFilterView.getQuery()));
  }

  private void setRecentsVisible(boolean visible) {
    recentHeader.setVisibility(visible ? View.VISIBLE : View.GONE);
    recentContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
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
