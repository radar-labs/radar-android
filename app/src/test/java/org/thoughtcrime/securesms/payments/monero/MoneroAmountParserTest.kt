package org.thoughtcrime.securesms.payments.monero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneroAmountParserTest {
  @Test fun decimalSeparators() {
    assertEquals(100_000_000L, MoneroAmountParser.parse("0,0001").atomicUnits)
    assertEquals(100_000_000L, MoneroAmountParser.parse("0.0001").atomicUnits)
    assertEquals(1L, MoneroAmountParser.parse("0.000000000001").atomicUnits)
  }

  @Test fun rejectsAmbiguousOrInvalidAmounts() {
    listOf("", "0", "-1", "NaN", "1e3", "1,000.1", "0.0000000000001", "999999999999999999999").forEach {
      assertThrows(IllegalArgumentException::class.java) { MoneroAmountParser.parse(it) }
    }
  }
}
