package org.thoughtcrime.securesms.payments;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.mobilecoin.lib.AccountKey;
import com.mobilecoin.lib.Amount;
import com.mobilecoin.lib.DefragmentationDelegate;
import com.mobilecoin.lib.MobileCoinClient;
import com.mobilecoin.lib.PendingTransaction;
import com.mobilecoin.lib.exceptions.AttestationException;
import com.mobilecoin.lib.exceptions.BadEntropyException;
import com.mobilecoin.lib.exceptions.FogReportException;
import com.mobilecoin.lib.exceptions.FogSyncException;
import com.mobilecoin.lib.exceptions.InsufficientFundsException;
import com.mobilecoin.lib.exceptions.InvalidFogResponse;
import com.mobilecoin.lib.exceptions.InvalidTransactionException;
import com.mobilecoin.lib.exceptions.InvalidUriException;
import com.mobilecoin.lib.exceptions.NetworkException;
import com.mobilecoin.lib.exceptions.TransactionBuilderException;
import com.mobilecoin.lib.network.TransportProtocol;

import org.jetbrains.annotations.NotNull;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.keyvalue.PaymentsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.whispersystems.signalservice.api.payments.Money;
import org.whispersystems.signalservice.internal.push.AuthCredentials;

import java.io.IOException;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import breez_sdk_spark.LnurlPayResponse;
import breez_sdk_spark.PaymentStatus;

public final class Wallet {

  private static final String TAG         = Log.tag(Wallet.class);

  private final MobileCoinConfig        mobileCoinConfig;
  private final MobileCoinClient        mobileCoinClient;
  private final AccountKey              account;
  private final MobileCoinPublicAddress publicAddress;

  private BreezSdkWrapper breezSdkWrapper;

  public Wallet(@NonNull MobileCoinConfig mobileCoinConfig, @NonNull Entropy paymentsEntropy) {
    this.mobileCoinConfig = mobileCoinConfig;
    try {
      this.account       = AccountKey.fromBip39Entropy(paymentsEntropy.getBytes(), 0, mobileCoinConfig.getFogReportUri(), "", mobileCoinConfig.getFogAuthoritySpki());
      this.publicAddress = new MobileCoinPublicAddress(account.getPublicAddress());

      this.mobileCoinClient = new MobileCoinClient(account,
                                                   mobileCoinConfig.getFogUri(),
                                                   mobileCoinConfig.getConsensusUris(),
                                                   mobileCoinConfig.getConfig(),
                                                   TransportProtocol.forGRPC());

      this.breezSdkWrapper = BreezSdkWrapper.Companion.connectWrapper(paymentsEntropy.getBytes());
    } catch (InvalidUriException | BadEntropyException e) {
      throw new AssertionError(e);
    }
    try {
      reauthorizeClient();
    } catch (IOException e) {
      Log.w(TAG, "Failed to authorize client", e);
    }
  }

  public @NotNull Map<String, Double> getExchangeRate() {
    return breezSdkWrapper.getConversions();
  }

  public @NonNull MobileCoinPublicAddress getMobileCoinPublicAddress() {
    return publicAddress;
  }

  public @NonNull LightningAddress getLightningAddress() {
    return this.breezSdkWrapper.getLightningAddress();
  }

  @AnyThread
  public @NonNull Balance getCachedBalance() {
    return SignalStore.payments().mobileCoinLatestBalance();
  }

  @AnyThread
  public @NonNull MobileCoinLedgerWrapper getCachedLedger() {
    return SignalStore.payments().mobileCoinLatestFullLedger();
  }

  @WorkerThread
  public @NonNull MobileCoinLedgerWrapper getFullLedger() {
    return getFullLedger(true);
  }

  @WorkerThread
  private @NonNull MobileCoinLedgerWrapper getFullLedger(boolean retryOnAuthFailure) {
    PaymentsValues paymentsValues = SignalStore.payments();
    try {
      MobileCoinLedgerWrapper ledger = tryGetFullLedger(null);

      paymentsValues.setMobileCoinFullLedger(Objects.requireNonNull(ledger));
    } catch (IOException | FogSyncException e) {
      if ((retryOnAuthFailure && e.getCause() instanceof NetworkException) &&
          (((NetworkException) e.getCause()).statusCode == 401))
      {
        Log.w(TAG, "Failed to get up to date ledger, due to temp auth failure, retrying", e);
        return getFullLedger(false);
      } else {
        Log.w(TAG, "Failed to get up to date ledger", e);
      }
    }

    return getCachedLedger();
  }

