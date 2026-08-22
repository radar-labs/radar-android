package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.util.AppStartup;
import org.thoughtcrime.securesms.util.ConfigurationUtil;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.dynamiclanguage.DynamicLanguageContextWrapper;

import java.util.Objects;

/**
 * Base class for all activities. The vast majority of activities shouldn't extend this directly.
 * Instead, they should extend {@link PassphraseRequiredActivity} so they're protected by
 * screen lock.
 */
public abstract class BaseActivity extends AppCompatActivity {
  private static final String TAG = Log.tag(BaseActivity.class);

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    AppStartup.getInstance().onCriticalRenderEventStart();
    logEvent("onCreate()");
    super.onCreate(savedInstanceState);
    if (fitsSystemBars()) {
      applySystemBarInsetsToContent();
    }
    AppStartup.getInstance().onCriticalRenderEventEnd();
  }

  /**
   * Whether this activity wants its content kept clear of the status and navigation bars.
   *
   * <p>The app used to get this from {@code android:windowOptOutEdgeToEdgeEnforcement} on its base
   * themes, but that attribute is only honoured up to targetSdk 35. Targeting 36 made it a no-op,
   * so every screen that does not handle insets itself started drawing underneath the system bars —
   * clipped buttons at the bottom, toolbars colliding with the clock at the top.
   *
   * <p>Activities that deliberately draw edge to edge call {@code enableEdgeToEdge()} and position
   * their own content against the insets. They override this to {@code false}.
   */
  protected boolean fitsSystemBars() {
    return true;
  }

  /**
   * Reproduces what the framework used to do for us: pad the content view by the system bars and
   * the display cutout, then report those insets as consumed so nothing inside pads for them a
   * second time.
   *
   * <p>Consuming is the important half. Several screens (the conversation view, media, calls) have
   * their own inset handling that was written for a window which reported zero system-bar insets;
   * leaving the insets in place would make them pad on top of this padding. Zeroing them restores
   * exactly the values those screens saw before the target bump.
   *
   * <p>The IME inset is deliberately passed through untouched — keyboard avoidance is a separate
   * concern from the system bars, and screens that use it need to keep receiving it.
   */
  private void applySystemBarInsetsToContent() {
    final View content = findViewById(android.R.id.content);
    if (content == null) {
      return;
    }

    ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
      Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

      view.setPadding(bars.left, bars.top, bars.right, bars.bottom);

      return new WindowInsetsCompat.Builder(windowInsets)
          .setInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
          .build();
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    WindowUtil.initializeScreenshotSecurity(this, getWindow());
  }

  @Override
  protected void onStart() {
    logEvent("onStart()");
    AppDependencies.getShakeToReport().registerActivity(this);
    super.onStart();
  }

  @Override
  protected void onStop() {
    logEvent("onStop()");
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    logEvent("onDestroy()");
    super.onDestroy();
  }

  protected void startActivitySceneTransition(Intent intent, View sharedView, String transitionName) {
    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(this, sharedView, transitionName)
                                         .toBundle();
    ActivityCompat.startActivity(this, intent, bundle);
  }

  @Override
  protected void attachBaseContext(@NonNull Context newBase) {
    super.attachBaseContext(newBase);

    Configuration configuration      = new Configuration(newBase.getResources().getConfiguration());
    int           appCompatNightMode = getDelegate().getLocalNightMode() != AppCompatDelegate.MODE_NIGHT_UNSPECIFIED ? getDelegate().getLocalNightMode()
                                                                                                                     : AppCompatDelegate.getDefaultNightMode();

    configuration.uiMode      = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mapNightModeToConfigurationUiMode(newBase, appCompatNightMode);
    configuration.orientation = Configuration.ORIENTATION_UNDEFINED;

    applyOverrideConfiguration(configuration);
  }

  @Override
  public void applyOverrideConfiguration(@NonNull Configuration overrideConfiguration) {
    DynamicLanguageContextWrapper.prepareOverrideConfiguration(this, overrideConfiguration);
    super.applyOverrideConfiguration(overrideConfiguration);
  }

  private void logEvent(@NonNull String event) {
    Log.d(TAG, "[" + Log.tag(getClass()) + "] " + event);
  }

  public final @NonNull ActionBar requireSupportActionBar() {
    return Objects.requireNonNull(getSupportActionBar());
  }

  private static int mapNightModeToConfigurationUiMode(@NonNull Context context, @AppCompatDelegate.NightMode int appCompatNightMode) {
    if (appCompatNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
      return Configuration.UI_MODE_NIGHT_YES;
    } else if (appCompatNightMode == AppCompatDelegate.MODE_NIGHT_NO) {
      return Configuration.UI_MODE_NIGHT_NO;
    }
    return ConfigurationUtil.getNightModeConfiguration(context.getApplicationContext());
  }
}
