/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments

import java.math.BigInteger
import java.nio.BufferUnderflowException

/**
 * Frozen, read-only decoder for `LnurlPayResponse` blobs serialized by **breez_sdk_spark 0.14.0**
 * (uniffi wire format). Receipts in this layout come from old-breez Radar iOS senders and from
 * local installs that stored receipts before the 0.18.0 upgrade; the 0.18.0 bindings cannot read
 * them (`PaymentDetails.Lightning` gained a trailing field, so positional reads underflow).
 *
 * The layout below was derived from the bytecode of the real 0.14.0 bindings AAR and is verified
 * against golden blobs produced by those bindings in [ReceiptCodecTest]. It must NEVER be updated
 * to a newer SDK layout — it exists precisely to keep the 0.14.0 generation readable. Newer
 * receipts are covered by the proto tail ([ReceiptCodec]) or the current SDK's own reader.
 *
 * uniffi encoding primitives (all big-endian):
 *  - String:    i32 length + UTF-8 bytes
 *  - u128:      String holding the decimal value
 *  - Option<T>: 1 flag byte (0/1) + T when present
 *  - enum:      i32, 1-based declaration index
 *  - bool:      1 byte
 *
 * ```
 * LnurlPayResponse = Payment, Opt<SuccessActionProcessed>
 * Payment          = id:Str, paymentType:Enum(2), status:Enum(3), amount:u128, fees:u128,
 *                    timestamp:i64, method:Enum(6), Opt<PaymentDetails>, Opt<ConversionDetails>
 * PaymentDetails   = variant:i32 of
 *   1 Spark      = Opt<SparkInvoicePaymentDetails>, Opt<SparkHtlcDetails>, Opt<ConversionInfo>
 *   2 Token      = TokenMetadata, txHash:Str, txType:Enum(3), Opt<SparkInvoicePaymentDetails>, Opt<ConversionInfo>
 *   3 Lightning  = Opt<Str>, invoice:Str, destinationPubkey:Str, SparkHtlcDetails,
 *                  Opt<LnurlPayInfo>, Opt<LnurlWithdrawInfo>, Opt<LnurlReceiveMetadata>
 *   4 Withdraw   = txId:Str
 *   5 Deposit    = txId:Str
 * SparkInvoicePaymentDetails = Opt<Str>, Str
 * SparkHtlcDetails           = paymentHash:Str, Opt<Str>, expiryTime:i64, status:Enum(3)
 * TokenMetadata              = Str, Str, Str, Str, decimals:i32, maxSupply:u128, bool
 * ConversionInfo             = Str, Str, status:Enum(5), Opt<u128>, Opt<ConversionPurpose>, Opt<Enum(2)>
 * ConversionPurpose          = variant:i32 of 1:{Str}, 2:{}, 3:{}
 * ConversionDetails          = status:Enum(5), Opt<ConversionStep>, Opt<ConversionStep>
 * ConversionStep             = Str, u128, u128, method:Enum(6), Opt<TokenMetadata>, Opt<Enum(2)>
 * LnurlPayInfo               = Opt<Str> x4, Opt<SuccessActionProcessed>, Opt<SuccessAction>
 * LnurlWithdrawInfo          = Str
 * LnurlReceiveMetadata       = Opt<Str> x3
 * SuccessActionProcessed     = variant:i32 of 1 Aes:{AesResult}, 2 Message:{Str}, 3 Url:{UrlData}
 * AesResult                  = variant:i32 of 1 Decrypted:{Str,Str}, 2 ErrorStatus:{Str}
 * SuccessAction              = variant:i32 of 1 Aes:{Str,Str,Str}, 2 Message:{Str}, 3 Url:{UrlData}
 * UrlData                    = Str, Str, bool
 * ```
 */
internal object Breez014ReceiptReader {

  /** Guards string allocations against garbage that happens to parse as a huge length. */
  private const val MAX_STRING_BYTES = 1 shl 20

  /**
   * Parses [receipt] as a 0.14.0-layout blob, returning null on any structural anomaly.
   * Trailing bytes are permitted: the pre-tail Android implementation stored uniffi's worst-case
   * allocation (zero padding after the value), and [ReceiptCodec] blobs carry a proto tail.
   */
  fun read(receipt: ByteArray): ReceiptData? {
    return try {
      Walker(java.nio.ByteBuffer.wrap(receipt)).readLnurlPayResponse()
    } catch (e: BadLayout) {
      null
    } catch (e: BufferUnderflowException) {
      null
    } catch (e: NumberFormatException) {
      null
    }
  }

  private class BadLayout : Exception(null, null, false, false)

  private class Walker(private val buf: java.nio.ByteBuffer) {

