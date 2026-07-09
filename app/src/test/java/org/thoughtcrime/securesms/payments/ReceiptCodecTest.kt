/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments

import breez_sdk_spark.ByteBuffer
import breez_sdk_spark.FfiConverterTypeLnurlPayResponse
import breez_sdk_spark.LnurlPayResponse
import breez_sdk_spark.Payment
import breez_sdk_spark.PaymentDetails
import breez_sdk_spark.PaymentMethod
import breez_sdk_spark.PaymentStatus
import breez_sdk_spark.PaymentType
import breez_sdk_spark.SparkHtlcDetails
import breez_sdk_spark.SparkHtlcStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.LogRecorder
import java.math.BigInteger

/**
 * The golden `V*` vectors below were serialized by the REAL breez_sdk_spark 0.14.0 bindings
 * (reflection harness over the published AAR), i.e. they are byte-identical to what an old-breez
 * Radar iOS sender puts on the wire and to what pre-0.18 installs stored locally. They must never
 * be regenerated with a newer SDK — they pin the frozen [Breez014ReceiptReader] layout.
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
}
