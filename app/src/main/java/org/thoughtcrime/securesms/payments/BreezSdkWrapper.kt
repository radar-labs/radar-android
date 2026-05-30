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

    try {
      val existing = runBlocking { sdk.getLightningAddress() }

      // Keep an existing address only if it's on our configured lnurl domain; otherwise (or if no
      // address is registered yet) register a freshly-generated random username. Mirrors iOS
      // BreezSdk.validateInitialLightningAddress().
      val info = if (existing != null && existing.lightningAddress.contains("@$LNURL_DOMAIN")) {
        existing
      } else {
        tryToRegisterLightningAddress(sdk)
      }

      if (info != null) {
        val address = LightningAddress(info.lightningAddress, info.lnurl.bech32)
        ProfileUtil.uploadLightingProfile(AppDependencies.application, address)
        return address
      }

      return LightningAddress("\\*.*/ No Address Found", "")
    } catch (e: SdkException) {
      Log.e("BreezSdk", "Error getting lightning address", e)
      return LightningAddress("\\*.*/ Error getting lightning address", "")
    }
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
   * Prepares an LNURL/lightning-address payment for [amount] sats, returning the Breez quote
   * (which carries the real network [PrepareLnurlPayResponse.feeSats]). Throws
   * [UnsupportedOperationException] for non-LNURL inputs.
   */
  private fun prepareLnurl(address: String, amount: BigInteger): PrepareLnurlPayResponse {
    val inputType = runBlocking { sdk!!.parse(address) }

    // Breez SDK 0.14.0: `amountSats: ULong` → `amount: BigInteger`;
    // `optionalValidateSuccessActionUrl` → named `validateSuccessActionUrl: Boolean?`.
    val payRequest: LnurlPayRequestDetails = when (inputType) {
      is InputType.LightningAddress -> inputType.v1.payRequest
      is InputType.LnurlPay         -> inputType.v1
      else                          -> throw UnsupportedOperationException()
    }

    val req = PrepareLnurlPayRequest(
      amount = amount.toLong().toBigInteger(),
      payRequest = payRequest,
      comment = null,
      validateSuccessActionUrl = true
    )
    return runBlocking { sdk!!.prepareLnurlPay(req) }
  }

  /**
   * The real network fee for sending [amount] sats to [address], obtained from the Breez
   * prepared-payment quote. Matches the fee that will actually be charged (and later shown in
   * payment details), unlike the coarser `recommendedFees()` estimate used by iOS's confirm screen.
   */
  fun getLnurlFee(address: String, amount: BigInteger): Money.Satoshi {
    if (sdk == null) return Money.Satoshi.ZERO
    return Money.satoshi(prepareLnurl(address, amount).feeSats.toLong().toBigInteger())
  }

  /** Result of sending a payment: the serialized response plus the actual network fee charged. */
  data class SendPaymentResult(val response: ByteArray, val feeSats: BigInteger)

  fun sendPayment(address: String, amount: BigInteger): SendPaymentResult {
    val prepareResponse = prepareLnurl(address, amount)

    // Capture the real fee from the prepared payment so it can be persisted on the
    // transaction. Mirrors iOS, which stores `prepareLnurlPay().feeSats` as the payment fee.
    val feeSats = prepareResponse.feeSats.toLong().toBigInteger()

    val response = runBlocking { sdk!!.lnurlPay(LnurlPayRequest(prepareResponse)) }

    val allocationSize = FfiConverterTypeLnurlPayResponse.allocationSize(response)
    val buffer = ByteBuffer(java.nio.ByteBuffer.allocate(allocationSize.toInt()))
    FfiConverterTypeLnurlPayResponse.write(response, buffer)

    return SendPaymentResult(buffer.internal().array(), feeSats)
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

    fun connect(entropy: ByteArray): BreezSdk {
      LightningLogger.installIfNeeded()
      if (sdkSingelton == null) {
        val config = defaultConfig(Network.MAINNET)
        config.apiKey =
          "MIIBdzCCASmgAwIBAgIHPpJHKP1qXzAFBgMrZXAwEDEOMAwGA1UEAxMFQnJlZXowHhcNMjUxMDIzMTQwNDQ4WhcNMzUxMDIxMTQwNDQ4WjAxMRQwEgYDVQQKEwtDYWtlIFdhbGxldDEZMBcGA1UEAxMQU2V0aCBGb3IgUHJpdmFjeTAqMAUGAytlcAMhANCD9cvfIDwcoiDKKYdT9BunHLS2/OuKzV8NS0SzqV13o4GAMH4wDgYDVR0PAQH/BAQDAgWgMAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYEFNo5o+5ea0sNMlW/75VgGJCv2AcJMB8GA1UdIwQYMBaAFN6q1pJW843ndJIW/Ey2ILJrKJhrMB4GA1UdEQQXMBWBE3NldGhAY2FrZXdhbGxldC5jb20wBQYDK2VwA0EAl+naPfCBseV7eS4SoP0q0kvo2GHCywXoIbnlBa0y+/wlfu+oILtsGv3jGQ2egCnpgHe87yzR0ygclzz8r/jdAQ=="

        config.lnurlDomain = LNURL_DOMAIN
        config.preferSparkOverLightning = true

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

    fun deserializeLnurlPayResponse(serialized: ByteArray): LnurlPayResponse {
      val buffer = ByteBuffer(java.nio.ByteBuffer.wrap(serialized))
      return FfiConverterTypeLnurlPayResponse.read(buffer)
    }

    fun getIdentifierFromReceipt(receipt: ByteArray): String? {
      val response = deserializeLnurlPayResponse(receipt)
      return getIdentifierFromReceipt(response)
    }

    fun getIdentifierFromReceipt(response: LnurlPayResponse): String? = when (response.payment.details) {
      is PaymentDetails.Lightning -> (response.payment.details as PaymentDetails.Lightning).htlcDetails.paymentHash
      is PaymentDetails.Deposit -> (response.payment.details as PaymentDetails.Deposit).txId
      is PaymentDetails.Withdraw -> (response.payment.details as PaymentDetails.Withdraw).txId

      is PaymentDetails.Spark -> null
      is PaymentDetails.Token -> null
      null -> null
    }
  }
}

