/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments

import breez_sdk_spark.ByteBuffer
import breez_sdk_spark.FfiConverterTypeLnurlPayResponse
import breez_sdk_spark.LnurlPayInfo
import breez_sdk_spark.LnurlPayResponse
import breez_sdk_spark.LnurlReceiveMetadata
import breez_sdk_spark.MessageSuccessActionData
import breez_sdk_spark.Payment
import breez_sdk_spark.PaymentDetails
import breez_sdk_spark.PaymentMethod
import breez_sdk_spark.PaymentStatus
import breez_sdk_spark.PaymentType
import breez_sdk_spark.SendPaymentResponse
import breez_sdk_spark.SparkHtlcDetails
import breez_sdk_spark.SparkHtlcStatus
import breez_sdk_spark.SuccessAction
import breez_sdk_spark.SuccessActionProcessed
import breez_sdk_spark.UrlSuccessActionData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.LogRecorder
import java.math.BigInteger

/**
 * The golden `V1`–`V5` vectors below were serialized by the REAL breez_sdk_spark 0.14.0 bindings
 * (reflection harness over the published AAR), i.e. they are byte-identical to what an old-breez
 * Radar iOS sender puts on the wire and to what pre-0.18 installs stored locally. They must never
 * be regenerated with a newer SDK — they pin the frozen [Breez014ReceiptReader] layout.
 *
 * `V6` and `V7` were serialized the same way by the REAL 0.23.1 bindings, the version Radar iOS
 * ships, so they are what a current iOS sender puts on the wire. 0.18.0 and 0.27.2 produce the same
 * bytes for them. They must never be regenerated either: they pin the layout both platforms read.
 */
class ReceiptCodecTest {

  companion object {
    @BeforeClass
    @JvmStatic
    fun setup() {
      Log.initialize(LogRecorder())
    }

    /** Typical Radar lightning-address send: no success action, no conversion. */
    private const val V1_LIGHTNING_PLAIN =
      "0000000a706d742d61626331323300000001000000010000000431353030000000013200000000686d17e000000001010000000300000000106c6e626331357531706578616d706c650000000e30326465616462656566636166650000002065336230633434323938666331633134396166626634633839393666623932340100000008636166656261626500000000686d660000000001010100000016746964653762616e616e614072616461722e6361736800010000000a72616461722e6361736800000000000000"

    /** Lightning with success actions in every slot (variant coverage). */
    private const val V2_LIGHTNING_ACTIONS =
      "0000000a706d742d64656634353600000001000000020000000432303030000000013300000000686d184400000001010000000301000000046d656d6f000000106c6e626332307531706578616d706c650000000830336161626263630000000c6666656564646363626261610000000000686d66010000000101010000000c784072616461722e6361736801000000026869010000000a72616461722e6361736801000000077b226d223a317d0100000003000000096f70656e20746869730000001468747470733a2f2f72616461722e636173682f72010100000002000000037261770000000100000002000000075468616e6b7321"

    /** Spark-details receipt (Radar->Radar routed over spark rails): no identifier. */
    private const val V3_SPARK =
      "0000000b706d742d737061726b2d31000000020000000200000003373737000000013000000000686d18a800000002010000000101000000000f737061726b696e766f69636531323300000000"

    /** No payment details, failed status. */
    private const val V4_NO_DETAILS_FAILED =
      "0000000a706d742d6e756c6c2d310000000100000003000000023130000000013100000000686d190c00000006000000"

    /** Deposit details (0.14 deposits have no vout field). */
    private const val V5_DEPOSIT =
      "00000009706d742d6465702d3100000002000000010000000435303030000000013000000000686d197000000004010000000500000007747869643738390000"

    /** 0.23.1 lightning-address send with every optional slot filled; see [iosLnurlPayResponse]. */
    private const val V6_0_23_LNURL_LIGHTNING =
      "0000000f706d742d6c696768746e696e672d3100000001000000010000000434323030000000013700000000686d1c37000000010100000003010000001250617920666f726765686176656e30343237000000106c6e626334327531706578616d706c650000000e30326465616462656566636166650000004039663836643038313838346337643635396132666561613063353561643031356133626634663162326230623832326364313564366331356230663030613038010000000c707265696d6167652d68657800000000686d660000000002010100000019666f726765686176656e303432374072616461722e6361736801000000096120636f6d6d656e74010000000a72616461722e6361736801000000255b5b22746578742f706c61696e222c2250617920666f726765686176656e30343237225d5d0100000002000000175468616e6b7320666f7220746865207061796d656e74210100000003000000096f70656e20746869730000001468747470733a2f2f72616461722e636173682f720100010000010000000e73656e64657220636f6d6d656e7400000100000003000000096f70656e20746869730000001468747470733a2f2f72616461722e636173682f7201"

    /** 0.23.1 plain BOLT11 send (SendPaymentResponse, without the "BOLT" marker); see [iosBolt11Response]. */
    private const val V7_0_23_BOLT11_PLAIN =
      "0000000f706d742d6c696768746e696e672d3200000001000000020000000431353030000000013200000000686d17e0000000010100000003000000000d6c6e626331706578616d706c650000000830327075626b65790000004039663836643038313838346337643635396132666561613063353561643031356133626634663162326230623832326364313564366331356230663030613038010000000c707265696d6167652d68657800000000686d6600000000020000000000"

    private const val IOS_PAYMENT_HASH = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"

    private fun fromHex(hex: String): ByteArray {
      check(hex.length % 2 == 0)
      return ByteArray(hex.length / 2) { i ->
        ((Character.digit(hex[2 * i], 16) shl 4) + Character.digit(hex[2 * i + 1], 16)).toByte()
      }
    }
  }

