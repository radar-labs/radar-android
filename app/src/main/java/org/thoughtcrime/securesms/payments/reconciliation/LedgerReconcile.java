package org.thoughtcrime.securesms.payments.reconciliation;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;


import org.signal.core.util.MapUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.payments.*;
import org.thoughtcrime.securesms.payments.history.TransactionReconstruction;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public final class LedgerReconcile {

  private static final String TAG = Log.tag(LedgerReconcile.class);

  @WorkerThread
  public static @NonNull List<Payment> reconcile(@NonNull Collection<? extends Payment> localPaymentTransactions,
                                                 @NonNull MobileCoinLedgerWrapper ledgerWrapper)
  {
    long start = System.currentTimeMillis();
    try {
      return reconcile(localPaymentTransactions, ledgerWrapper.getAllTxos());
    } finally {
      Log.d(TAG, String.format(Locale.US, "Took %d ms - Ledger %d, Local %d", System.currentTimeMillis() - start, ledgerWrapper.getAllTxos().size(), localPaymentTransactions.size()));
    }
  }

  @WorkerThread
  private static @NonNull List<Payment> reconcile(@NonNull Collection<? extends Payment> allLocalPaymentTransactions,
                                                  @NonNull List<MobileCoinLedgerWrapper.OwnedTxo> allTxOuts)
  {
    List<? extends Payment> nonFailedLocalPayments = allLocalPaymentTransactions.stream().filter(i -> i.getState() != State.FAILED).collect(Collectors.toList());
    Set<String> allKnownKeys = new HashSet<>(nonFailedLocalPayments.size());

    for (Payment paymentTransaction : nonFailedLocalPayments) {
      byte[] receiptBytes = paymentTransaction.getReceipt();
      if (receiptBytes == null || receiptBytes.length == 0) continue;

      String key = BreezSdkWrapper.Companion.getIdentifierFromReceipt(receiptBytes);
      if (key == null) continue;

      allKnownKeys.add(key);
    }

    Set<MobileCoinLedgerWrapper.OwnedTxo> knownTxosByPublicKeys = allTxOuts.stream()
                                                                        .filter(t -> allKnownKeys.contains(t.getIdentifier()))
                                                                        .collect(Collectors.toSet());

    // any TXO that we can't pair up the pub key for, we don't have a detail for how it got into the account
    Set<MobileCoinLedgerWrapper.OwnedTxo> unknownTxOutsReceived = new HashSet<>(allTxOuts);
    unknownTxOutsReceived.removeAll(knownTxosByPublicKeys);

    if (unknownTxOutsReceived.isEmpty()) {
      return allLocalPaymentTransactions.stream().map(t -> (Payment) t).collect(Collectors.toList());
    }

    List<Payment> reconstructedPayments = new ArrayList<>(unknownTxOutsReceived.size());
    List<Payment> localPayments         = new ArrayList<>(allLocalPaymentTransactions.size());
    localPayments.addAll(allLocalPaymentTransactions);

    for (MobileCoinLedgerWrapper.OwnedTxo txo : unknownTxOutsReceived) {
      reconstructedPayments.add(new ReconstructedPayment(0L, txo.getReceivedInBlockTimestamp(), txo.getDirection(), txo.getValue(), txo.getFee()));
    }

    reconstructedPayments.sort(Payment.DESCENDING_TIMESTAMP);

    return ZipList.zipList(localPayments, reconstructedPayments, Payment.DESCENDING_TIMESTAMP);
  }

  public static class BlockOverridePayment extends PaymentDecorator {
    private final long blockIndex;
    private final long blockTimestamp;

    static Payment override(@NonNull Payment payment, long blockIndex, long blockTimestamp) {
      if (payment.getBlockTimestamp() == blockTimestamp && payment.getBlockIndex() == blockIndex) {
        return payment;
      } else {
        return new BlockOverridePayment(payment, blockIndex, blockTimestamp);
      }
    }

    private BlockOverridePayment(@NonNull Payment inner, long blockIndex, long blockTimestamp) {
      super(inner);
      this.blockIndex     = blockIndex;
      this.blockTimestamp = blockTimestamp;
    }

    @Override
    public long getBlockIndex() {
      return blockIndex;
    }

    @Override
    public long getBlockTimestamp() {
      return blockTimestamp != 0 ? blockTimestamp
                                 : super.getBlockTimestamp();
    }
  }
}
