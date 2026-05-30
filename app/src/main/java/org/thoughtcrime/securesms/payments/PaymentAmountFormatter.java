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

  private PaymentAmountFormatter() {}

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