    fun readLnurlPayResponse(): ReceiptData {
      // Payment
      val paymentId = str()
      enum(2) // paymentType SEND/RECEIVE
      val status = when (enum(3)) {
        1 -> ReceiptStatus.COMPLETED
        2 -> ReceiptStatus.PENDING
        else -> ReceiptStatus.FAILED
      }
      val amount = u128()
      val fees = u128()
      val timestamp = i64()
      enum(6) // method

      var identifier: String? = null
      var detailsType = ReceiptDetailsType.NONE
      opt {
        when (enum(5)) {
          1 -> { // Spark
            detailsType = ReceiptDetailsType.SPARK
            opt { sparkInvoicePaymentDetails() }
            opt { sparkHtlcDetails() }
            opt { conversionInfo() }
          }
          2 -> { // Token
            detailsType = ReceiptDetailsType.TOKEN
            tokenMetadata()
            str() // txHash
            enum(3) // txType
            opt { sparkInvoicePaymentDetails() }
            opt { conversionInfo() }
          }
          3 -> { // Lightning
            detailsType = ReceiptDetailsType.LIGHTNING
            opt { str() } // description
            str() // invoice
            str() // destinationPubkey
            identifier = sparkHtlcDetails()
            opt { lnurlPayInfo() }
            opt { str() } // LnurlWithdrawInfo
            opt { lnurlReceiveMetadata() }
          }
          4 -> { // Withdraw
            detailsType = ReceiptDetailsType.WITHDRAW
            identifier = str()
          }
          else -> { // 5 Deposit
            detailsType = ReceiptDetailsType.DEPOSIT
            identifier = str()
          }
        }
      }
      opt { conversionDetails() }
      opt { successActionProcessed() }
      // Trailing bytes are allowed by design; do not require exact consumption.

      return ReceiptData(
        identifier = identifier,
        paymentId = paymentId,
        status = status,
        amountSats = amount,
        feeSats = fees,
        timestampSeconds = timestamp,
        detailsType = detailsType
      )
    }

    /** @return the paymentHash */
    private fun sparkHtlcDetails(): String {
      val paymentHash = str()
      opt { str() } // preimage
      i64() // expiryTime
      enum(3) // status
      return paymentHash
    }

    private fun sparkInvoicePaymentDetails() {
      opt { str() }
      str()
    }

    private fun tokenMetadata() {
      str(); str(); str(); str()
      i32() // decimals
      u128() // maxSupply
      bool()
    }

    private fun conversionInfo() {
      str(); str()
      enum(5) // status
      opt { u128() }
      opt { conversionPurpose() }
      opt { enum(2) } // AmountAdjustmentReason
    }

    private fun conversionPurpose() {
      when (enum(3)) {
        1 -> str() // OngoingPayment(paymentId)
        else -> Unit // 2, 3: field-less variants
      }
    }

    private fun conversionDetails() {
      enum(5) // status
      opt { conversionStep() }
      opt { conversionStep() }
    }

    private fun conversionStep() {
      str()
      u128()
      u128()
      enum(6) // method
      opt { tokenMetadata() }
      opt { enum(2) } // AmountAdjustmentReason
    }

    private fun lnurlPayInfo() {
      opt { str() }; opt { str() }; opt { str() }; opt { str() }
      opt { successActionProcessed() }
      opt { successAction() }
    }

    private fun lnurlReceiveMetadata() {
      opt { str() }; opt { str() }; opt { str() }
    }

    private fun successActionProcessed() {
      when (enum(3)) {
        1 -> aesResult()
        2 -> str() // MessageSuccessActionData
        else -> urlData() // 3
      }
    }

    private fun aesResult() {
      when (enum(2)) {
        1 -> { str(); str() } // Decrypted(description, plaintext)
        else -> str() // 2 ErrorStatus(reason)
      }
    }

    private fun successAction() {
      when (enum(3)) {
        1 -> { str(); str(); str() } // AesSuccessActionData
        2 -> str() // MessageSuccessActionData
        else -> urlData() // 3
      }
    }

    private fun urlData() {
      str(); str(); bool()
    }

    // Primitives ------------------------------------------------------------------------------

    private fun fail(): Nothing = throw BadLayout()

    private fun i32(): Int {
      if (buf.remaining() < 4) fail()
      return buf.int
    }

    private fun i64(): Long {
      if (buf.remaining() < 8) fail()
      return buf.long
    }

    private fun byte(): Int {
      if (buf.remaining() < 1) fail()
      return buf.get().toInt() and 0xFF
    }

    private fun bool() {
      if (byte() > 1) fail()
    }

    /** uniffi Option flag: strictly 0 or 1 — anything else means we are reading garbage. */
    private inline fun opt(readValue: () -> Unit) {
      when (byte()) {
        0 -> Unit
        1 -> readValue()
        else -> fail()
      }
    }

    /** 1-based uniffi enum index, validated against the 0.14.0 variant count. */
    private fun enum(max: Int): Int {
      val value = i32()
      if (value < 1 || value > max) fail()
      return value
    }

    private fun str(): String {
      val length = i32()
      if (length < 0 || length > MAX_STRING_BYTES || length > buf.remaining()) fail()
      val bytes = ByteArray(length)
      buf.get(bytes)
      return String(bytes, Charsets.UTF_8)
    }

    /** breez encodes u128 as its decimal-string form. */
    private fun u128(): BigInteger {
      val text = str()
      if (text.isEmpty() || text.length > 64) fail()
      return BigInteger(text) // NumberFormatException on garbage -> null via read()
    }
  }
}
