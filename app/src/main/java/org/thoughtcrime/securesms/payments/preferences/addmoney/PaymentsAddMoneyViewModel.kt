package org.thoughtcrime.securesms.payments.preferences.addmoney

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.signal.core.util.Result
import org.signal.core.util.logging.Log

internal class PaymentsAddMoneyViewModel(private val repository: PaymentsAddMoneyRepository) : ViewModel() {
  private val selfAddressAndUri = MutableLiveData<AddressAndUri>()
  private val disposables = CompositeDisposable()

  val errors = MutableLiveData<PaymentsAddMoneyRepository.Error>()
  val selfAddressB58: LiveData<String> = selfAddressAndUri.map { it!!.addressB58 }

  init {
    refresh()
  }

  /** Re-fetches the wallet address. Called after the username changes on the edit screen. */
  fun refresh() {
    disposables.add(
      repository.getWalletAddress().subscribe(
        { result ->
          when (result) {
            is Result.Success -> selfAddressAndUri.postValue(result.success)
            is Result.Failure -> errors.postValue(result.failure)
          }
        },
        { throwable ->
          // Defensive backstop: getWalletAddress() already maps failures to a Result.Failure, so this
          // shouldn't fire. Guard anyway so an unexpected error can never reach RxJava's global handler
          // and crash the app.
          Log.w(TAG, "Unexpected error fetching wallet address.", throwable)
          errors.postValue(PaymentsAddMoneyRepository.Error.COULD_NOT_GET_WALLET_ADDRESS)
        }
      )
    )
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(PaymentsAddMoneyViewModel(PaymentsAddMoneyRepository()))!!
    }
  }

  companion object {
    private val TAG = Log.tag(PaymentsAddMoneyViewModel::class.java)
  }
}
