/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments

import breez_sdk_spark.ByteBuffer
import breez_sdk_spark.FfiConverterTypeLnurlPayResponse
import breez_sdk_spark.LnurlPayResponse
import breez_sdk_spark.PaymentDetails
import breez_sdk_spark.PaymentStatus
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.payments.proto.LightningReceipt
import java.math.BigInteger

/** Everything Radar actually consumes from a stored payment receipt. */
data class ReceiptData(
  /** Lightning paymentHash or on-chain txId; null when the payment has none (spark/token). */
  val identifier: String?,
  /** The breez `payment.id`. */
  val paymentId: String?,
  val status: ReceiptStatus,
  val amountSats: BigInteger,
  val feeSats: BigInteger,
  /** Seconds since epoch, from the breez payment. */
  val timestampSeconds: Long,
  val detailsType: ReceiptDetailsType
)

enum class ReceiptStatus { UNKNOWN, PENDING, COMPLETED, FAILED }

enum class ReceiptDetailsType { NONE, LIGHTNING, SPARK, TOKEN, DEPOSIT, WITHDRAW }

/**
 * Encodes/decodes the payment receipt blob that is stored in [org.thoughtcrime.securesms.database.PaymentTable]
 * and sent to the recipient inside the payment notification message.
 *
 * The blob is dual-format:
 *
 * ```
 * [ uniffi-serialized LnurlPayResponse ][ LightningReceipt proto ][ len: u32 BE ][ "RDRCPT01" ]
 * ```
 *
 * - The uniffi prefix is what Radar iOS (any breez_sdk_spark version) and older readers parse.
 *   uniffi reads are positional and ignore trailing bytes, so the appended tail is invisible to them.
 * - The proto tail is authoritative for us: uniffi's wire format is NOT stable across
 *   breez_sdk_spark releases (0.14.0 receipts stopped parsing under 0.18.0), while proto
 *   field-tags survive any future SDK bump.
 *
 * [decode] therefore tries, in order:
 *  1. the magic-delimited proto tail,
 *  2. the current SDK's uniffi reader (tail-less blobs written by the current SDK, incl. iOS 0.18 senders),
 *  3. a frozen hand-rolled reader for the 0.14.0 layout ([Breez014ReceiptReader]) — receipts from
 *     old-breez iOS senders and pre-upgrade local installs,
 *  4. null — callers treat an undecodable receipt as a non-fatal skip.
 */
object ReceiptCodec {

  private val TAG = Log.tag(ReceiptCodec::class.java)

  /** Tail magic: "RDRCPT" + 2-digit structure version. Bump the digits only if the footer layout changes. */
  private val MAGIC = "RDRCPT01".toByteArray(Charsets.US_ASCII)
  private const val LEN_SIZE = 4
  private val FOOTER_SIZE = LEN_SIZE + MAGIC.size

  /**
   * Serializes [response] for storage/wire: the plain uniffi bytes (trimmed of the allocation
   * padding the previous implementation kept) plus the version-stable proto tail.
   */
  @JvmStatic
  fun encode(response: LnurlPayResponse): ByteArray {
    val size = FfiConverterTypeLnurlPayResponse.allocationSize(response)
    val nioBuffer = java.nio.ByteBuffer.allocate(size.toInt())
    FfiConverterTypeLnurlPayResponse.write(response, ByteBuffer(nioBuffer))
    // allocationSize is a worst-case bound (3 bytes/char for strings); position() is the real end.
    val uniffiBytes = nioBuffer.array().copyOf(nioBuffer.position())

    val proto = toProto(fromSdk(response)).encode()
    val footer = java.nio.ByteBuffer.allocate(FOOTER_SIZE)
    footer.putInt(proto.size)
    footer.put(MAGIC)

    return uniffiBytes + proto + footer.array()
  }

  /** Decodes a receipt blob from any supported generation, or null if nothing can read it. */
  @JvmStatic
  fun decode(receipt: ByteArray): ReceiptData? {
    parseTail(receipt)?.let { return it }

    try {
      return fromSdk(deserializeLnurlPayResponse(receipt))
    } catch (e: Throwable) {
      // Not written by the current SDK version (e.g. an old-breez iOS sender, or a receipt stored
      // before an SDK upgrade). Positional uniffi reads fail with BufferUnderflowException or
      // similar once the layout shifts. Throwable rather than RuntimeException on purpose: a
      // garbage length field makes the SDK reader attempt a giant transient ByteArray, which
      // surfaces as OutOfMemoryError — for a blob this small that is just another decode failure,
      // not a real OOM, and it must not crash the calling job.
      Log.d(TAG, "Receipt is not current-SDK uniffi; trying 0.14 layout", e)
    }

    val legacy = Breez014ReceiptReader.read(receipt)
    if (legacy == null) {
      Log.w(TAG, "Unable to decode receipt under any known format (${receipt.size} bytes)")
    }
    return legacy
  }

