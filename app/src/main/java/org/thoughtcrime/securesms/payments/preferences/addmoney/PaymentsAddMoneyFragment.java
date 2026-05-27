package org.thoughtcrime.securesms.payments.preferences.addmoney;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;

import org.signal.core.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.qr.QrView;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;

public final class PaymentsAddMoneyFragment extends LoggingFragment {

  private String  lightningAddress;
  private String  onchainAddress;
  private boolean showingOnchain;

  public PaymentsAddMoneyFragment() {
    super(R.layout.payments_add_money_fragment);
  }

  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    PaymentsAddMoneyViewModel viewModel = new ViewModelProvider(this, new PaymentsAddMoneyViewModel.Factory()).get(PaymentsAddMoneyViewModel.class);

    Toolbar           toolbar       = view.findViewById(R.id.payments_add_money_toolbar);
    QrView            qrImageView   = view.findViewById(R.id.payments_add_money_qr_image);
    TextView          addressView   = view.findViewById(R.id.payments_add_money_abbreviated_wallet_address);
    View              copyAddress   = view.findViewById(R.id.payments_add_money_copy_address_button);
    LearnMoreTextView info          = view.findViewById(R.id.payments_add_money_info);
    View              editUsername  = view.findViewById(R.id.payments_add_money_edit_username_button);
    MaterialButton    networkToggle = view.findViewById(R.id.payments_add_money_network_toggle);

    editUsername.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.action_paymentsAddMoney_to_editLightningUsername));

    info.setLearnMoreVisible(true);
    info.setLink(getString(R.string.PaymentsAddMoneyFragment__learn_more__information));

    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

    viewModel.getSelfAddressB58().observe(getViewLifecycleOwner(), address -> {
      lightningAddress = address;
      if (!showingOnchain) {
        render(qrImageView, addressView);
      }
    });

    copyAddress.setOnClickListener(v -> {
      String address = currentAddress();
      if (address != null) {
        copyAddressToClipboard(address);
      }
    });

    // Toggle between the Lightning address and an on-chain Bitcoin address (mirrors iOS onchain Add Funds).
    networkToggle.setOnClickListener(v -> {
      showingOnchain = !showingOnchain;
      networkToggle.setText(showingOnchain ? R.string.PaymentsAddMoneyFragment__show_lightning_address
                                           : R.string.PaymentsAddMoneyFragment__show_onchain_address);
      if (showingOnchain && onchainAddress == null) {
        fetchOnchainAddress(qrImageView, addressView);
      } else {
        render(qrImageView, addressView);
      }
    });

    viewModel.getErrors().observe(getViewLifecycleOwner(), error -> {
      switch (error) {
        case PAYMENTS_NOT_ENABLED: throw new AssertionError("Payments are not enabled");
        default                  : throw new AssertionError();
      }
    });
  }

  private @Nullable String currentAddress() {
    return showingOnchain ? onchainAddress : lightningAddress;
  }

  private void render(@NonNull QrView qr, @NonNull TextView addressView) {
    String address = currentAddress();
    if (address == null) {
      return;
    }
    setAddress(addressView, address);
    // Cake/wallet compatibility: encode the Lightning QR as a "lightning:" URI (mirrors iOS
    // 4f069a4e30, which prefers a BOLT11 invoice and falls back to "lightning:<address>").
    // The on-chain QR stays the raw Bitcoin address.
    qr.setQrText(showingOnchain ? address : "lightning:" + address);
  }

  private void fetchOnchainAddress(@NonNull QrView qr, @NonNull TextView addressView) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                   () -> SignalStore.payments().breezSdkWrapperLatest().getOnchainAddress(),
                   address -> {
                     onchainAddress = address;
                     if (showingOnchain) {
                       render(qr, addressView);
                     }
                   });
  }

  /** Shows the address with the {@code @domain} portion tinted for Lightning addresses (mirrors iOS receive screen). */
  private void setAddress(@NonNull TextView view, @NonNull String address) {
    int atIndex = address.indexOf('@');
    if (atIndex < 0) {
      view.setText(address);
      return;
    }
    SpannableString span = new SpannableString(address);
    span.setSpan(new ForegroundColorSpan(0xFF0069FE), atIndex, address.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    view.setText(span);
  }

  private void copyAddressToClipboard(@NonNull String address) {
    Context          context   = requireContext();
    ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), address));

    Toast.makeText(context, R.string.PaymentsAddMoneyFragment__copied_to_clipboard, Toast.LENGTH_SHORT).show();
  }
}
