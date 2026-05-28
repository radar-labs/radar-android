package org.thoughtcrime.securesms.payments.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.PaymentLedgerUpdateJob;
import org.thoughtcrime.securesms.payments.preferences.details.PaymentDetailsFragmentArgs;
import org.thoughtcrime.securesms.payments.preferences.details.PaymentDetailsParcelable;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

import java.util.UUID;

public class PaymentsActivity extends PassphraseRequiredActivity {

  public static final String EXTRA_PAYMENTS_STARTING_ACTION = "payments_starting_action";
  public static final String EXTRA_STARTING_ARGUMENTS       = "payments_starting_arguments";
  /**
   * When set to {@code true}, the activity hosts the {@link PaymentSettingsMenuFragment}
   * as the graph's start destination (so back exits the activity rather than landing on
   * the wallet UI). Used by the Settings → Payments entry to mirror iOS
   * {@code PaymentSettingsMenuViewController}.
   */
  public static final String EXTRA_OPEN_SETTINGS_MENU       = "payments_open_settings_menu";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  public static Intent navigateToPaymentDetails(@NonNull Context context, @NonNull UUID paymentId) {
    Intent intent = new Intent(context, PaymentsActivity.class);

    intent.putExtra(EXTRA_PAYMENTS_STARTING_ACTION, R.id.action_directly_to_paymentDetails);
    intent.putExtra(EXTRA_STARTING_ARGUMENTS, new PaymentDetailsFragmentArgs.Builder(PaymentDetailsParcelable.forUuid(paymentId)).build().toBundle());

    return intent;
  }

  /** Opens the activity with {@link PaymentSettingsMenuFragment} as the start destination. */
  public static Intent navigateToSettingsMenu(@NonNull Context context) {
    Intent intent = new Intent(context, PaymentsActivity.class);
    intent.putExtra(EXTRA_OPEN_SETTINGS_MENU, true);
    return intent;
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState, boolean ready) {
    dynamicTheme.onCreate(this);

    setContentView(R.layout.payments_activity);

    NavController controller       = Navigation.findNavController(this, R.id.nav_host_fragment);
    NavGraph      graph            = controller.getNavInflater().inflate(R.navigation.payments_preferences);
    boolean       openSettingsMenu = getIntent().getBooleanExtra(EXTRA_OPEN_SETTINGS_MENU, false);

    if (openSettingsMenu) {
      graph.setStartDestination(R.id.paymentSettingsMenu);
    }
    controller.setGraph(graph, null);

    int startingAction = getIntent().getIntExtra(EXTRA_PAYMENTS_STARTING_ACTION, R.id.paymentsHome);
    if (startingAction != R.id.paymentsHome) {
      SafeNavigation.safeNavigate(controller, startingAction, getIntent().getBundleExtra(EXTRA_STARTING_ARGUMENTS));
    }
  }

  @Override
  protected void onResume() {
    super.onResume();

    dynamicTheme.onResume(this);

    AppDependencies.getJobManager()
                   .add(PaymentLedgerUpdateJob.updateLedger());
  }
}
