/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments

import android.annotation.SuppressLint
import breez_sdk_spark.*
import com.mobilecoin.lib.Mnemonics
import kotlinx.coroutines.runBlocking
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.payments.MobileCoinLedgerWrapper.OwnedTxo
import org.thoughtcrime.securesms.util.ProfileUtil
import org.whispersystems.signalservice.api.payments.Money
import java.io.IOException
import java.math.BigInteger
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.ZoneOffset

class BreezSdkWrapper(ledger: BreezSdk?) {
  private val sdk: BreezSdk? = ledger

  fun getConversions(): Map<String, Double> {
    val sdk = this.sdk ?: return emptyMap()
    val rates = runBlocking { sdk.listFiatRates() }

    val ratesMap = mutableMapOf<String, Double>()
    for (rate in rates.rates) {
      ratesMap[rate.coin] = rate.value
    }
    return ratesMap.toMap()
  }

  fun getBalance(): Balance {
    if (sdk == null) {
      return Balance(Money.Satoshi.ZERO, Money.Satoshi.ZERO, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
    }
    val rq = GetInfoRequest(true)
    val balance = runBlocking { sdk.getInfo(rq) }

    val fullAmount = Money.satoshi(BigInteger(balance.balanceSats.toString()))

    return Balance(fullAmount, fullAmount, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
  }

  fun serialize(): ByteArray {
    return ByteArray(0)
  }

  fun getTransactions(): MutableList<OwnedTxo> {
    val txos = mutableListOf<OwnedTxo>()

    if (sdk == null) {
      return txos
    }

    val payments = runBlocking { sdk.listPayments(ListPaymentsRequest()) }


    payments.payments.forEach { payment -> txos.add(OwnedTxo(payment)) }


    return txos
  }

  @SuppressLint("LogTagInlined")
  fun getLightningAddress(): LightningAddress {
    if (sdk == null) {
      return LightningAddress("\\*.*/ No Address Found. Missing SDK", "")
    }

    // Lenient UI accessor: resolve + publish the address, but never throw — fall back to a
    // placeholder so screens that display the address can't crash. The reliable, retrying publish
    // path is publishLightningAddress(), driven by PublishLightningAddressJob.
    return try {
      publishLightningAddress()
    } catch (e: Exception) {
      Log.e("BreezSdk", "Error getting lightning address", e)
      LightningAddress("\\*.*/ No Address Found", "")
    }
  }

  /**
   * Resolves the wallet's lightning address — reusing the one already registered on our
   * [LNURL_DOMAIN], or registering a freshly-generated random username — and publishes it to the
   * account's Signal profile (at the fixed lightning profile version) so other users can pay it.
   *
   * Unlike [getLightningAddress], this throws on any failure instead of returning a placeholder, so
   * a caller such as [org.thoughtcrime.securesms.jobs.PublishLightningAddressJob] can retry until
   * the address is actually published. Keeping an existing on-domain address (otherwise registering
   * a new username) mirrors iOS `BreezSdk.validateInitialLightningAddress()`.
   */
  @Throws(IOException::class)
  fun publishLightningAddress(): LightningAddress {
    val sdk = this.sdk ?: throw IOException("Breez SDK is not available")

    val info = try {
      val existing = runBlocking { sdk.getLightningAddress() }
      if (existing != null && existing.lightningAddress.contains("@$LNURL_DOMAIN")) {
        existing
      } else {
        tryToRegisterLightningAddress(sdk)
      }
    } catch (e: SdkException) {
      throw IOException("Failed to resolve lightning address", e)
    } ?: throw IOException("Could not register a lightning address (out of retries)")

    val address = LightningAddress(info.lightningAddress, info.lnurl.bech32)
    ProfileUtil.uploadLightingProfile(AppDependencies.application, address)
    return address
  }

  /**
   * Registers a randomly-generated lightning-address username, retrying until an available one is
   * found (up to [rateLimit] + 1 attempts). Mirrors iOS `BreezSdk.tryToRegisterLightningAddress`.
   */
  private fun tryToRegisterLightningAddress(sdk: BreezSdk, rateLimit: Int = 5): LightningAddressInfo? = runBlocking {
    for (i in 0..rateLimit) {
      try {
        val username = generateUsername()
        if (sdk.checkLightningAddressAvailable(CheckLightningAddressRequest(username))) {
          return@runBlocking sdk.registerLightningAddress(RegisterLightningAddressRequest(username))
        }
      } catch (e: SdkException) {
        Log.w("BreezSdk", "Cannot register lightning address; retrying", e)
      }
    }
    Log.w("BreezSdk", "Cannot register lightning address. Out of rate limit: $rateLimit")
    null
  }

  /**
   * Builds a random username of the form `<word><word><0000-9999>` (e.g. `forgehaven0427`) from
   * [USERNAME_WORDS], using secure randomness. Mirrors iOS `BreezSdk.generateUsername()`.
   */
  private fun generateUsername(): String {
    val bytes = ByteArray(6)
    SecureRandom().nextBytes(bytes)
    val index1 = ((bytes[0].toInt() and 0xFF) shl 8 or (bytes[1].toInt() and 0xFF)) % USERNAME_WORDS.size
    val index2 = ((bytes[2].toInt() and 0xFF) shl 8 or (bytes[3].toInt() and 0xFF)) % USERNAME_WORDS.size
    val number = ((bytes[4].toInt() and 0xFF) shl 8 or (bytes[5].toInt() and 0xFF)) % 10000
    return "${USERNAME_WORDS[index1]}${USERNAME_WORDS[index2]}${"%04d".format(number)}"
  }

  /**
   * A payment quote from Breez, whichever kind of destination produced it. Both variants carry the
   * real network fee, so callers never have to know which shape they are holding.
   */
  private sealed class PreparedPayment {
    abstract val feeSats: BigInteger

    data class Lnurl(val response: PrepareLnurlPayResponse) : PreparedPayment() {
      override val feeSats: BigInteger get() = response.feeSats.toLong().toBigInteger()
    }

    data class Bolt11(val response: PrepareSendPaymentResponse) : PreparedPayment() {
      override val feeSats: BigInteger
        get() = when (val method = response.paymentMethod) {
          // A Spark transfer fee, when present, is what actually gets charged; the lightning fee is
          // the fallback for a payment that leaves the Spark network.
          is SendPaymentMethod.Bolt11Invoice -> (method.sparkTransferFeeSats ?: method.lightningFeeSats).toLong().toBigInteger()
          else                               -> BigInteger.ZERO
        }
    }
  }

  /**
   * Prepares a payment to [destination] for [amount] sats, returning the Breez quote (which carries
   * the real network fee).
   *
   * [destination] is whatever the user gave us — a lightning address, an LNURL, or a BOLT11
   * invoice — and the SDK's own parser decides which it is rather than this class guessing.
   * Throws [UnsupportedOperationException] for destinations Radar does not send to.
   */
  private fun prepare(destination: String, amount: BigInteger): PreparedPayment {
    val sdk = this.sdk ?: throw IllegalStateException("Breez SDK is not available")
    val inputType = runBlocking { sdk.parse(destination) }

    // Breez SDK 0.14.0: `amountSats: ULong` → `amount: BigInteger`;
    // `optionalValidateSuccessActionUrl` → named `validateSuccessActionUrl: Boolean?`.
    val payRequest: LnurlPayRequestDetails = when (inputType) {
      is InputType.LightningAddress -> inputType.v1.payRequest
      is InputType.LnurlPay         -> inputType.v1
      is InputType.Bolt11Invoice    -> return prepareBolt11(sdk, inputType.v1, amount)
      else                          -> throw UnsupportedOperationException("Unsupported payment destination")
    }

    val req = PrepareLnurlPayRequest(
      amount = amount.toLong().toBigInteger(),
      payRequest = payRequest,
      comment = null,
      validateSuccessActionUrl = true
    )
    return PreparedPayment.Lnurl(runBlocking { sdk.prepareLnurlPay(req) })
  }

  /**
   * Prepares a BOLT11 invoice.
   *
   * An invoice may carry its own amount, in which case that amount is what gets paid and a
   * caller-supplied one is rejected by the SDK. [amount] is therefore only forwarded for an
   * amountless invoice, where the sender is the one choosing. Mirrors iOS `prepareOutgoingPayment`.
   *
   * When the invoice does carry an amount we refuse to proceed unless it matches what the caller
   * asked for. The user confirmed a specific number on the previous screen; quietly paying the
   * invoice's amount instead would let a pasted invoice spend more than was authorised.
   */
  private fun prepareBolt11(sdk: BreezSdk, details: Bolt11InvoiceDetails, amount: BigInteger): PreparedPayment.Bolt11 {
    val invoiceSats: BigInteger? = details.amountMsat?.let { BigInteger.valueOf((it / 1000uL).toLong()) }

    if (invoiceSats != null && invoiceSats != amount) {
      throw InvoiceAmountMismatchException(invoiceSats, amount)
    }

    val request = PrepareSendPaymentRequest(
      paymentRequest = PaymentRequest.Input(details.invoice.bolt11),
      amount = if (invoiceSats == null) amount.toLong().toBigInteger() else null
    )
    return PreparedPayment.Bolt11(runBlocking { sdk.prepareSendPayment(request) })
  }

  /**
   * Thrown when a BOLT11 invoice specifies an amount that differs from the one the user entered.
   * Carries both so a caller can tell the user what the invoice actually asks for.
   */
  class InvoiceAmountMismatchException(val invoiceSats: BigInteger, val requestedSats: BigInteger) :
    IllegalArgumentException("Invoice is for $invoiceSats sats but $requestedSats sats was requested")

  /** The amount a BOLT11 invoice asks for in sats, or null if it is amountless or unparseable. */
  fun bolt11AmountSats(invoice: String): BigInteger? {
    val sdk = this.sdk ?: return null
    return runCatching {
      val parsed = runBlocking { sdk.parse(invoice) }
      (parsed as? InputType.Bolt11Invoice)?.v1?.amountMsat?.let { BigInteger.valueOf((it / 1000uL).toLong()) }
    }.getOrNull()
  }

  /**
   * The real network fee for sending [amount] sats to [destination], obtained from the Breez
   * prepared-payment quote. Matches the fee that will actually be charged (and later shown in
   * payment details), unlike the coarser `recommendedFees()` estimate used by iOS's confirm screen.
   */
  fun getLnurlFee(destination: String, amount: BigInteger): Money.Satoshi {
    if (sdk == null) return Money.Satoshi.ZERO
    return Money.satoshi(prepare(destination, amount).feeSats)
  }

  /** Result of sending a payment: the serialized response plus the actual network fee charged. */
  data class SendPaymentResult(val response: ByteArray, val feeSats: BigInteger)

  fun sendPayment(destination: String, amount: BigInteger): SendPaymentResult {
    val sdk = this.sdk ?: throw IllegalStateException("Breez SDK is not available")

    // Capture the real fee from the prepared payment so it can be persisted on the
    // transaction. Mirrors iOS, which stores the prepared quote's fee as the payment fee.
    return when (val prepared = prepare(destination, amount)) {
      is PreparedPayment.Lnurl -> {
        val response = runBlocking { sdk.lnurlPay(LnurlPayRequest(prepared.response)) }
        // Dual-format receipt: raw uniffi bytes (readable by Radar iOS) + a version-stable proto
        // tail that survives breez_sdk_spark upgrades. See ReceiptCodec.
        SendPaymentResult(ReceiptCodec.encode(response), prepared.feeSats)
      }
      is PreparedPayment.Bolt11 -> {
        val response = runBlocking { sdk.sendPayment(SendPaymentRequest(prepared.response)) }
        SendPaymentResult(ReceiptCodec.encode(response), prepared.feeSats)
      }
    }
  }

  /** The currently-registered lightning-address username, or null if none/unavailable. */
  fun getUsername(): String? {
    if (sdk == null) return null
    return runBlocking { sdk.getLightningAddress()?.username }
  }

  /** Whether [username] can be registered as a lightning address. */
  fun isUsernameAvailable(username: String): Boolean {
    if (sdk == null) return false
    return runBlocking { sdk.checkLightningAddressAvailable(CheckLightningAddressRequest(username)) }
  }

  /**
   * A freshly minted BOLT11 invoice for this wallet, paired with its own absolute expiry so
   * callers can re-mint before it lapses. [expiresAtMillis] is null when the expiry could not be
   * read back, which callers should treat as "unknown — do not auto-refresh".
   */
  data class LightningInvoice(val bolt11: String, val expiresAtMillis: Long?)

  /**
   * Mints a BOLT11 invoice for this wallet directly from the SDK.
   *
   * The invoice is deliberately *amountless*: the Add Funds screen has no amount field, so the
   * sender chooses what to pay.
   *
   * [expirySeconds] has no documented ceiling in the Spark SDK, so a rejected value falls back to
   * the SDK default rather than failing the whole call — otherwise the caller loses the invoice
   * entirely and shows something non-payable in its place. Pass 0 to use the SDK default outright.
   * Mirrors iOS `fetchLightningInvoice`.
   */
  @JvmOverloads
  @Throws(IOException::class)
  fun fetchLightningInvoice(description: String = "", expirySeconds: Int = 0): LightningInvoice {
    val sdk = this.sdk ?: throw IOException("Breez SDK is not available")
    val expirySecs: UInt? = if (expirySeconds > 0) expirySeconds.toUInt() else null

    val bolt11 = try {
      mintBolt11Invoice(sdk, description, expirySecs)
    } catch (e: Exception) {
      if (expirySecs == null) {
        throw IOException("Could not mint a lightning invoice", e)
      }
      Log.w("BreezSdk", "Breez rejected expirySecs=$expirySecs; retrying with the SDK default", e)
      try {
        mintBolt11Invoice(sdk, description, null)
      } catch (retry: Exception) {
        throw IOException("Could not mint a lightning invoice", retry)
      }
    }

    return LightningInvoice(bolt11, expiryOf(sdk, bolt11))
  }

  private fun mintBolt11Invoice(sdk: BreezSdk, description: String, expirySecs: UInt?): String {
    return runBlocking {
      sdk.receivePayment(
        ReceivePaymentRequest(
          ReceivePaymentMethod.Bolt11Invoice(
            description = description,
            amountSats = null,
            expirySecs = expirySecs,
            paymentHash = null
          )
        )
      ).paymentRequest
    }
  }

  /**
   * Reads the absolute expiry back off a freshly minted invoice rather than assuming a window the
   * SDK never promised. Null when the invoice cannot be parsed.
   */
  private fun expiryOf(sdk: BreezSdk, bolt11: String): Long? {
    return runCatching {
      val parsed = runBlocking { sdk.parse(bolt11) }
      if (parsed !is InputType.Bolt11Invoice) {
        Log.w("BreezSdk", "Minted invoice did not parse as a BOLT11 invoice; expiry unknown.")
        return@runCatching null
      }
      val details = parsed.v1
      (details.timestamp + details.expiry).toLong() * 1000L
    }.getOrElse {
      Log.w("BreezSdk", "Could not parse minted invoice to read its expiry", it)
      null
    }
  }

  /** Fetches an on-chain (Bitcoin) receive address, or null if unavailable. */
  fun getOnchainAddress(): String? {
    val sdk = this.sdk ?: return null
    return runCatching {
      // Breez 0.14.0: BitcoinAddress is a data class with `newAddress: Boolean?`.
      // `null` defers to the SDK default (matches the implicit 0.9.1 behavior).
      runBlocking { sdk.receivePayment(ReceivePaymentRequest(ReceivePaymentMethod.BitcoinAddress(null))).paymentRequest }
    }.getOrNull()
  }

  /** Registers [username] as the wallet's lightning address and re-uploads the profile address. */
  fun registerUsername(username: String) {
    val sdk = this.sdk ?: throw IllegalStateException("Breez SDK is not available")
    runBlocking { sdk.registerLightningAddress(RegisterLightningAddressRequest(username)) }
    val info = runBlocking { sdk.getLightningAddress() }
    if (info != null) {
      ProfileUtil.uploadLightingProfile(AppDependencies.application, LightningAddress(info.lightningAddress, info.lnurl.bech32))
    }
  }

  /** Unregisters the wallet's lightning address (best-effort). Used when deleting the wallet. */
  fun deleteLightningAddress() {
    val sdk = this.sdk ?: return
    runCatching { runBlocking { sdk.deleteLightningAddress() } }
      .onFailure { Log.w("BreezSdk", "deleteLightningAddress failed", it) }
  }

  companion object {
    var sdkSingelton: BreezSdk? = null

    /** The lnurl domain our lightning addresses are registered under (e.g. `name@radar.cash`). */
    const val LNURL_DOMAIN = "radar.cash"

    /**
     * Word list used to build human-friendly random lightning-address usernames. Kept identical to
     * iOS `BreezSdk.usernameWords` so both platforms draw from the same namespace.
     */
    private val USERNAME_WORDS: List<String> = listOf(
      "amber", "arctic", "azure", "beacon", "birch", "blast", "blaze", "bloom",
      "bolt", "bravo", "breeze", "bright", "brisk", "bronze", "brook", "burst",
      "calm", "cedar", "chain", "chase", "chief", "chill", "cipher", "citrus",
      "clover", "coast", "cobalt", "comet", "coral", "craft", "crest", "crisp",
      "crown", "crush", "crystal", "cyber", "delta", "dense", "depot", "depth",
      "drift", "dusk", "echo", "ember", "falcon", "fern", "finch", "flame",
      "flash", "fleet", "flint", "float", "flux", "forge", "forte", "frost",
      "gale", "ghost", "glade", "gleam", "glide", "glow", "grand", "grant",
      "gust", "haven", "hawk", "hazel", "haze", "helix", "helm", "hive",
      "indie", "inlet", "iris", "ivory", "jade", "jasper", "jetty", "kindle",
      "kite", "lance", "lark", "laser", "latch", "lava", "layer", "leap",
      "ledge", "light", "lotus", "lunar", "lynx", "maple", "marble", "marsh",
      "mist", "mosaic", "moss", "mural", "nova", "oaken", "ocean", "onyx",
      "orbit", "otter", "oxide", "ozone", "pact", "peak", "pearl", "petal",
      "pilot", "pine", "pivot", "pixel", "plaza", "plume", "polar", "pulse",
      "quartz", "quest", "radar", "rapid", "raven", "realm", "relay", "ridge",
      "ripple", "river", "roam", "rogue", "rover", "ruby", "rush", "sage",
      "scout", "serene", "shade", "shift", "shore", "signal", "silver", "slate",
      "solar", "sonic", "spark", "spire", "split", "sprint", "stark", "steel",
      "storm", "strata", "streak", "stream", "stride", "swift", "talon", "teal",
      "terra", "thunder", "tide", "timber", "titan", "torch", "trail", "trend",
      "tropic", "turbo", "ultra", "unity", "vapor", "vault", "vector", "verde",
      "vibe", "vista", "vital", "vivid", "volt", "wave", "wisp", "zenith"
    )

    /** Drops the cached SDK so the next connect starts fresh (e.g. after wallet deletion). */
    fun reset() {
      sdkSingelton = null
    }

    @Synchronized
    fun connect(entropy: ByteArray): BreezSdk {
      LightningLogger.installIfNeeded()
      if (sdkSingelton == null) {
        val config = defaultConfig(Network.MAINNET)
        config.apiKey =
          "MIIBdzCCASmgAwIBAgIHPpJHKP1qXzAFBgMrZXAwEDEOMAwGA1UEAxMFQnJlZXowHhcNMjUxMDIzMTQwNDQ4WhcNMzUxMDIxMTQwNDQ4WjAxMRQwEgYDVQQKEwtDYWtlIFdhbGxldDEZMBcGA1UEAxMQU2V0aCBGb3IgUHJpdmFjeTAqMAUGAytlcAMhANCD9cvfIDwcoiDKKYdT9BunHLS2/OuKzV8NS0SzqV13o4GAMH4wDgYDVR0PAQH/BAQDAgWgMAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYEFNo5o+5ea0sNMlW/75VgGJCv2AcJMB8GA1UdIwQYMBaAFN6q1pJW843ndJIW/Ey2ILJrKJhrMB4GA1UdEQQXMBWBE3NldGhAY2FrZXdhbGxldC5jb20wBQYDK2VwA0EAl+naPfCBseV7eS4SoP0q0kvo2GHCywXoIbnlBa0y+/wlfu+oILtsGv3jGQ2egCnpgHe87yzR0ygclzz8r/jdAQ=="

        config.lnurlDomain = LNURL_DOMAIN
        config.preferSparkOverLightning = true
        // Cap auto-claiming of on-chain deposits at the network's own recommended fee plus 5
        // sat/vB of headroom, rather than a flat 5 sat/vB ceiling. A fixed cap silently stops
        // claiming deposits whenever the mempool sits above it, which is exactly when a user is
        // most likely to be watching for the funds. Matches iOS (79f87e5654).
        config.maxDepositClaimFee = MaxFee.NetworkRecommended(leewaySatPerVbyte = 5u)

        val dataDir = AppDependencies.application.applicationInfo.dataDir

        sdkSingelton = runBlocking {
          connect(
            ConnectRequest(
              config = config,
              // Cake/BIP-39 compatibility: seed the SDK from the BIP-39 mnemonic of the entropy
              // (Spark runs standard BIP-39 PBKDF2 internally) so the backup phrase interoperates
              // with other BIP-39 wallets. Mirrors iOS 85485a8b7d.
              seed = Seed.Mnemonic(Mnemonics.bip39EntropyToMnemonic(entropy), null),
              storageDir = "$dataDir/.lndata"
            )
          )
        }
      }

      return sdkSingelton!!
    }

    fun connectWrapper(entropy: ByteArray): BreezSdkWrapper {
      return BreezSdkWrapper(connect(entropy))
    }

    /**
     * The reconciliation key (lightning paymentHash / on-chain txId) of a stored receipt, or null
     * when the receipt has none (spark/token payments) or cannot be decoded under any known
     * format. Callers (e.g. LedgerReconcile) already skip null keys, so an unreadable receipt is
     * a graceful no-op rather than a crash.
     */
    fun getIdentifierFromReceipt(receipt: ByteArray): String? = ReceiptCodec.decode(receipt)?.identifier
  }
}