  /** Maps an in-memory SDK response to the fields we persist. */
  @JvmStatic
  fun fromSdk(response: LnurlPayResponse): ReceiptData {
    val payment = response.payment
    val (identifier, detailsType) = when (val details = payment.details) {
      is PaymentDetails.Lightning -> details.htlcDetails.paymentHash to ReceiptDetailsType.LIGHTNING
      is PaymentDetails.Deposit -> details.txId to ReceiptDetailsType.DEPOSIT
      is PaymentDetails.Withdraw -> details.txId to ReceiptDetailsType.WITHDRAW
      is PaymentDetails.Spark -> null to ReceiptDetailsType.SPARK
      is PaymentDetails.Token -> null to ReceiptDetailsType.TOKEN
      null -> null to ReceiptDetailsType.NONE
    }
    val status = when (payment.status) {
      PaymentStatus.COMPLETED -> ReceiptStatus.COMPLETED
      PaymentStatus.PENDING -> ReceiptStatus.PENDING
      PaymentStatus.FAILED -> ReceiptStatus.FAILED
    }
    return ReceiptData(
      identifier = identifier,
      paymentId = payment.id,
      status = status,
      amountSats = payment.amount,
      feeSats = payment.fees,
      timestampSeconds = payment.timestamp.toLong(),
      detailsType = detailsType
    )
  }

  private fun deserializeLnurlPayResponse(serialized: ByteArray): LnurlPayResponse {
    return FfiConverterTypeLnurlPayResponse.read(ByteBuffer(java.nio.ByteBuffer.wrap(serialized)))
  }

  private fun parseTail(receipt: ByteArray): ReceiptData? {
    if (receipt.size < FOOTER_SIZE) return null
    for (i in MAGIC.indices) {
      if (receipt[receipt.size - MAGIC.size + i] != MAGIC[i]) return null
    }
    val lenOffset = receipt.size - FOOTER_SIZE
    val len = java.nio.ByteBuffer.wrap(receipt, lenOffset, LEN_SIZE).int
    if (len < 0 || len > lenOffset) return null

    val proto = try {
      LightningReceipt.ADAPTER.decode(receipt.copyOfRange(lenOffset - len, lenOffset))
    } catch (e: Exception) {
      Log.w(TAG, "Receipt has a valid tail magic but an unparseable proto payload", e)
      return null
    }
    return fromProto(proto)
  }

  private fun toProto(data: ReceiptData): LightningReceipt {
    return LightningReceipt(
      identifier = data.identifier ?: "",
      paymentId = data.paymentId ?: "",
      status = when (data.status) {
        ReceiptStatus.PENDING -> LightningReceipt.Status.PENDING
        ReceiptStatus.COMPLETED -> LightningReceipt.Status.COMPLETED
        ReceiptStatus.FAILED -> LightningReceipt.Status.FAILED
        ReceiptStatus.UNKNOWN -> LightningReceipt.Status.UNKNOWN
      },
      amountSats = data.amountSats.toString(),
      feeSats = data.feeSats.toString(),
      timestamp = data.timestampSeconds,
      detailsType = when (data.detailsType) {
        ReceiptDetailsType.NONE -> LightningReceipt.DetailsType.NONE
        ReceiptDetailsType.LIGHTNING -> LightningReceipt.DetailsType.LIGHTNING
        ReceiptDetailsType.SPARK -> LightningReceipt.DetailsType.SPARK
        ReceiptDetailsType.TOKEN -> LightningReceipt.DetailsType.TOKEN
        ReceiptDetailsType.DEPOSIT -> LightningReceipt.DetailsType.DEPOSIT
        ReceiptDetailsType.WITHDRAW -> LightningReceipt.DetailsType.WITHDRAW
      }
    )
  }

  private fun fromProto(proto: LightningReceipt): ReceiptData? {
    val amount = proto.amountSats.toBigIntegerOrNull() ?: return null
    val fee = proto.feeSats.toBigIntegerOrNull() ?: return null
    return ReceiptData(
      identifier = proto.identifier.ifEmpty { null },
      paymentId = proto.paymentId.ifEmpty { null },
      status = when (proto.status) {
        LightningReceipt.Status.PENDING -> ReceiptStatus.PENDING
        LightningReceipt.Status.COMPLETED -> ReceiptStatus.COMPLETED
        LightningReceipt.Status.FAILED -> ReceiptStatus.FAILED
        LightningReceipt.Status.UNKNOWN -> ReceiptStatus.UNKNOWN
      },
      amountSats = amount,
      feeSats = fee,
      timestampSeconds = proto.timestamp,
      detailsType = when (proto.detailsType) {
        LightningReceipt.DetailsType.NONE -> ReceiptDetailsType.NONE
        LightningReceipt.DetailsType.LIGHTNING -> ReceiptDetailsType.LIGHTNING
        LightningReceipt.DetailsType.SPARK -> ReceiptDetailsType.SPARK
        LightningReceipt.DetailsType.TOKEN -> ReceiptDetailsType.TOKEN
        LightningReceipt.DetailsType.DEPOSIT -> ReceiptDetailsType.DEPOSIT
        LightningReceipt.DetailsType.WITHDRAW -> ReceiptDetailsType.WITHDRAW
      }
    )
  }

  private fun String.toBigIntegerOrNull(): BigInteger? {
    if (isEmpty()) return BigInteger.ZERO
    return try {
      BigInteger(this)
    } catch (e: NumberFormatException) {
      null
    }
  }
}
