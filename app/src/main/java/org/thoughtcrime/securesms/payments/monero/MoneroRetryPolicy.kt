package org.thoughtcrime.securesms.payments.monero

import im.molly.monero.sdk.BackoffPolicy
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Bound the SDK's otherwise infinite per-request retry loop. */
internal class MoneroRetryPolicy : BackoffPolicy {
  override fun waitTime(retryCount: Int): Duration {
    if (retryCount >= 2) throw IOException("The Monero node did not respond. Please retry synchronization.")
    return if (retryCount == 0) Duration.ZERO else 1.seconds
  }
}
