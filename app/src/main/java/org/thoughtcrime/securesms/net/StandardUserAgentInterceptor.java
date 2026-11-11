package org.thoughtcrime.securesms.net;

import android.os.Build;

import org.thoughtcrime.securesms.BuildConfig;

/**
 * The user agent that should be used by default -- includes app name, version, etc.
 *
 * NOTE: This intentionally reports {@code SIGNAL_CLIENT_VERSION} (a real upstream Signal version),
 * NOT Radar's product {@code VERSION_NAME} ("1.0.x"). Signal's servers reject unrecognized/deprecated
 * client versions with HTTP 499, which blocks registration. See {@code signalUpstreamVersionName} in build.gradle.kts.
 */
public class StandardUserAgentInterceptor extends UserAgentInterceptor {

  public static final String USER_AGENT = "Signal-Android/" + BuildConfig.SIGNAL_CLIENT_VERSION + " Android/" + Build.VERSION.SDK_INT;

  public StandardUserAgentInterceptor() {
    super(USER_AGENT);
  }
}
