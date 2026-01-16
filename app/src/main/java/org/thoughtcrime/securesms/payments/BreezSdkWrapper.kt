/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments

import android.annotation.SuppressLint
import breez_sdk_spark.*
import kotlinx.coroutines.runBlocking
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.MobileCoinLedgerWrapper.OwnedTxo
import org.whispersystems.signalservice.api.payments.Money
import java.math.BigInteger
import java.time.LocalDateTime
import java.time.ZoneOffset

class BreezSdkWrapper(ledger: BreezSdk?) {
  private val sdk: BreezSdk? = ledger

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
      val lnAddress = runBlocking { sdk.getLightningAddress() }

      if (lnAddress == null) {
        runBlocking { sdk.registerLightningAddress(RegisterLightningAddressRequest(SignalStore.account.username!!)) }
      }

      return runBlocking {
        val lnAddress = sdk.getLightningAddress()
        if (lnAddress == null) LightningAddress("\\*.*/ No Address Found", "") else LightningAddress(lnAddress.lightningAddress, lnAddress.lnurl)
      }
    } catch (e: SdkException) {
      Log.e("BreezSdk", "Error getting lightning address", e)
      return LightningAddress("\\*.*/ Error getting lightning address", "")
    }
  }

  fun sendPayment(address: String, amount: BigInteger) {

    try {
      val inputType = runBlocking { sdk!!.parse(address) }
      if (inputType is InputType.LightningAddress) {
        val amountSats = amount.toLong().toULong()
        val payRequest = inputType.v1.payRequest
        val optionalValidateSuccessActionUrl = true

        val req = PrepareLnurlPayRequest(
          amountSats = amountSats,
          payRequest = payRequest,
          comment = null,
          optionalValidateSuccessActionUrl
        )
        val prepareResponse = runBlocking { sdk!!.prepareLnurlPay(req) }

        val feeSats = prepareResponse.feeSats

        val response = runBlocking { sdk!!.lnurlPay(LnurlPayRequest(prepareResponse)) }

      }
    } catch (e: Exception) {
      // handle error
    }
  }


  companion object {
    var sdkSingelton: BreezSdk? = null

    fun connect(entropy: ByteArray): BreezSdk {
      if (sdkSingelton == null) {
        val config = defaultConfig(Network.MAINNET)
        config.apiKey =
          "MIIBdzCCASmgAwIBAgIHPpJHKP1qXzAFBgMrZXAwEDEOMAwGA1UEAxMFQnJlZXowHhcNMjUxMDIzMTQwNDQ4WhcNMzUxMDIxMTQwNDQ4WjAxMRQwEgYDVQQKEwtDYWtlIFdhbGxldDEZMBcGA1UEAxMQU2V0aCBGb3IgUHJpdmFjeTAqMAUGAytlcAMhANCD9cvfIDwcoiDKKYdT9BunHLS2/OuKzV8NS0SzqV13o4GAMH4wDgYDVR0PAQH/BAQDAgWgMAwGA1UdEwEB/wQCMAAwHQYDVR0OBBYEFNo5o+5ea0sNMlW/75VgGJCv2AcJMB8GA1UdIwQYMBaAFN6q1pJW843ndJIW/Ey2ILJrKJhrMB4GA1UdEQQXMBWBE3NldGhAY2FrZXdhbGxldC5jb20wBQYDK2VwA0EAl+naPfCBseV7eS4SoP0q0kvo2GHCywXoIbnlBa0y+/wlfu+oILtsGv3jGQ2egCnpgHe87yzR0ygclzz8r/jdAQ=="

        config.lnurlDomain = "cake.cash"

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


  }
}

