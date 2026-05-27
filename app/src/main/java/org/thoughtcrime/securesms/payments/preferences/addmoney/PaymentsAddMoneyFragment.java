package org.thoughtcrime.securesms.payments.preferences.addmoney;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;

import org.signal.core.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.qr.QrView;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

/**
 * Add Funds (receive) screen. Mirrors iOS PaymentsTransferInViewController: a Lightning/Onchain
 * segmented switcher, a bordered QR with the radar logo centered, the address with a pencil-edit,
 * and a Copy pill; Share is in the toolbar.
 */
public final class PaymentsAddMoneyFragment extends LoggingFragment {

  /** FragmentResult key the edit-username screen posts after a successful change, to trigger a refresh. */
  public static final String REQUEST_KEY_USERNAME_CHANGED = "payments_add_money.username_changed";

  private PaymentsAddMoneyViewModel viewModel;

  private boolean showingOnchain;
  private String  lightningAddress;
  private String  onchainAddress;

  private QrView              qrView;
  private View                logoBox;
  private ProgressBar         spinner;
  private TextView            addressView;
  private AppCompatImageButton pencil;
  private MaterialButton      tabLightning;
  private MaterialButton      tabOnchain;

  public PaymentsAddMoneyFragment() {
    super(R.layout.payments_add_money_fragment);
  }

  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    viewModel = new ViewModelProvider(this, new PaymentsAddMoneyViewModel.Factory()).get(PaymentsAddMoneyViewModel.class);

    Toolbar        toolbar = view.findViewById(R.id.payments_add_money_toolbar);
    MaterialButton copyBtn = view.findViewById(R.id.payments_add_money_copy_address_button);

