/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments

import android.annotation.SuppressLint
import breez_sdk_spark.*
import kotlinx.coroutines.runBlocking
import org.signal.core.util.CryptoUtil
import org.signal.core.util.Hex
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.MobileCoinLedgerWrapper.OwnedTxo
import org.thoughtcrime.securesms.util.ProfileUtil
import org.whispersystems.signalservice.api.payments.Money
import java.math.BigInteger
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

class BreezSdkWrapper(ledger: BreezSdk?) {
  private val sdk: BreezSdk? = ledger

  fun getConversions(): Map<String, Double> {
    val rates = runBlocking { sdk!!.listFiatRates() }

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
      val lnAddress = runBlocking {
        val lnAddress = sdk.getLightningAddress()
        if (lnAddress == null) null else LightningAddress(lnAddress.lightningAddress, lnAddress.lnurl.bech32)
      }

      if (lnAddress != null) {
        ProfileUtil.uploadLightingProfile(AppDependencies.application, lnAddress)
        return lnAddress
      }

      val username = Hex.toStringCondensed(CryptoUtil.sha256(SignalStore.account.getServiceIds().aci.toString().uppercase(Locale.getDefault()).toByteArray())).substring(0, 10)

      runBlocking { sdk.registerLightningAddress(RegisterLightningAddressRequest(username)) }

      val lightningAddress = runBlocking {
        val lnAddress = sdk.getLightningAddress()
        if (lnAddress == null) null else LightningAddress(lnAddress.lightningAddress, lnAddress.lnurl.bech32)
      }

      if (lightningAddress != null) ProfileUtil.uploadLightingProfile(AppDependencies.application, lightningAddress)

      return lightningAddress ?: LightningAddress("\\*.*/ No Address Found", "")
    } catch (e: SdkException) {
      Log.e("BreezSdk", "Error getting lightning address", e)
      return LightningAddress("\\*.*/ Error getting lightning address", "")
    }
  }

  fun sendPayment(address: String, amount: BigInteger): ByteArray {
    val inputType = runBlocking { sdk!!.parse(address) }

    if (inputType !is InputType.LightningAddress && inputType !is InputType.LnurlPay) {
      throw UnsupportedOperationException()
    }

    val amountSats = amount.toLong().toULong()
    val optionalValidateSuccessActionUrl = true
    var payRequest: LnurlPayRequestDetails? = null

    if (inputType is InputType.LightningAddress) {
      payRequest = inputType.v1.payRequest
    } else if (inputType is InputType.LnurlPay) {
      payRequest = inputType.v1
    }
    val req = PrepareLnurlPayRequest(
      amountSats = amountSats,
      payRequest = payRequest!!,
      comment = null,
      optionalValidateSuccessActionUrl
    )
    val prepareResponse = runBlocking { sdk!!.prepareLnurlPay(req) }

    val response = runBlocking { sdk!!.lnurlPay(LnurlPayRequest(prepareResponse)) }

    val allocationSize = FfiConverterTypeLnurlPayResponse.allocationSize(response)
    val buffer = ByteBuffer(java.nio.ByteBuffer.allocate(allocationSize.toInt()))
    FfiConverterTypeLnurlPayResponse.write(response, buffer)

    return buffer.internal().array()
  }


  companion object {
    var sdkSingelton: BreezSdk? = null

    fun connect(entropy: ByteArray): BreezSdk {
      if (sdkSingelton == null) {
        val config = defaultConfig(Network.MAINNET)
        config.apiKey =
          "MIIBdzCCASmgAwIBAgIHPpJHKP1qXzAFBgMrZXAwEDEOMAwGA1UEAxMFQnJlZXowHhcNMjUxMDIzMTQwNDQ4WhcNMzUxMDIxMTQwNDQ4WjAxMRQwEgYDVQQKEwtDYWtlIFdhbGxldDEZMBcGA1UEAxMQU2V0aCBGb3IgUHJpdmFjeTAqMAUGAytlcAMhANCD9cvfIDwcoiDKKYdT9BunHLS2/OuKzV8NS0SzqV13o4GAMH4wDgYDVR0PAQH/BAQDAgWgMAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYEFNo5o+5ea0sNMlW/75VgGJCv2AcJMB8GA1UdIwQYMBaAFN6q1pJW843ndJIW/Ey2ILJrKJhrMB4GA1UdEQQXMBWBE3NldGhAY2FrZXdhbGxldC5jb20wBQYDK2VwA0EAl+naPfCBseV7eS4SoP0q0kvo2GHCywXoIbnlBa0y+/wlfu+oILtsGv3jGQ2egCnpgHe87yzR0ygclzz8r/jdAQ=="

        config.lnurlDomain = "radar.cash"
        config.preferSparkOverLightning = true

        val dataDir = AppDependencies.application.applicationInfo.dataDir

        sdkSingelton = runBlocking {
          connect(
            ConnectRequest(
              config = config,
              seed = Seed.Entropy(entropy),
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

