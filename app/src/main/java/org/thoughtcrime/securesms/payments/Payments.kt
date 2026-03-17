package org.thoughtcrime.securesms.payments

import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.currency.CurrencyExchange
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class Payments(private val mobileCoinConfig: MobileCoinConfig) {
  private var _wallet: Wallet? = null
  private var currencyConversions: BreezCurrencyConversions? = null

  val wallet: Wallet
    @Synchronized
    get() {
      if (_wallet != null) return _wallet!!

      val paymentsEntropy = SignalStore.payments.paymentsEntropy
      _wallet = Wallet(mobileCoinConfig, paymentsEntropy!!)
      return _wallet!!
    }

  @Synchronized
  fun closeWallet() {
    _wallet = null
  }

  @WorkerThread
  @Synchronized
  @Throws(IOException::class)
  fun getCurrencyExchange(refreshIfAble: Boolean): CurrencyExchange {
    if (currencyConversions == null || shouldRefresh(refreshIfAble, currencyConversions!!.timestamp)) {
      val currencyConversionsMap = wallet.exchangeRate
      val newCurrencyConversions = BreezCurrencyConversions(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC), currencyConversionsMap)

      Log.i(TAG, "Currency conversion data is unavailable or a refresh was requested and available")
      if (currencyConversions == null || (newCurrencyConversions.timestamp > currencyConversions!!.timestamp)) {
        currencyConversions = newCurrencyConversions
      }
    }

    if (currencyConversions != null) {
      return CurrencyExchange(currencyConversions!!.currencies, currencyConversions!!.timestamp)
    }

    throw IOException("Unable to retrieve currency conversions")
  }

  private fun shouldRefresh(refreshIfAble: Boolean, lastRefreshTime: Long): Boolean {
    return refreshIfAble && System.currentTimeMillis() - lastRefreshTime >= MINIMUM_ELAPSED_TIME_BETWEEN_REFRESH
  }

  class BreezCurrencyConversions(val timestamp: Long, val currencies: Map<String, Double>)

  companion object {
    private val TAG = tag(Payments::class.java)

    private val MINIMUM_ELAPSED_TIME_BETWEEN_REFRESH = TimeUnit.MINUTES.toMillis(1)
  }
}