  // Golden 0.14 vectors — the frozen legacy branch ---------------------------------------------

  @Test
  fun `decodes 0_14 lightning receipt`() {
    val data = ReceiptCodec.decode(fromHex(V1_LIGHTNING_PLAIN))!!

    assertEquals("e3b0c44298fc1c149afbf4c8996fb924", data.identifier)
    assertEquals("pmt-abc123", data.paymentId)
    assertEquals(ReceiptStatus.COMPLETED, data.status)
    assertEquals(BigInteger.valueOf(1500), data.amountSats)
    assertEquals(BigInteger.valueOf(2), data.feeSats)
    assertEquals(1751980000L, data.timestampSeconds)
    assertEquals(ReceiptDetailsType.LIGHTNING, data.detailsType)
  }

  @Test
  fun `decodes 0_14 lightning receipt with allocation padding`() {
    // The pre-tail Android implementation stored uniffi's worst-case allocation, so real local
    // receipts carry trailing zeros after the value.
    val padded = fromHex(V1_LIGHTNING_PLAIN) + ByteArray(137)

    val data = ReceiptCodec.decode(padded)!!

    assertEquals("e3b0c44298fc1c149afbf4c8996fb924", data.identifier)
    assertEquals(BigInteger.valueOf(1500), data.amountSats)
  }

  @Test
  fun `decodes 0_14 lightning receipt with success actions`() {
    val data = ReceiptCodec.decode(fromHex(V2_LIGHTNING_ACTIONS))!!

    assertEquals("ffeeddccbbaa", data.identifier)
    assertEquals("pmt-def456", data.paymentId)
    assertEquals(ReceiptStatus.PENDING, data.status)
    assertEquals(BigInteger.valueOf(2000), data.amountSats)
    assertEquals(BigInteger.valueOf(3), data.feeSats)
    assertEquals(1751980100L, data.timestampSeconds)
    assertEquals(ReceiptDetailsType.LIGHTNING, data.detailsType)
  }

  @Test
  fun `decodes 0_14 spark receipt without identifier`() {
    val data = ReceiptCodec.decode(fromHex(V3_SPARK))!!

    assertNull(data.identifier)
    assertEquals("pmt-spark-1", data.paymentId)
    assertEquals(ReceiptStatus.PENDING, data.status)
    assertEquals(BigInteger.valueOf(777), data.amountSats)
    assertEquals(ReceiptDetailsType.SPARK, data.detailsType)
  }

  @Test
  fun `decodes 0_14 receipt without details`() {
    val data = ReceiptCodec.decode(fromHex(V4_NO_DETAILS_FAILED))!!

    assertNull(data.identifier)
    assertEquals(ReceiptStatus.FAILED, data.status)
    assertEquals(BigInteger.TEN, data.amountSats)
    assertEquals(ReceiptDetailsType.NONE, data.detailsType)
  }

  @Test
  fun `decodes 0_14 deposit receipt`() {
    val data = ReceiptCodec.decode(fromHex(V5_DEPOSIT))!!

    assertEquals("txid789", data.identifier)
    assertEquals(ReceiptStatus.COMPLETED, data.status)
    assertEquals(BigInteger.valueOf(5000), data.amountSats)
    assertEquals(ReceiptDetailsType.DEPOSIT, data.detailsType)
  }

  // Current-generation encode/decode ------------------------------------------------------------

  @Test
  fun `encode decode round trip`() {
    val encoded = ReceiptCodec.encode(sdkResponse())

    val data = ReceiptCodec.decode(encoded)!!

    assertEquals("hash18", data.identifier)
    assertEquals("pmt-18", data.paymentId)
    assertEquals(ReceiptStatus.COMPLETED, data.status)
    assertEquals(BigInteger.valueOf(4200), data.amountSats)
    assertEquals(BigInteger.valueOf(7), data.feeSats)
    assertEquals(1751981111L, data.timestampSeconds)
    assertEquals(ReceiptDetailsType.LIGHTNING, data.detailsType)
  }

