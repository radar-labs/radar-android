package org.thoughtcrime.securesms.payments

import breez_sdk_spark.Payment
import breez_sdk_spark.PaymentDetails
import breez_sdk_spark.PaymentType
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
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

    val identifier: String?
      get() = when (payment.details) {
        is PaymentDetails.Lightning -> (payment.details as PaymentDetails.Lightning).paymentHash
        is PaymentDetails.Deposit -> (payment.details as PaymentDetails.Deposit).txId
        is PaymentDetails.Withdraw -> (payment.details as PaymentDetails.Withdraw).txId
        else -> null
      }

    val paymentId: String
      get() = payment.id

    val publicKey: ByteString
      get() {
        if (payment.details is PaymentDetails.Lightning) {
          return (payment.details as PaymentDetails.Lightning).destinationPubkey.encodeUtf8()
        }
        return ByteString.EMPTY
      }

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

    val fee: Money
      get() = Money.satoshi(payment.fees)
  }
}
