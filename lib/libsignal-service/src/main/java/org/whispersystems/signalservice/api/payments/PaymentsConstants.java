package org.whispersystems.signalservice.api.payments;

public final class PaymentsConstants {

  private PaymentsConstants() {}

  /** Entropy length used for wallets this client creates, i.e. a {@link #MNEMONIC_LENGTH} phrase. */
  public static final int PAYMENTS_ENTROPY_LENGTH = 16;
  public static final int MNEMONIC_LENGTH         = Math.round(PAYMENTS_ENTROPY_LENGTH * 0.75f);
  public static final int SHORT_FRACTION_LENGTH   = 4;

  /**
   * Entropy lengths this client can <em>restore</em>, newest first. New wallets are always minted at
   * {@link #PAYMENTS_ENTROPY_LENGTH}, but wallets created before the switch to a 12-word phrase hold
   * 32 bytes, and their owners must still be able to recover them.
   */
  public static final int[] SUPPORTED_PAYMENTS_ENTROPY_LENGTHS = { 16, 32 };

  /** Recovery phrase word counts this client can restore, matching {@link #SUPPORTED_PAYMENTS_ENTROPY_LENGTHS}. */
  public static final int[] SUPPORTED_MNEMONIC_LENGTHS = { 12, 24 };

  public static boolean isSupportedPaymentsEntropyLength(int length) {
    return contains(SUPPORTED_PAYMENTS_ENTROPY_LENGTHS, length);
  }

  public static boolean isSupportedMnemonicLength(int wordCount) {
    return contains(SUPPORTED_MNEMONIC_LENGTHS, wordCount);
  }

  private static boolean contains(int[] values, int value) {
    for (int candidate : values) {
      if (candidate == value) {
        return true;
      }
    }
    return false;
  }

}