  @Test
  fun `proto tail survives a uniffi layout change`() {
    // Corrupt the uniffi prefix (as a future SDK's positional reader would effectively
    // experience) and confirm the tail alone still yields the receipt.
    val encoded = ReceiptCodec.encode(sdkResponse())
    encoded[6] = 'X'.code.toByte() // inside the payment-id string of the uniffi prefix

    val data = ReceiptCodec.decode(encoded)!!

    assertEquals("pmt-18", data.paymentId) // from the tail, not the corrupted prefix
    assertEquals(BigInteger.valueOf(4200), data.amountSats)
  }

  @Test
  fun `uniffi prefix of an encoded receipt stays readable by a plain positional reader`() {
    // Radar iOS parses receipts with FfiConverterTypeLnurlPayResponse.read, which is positional
    // and ignores trailing bytes — exactly what we do here. The appended tail must be invisible.
    val response = sdkResponse()

    val reread = FfiConverterTypeLnurlPayResponse.read(ByteBuffer(java.nio.ByteBuffer.wrap(ReceiptCodec.encode(response))))

    assertEquals(response, reread)
  }

  @Test
  fun `decodes tail-less current-SDK receipt with allocation padding`() {
    // What a Radar iOS 0.18 sender (or the previous Android implementation on 0.18) produces:
    // plain uniffi bytes, over-allocated buffer included, no tail.
    val response = sdkResponse()
    val size = FfiConverterTypeLnurlPayResponse.allocationSize(response)
    val nioBuffer = java.nio.ByteBuffer.allocate(size.toInt())
    FfiConverterTypeLnurlPayResponse.write(response, ByteBuffer(nioBuffer))

    val data = ReceiptCodec.decode(nioBuffer.array())!!

    assertEquals("hash18", data.identifier)
    assertEquals(BigInteger.valueOf(4200), data.amountSats)
  }

  @Test
  fun `round trips u128-scale amounts`() {
    val huge = BigInteger("340282366920938463463374607431768211455") // 2^128 - 1
    val response = sdkResponse(amount = huge)

    val data = ReceiptCodec.decode(ReceiptCodec.encode(response))!!

    assertEquals(huge, data.amountSats)
  }

  // Cross-platform layout: Radar iOS on breez 0.23 ---------------------------------------------

  @Test
  fun `decodes tail-less 0_23 lnurl receipt from Radar iOS`() {
    val data = ReceiptCodec.decode(fromHex(V6_0_23_LNURL_LIGHTNING))!!

    assertEquals(IOS_PAYMENT_HASH, data.identifier)
    assertEquals("pmt-lightning-1", data.paymentId)
    assertEquals(ReceiptStatus.COMPLETED, data.status)
    assertEquals(BigInteger.valueOf(4200), data.amountSats)
    assertEquals(BigInteger.valueOf(7), data.feeSats)
    assertEquals(1751981111L, data.timestampSeconds)
    assertEquals(ReceiptDetailsType.LIGHTNING, data.detailsType)
  }

  @Test
  fun `decodes tail-less 0_23 bolt11 receipt from Radar iOS`() {
    val data = ReceiptCodec.decode("BOLT".toByteArray(Charsets.US_ASCII) + fromHex(V7_0_23_BOLT11_PLAIN))!!

    assertEquals(IOS_PAYMENT_HASH, data.identifier)
    assertEquals("pmt-lightning-2", data.paymentId)
    assertEquals(ReceiptStatus.PENDING, data.status)
    assertEquals(BigInteger.valueOf(1500), data.amountSats)
    assertEquals(BigInteger.valueOf(2), data.feeSats)
    assertEquals(ReceiptDetailsType.LIGHTNING, data.detailsType)
  }

  @Test
  fun `uniffi prefix is byte-identical to what Radar iOS on 0_23 writes`() {
    // Radar iOS reads only the uniffi prefix, positionally, with its own breez version. If an SDK
    // bump changes these bytes, iOS stops reading what Android sends: compare against the breez
    // version iOS ships before accepting a new layout here.
    val lnurl = fromHex(V6_0_23_LNURL_LIGHTNING)
    val bolt11 = "BOLT".toByteArray(Charsets.US_ASCII) + fromHex(V7_0_23_BOLT11_PLAIN)

    assertArrayEquals(lnurl, ReceiptCodec.encode(iosLnurlPayResponse()).copyOf(lnurl.size))
    assertArrayEquals(bolt11, ReceiptCodec.encode(iosBolt11Response()).copyOf(bolt11.size))
  }

  // Rejection ----------------------------------------------------------------------------------

  @Test
  fun `rejects garbage`() {
    assertNull(ReceiptCodec.decode(ByteArray(0)))
    assertNull(ReceiptCodec.decode(ByteArray(64) { 0x5A }))
    assertNull(ReceiptCodec.decode(fromHex(V1_LIGHTNING_PLAIN).copyOf(40))) // truncated
  }

