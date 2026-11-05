package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.ComparatorCompat;

import org.thoughtcrime.securesms.payments.proto.PaymentMetaData;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.Comparator;
import java.util.UUID;

/**
 * Represents one payment as displayed to the user.
 * <p>
 * It could be from a sent or received Signal payment message or reconstructed.
 */
public interface Payment {
  Comparator<Payment> ASCENDING_TIMESTAMP  = Comparator.comparingLong(Payment::getTimestamp);
  Comparator<Payment> DESCENDING_TIMESTAMP = ComparatorCompat.reversed(ASCENDING_TIMESTAMP);

  @NonNull UUID getUuid();

  @NonNull Payee getPayee();

  long getBlockIndex();

  long getBlockTimestamp();

  long getTimestamp();

  default long getDisplayTimestamp() {
    long blockTimestamp = getBlockTimestamp();
    if (blockTimestamp > 0) {
      return blockTimestamp;
    } else {
      return getTimestamp();
    }
  }

  @NonNull Direction getDirection();

  @NonNull State getState();

  @Nullable FailureReason getFailureReason();

  @NonNull String getNote();

  /**
   * The LNURL sender comment attached to a received lightning payment, or null if none.
   * Defaults to null so only payment types that actually carry one need to override it.
   */
  default @Nullable String getSenderComment() {
    return null;
  }

  /**
   * Always >= 0, does not include fee
   */
  @NonNull Money getAmount();

  /**
   * Always >= 0
   */
  @NonNull Money getFee();

  @NonNull PaymentMetaData getPaymentMetaData();

  default byte[] getReceipt() {
    return null;
  }

  boolean isSeen();

  /**
   * Negative if sent, positive if received.
   */
  default @NonNull Money getAmountWithDirection() {
    return switch (getDirection()) {
      case SENT -> getAmount().negate();
      case RECEIVED -> getAmount();
    };
  }

  /**
   * Negative if sent including fee, positive if received.
   */
  default @NonNull Money getAmountPlusFeeWithDirection() {
    return switch (getDirection()) {
      case SENT -> getAmount().add(getFee()).negate();
      case RECEIVED -> getAmount();
    };
  }

  default boolean isDefrag() {
    return getDirection() == Direction.SENT &&
           getPayee().hasRecipientId() &&
           getPayee().requireRecipientId().equals(Recipient.self().getId());
  }
}