  /**
   * Retrieve a user owned ledger
   * @param minimumBlockIndex require the returned ledger to include all TxOuts to at least minimumBlockIndex
   * @return a wrapped MobileCoin ledger that contains only TxOuts owned by the AccountKey
   *         or null if the requested minimumBlockIndex cannot be retrieved
   */
  @WorkerThread
  public @NonNull MobileCoinLedgerWrapper tryGetFullLedger(@Nullable Long minimumBlockIndex) throws IOException, FogSyncException {
    return new MobileCoinLedgerWrapper(breezSdkWrapper);
  }

  @WorkerThread
  public @NonNull Money.Satoshi getFee(@NonNull Money amount) throws IOException {
    return Money.Satoshi.ZERO;
  }

  @WorkerThread
  public @NonNull PaymentSubmissionResult sendPayment(@NonNull LightningAddress to,
                                                      @NonNull Money amount,
                                                      @NonNull Money totalFee)
  {
    List<TransactionSubmissionResult> transactionSubmissionResults = new LinkedList<>();
    sendPayment(to, amount, totalFee, false, transactionSubmissionResults);
    return new PaymentSubmissionResult(transactionSubmissionResults);
  }

  @WorkerThread
  public @NonNull TransactionStatusResult getSentTransactionStatus(@NonNull PaymentTransactionId transactionId) throws IOException, FogSyncException {
    return TransactionStatusResult.complete(0L);
//    try {
//      PaymentTransactionId.MobileCoin mobcoinTransaction = (PaymentTransactionId.MobileCoin) transactionId;
//      Transaction                     transaction        = Transaction.fromBytes(mobcoinTransaction.getTransaction());
//      Transaction.Status              status             = mobileCoinClient.getTransactionStatusQuick(transaction);
//      switch (status) {
//        case UNKNOWN:
//          Log.w(TAG, "Unknown sent Transaction Status");
//          return TransactionStatusResult.inProgress();
//        case FAILED:
//          return TransactionStatusResult.failed();
//        case ACCEPTED:
//          return TransactionStatusResult.complete(status.getBlockIndex().longValue());
//        default:
//          throw new IllegalStateException("Unknown Transaction Status: " + status);
//      }
//    } catch (SerializationException e) {
//      Log.w(TAG, e);
//      return TransactionStatusResult.failed();
//    } catch (NetworkException e) {
//      Log.w(TAG, e);
//      throw new IOException(e);
//    }
  }

  @WorkerThread
  public @NonNull ReceivedTransactionStatus getReceivedTransactionStatus(@NonNull byte[] receiptBytes) throws IllegalStateException{
    LnurlPayResponse          response = BreezSdkWrapper.Companion.deserializeLnurlPayResponse(receiptBytes);
    PaymentStatus             status   = response.getPayment().getStatus();

    if (status == PaymentStatus.FAILED) {
      return ReceivedTransactionStatus.failed();
    } else {
      final BigInteger amount = response.getPayment().getAmount();
      return ReceivedTransactionStatus.complete(Money.satoshi(amount), 0L);
    }
  }

