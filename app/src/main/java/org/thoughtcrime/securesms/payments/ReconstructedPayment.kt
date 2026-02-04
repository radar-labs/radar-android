package org.thoughtcrime.securesms.payments

import org.signal.core.util.UuidUtil
import org.thoughtcrime.securesms.payments.proto.PaymentMetaData
import org.whispersystems.signalservice.api.payments.Money
import java.util.*

class ReconstructedPayment(
  private val blockIndex: Long,
  private val blockTimestamp: Long,
  private val direction: Direction,
  private val amount: Money,
  private val fee: Money
) : Payment {
  override fun getUuid(): UUID = UuidUtil.UNKNOWN_UUID

  override fun getPayee(): Payee = Payee.UNKNOWN

  override fun getBlockIndex(): Long = blockIndex

  override fun getTimestamp(): Long = blockTimestamp

  override fun getBlockTimestamp(): Long = blockTimestamp

  override fun getDirection(): Direction = direction

  override fun getState(): State = State.SUCCESSFUL

  override fun getFailureReason(): FailureReason? = null

  override fun getNote(): String = ""

  override fun getAmount(): Money = amount

  override fun getFee(): Money = fee

  override fun getPaymentMetaData(): PaymentMetaData = PaymentMetaData()

  override fun isSeen(): Boolean = true
}
