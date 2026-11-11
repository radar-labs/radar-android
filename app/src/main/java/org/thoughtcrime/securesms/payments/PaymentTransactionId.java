package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;

import org.whispersystems.signalservice.api.payments.Money;

public abstract class PaymentTransactionId {

  private PaymentTransactionId() {}

  public static final class MobileCoin extends PaymentTransactionId {

    private final byte[]           transaction;
    private final byte[]           receipt;
    private final Money.Satoshi fee;

    public MobileCoin(@NonNull byte[] transaction,
                      @NonNull byte[] receipt,
                      @NonNull Money.Satoshi fee)
    {
      this.transaction = transaction;
      this.receipt     = receipt;
      this.fee         = fee;

//      if (transaction.length == 0 || receipt.length == 0) {
//        throw new AssertionError("Both transaction and receipt must be specified");
//      }
    }

    public @NonNull byte[] getTransaction() {
      return transaction;
    }

    public @NonNull byte[] getReceipt() {
      return receipt;
    }

    public @NonNull Money.Satoshi getFee() {
      return fee;
    }
  }
}
