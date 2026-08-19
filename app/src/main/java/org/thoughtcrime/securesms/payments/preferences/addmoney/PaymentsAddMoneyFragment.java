package org.thoughtcrime.securesms.payments.preferences.addmoney;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.MenuItem;
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
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.qr.QrView;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.LightningInvoiceFetcher;
import org.thoughtcrime.securesms.payments.onboarding.PaymentsOnboardingDepositReceivedFragment;
import org.thoughtcrime.securesms.payments.preferences.PaymentsHomeRepository;
import org.thoughtcrime.securesms.util.AsynchronousCallback;

import java.util.Locale;
import java.util.Objects;

/**
 * Add Funds (receive) screen. Mirrors iOS PaymentsTransferInViewController: a Lightning/Onchain
 * segmented switcher, a bordered QR with the radar logo centered, the address with a pencil-edit,
 * and a Copy pill; Share is in the toolbar.
 *
 * <p>The same screen is reused during payments onboarding. When {@link #ARG_ONBOARDING} is set
 * (mirroring iOS {@code PaymentsTransferInViewController(isOnboarding:)}) the network toggle is
 * hidden (Lightning only), the instruction copy changes, a pinned Continue button advances the
 * onboarding flow, and an incoming deposit pushes the deposit-received screen.
 */
public final class PaymentsAddMoneyFragment extends LoggingFragment {

  private static final String TAG = Log.tag(PaymentsAddMoneyFragment.class);

  /** FragmentResult key the edit-username screen posts after a successful change, to trigger a refresh. */
  public static final String REQUEST_KEY_USERNAME_CHANGED = "payments_add_money.username_changed";

  /** Boolean navigation argument: true when shown as part of the onboarding flow. */
  public static final String ARG_ONBOARDING = "isOnboarding";

  private PaymentsAddMoneyViewModel viewModel;

  private boolean isOnboarding;
  private boolean showingOnchain;
  private String  lightningAddress;
  private String  onchainAddress;

  // Onboarding-only deposit watch: advance once on the 0 -> positive balance transition.
  private boolean sawInitialBalance;
  private boolean initialBalancePositive;
  private boolean navigatedToDeposit;

  /** BIP-21 URI scheme prefixed onto the on-chain address in the QR payload. */
  private static final String BITCOIN_URI_SCHEME = "bitcoin:";

  /** Cached BOLT11 invoice for the current {@link #lightningAddress}; cleared when the address changes. */
  private @Nullable String  bolt11Invoice;
  private boolean           bolt11FetchInFlight;

  private QrView              qrView;
  private View                logoBox;
  private ProgressBar         spinner;
  private TextView            addressView;
  private AppCompatImageButton pencil;
  private MaterialButton      tabLightning;
  private MaterialButton      tabOnchain;
  private View                networkToggle;
  private MaterialButton      continueButton;

  public PaymentsAddMoneyFragment() {
    super(R.layout.payments_add_money_fragment);
  }

  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    viewModel = new ViewModelProvider(this, new PaymentsAddMoneyViewModel.Factory()).get(PaymentsAddMoneyViewModel.class);

    isOnboarding = getArguments() != null && getArguments().getBoolean(ARG_ONBOARDING, false);

    Toolbar        toolbar = view.findViewById(R.id.payments_add_money_toolbar);
    MaterialButton copyBtn = view.findViewById(R.id.payments_add_money_copy_address_button);

