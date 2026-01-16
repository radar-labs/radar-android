package org.thoughtcrime.securesms.payments

import breez_sdk_spark.Payment
import breez_sdk_spark.PaymentType
import okio.ByteString
import org.whispersystems.signalservice.api.payments.Money
import java.math.BigInteger

class MobileCoinLedgerWrapper(private val lnWrapper: BreezSdkWrapper) {
  val balance: Balance = lnWrapper.getBalance()

  fun serialize(): ByteArray {
    return ByteArray(0)
  }

  val allTxos: List<OwnedTxo?>
    get() = lnWrapper.getTransactions()

  class OwnedTxo internal constructor(private val payment: Payment) {
    val value: Money
      get() {
        if (payment.paymentType == PaymentType.SEND) {
          return Money.satoshi(BigInteger.valueOf(-1L) * payment.amount)
        }
        return Money.satoshi(payment.amount)
      }

    val direction: Direction
    get() {
      if (payment.paymentType == PaymentType.SEND)
        return Direction.SENT
      return Direction.RECEIVED
    }

    val keyImage: ByteString
      get() = ByteString.EMPTY

    val publicKey: ByteString
      get() = ByteString.EMPTY

    val receivedInBlock: Long
      get() = 0

    val spentInBlock: Long?
      get() = null

    val isSpent: Boolean
      get() = this.spentInBlock != null && this.spentInBlock != 0L

    val receivedInBlockTimestamp: Long
      get() = payment.timestamp.toLong() * 1000

    val spentInBlockTimestamp: Long?
      get() = null
  }
}
