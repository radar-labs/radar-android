package org.thoughtcrime.securesms.payments.monero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration

class MoneroRetryPolicyTest {
  @Test fun firstRequestDoesNotWait() {
    assertEquals(Duration.ZERO, MoneroRetryPolicy().waitTime(0))
  }

  @Test fun retriesAreBounded() {
    assertEquals(1000L, MoneroRetryPolicy().waitTime(1).inWholeMilliseconds)
    assertThrows(IOException::class.java) { MoneroRetryPolicy().waitTime(2) }
    assertThrows(IOException::class.java) { MoneroRetryPolicy().waitTime(100) }
  }
}
