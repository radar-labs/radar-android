package org.thoughtcrime.securesms.payments.backup.entry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.payments.Mnemonic;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.livedata.Store;
import org.whispersystems.signalservice.api.payments.PaymentsConstants;

public class PaymentsRecoveryEntryViewModel extends ViewModel {

  private Store<PaymentsRecoveryEntryViewState> state  = new Store<>(new PaymentsRecoveryEntryViewState());
  private SingleLiveEvent<Events>               events = new SingleLiveEvent<>();
  /**
   * Buffer for the phrase being entered. Length is switchable so a wallet created before the move
   * to a 12-word phrase — whose owner holds 24 words — can still be recovered by hand.
   */
  private String[]                              words  = new String[PaymentsConstants.MNEMONIC_LENGTH];

  @NonNull LiveData<PaymentsRecoveryEntryViewState> getState() {
    return state.getStateLiveData();
  }

  @NonNull LiveData<Events> getEvents() {
    return events;
  }

  @NonNull String[] getWords() {
    return words;
  }

  int getWordCount() {
    return words.length;
  }

  /**
   * Switches the length of phrase being entered. Anything typed so far is discarded — the two
   * lengths are different phrases, not a prefix of one another, so carrying words across would
   * only produce a phrase the user did not mean. Mirrors iOS, which also resets on switch.
   */
  void setWordCount(int wordCount) {
    if (!PaymentsConstants.isSupportedMnemonicLength(wordCount) || wordCount == words.length) {
      return;
    }

    words = new String[wordCount];
    state.update(s -> new PaymentsRecoveryEntryViewState(0, false, ""));
  }

  void onWordChanged(@NonNull String entry) {
    state.update(s -> new PaymentsRecoveryEntryViewState(s.getWordIndex(), isValid(entry), entry));
  }

  void onNextClicked() {
    state.update(s -> {
      words[s.getWordIndex()] = s.getCurrentEntry();

      if (s.getWordIndex() == words.length - 1) {
        events.postValue(Events.GO_TO_CONFIRM);
        return new PaymentsRecoveryEntryViewState(0, isValid(words[0]), words[0]);
      } else {
        int newIndex = s.getWordIndex() + 1;
        return new PaymentsRecoveryEntryViewState(newIndex, isValid(words[newIndex]), words[newIndex]);
      }
    });
  }

  private boolean isValid(@Nullable String string) {
    if (string == null) return false;
    else                return Mnemonic.BIP39_WORDS_ENGLISH.contains(string.toLowerCase());
  }

  enum Events {
    GO_TO_CONFIRM
  }
}
