package org.thoughtcrime.securesms.payments.preferences.transfer;

import org.thoughtcrime.securesms.payments.LightningAddress;
import java.util.regex.Pattern;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import org.signal.core.util.logging.Log;
import org.signal.qr.QrScannerView;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.mediasend.camerax.CameraXModelBlocklist;
import org.thoughtcrime.securesms.payments.MobileCoinPublicAddress;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

public final class PaymentsTransferQrScanFragment extends LoggingFragment {

  private static final String TAG = Log.tag(PaymentsTransferQrScanFragment.class);

  /** Fragment result carrying the scanned destination back to whichever screen launched the scan. */
  public static final String REQUEST_KEY_SCANNED_DESTINATION = "payments.scanned.destination";
  public static final String RESULT_DESTINATION             = "destination";

  /**
   * Turns a scanned code into something the payment pipeline can send to.
   *
   * <p>Radar pays over Lightning, so a `lightning:` URI, a bare BOLT11 invoice and a
   * user@host lightning address all have to be understood — previously this only decoded a
   * MobileCoin base58 address, which meant it could not read Radar's own receive QR. The
   * MobileCoin decode is kept last so nothing that used to scan stops scanning.
   */
  private static @Nullable String parseScannedDestination(@NonNull String data) {
    String trimmed = data.trim();

    if (trimmed.regionMatches(true, 0, LIGHTNING_URI_SCHEME, 0, LIGHTNING_URI_SCHEME.length())) {
      trimmed = trimmed.substring(LIGHTNING_URI_SCHEME.length());
    }

    if (LightningAddress.looksLikeBolt11Invoice(trimmed) || LIGHTNING_ADDRESS_SHAPE.matcher(trimmed).matches()) {
      return trimmed;
    }

    try {
      return MobileCoinPublicAddress.fromQr(data).getPaymentAddressBase58();
    } catch (MobileCoinPublicAddress.AddressException e) {
      return null;
    }
  }

  private static final String  LIGHTNING_URI_SCHEME   = "lightning:";
  private static final Pattern LIGHTNING_ADDRESS_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private final LifecycleDisposable lifecycleDisposable = new LifecycleDisposable();

  private LinearLayout              overlay;
  private QrScannerView             scannerView;
  private PaymentsTransferViewModel viewModel;

  public PaymentsTransferQrScanFragment() {
    super(R.layout.payments_transfer_qr_scan_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    overlay     = view.findViewById(R.id.overlay);
    scannerView = view.findViewById(R.id.scanner);

    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
      overlay.setOrientation(LinearLayout.HORIZONTAL);
    } else {
      overlay.setOrientation(LinearLayout.VERTICAL);
    }

    // This fragment is reachable from two places: the transfer screen, which reads the result off a
    // graph-scoped view model, and the send screen, which is not part of that graph and takes the
    // result as a fragment result instead. Only look up the view model when its graph is actually
    // on the back stack, otherwise getViewModelStoreOwner throws.
    viewModel = null;
    try {
      viewModel = new ViewModelProvider(Navigation.findNavController(view).getViewModelStoreOwner(R.id.payments_transfer), new PaymentsTransferViewModel.Factory()).get(PaymentsTransferViewModel.class);
    } catch (IllegalArgumentException e) {
      Log.d(TAG, "Not hosted in the transfer graph; reporting the scan as a fragment result only.");
    }

    Toolbar toolbar = view.findViewById(R.id.payments_transfer_scan_qr);
    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

    scannerView.start(getViewLifecycleOwner(), CameraXModelBlocklist.isBlocklisted());

    lifecycleDisposable.bindTo(getViewLifecycleOwner());

    Disposable qrDisposable = scannerView
        .getQrData()
        .distinctUntilChanged()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(data -> {
          String destination = parseScannedDestination(data);
          if (destination == null) {
            Log.w(TAG, "Scanned code is not a payable destination");
            return;
          }

          if (viewModel != null) {
            viewModel.postQrData(destination);
          }

          Bundle result = new Bundle();
          result.putString(RESULT_DESTINATION, destination);
          getParentFragmentManager().setFragmentResult(REQUEST_KEY_SCANNED_DESTINATION, result);

          // popBackStack() rather than a named action: the action id only exists on the transfer
          // graph's copy of this destination, and this fragment now has two homes.
          Navigation.findNavController(requireView()).popBackStack();
        });

    lifecycleDisposable.add(qrDisposable);
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfiguration) {
    super.onConfigurationChanged(newConfiguration);

    if (newConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      overlay.setOrientation(LinearLayout.HORIZONTAL);
    } else {
      overlay.setOrientation(LinearLayout.VERTICAL);
    }
  }
}