    qrView       = view.findViewById(R.id.payments_add_money_qr_image);
    logoBox      = view.findViewById(R.id.payments_add_money_logo_box);
    spinner      = view.findViewById(R.id.payments_add_money_qr_spinner);
    addressView  = view.findViewById(R.id.payments_add_money_abbreviated_wallet_address);
    pencil       = view.findViewById(R.id.payments_add_money_edit_pencil);
    tabLightning = view.findViewById(R.id.payments_add_money_tab_lightning);
    tabOnchain   = view.findViewById(R.id.payments_add_money_tab_onchain);

    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());
    toolbar.inflateMenu(R.menu.payments_add_money_menu);
    toolbar.setOnMenuItemClickListener(item -> {
      if (item.getItemId() == R.id.payments_add_money_menu_share) {
        shareAddress();
        return true;
      }
      return false;
    });

    tabLightning.setOnClickListener(v -> selectLightning());
    tabOnchain.setOnClickListener(v -> selectOnchain());
    applyTabStyling();

    pencil.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.action_paymentsAddMoney_to_editLightningUsername));
    copyBtn.setOnClickListener(v -> copyAddress());

    showSpinner(true);

    viewModel.getSelfAddressB58().observe(getViewLifecycleOwner(), address -> {
      lightningAddress = address;
      if (!showingOnchain) {
        renderAll();
      }
    });

    viewModel.getErrors().observe(getViewLifecycleOwner(), error -> {
      switch (error) {
        case PAYMENTS_NOT_ENABLED: throw new AssertionError("Payments are not enabled");
        default                  : throw new AssertionError();
      }
    });

    // Refresh when the username was changed on the edit screen (mirrors iOS walletAddressDidLoad).
    getParentFragmentManager().setFragmentResultListener(REQUEST_KEY_USERNAME_CHANGED, getViewLifecycleOwner(),
        (key, result) -> viewModel.refresh());

    prefetchOnchainAddress();
  }

  // MARK: - Network switcher

  private void selectLightning() {
    if (!showingOnchain) return;
    showingOnchain = false;
    applyTabStyling();
    pencil.setVisibility(View.VISIBLE);
    renderAll();
  }

  private void selectOnchain() {
    if (showingOnchain) return;
    showingOnchain = true;
    applyTabStyling();
    pencil.setVisibility(View.GONE);
    renderAddress();

    if (onchainAddress != null) {
      renderQr();
      return;
    }
    showSpinner(true);
    fetchOnchainAddress(true);
  }

  private void applyTabStyling() {
    styleTab(tabLightning, !showingOnchain);
    styleTab(tabOnchain, showingOnchain);
  }

  private void styleTab(@NonNull MaterialButton tab, boolean selected) {
    @ColorInt int accent = ContextCompat.getColor(requireContext(), R.color.radar_accent_blue);
    @ColorInt int gray   = ContextCompat.getColor(requireContext(), R.color.signal_text_secondary);
    @ColorInt int white  = ContextCompat.getColor(requireContext(), R.color.white);
    tab.setBackgroundTintList(ColorStateList.valueOf(selected ? white : Color.TRANSPARENT));
    tab.setTextColor(selected ? accent : gray);
    tab.setIconTint(ColorStateList.valueOf(selected ? accent : gray));
  }

  // MARK: - On-chain fetch

  private void prefetchOnchainAddress() {
    if (onchainAddress != null) return;
    fetchOnchainAddress(false);
  }

  private void fetchOnchainAddress(boolean fromUserToggle) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                   () -> SignalStore.payments().breezSdkWrapperLatest().getOnchainAddress(),
                   address -> {
                     if (address == null) {
                       if (fromUserToggle && showingOnchain) {
                         Toast.makeText(requireContext(), R.string.PaymentsAddMoneyFragment__couldnt_load_bitcoin_address, Toast.LENGTH_SHORT).show();
                         selectLightning();
                       }
                       return;
                     }
                     onchainAddress = address;
                     if (showingOnchain) {
                       renderAll();
                     }
                   });
  }

  // MARK: - Rendering

  private void renderAll() {
    renderAddress();
    renderQr();
  }

  private @Nullable String currentAddress() {
    return showingOnchain ? onchainAddress : lightningAddress;
  }

  private void renderQr() {
    String address = currentAddress();
    if (address == null) {
      showSpinner(true);
      return;
    }
    qrView.setQrText(showingOnchain ? address : "lightning:" + address);
    showSpinner(false);
  }

  private void showSpinner(boolean show) {
    spinner.setVisibility(show ? View.VISIBLE : View.GONE);
    qrView.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
    logoBox.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
  }

  private void renderAddress() {
    String address = currentAddress();
    @ColorInt int primary   = ContextCompat.getColor(requireContext(), R.color.signal_text_primary);
    @ColorInt int secondary = ContextCompat.getColor(requireContext(), R.color.signal_text_secondary);

    if (address == null) {
      addressView.setTypeface(Typeface.DEFAULT);
      addressView.setMaxLines(1);
      addressView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
      addressView.setTextColor(secondary);
      addressView.setText(R.string.PaymentsAddMoneyFragment__loading);
      return;
    }
    if (showingOnchain) {
      renderOnchainAddress(address, primary, secondary);
    } else {
      renderLightningAddress(address, primary);
    }
  }

  private void renderLightningAddress(@NonNull String address, @ColorInt int primary) {
    addressView.setTypeface(Typeface.DEFAULT);
    addressView.setMaxLines(1);
    addressView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
    int at = address.indexOf('@');
    if (at < 0) {
      addressView.setTextColor(primary);
      addressView.setText(address);
      return;
    }
    @ColorInt int accent = ContextCompat.getColor(requireContext(), R.color.radar_accent_blue);
    SpannableString span = new SpannableString(address);
    span.setSpan(new ForegroundColorSpan(primary), 0, at, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    span.setSpan(new ForegroundColorSpan(accent), at, address.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    addressView.setText(span);
  }

  private void renderOnchainAddress(@NonNull String address, @ColorInt int primary, @ColorInt int ternary) {
    addressView.setTypeface(Typeface.MONOSPACE);
    addressView.setMaxLines(3);
    addressView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    SpannableStringBuilder builder = new SpannableStringBuilder();
    int group = 0;
    for (int i = 0; i < address.length(); i += 4, group++) {
      if (group > 0) {
        builder.append(" ");
      }
      int end   = Math.min(i + 4, address.length());
      int start = builder.length();
      builder.append(address, i, end);
      builder.setSpan(new ForegroundColorSpan(group % 2 == 0 ? primary : ternary), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    addressView.setText(builder);
  }

  // MARK: - Actions

  private void copyAddress() {
    String address = currentAddress();
    if (address == null) return;
    Context          context   = requireContext();
    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), address));
    Toast.makeText(context, R.string.PaymentsAddMoneyFragment__wallet_address_copied, Toast.LENGTH_SHORT).show();
  }

  private void shareAddress() {
    String address = currentAddress();
    if (address == null) return;
    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.setType("text/plain");
    intent.putExtra(Intent.EXTRA_TEXT, address);
    startActivity(Intent.createChooser(intent, null));
  }
}
