package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Objects;

public final class Payee {
  private final RecipientId             recipientId;
  private final LightningAddress publicAddress;

  /**
   * Used for reconstructed payments from the ledger where we do not know who it was from or to.
   */
  public static final Payee UNKNOWN = new Payee(null, null);

  public static Payee fromRecipientAndAddress(@NonNull RecipientId recipientId, @NonNull MobileCoinPublicAddress publicAddress) {
     return new Payee(Objects.requireNonNull(recipientId));
  }

  public Payee(@NonNull RecipientId recipientId) {
    this(Objects.requireNonNull(recipientId), null);
  }

  public Payee(@NonNull MobileCoinPublicAddress publicAddress) {
    this(null, null);
  }

  public Payee(@NonNull LightningAddress publicAddress) {
    this(null, Objects.requireNonNull(publicAddress));
  }

  public Payee(@Nullable RecipientId recipientId, @Nullable LightningAddress publicAddress) {
    this.recipientId   = recipientId;
    this.publicAddress = publicAddress;
  }

  public boolean hasRecipientId() {
    return recipientId != null && !recipientId.isUnknown();
  }

  public @NonNull RecipientId requireRecipientId() {
    return Objects.requireNonNull(recipientId);
  }

  public boolean hasPublicAddress() {
    return publicAddress != null;
  }

  @Deprecated
  public @NonNull MobileCoinPublicAddress requirePublicAddress() {
    return null;
  }

  public @NonNull LightningAddress requireLightningAddress() {
    return Objects.requireNonNull(publicAddress);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final Payee payee = (Payee) o;
    return Objects.equals(recipientId, payee.recipientId) &&
           Objects.equals(publicAddress, payee.publicAddress);
  }

  @Override
  public int hashCode() {
    return Objects.hash(recipientId, publicAddress);
  }
}