    qrView         = view.findViewById(R.id.payments_add_money_qr_image);
    logoBox        = view.findViewById(R.id.payments_add_money_logo_box);
    spinner        = view.findViewById(R.id.payments_add_money_qr_spinner);
    addressView    = view.findViewById(R.id.payments_add_money_abbreviated_wallet_address);
    pencil         = view.findViewById(R.id.payments_add_money_edit_pencil);
    tabLightning   = view.findViewById(R.id.payments_add_money_tab_lightning);
    tabOnchain     = view.findViewById(R.id.payments_add_money_tab_onchain);
    networkToggle  = view.findViewById(R.id.payments_add_money_network_toggle);
    continueButton = view.findViewById(R.id.payments_add_money_continue);

    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());
    toolbar.inflateMenu(R.menu.payments_add_money_menu);
    MenuItem shareItem = toolbar.getMenu().findItem(R.id.payments_add_money_menu_share);

    // The shared symbol_share_android_24 drawable has a hardcoded black fill, so tint it with the
    // theme-aware on-surface color to match the navigation icon in both light and dark themes.
    Drawable shareIcon = shareItem.getIcon();
    if (shareIcon != null) {
      shareIcon = DrawableCompat.wrap(shareIcon.mutate());
      DrawableCompat.setTint(shareIcon, ContextCompat.getColor(requireContext(), R.color.signal_colorOnSurface));
      shareItem.setIcon(shareIcon);
    }

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

    pencil.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.editLightningUsername));
    copyBtn.setOnClickListener(v -> copyAddress());

    if (isOnboarding) {
      // Onboarding: Lightning only, hardcoded instruction, pinned Continue, watch for a deposit.
      networkToggle.setVisibility(View.GONE);
      ((TextView) view.findViewById(R.id.payments_add_money_instruction))
          .setText(R.string.PaymentsAddMoneyFragment__send_bitcoin_over_lightning_onboarding);
      continueButton.setVisibility(View.VISIBLE);
      continueButton.setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.action_addFunds_to_setupComplete));
      observeDepositForOnboarding();
    }

    showSpinner(true);

    viewModel.getSelfAddressB58().observe(getViewLifecycleOwner(), address -> {
      if (!Objects.equals(address, lightningAddress)) {
        // Invalidate the BOLT11 cache — the previous invoice was tied to the previous address.
        bolt11Invoice = null;
        bolt11FetchInFlight = false;
      }
      lightningAddress = address;
      if (!showingOnchain) {
        renderAll();
      }
    });

    viewModel.getErrors().observe(getViewLifecycleOwner(), error -> {
      switch (error) {
        case PAYMENTS_NOT_ENABLED:
          // During onboarding, payments may still be activating (kicked off on the Add Funds intro
          // step); treat this as transient rather than crashing — enable and retry.
          if (isOnboarding) {
            ensurePaymentsEnabledThenRefresh();
            return;
          }
          throw new AssertionError("Payments are not enabled");
        case NOT_REGISTERED:
          showSpinner(false);
          showDeviceUnregisteredDialog();
          break;
        case COULD_NOT_GET_WALLET_ADDRESS:
        default:
          showSpinner(false);
          Toast.makeText(requireContext(), R.string.NetworkFailure__network_error_check_your_connection_and_try_again, Toast.LENGTH_SHORT).show();
          break;
      }
    });

    // Refresh when the username was changed on the edit screen (mirrors iOS walletAddressDidLoad).
    getParentFragmentManager().setFragmentResultListener(REQUEST_KEY_USERNAME_CHANGED, getViewLifecycleOwner(),
        (key, result) -> viewModel.refresh());

    // Onboarding is Lightning-only, so the on-chain address is never needed there.
    if (!isOnboarding) {
      prefetchOnchainAddress();
    }
  }

  /**
   * Onboarding-only recovery: if payments aren't enabled yet, enable them (mirrors iOS
   * {@code enablePayments}) and re-fetch the wallet address once done. No-ops to a plain refresh
   * if they're already enabled (the flag may have flipped between error emission and handling).
   */
  private void ensurePaymentsEnabledThenRefresh() {
    if (SignalStore.payments().mobileCoinPaymentsEnabled()) {
      viewModel.refresh();
      return;
    }
    new PaymentsHomeRepository().activatePayments(new AsynchronousCallback.WorkerThread<Void, PaymentsHomeRepository.Error>() {
      @Override public void onComplete(@Nullable Void result) {
        ThreadUtil.runOnMain(() -> {
          if (isAdded()) {
            viewModel.refresh();
          }
        });
      }

      @Override public void onError(@Nullable PaymentsHomeRepository.Error error) {
        Log.w(TAG, "Failed to enable payments on onboarding Add Funds screen: " + error);
      }
    });
  }

  /**
   * Shown when the wallet-address fetch comes back 403 (PaymentsRegionException). In this fork that
   * means the device is no longer registered — typically because the account was re-registered on
   * another device, which deregisters this one. Inform the user instead of crashing, then pop back.
   */
  private void showDeviceUnregisteredDialog() {
    if (!isAdded()) {
      return;
    }
    new MaterialAlertDialogBuilder(requireContext())
        .setMessage(R.string.UnauthorizedReminder_this_is_likely_because_you_registered_your_phone_number_with_Signal_on_a_different_device)
        .setCancelable(false)
        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
          if (isAdded()) {
            Navigation.findNavController(requireView()).popBackStack();
          }
        })
        .show();
  }

  // MARK: - Onboarding deposit watch

  /**
   * Watches the wallet balance while the onboarding Add Funds screen is visible and, on the first
   * 0 -> positive transition, advances to the deposit-received screen. Mirrors the iOS onboarding
   * coordinator's {@code incomingPaymentReceived} observation.
   */
  private void observeDepositForOnboarding() {
    SignalStore.payments().liveMobileCoinBalance().observe(getViewLifecycleOwner(), balance -> {
      boolean positive = balance != null && balance.getFullAmount().isPositive();
      if (!sawInitialBalance) {
        sawInitialBalance      = true;
        initialBalancePositive = positive;
        return;
      }
      if (!navigatedToDeposit && positive && !initialBalancePositive) {
        navigatedToDeposit = true;
        Bundle args = new Bundle();
        args.putString(PaymentsOnboardingDepositReceivedFragment.ARG_AMOUNT, balance.getFullAmount().serializeAmountString());
        Navigation.findNavController(requireView()).navigate(R.id.action_addFunds_to_depositReceived, args);
      }
    });
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
    String payload;
    if (showingOnchain) {
      // BIP-21 `bitcoin:` scheme in the QR payload only, so a scanner can hand the address to any
      // wallet registered for that scheme instead of treating it as opaque text. The visible label,
      // Copy and Share all keep the bare address (they read currentAddress(), not this payload).
      // Guarded in case the SDK ever returns an already-schemed payment request. Mirrors iOS.
      payload = address.toLowerCase(Locale.US).startsWith(BITCOIN_URI_SCHEME) ? address : BITCOIN_URI_SCHEME + address;
    } else if (bolt11Invoice != null) {
      // Preferred: encode the LNURL-pay-resolved BOLT11 invoice (mirrors iOS commit 4f069a4e30).
      payload = "lightning:" + bolt11Invoice;
    } else {
      // Fallback while the invoice is being fetched (or after a fetch error): the address itself.
      payload = "lightning:" + address;
      if (!bolt11FetchInFlight) {
        fetchBolt11Invoice(address);
      }
    }
    qrView.setQrText(payload);
    showSpinner(false);
  }

  /**
   * Resolves the current Lightning address to a BOLT11 invoice via LNURL-pay (in the background).
   * On success, re-renders the QR with `lightning:<bolt11>`. On failure, leaves the
   * `lightning:<address>` fallback in place. Mirrors iOS `getBolt11FromLightningAddress`.
   */
  private void fetchBolt11Invoice(@NonNull String addressAtFetchTime) {
    bolt11FetchInFlight = true;
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(),
                   () -> {
                     try {
                       return LightningInvoiceFetcher.fetchBolt11(addressAtFetchTime, 0L);
                     } catch (Exception e) {
                       Log.w(TAG, "BOLT11 fetch failed; falling back to lightning:<address>", e);
                       return null;
                     }
                   },
                   invoice -> {
                     bolt11FetchInFlight = false;
                     // Discard a stale response if the user changed username (and thus address) while in flight.
                     if (invoice != null && addressAtFetchTime.equals(lightningAddress)) {
                       bolt11Invoice = invoice;
                       if (!showingOnchain) {
                         renderQr();
                       }
                     }
                   });
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
    // For Lightning, share the resolved BOLT11 invoice rather than the lightning address
    // (e.g. "xxx@radar.cash"); fall back to the address while the invoice is still being fetched.
    String shareText = (!showingOnchain && bolt11Invoice != null) ? bolt11Invoice : address;
    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.setType("text/plain");
    intent.putExtra(Intent.EXTRA_TEXT, shareText);
    startActivity(Intent.createChooser(intent, null));
  }
}
