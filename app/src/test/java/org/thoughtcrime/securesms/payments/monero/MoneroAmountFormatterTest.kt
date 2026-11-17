package org.thoughtcrime.securesms.payments.monero

import im.molly.monero.sdk.MoneroAmount
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneroAmountFormatterTest {
  @Test fun removesOnlyInsignificantZeros() {
    mapOf(0L to "0", 1L to "0.000000000001", 1_000_000_000L to "0.001", 1_000_000_000_001L to "1.000000000001", 10_000_000_000_000L to "10").forEach { (units, expected) ->
      assertEquals(expected, MoneroAmountFormatter.format(MoneroAmount(units)))
    }
  }
}
