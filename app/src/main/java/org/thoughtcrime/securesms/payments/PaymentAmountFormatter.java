package org.thoughtcrime.securesms.payments;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.whispersystems.signalservice.api.payments.FormatterOptions;
import org.whispersystems.signalservice.api.payments.Money;

import java.math.BigInteger;
import java.text.NumberFormat;

/**
 * Single source of truth for rendering a {@link Money} amount as a user-facing string.
 *
 * <p>Mirrors iOS {@code PaymentsFormat}: every place that shows a payment amount (home
 * balance, chat bubbles, transaction history, chat-list snippets, message bodies, the
 * send/confirm flow, …) routes through here, so flipping the sats/BTC display preference
 * ({@link org.thoughtcrime.securesms.keyvalue.PaymentsValues#getShowInSats()}) updates the
 * amount everywhere consistently.
 *
 * <p>When the amount is a Bitcoin/Lightning value and the user has chosen the sats unit, it
 * is rendered in raw satoshis (grouped, no decimals) with a {@code "sats"} unit. All
 * {@link FormatterOptions} (sign prefix, always-positive, unit, spacing) are honored in the
 * sats path exactly as the BTC {@code CryptoFormatter} honors them, so callers don't need to
 * special-case the unit. For MobileCoin or when sats display is off, formatting delegates to
 * {@link Money#toString(FormatterOptions)} unchanged.
 */
public final class PaymentAmountFormatter {

  public static final String SATS_UNIT = "sats";

  /**
   * Placeholder rendered in place of an amount while the user has chosen to hide their balance.
   * Single definition so every masked surface uses the same glyph run.
   */
  public static final String HIDDEN_AMOUNT = "\u2022\u2022\u2022\u2022\u2022\u2022";

  private PaymentAmountFormatter() {}

  /** Whether amounts should currently be masked, i.e. the user has hidden their balance. */
  public static boolean isBalanceHidden() {
    return SignalStore.payments().getBalanceHidden();
  }

  /** Whether the given amount should currently be rendered in sats. */
  public static boolean isSatsDisplay(@NonNull Money money) {
    return money instanceof Money.Satoshi && SignalStore.payments().getShowInSats();
  }

  /**
   * The unit label that {@link #format(Money, FormatterOptions)} appends for the given amount
   * ({@code "sats"} or the currency code, e.g. {@code "BTC"}). Useful for callers that need to
   * highlight the unit substring (see {@link MoneyView}).
   */
  public static @NonNull String unit(@NonNull Money money) {
    return isSatsDisplay(money) ? SATS_UNIT : money.getCurrency().getCurrencyCode();
  }

  /** Formats with default options, respecting the sats/BTC preference. */
  public static @NonNull String format(@NonNull Money money) {
    return format(money, FormatterOptions.defaults());
  }

  /**
   * Formats respecting the sats/BTC preference <em>and</em> the hide-balance preference, returning
   * {@link #HIDDEN_AMOUNT} while the balance is hidden.
   *
   * <p>Use this for any amount that is merely being <em>displayed</em> — a settled payment, a
   * balance readout. Do <em>not</em> use it for an amount the user is actively entering or
   * confirming: hiding the balance is about not shoulder-surfing your holdings, not about being
   * unable to see what you are about to send. Mirrors the split iOS draws between
   * {@code PaymentsFormat.formattedBalance} (masks) and {@code PaymentsFormat.format} (does not).
   */
  public static @NonNull String formatRespectingHiddenBalance(@NonNull Money money) {
    return formatRespectingHiddenBalance(money, FormatterOptions.defaults());
  }

  /** @see #formatRespectingHiddenBalance(Money) */
  public static @NonNull String formatRespectingHiddenBalance(@NonNull Money money, @NonNull FormatterOptions options) {
    return isBalanceHidden() ? HIDDEN_AMOUNT : format(money, options);
  }

  /** Formats with the given options, respecting the sats/BTC preference. */
  public static @NonNull String format(@NonNull Money money, @NonNull FormatterOptions options) {
    if (isSatsDisplay(money)) {
      return formatSats((Money.Satoshi) money, options);
    }
    return money.toString(options);
  }

  private static @NonNull String formatSats(@NonNull Money.Satoshi sats, @NonNull FormatterOptions options) {
    BigInteger    raw      = sats.toSatoshiBigInteger();
    BigInteger    toFormat = options.alwaysPositive ? raw.abs() : raw;
    NumberFormat  format   = NumberFormat.getNumberInstance(options.locale);
    StringBuilder builder  = new StringBuilder();

    format.setGroupingUsed(true);
    format.setMaximumFractionDigits(0);

    if (toFormat.signum() == 1 && options.alwaysPrefixWithSign) {
      builder.append("+");
    }

    builder.append(format.format(toFormat));

    if (options.withSpaceBeforeUnit && options.withUnit) {
      builder.append(" ");
    }

    if (options.withUnit) {
      builder.append(SATS_UNIT);
    }

    return builder.toString();
  }
}
