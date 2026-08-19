package org.thoughtcrime.securesms.payments.create;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.transition.TransitionManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiEditText;
import org.thoughtcrime.securesms.payments.FiatMoneyUtil;
import org.thoughtcrime.securesms.payments.MoneyView;
import org.thoughtcrime.securesms.payments.PaymentAmountFormatter;
import org.thoughtcrime.securesms.payments.preferences.RecipientHasNotEnabledPaymentsDialog;
import org.thoughtcrime.securesms.util.PlayStoreUtil;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;
import org.whispersystems.signalservice.api.payments.Money;

import java.text.DecimalFormatSymbols;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;

public class CreatePaymentFragment extends LoggingFragment {

  private static final Map<Integer,AmountKeyboardGlyph> ID_TO_GLYPH = new HashMap<Integer, AmountKeyboardGlyph>() {{
    put(R.id.create_payment_fragment_keyboard_decimal, AmountKeyboardGlyph.DECIMAL);
    put(R.id.create_payment_fragment_keyboard_lt, AmountKeyboardGlyph.BACK);
    put(R.id.create_payment_fragment_keyboard_0, AmountKeyboardGlyph.ZERO);
    put(R.id.create_payment_fragment_keyboard_1, AmountKeyboardGlyph.ONE);
    put(R.id.create_payment_fragment_keyboard_2, AmountKeyboardGlyph.TWO);
    put(R.id.create_payment_fragment_keyboard_3, AmountKeyboardGlyph.THREE);
    put(R.id.create_payment_fragment_keyboard_4, AmountKeyboardGlyph.FOUR);
    put(R.id.create_payment_fragment_keyboard_5, AmountKeyboardGlyph.FIVE);
    put(R.id.create_payment_fragment_keyboard_6, AmountKeyboardGlyph.SIX);
    put(R.id.create_payment_fragment_keyboard_7, AmountKeyboardGlyph.SEVEN);
    put(R.id.create_payment_fragment_keyboard_8, AmountKeyboardGlyph.EIGHT);
    put(R.id.create_payment_fragment_keyboard_9, AmountKeyboardGlyph.NINE);
  }};

  private ConstraintLayout constraintLayout;
  private TextView         balance;
  private MoneyView        amount;
  private TextView         exchange;
  private View             pay;
  private View             request;
  private EmojiEditText    note;
  private View             notePaste;
  private View             toggle;

  private ConstraintSet cryptoConstraintSet;
  private ConstraintSet fiatConstraintSet;

