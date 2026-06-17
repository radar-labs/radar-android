package org.thoughtcrime.securesms.payments.preferences.addmoney

import androidx.annotation.MainThread
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.whispersystems.signalservice.internal.push.exceptions.PaymentsRegionException
import org.signal.core.util.Result as SignalResult

internal class PaymentsAddMoneyRepository {
  @MainThread
  fun getWalletAddress(): Single<SignalResult<AddressAndUri, Error>> {
    if (!SignalStore.payments.mobileCoinPaymentsEnabled()) {
      return Single.just(SignalResult.failure(Error.PAYMENTS_NOT_ENABLED))
    }

    return Single.fromCallable<SignalResult<AddressAndUri, Error>> {
      val paymentAddress = AppDependencies.payments.wallet.lightningAddress
      SignalResult.success(AddressAndUri(paymentAddress.paymentAddress, paymentAddress.paymentAddressUri))
    }
      .onErrorReturn { throwable ->
        // Fetching the lightning address re-uploads the profile; a failure there must surface as a
        // typed Result rather than escape and crash the RxJava chain.
        if (throwable is PaymentsRegionException) {
          // A 403 here means this device is no longer registered — most commonly because the account
          // was just re-registered on another device, which deregisters this one.
          Log.w(TAG, "Wallet address fetch rejected with 403; device is no longer registered.", throwable)
          SignalResult.failure(Error.NOT_REGISTERED)
        } else {
          Log.w(TAG, "Failed to fetch wallet address.", throwable)
          SignalResult.failure(Error.COULD_NOT_GET_WALLET_ADDRESS)
        }
      }
      .subscribeOn(Schedulers.io())
      .observeOn(Schedulers.io())
  }

  internal enum class Error {
    PAYMENTS_NOT_ENABLED,
    NOT_REGISTERED,
    COULD_NOT_GET_WALLET_ADDRESS
  }

  companion object {
    private val TAG = Log.tag(PaymentsAddMoneyRepository::class.java)
  }
}
