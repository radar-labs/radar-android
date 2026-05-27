package org.thoughtcrime.securesms.payments.preferences.addmoney

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.signal.core.util.Result

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
      repository.getWalletAddress().subscribe { result ->
        when (result) {
          is Result.Success -> selfAddressAndUri.postValue(result.success)
          is Result.Failure -> errors.postValue(result.failure)
        }
      }
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
}