  public CreatePaymentFragment() {
    super(R.layout.create_payment_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar toolbar = view.findViewById(R.id.create_payment_fragment_toolbar);

    toolbar.setNavigationOnClickListener(this::goBack);

    CreatePaymentFragmentArgs      arguments = CreatePaymentFragmentArgs.fromBundle(requireArguments());
    CreatePaymentViewModel.Factory factory   = new CreatePaymentViewModel.Factory(arguments.getPayee(), arguments.getNote());
    CreatePaymentViewModel         viewModel = new ViewModelProvider(Navigation.findNavController(view).getViewModelStoreOwner(R.id.payments_create), factory).get(CreatePaymentViewModel.class);

    constraintLayout = view.findViewById(R.id.create_payment_fragment_amount_header);
    request          = view.findViewById(R.id.create_payment_fragment_request);
    amount           = view.findViewById(R.id.create_payment_fragment_amount);
    exchange         = view.findViewById(R.id.create_payment_fragment_exchange);
    pay              = view.findViewById(R.id.create_payment_fragment_pay);
    balance          = view.findViewById(R.id.create_payment_fragment_balance);
    note             = view.findViewById(R.id.create_payment_fragment_note);
    notePaste        = view.findViewById(R.id.create_payment_fragment_note_paste);
    toggle           = view.findViewById(R.id.create_payment_fragment_toggle);

    TextView decimal = view.findViewById(R.id.create_payment_fragment_keyboard_decimal);
    decimal.setText(String.valueOf(DecimalFormatSymbols.getInstance().getDecimalSeparator()));

    View infoTapTarget = view.findViewById(R.id.create_payment_fragment_info_tap_region);
    infoTapTarget.setVisibility(View.GONE);

    // The note is entered inline. Keep the view model in sync as the user types or pastes so the
    // current text is included when they tap Send (see getCreatePaymentDetails()).
    note.addTextChangedListener(new AfterTextChanged(editable -> viewModel.setNote(editable != null ? editable.toString() : null)));
    notePaste.setOnClickListener(v -> pasteIntoNote());

    pay.setOnClickListener(v -> {
      NavDirections directions = CreatePaymentFragmentDirections.actionCreatePaymentFragmentToConfirmPaymentFragment(viewModel.getCreatePaymentDetails())
                                                                .setFinishOnConfirm(arguments.getFinishOnConfirm());
      SafeNavigation.safeNavigate(Navigation.findNavController(v), directions);
    });

    toggle.setOnClickListener(v -> viewModel.toggleMoneyInputTarget());

    initializeConstraintSets();
    initializeKeyboardButtons(view, viewModel);

    viewModel.getInputState().observe(getViewLifecycleOwner(), inputState -> {
      updateAmount(inputState);
      updateExchange(inputState);
      updateMoneyInputTarget(inputState.getInputTarget());
    });

    viewModel.getIsPaymentsSupportedByPayee().observe(getViewLifecycleOwner(), isSupported -> {
      if (!isSupported) RecipientHasNotEnabledPaymentsDialog.show(requireContext(), () -> goBack(requireView()));
    });

    viewModel.isValidAmount().observe(getViewLifecycleOwner(), this::updateRequestAmountButtons);
    viewModel.getNote().observe(getViewLifecycleOwner(), this::updateNote);
    viewModel.getSpendableBalance().observe(getViewLifecycleOwner(), this::updateBalance);
    // NB: getNote() observed above only to seed the field with any note passed in via arguments.
    viewModel.getCanSendPayment().observe(getViewLifecycleOwner(), this::updatePayAmountButtons);
    viewModel.getEnclaveFailure().observe(getViewLifecycleOwner(), failure -> {
      if (failure) {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.PaymentsHomeFragment__update_required))
            .setMessage(getString(R.string.PaymentsHomeFragment__an_update_is_required))
            .setPositiveButton(R.string.PaymentsHomeFragment__update_now, (dialog, which) -> { PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext()); })
            .setNegativeButton(R.string.PaymentsHomeFragment__cancel, (dialog, which) -> {})
            .setCancelable(false)
            .show();
      }
    });
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    constraintLayout = null;
    notePaste        = null;
    balance          = null;
    amount           = null;
    exchange         = null;
    request          = null;
    toggle           = null;
    note             = null;
    pay              = null;
  }

  private void goBack(View v) {
    if (!Navigation.findNavController(v).popBackStack()) {
      requireActivity().finish();
    }
  }

  private void pasteIntoNote() {
    ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null || clipboard.getPrimaryClip().getItemCount() == 0) {
      return;
    }

    ClipData.Item item   = clipboard.getPrimaryClip().getItemAt(0);
    CharSequence  pasted = item.coerceToText(requireContext());
    if (TextUtils.isEmpty(pasted) || note.getText() == null) {
      return;
    }

    int start = Math.max(note.getSelectionStart(), 0);
    int end   = Math.max(note.getSelectionEnd(), 0);
    note.getText().replace(Math.min(start, end), Math.max(start, end), pasted);
    note.requestFocus();
  }

  private void updateNote(@Nullable CharSequence noteValue) {
    // Only seed the field when the incoming value differs (e.g. a note passed via arguments).
    // Writing on every emission would fight the user's typing and move the cursor, and the
    // text watcher already pushes edits back into the view model.
    if (!TextUtils.equals(note.getText(), noteValue)) {
      note.setText(noteValue);
      note.setSelection(noteValue != null ? noteValue.length() : 0);
    }
  }

  private void initializeKeyboardButtons(@NonNull View view, @NonNull CreatePaymentViewModel viewModel) {
    for (Map.Entry<Integer, AmountKeyboardGlyph> entry : ID_TO_GLYPH.entrySet()) {
      view.findViewById(entry.getKey()).setOnClickListener(v -> viewModel.updateAmount(requireContext(), entry.getValue()));
    }

    view.findViewById(R.id.create_payment_fragment_keyboard_lt).setOnLongClickListener(v -> {
      viewModel.clearAmount();
      return true;
    });
  }

  private void updateAmount(@NonNull InputState inputState) {
    switch (inputState.getInputTarget()) {
      case MONEY:
        amount.setMoney(inputState.getMoneyAmount(), PaymentAmountFormatter.unit(inputState.getMoney()));
        break;
      case FIAT_MONEY:
        amount.setMoney(inputState.getMoney(), false);
        break;
    }
  }

  private void updateExchange(@NonNull InputState inputState) {
    switch (inputState.getInputTarget()) {
      case MONEY:
        if (inputState.getFiatMoney().isPresent()) {
          exchange.setVisibility(View.VISIBLE);
          exchange.setText(FiatMoneyUtil.format(getResources(), inputState.getFiatMoney().get(), FiatMoneyUtil.formatOptions().withDisplayTime(false)));
          toggle.setVisibility(View.VISIBLE);
          toggle.setEnabled(true);
        } else {
          exchange.setVisibility(View.INVISIBLE);
          toggle.setVisibility(View.INVISIBLE);
          toggle.setEnabled(false);
        }
        break;
      case FIAT_MONEY:
        Currency currency = inputState.getFiatMoney().get().getCurrency();
        exchange.setText(FiatMoneyUtil.manualFormat(currency, inputState.getFiatAmount()));
        break;
    }
  }

  private void updateRequestAmountButtons(boolean isValidAmount) {
    request.setEnabled(isValidAmount);
  }

  private void updatePayAmountButtons(boolean isValidAmount) {
    pay.setEnabled(isValidAmount);
  }

  private void updateBalance(@NonNull Money balance) {
    String value = PaymentAmountFormatter.formatRespectingHiddenBalance(balance);
    this.balance.setText(SpanUtil.boldSubstring(getString(R.string.CreatePaymentFragment__available_balance_s, value), value));
  }

  private void initializeConstraintSets() {
    cryptoConstraintSet = new ConstraintSet();
    cryptoConstraintSet.clone(constraintLayout);

    fiatConstraintSet = new ConstraintSet();
    fiatConstraintSet.clone(getContext(), R.layout.create_payment_fragment_amount_toggle);
  }

  private void updateMoneyInputTarget(@NonNull InputTarget target) {
    TransitionManager.endTransitions(constraintLayout);
    TransitionManager.beginDelayedTransition(constraintLayout);

    switch (target) {
      case FIAT_MONEY:
        fiatConstraintSet.applyTo(constraintLayout);
        amount.setTextColor(ContextCompat.getColor(requireContext(), R.color.signal_text_secondary));
        exchange.setTextColor(ContextCompat.getColor(requireContext(), R.color.signal_text_primary));
        break;
      case MONEY:
        cryptoConstraintSet.applyTo(constraintLayout);
        exchange.setTextColor(ContextCompat.getColor(requireContext(), R.color.signal_text_secondary));
        amount.setTextColor(ContextCompat.getColor(requireContext(), R.color.signal_text_primary));
        break;
    }
  }
}