  @Test
  fun `rejects 0_14 receipt with a corrupted option flag`() {
    val corrupted = fromHex(V1_LIGHTNING_PLAIN)
    corrupted[corrupted.size - 1] = 2 // successAction flag must be 0 or 1

    assertNull(ReceiptCodec.decode(corrupted))
  }

  @Test
  fun `rejects a tail whose magic is valid but payload is bogus`() {
    val bogus = ByteArray(32) { 0x11 } + byteArrayOf(0, 0, 0, 32) + "RDRCPT01".toByteArray(Charsets.US_ASCII)

    assertNull(ReceiptCodec.decode(bogus))
  }

  // ----------------------------------------------------------------------------------------------

  private fun sdkResponse(amount: BigInteger = BigInteger.valueOf(4200)): LnurlPayResponse {
    val htlcDetails = SparkHtlcDetails(
      paymentHash = "hash18",
      preimage = null,
      expiryTime = 1752000000uL,
      status = SparkHtlcStatus.PREIMAGE_SHARED
    )
    val lightning = PaymentDetails.Lightning(
      description = null,
      invoice = "lnbc18example",
      destinationPubkey = "02pubkey",
      htlcDetails = htlcDetails,
      lnurlPayInfo = null,
      lnurlWithdrawInfo = null,
      lnurlReceiveMetadata = null,
      conversionInfo = null
    )
    val payment = Payment(
      id = "pmt-18",
      paymentType = PaymentType.SEND,
      status = PaymentStatus.COMPLETED,
      amount = amount,
      fees = BigInteger.valueOf(7),
      timestamp = 1751981111uL,
      method = PaymentMethod.LIGHTNING,
      details = lightning,
      conversionDetails = null
    )
    return LnurlPayResponse(payment, null)
  }

  private fun iosHtlcDetails(): SparkHtlcDetails {
    return SparkHtlcDetails(
      paymentHash = IOS_PAYMENT_HASH,
      preimage = "preimage-hex",
      expiryTime = 1752000000uL,
      status = SparkHtlcStatus.PREIMAGE_SHARED
    )
  }

  /** The payment serialized as [V6_0_23_LNURL_LIGHTNING]. */
  private fun iosLnurlPayResponse(): LnurlPayResponse {
    val url = UrlSuccessActionData(description = "open this", url = "https://radar.cash/r", matchesCallbackDomain = true)
    val lightning = PaymentDetails.Lightning(
      description = "Pay forgehaven0427",
      invoice = "lnbc42u1pexample",
      destinationPubkey = "02deadbeefcafe",
      htlcDetails = iosHtlcDetails(),
      lnurlPayInfo = LnurlPayInfo(
        lnAddress = "forgehaven0427@radar.cash",
        comment = "a comment",
        domain = "radar.cash",
        metadata = "[[\"text/plain\",\"Pay forgehaven0427\"]]",
        processedSuccessAction = SuccessActionProcessed.Message(MessageSuccessActionData(message = "Thanks for the payment!")),
        rawSuccessAction = SuccessAction.Url(url)
      ),
      lnurlWithdrawInfo = null,
      lnurlReceiveMetadata = LnurlReceiveMetadata(nostrZapRequest = null, nostrZapReceipt = null, senderComment = "sender comment"),
      conversionInfo = null
    )
    val payment = Payment(
      id = "pmt-lightning-1",
      paymentType = PaymentType.SEND,
      status = PaymentStatus.COMPLETED,
      amount = BigInteger.valueOf(4200),
      fees = BigInteger.valueOf(7),
      timestamp = 1751981111uL,
      method = PaymentMethod.LIGHTNING,
      details = lightning,
      conversionDetails = null
    )
    return LnurlPayResponse(payment, SuccessActionProcessed.Url(url))
  }

  /** The payment serialized as [V7_0_23_BOLT11_PLAIN]. */
  private fun iosBolt11Response(): SendPaymentResponse {
    val lightning = PaymentDetails.Lightning(
      description = null,
      invoice = "lnbc1pexample",
      destinationPubkey = "02pubkey",
      htlcDetails = iosHtlcDetails(),
      lnurlPayInfo = null,
      lnurlWithdrawInfo = null,
      lnurlReceiveMetadata = null,
      conversionInfo = null
    )
    val payment = Payment(
      id = "pmt-lightning-2",
      paymentType = PaymentType.SEND,
      status = PaymentStatus.PENDING,
      amount = BigInteger.valueOf(1500),
      fees = BigInteger.valueOf(2),
      timestamp = 1751980000uL,
      method = PaymentMethod.LIGHTNING,
      details = lightning,
      conversionDetails = null
    )
    return SendPaymentResponse(payment)
  }
}
