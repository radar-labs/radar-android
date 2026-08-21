package org.thoughtcrime.securesms.payments;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.util.DateUtils;
import org.whispersystems.signalservice.api.payments.Currency;
import org.whispersystems.signalservice.api.payments.FormatterOptions;
import org.whispersystems.signalservice.api.payments.Money;

import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class MoneyView extends AppCompatTextView {
  private FormatterOptions formatterOptions;

  public MoneyView(@NonNull Context context) {
    super(context);

    init(context, null);
  }

  public MoneyView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);

    init(context, attrs);
  }

  public MoneyView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context, attrs);
  }

  public void init(@NonNull Context context, @Nullable AttributeSet attrs) {
    FormatterOptions.Builder builder = FormatterOptions.builder(Locale.getDefault());

    TypedArray styledAttributes = context.obtainStyledAttributes(attrs, R.styleable.MoneyView, 0, 0);

    if (styledAttributes.getBoolean(R.styleable.MoneyView_always_show_sign, false)) {
      builder.alwaysPrefixWithSign();
    }

    // Radar: show a space between the amount and the currency unit (mirrors iOS "add a space
    // between balance and currency"). iOS applied it to the balance specifically; on Android
    // MoneyView is the single money formatter, so the space applies consistently.
    formatterOptions = builder.build();

    String value = styledAttributes.getString(R.styleable.MoneyView_money);
    if (value != null) {
      try {
        setMoney(Money.parse(value));
      } catch (Money.ParseException e) {
        throw new AssertionError("Invalid money format", e);
      }
    }

    styledAttributes.recycle();
  }

  public @NonNull String localizeAmountString(@NonNull String amount) {
    String decimalSeparator  = String.valueOf(DecimalFormatSymbols.getInstance().getDecimalSeparator());
    String groupingSeparator = String.valueOf(DecimalFormatSymbols.getInstance().getGroupingSeparator());

    return amount.replace(".", "__D__").replace(",", "__G__").replace("__D__", decimalSeparator).replace("__G__", groupingSeparator);
  }

  public void setMoney(@NonNull String amount, @NonNull Currency currency) {
    setMoney(amount, currency.getCurrencyCode());
  }

  /**
   * Renders a raw, in-progress amount string followed by an arbitrary unit label (e.g. {@code
   * "sats"} or {@code "BTC"}), highlighting the unit. Used by the send flow so the displayed unit
   * follows the user's sats/BTC preference instead of always being the currency code.
   */
  public void setMoney(@NonNull String amount, @NonNull String unit) {
    SpannableString balanceSpan = new SpannableString(localizeAmountString(amount) + " " + unit);
    int             unitIndex   = balanceSpan.length() - unit.length();
    balanceSpan.setSpan(new ForegroundColorSpan(ContextCompat.getColor(getContext(), R.color.payment_currency_code_foreground_color)), unitIndex, balanceSpan.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    setText(balanceSpan);
  }

  public void setMoney(@NonNull Money money) {
    setMoney(money, true, 0L);
  }

  public void setMoney(@NonNull Money money, boolean highlightCurrency) {
    setMoney(money, highlightCurrency, 0L);
  }

  public void setMoney(@NonNull Money money, boolean highlightCurrency, long timestamp) {
    setMoney(money, timestamp, (highlightCurrency ? new ForegroundColorSpan(ContextCompat.getColor(getContext(), R.color.payment_currency_code_foreground_color)) : null));
  }

  /**
   * Displays {@code money}, or {@link PaymentAmountFormatter#HIDDEN_AMOUNT} while the user has
   * hidden their balance. For amounts that are being shown back to the user (a settled payment, a
   * balance); the send-flow input keeps using {@link #setMoney} so the user can always see what
   * they are typing.
   */
  public void setMoneyRespectingHiddenBalance(@NonNull Money money) {
    setMoneyRespectingHiddenBalance(money, true, 0L);
  }

  /** @see #setMoneyRespectingHiddenBalance(Money) */
  public void setMoneyRespectingHiddenBalance(@NonNull Money money, boolean highlightCurrency) {
    setMoneyRespectingHiddenBalance(money, highlightCurrency, 0L);
  }

  /** @see #setMoneyRespectingHiddenBalance(Money) */
  public void setMoneyRespectingHiddenBalance(@NonNull Money money, boolean highlightCurrency, long timestamp) {
    setMoneyRespectingHiddenBalance(money, timestamp, (highlightCurrency ? new ForegroundColorSpan(ContextCompat.getColor(getContext(), R.color.payment_currency_code_foreground_color)) : null));
  }

  /** @see #setMoneyRespectingHiddenBalance(Money) */
  public void setMoneyRespectingHiddenBalance(@NonNull Money money, long timestamp, @Nullable Object currencySpan) {
    if (PaymentAmountFormatter.isBalanceHidden()) {
      setText(PaymentAmountFormatter.HIDDEN_AMOUNT);
      return;
    }
    setMoney(money, timestamp, currencySpan);
  }

  public void setMoney(@NonNull Money money, long timestamp, @Nullable Object currencySpan) {
    // The sats-vs-BTC decision and all formatting live in PaymentAmountFormatter, the single
    // display layer. Every caller (home balance, chat bubble, history, send flow, chat-list
    // snippet, …) renders through it, so flipping the preference updates them consistently.
    final String balance         = PaymentAmountFormatter.format(money, formatterOptions);
    final String unitToHighlight = PaymentAmountFormatter.unit(money);

    int currencyIndex = balance.indexOf(unitToHighlight);

    final SpannableString balanceSpan;

    if (timestamp > 0L) {
      balanceSpan = new SpannableString(getResources().getString(R.string.CurrencyAmountFormatter_s_at_s,
                                        balance,
                                        DateUtils.getTimeString(AppDependencies.getApplication(), Locale.getDefault(), timestamp)));
    } else {
      balanceSpan = new SpannableString(balance);
    }

    if (currencySpan != null && currencyIndex >= 0) {
      balanceSpan.setSpan(currencySpan, currencyIndex, currencyIndex + unitToHighlight.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    setText(balanceSpan);
  }

}