  @WorkerThread
  private void sendPayment(@NonNull LightningAddress to,
                           @NonNull Money amount,
                           @NonNull Money totalFee,
                           boolean defragmentFirst,
                           @NonNull List<TransactionSubmissionResult> results)
  {
    Log.i(TAG, "Sending payment to " + to + " with amount " + amount + " and fee " + totalFee);

    try {
      byte[] response = breezSdkWrapper.sendPayment(to.getPaymentAddress(), amount.requireBitcoin().toSatoshiBigInteger());

      results.add(TransactionSubmissionResult.successfullySubmitted(new PaymentTransactionId.MobileCoin(new byte[0], response, totalFee.requireBitcoin())));
    } catch (UnsupportedOperationException e) {
      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    //    Money.MobileCoin defragmentFees = Money.MobileCoin.ZERO;
//    if (defragmentFirst) {
//      try {
//        defragmentFees = defragment(amount., results);
//        SignalStore.payments().setEnclaveFailure(false);
//      } catch (InsufficientFundsException e) {
//        Log.w(TAG, "Insufficient funds", e);
//        results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.INSUFFICIENT_FUNDS, true));
//        return;
//      } catch (AttestationException e) {
//        results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, true));
//        SignalStore.payments().setEnclaveFailure(true);
//        return;
//      } catch (TimeoutException | InvalidTransactionException | InvalidFogResponse | TransactionBuilderException | NetworkException | FogReportException | FogSyncException e) {
//        Log.w(TAG, "Defragment failed", e);
//        results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, true));
//        return;
//      }
//    }
//
//    Money.MobileCoin   feeMobileCoin      = totalFee.subtract(defragmentFees).requireMobileCoin();
//    BigInteger         picoMob            = amount.requireMobileCoin().toPicoMobBigInteger();
//    PendingTransaction pendingTransaction = null;
//
//    Log.i(TAG, String.format("Total fee advised: %s\nDefrag fees: %s\nTransaction fee: %s", totalFee, defragmentFees, feeMobileCoin));
//
//    if (!feeMobileCoin.isPositive()) {
//      Log.i(TAG, "No fee left after defrag");
//      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
//      return;
//    }
//
//    try {
//      AccountSnapshot accountSnapshot = getCachedAccountSnapshot();
//      if (accountSnapshot != null) {
//        pendingTransaction = accountSnapshot.prepareTransaction(to.getAddress(),
//                                                                Amount.ofMOB(picoMob),
//                                                                Amount.ofMOB(feeMobileCoin.toPicoMobBigInteger()),
//                                                                TxOutMemoBuilder.createSenderAndDestinationRTHMemoBuilder(account));
//      } else {
//        pendingTransaction = mobileCoinClient.prepareTransaction(to.getAddress(),
//                                                                 Amount.ofMOB(picoMob),
//                                                                 Amount.ofMOB(feeMobileCoin.toPicoMobBigInteger()),
//                                                                 TxOutMemoBuilder.createSenderAndDestinationRTHMemoBuilder(account));
//      }
//      SignalStore.payments().setEnclaveFailure(false);
//    } catch (InsufficientFundsException e) {
//      Log.w(TAG, "Insufficient funds", e);
//      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.INSUFFICIENT_FUNDS, false));
//    } catch (FeeRejectedException e) {
//      Log.w(TAG, "Fee rejected " + totalFee, e);
//      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
//    } catch (InvalidFogResponse | FogReportException e) {
//      Log.w(TAG, "Invalid fog response", e);
//      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
//    } catch (FragmentedAccountException e) {
//      if (defragmentFirst) {
//        Log.w(TAG, "Account is fragmented, but already tried to defragment", e);
//        results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
//      } else {
//        Log.i(TAG, "Account is fragmented, defragmenting and retrying");
//        sendPayment(to, amount, totalFee, true, results);
//      }
//    } catch (AttestationException e) {
//      Log.w(TAG, "Attestation problem", e);
//      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
//      SignalStore.payments().setEnclaveFailure(true);
//    } catch (NetworkException e) {
//      Log.w(TAG, "Network problem", e);
//      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
//    } catch (TransactionBuilderException e) {
//      Log.w(TAG, "Builder problem", e);
//      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
//    } catch(FogSyncException e) {
//      Log.w(TAG, "Fog currently out of sync", e);
//      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.NETWORK_FAILURE, false));
//    }
//
//    if (pendingTransaction == null) {
//      Log.w(TAG, "Failed to create pending transaction");
//      return;
//    }
//
//    try {
//      Log.i(TAG, "Submitting transaction");
//      mobileCoinClient.submitTransaction(pendingTransaction.getTransaction());
//      Log.i(TAG, "Transaction submitted");
//      results.add(TransactionSubmissionResult.successfullySubmitted(new PaymentTransactionId.MobileCoin(pendingTransaction.getTransaction().toByteArray(), pendingTransaction.getReceipt().toByteArray(), feeMobileCoin)));
//      SignalStore.payments().setEnclaveFailure(false);
//    } catch (NetworkException e) {
//      Log.w(TAG, "Network problem", e);
//      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.NETWORK_FAILURE, false));
//    } catch (InvalidTransactionException e) {
//      Log.w(TAG, "Invalid transaction", e);
//      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
//    } catch (AttestationException e) {
//      Log.w(TAG, "Attestation problem", e);
//      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
//      SignalStore.payments().setEnclaveFailure(true);
//    } catch (SerializationException e) {
//      Log.w(TAG, "Serialization problem", e);
//      results.add(TransactionSubmissionResult.failure(TransactionSubmissionResult.ErrorCode.GENERIC_FAILURE, false));
//    }
  }

  /**
   * Attempts to defragment the account. It will at most merge 16 UTXOs to 1.
   * Therefore it may need to be called more than once before a certain payment is possible.
   */
  @WorkerThread
  private @NonNull Money.MobileCoin defragment(@NonNull Money.MobileCoin amount, @NonNull List<TransactionSubmissionResult> results)
      throws TransactionBuilderException, NetworkException, InvalidTransactionException, AttestationException, FogReportException, InvalidFogResponse, TimeoutException, InsufficientFundsException, FogSyncException
  {
    Log.i(TAG, "Defragmenting account");
    DefragDelegate defragDelegate = new DefragDelegate(mobileCoinClient, results);
    mobileCoinClient.defragmentAccount(Amount.ofMOB(amount.toPicoMobBigInteger()), defragDelegate, true);
    Log.i(TAG, "Account defragmented at a cost of " + defragDelegate.totalFeesSpent);
    return defragDelegate.totalFeesSpent;
  }

  private void reauthorizeClient() throws IOException {
    AuthCredentials authorization = mobileCoinConfig.getAuth();
    mobileCoinClient.setFogBasicAuthorization(authorization.username(), authorization.password());
  }

  public void refresh() {
    getFullLedger();
  }

  public enum TransactionStatus {
    COMPLETE,
    IN_PROGRESS,
    FAILED
  }

  public static final class TransactionStatusResult {
    private final TransactionStatus transactionStatus;
    private final long              blockIndex;

    public TransactionStatusResult(@NonNull TransactionStatus transactionStatus,
                                   long blockIndex)
    {
      this.transactionStatus = transactionStatus;
      this.blockIndex        = blockIndex;
    }

    static TransactionStatusResult inProgress() {
      return new TransactionStatusResult(TransactionStatus.IN_PROGRESS, 0);
    }

    static TransactionStatusResult failed() {
      return new TransactionStatusResult(TransactionStatus.FAILED, 0);
    }

    static TransactionStatusResult complete(long blockIndex) {
      return new TransactionStatusResult(TransactionStatus.COMPLETE, blockIndex);
    }

    public @NonNull TransactionStatus getTransactionStatus() {
      return transactionStatus;
    }

    public long getBlockIndex() {
      return blockIndex;
    }
  }

  public static final class ReceivedTransactionStatus {

    private final TransactionStatus status;
    private final Money             amount;
    private final long              blockIndex;

    public static ReceivedTransactionStatus failed() {
      return new ReceivedTransactionStatus(TransactionStatus.FAILED, null, 0);
    }

    public static ReceivedTransactionStatus inProgress() {
      return new ReceivedTransactionStatus(TransactionStatus.IN_PROGRESS, null, 0);
    }

    public static ReceivedTransactionStatus complete(@NonNull Money amount, long blockIndex) {
      return new ReceivedTransactionStatus(TransactionStatus.COMPLETE, amount, blockIndex);
    }

    private ReceivedTransactionStatus(@NonNull TransactionStatus status, @Nullable Money amount, long blockIndex) {
      this.status     = status;
      this.amount     = amount;
      this.blockIndex = blockIndex;
    }

    public @NonNull TransactionStatus getStatus() {
      return status;
    }

    public @NonNull Money getAmount() {
      if (status != TransactionStatus.COMPLETE || amount == null) {
        throw new IllegalStateException();
      }
      return amount;
    }

    public long getBlockIndex() {
      return blockIndex;
    }
  }

  private static class DefragDelegate implements DefragmentationDelegate {
    private       Money.MobileCoin                  totalFeesSpent = Money.MobileCoin.ZERO;

    DefragDelegate(@NonNull MobileCoinClient mobileCoinClient, @NonNull List<TransactionSubmissionResult> results) {}

    @Override
    public void onStart() {
      Log.i(TAG, "Defragmenting start");
    }

    @Override
    public boolean onStepReady(@NonNull PendingTransaction pendingTransaction, @NonNull BigInteger fee) {
      return true;
    }

    @Override
    public void onComplete() {
      Log.i(TAG, "Defragmenting complete");
    }

    @Override
    public void onCancel() {
      Log.w(TAG, "Defragmenting cancel");
    }
  }
}
