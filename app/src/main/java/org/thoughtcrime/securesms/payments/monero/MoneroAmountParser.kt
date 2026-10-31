package org.thoughtcrime.securesms.payments.monero

import im.molly.monero.sdk.MoneroAmount
import java.math.BigDecimal
import java.math.RoundingMode

internal object MoneroAmountParser {
  fun parse(input: String): MoneroAmount {
    val normalized = input.trim().replace(',', '.')
    require(normalized.matches(Regex("[0-9]+(?:\\.[0-9]{1,12})?")))
    return try {
      val units = BigDecimal(normalized).movePointRight(MoneroAmount.ATOMIC_UNIT_SCALE)
        .setScale(0, RoundingMode.UNNECESSARY).longValueExact()
      require(units > 0)
      MoneroAmount(units)
    } catch (error: ArithmeticException) {
      throw IllegalArgumentException("Invalid XMR amount", error)
    }
  }
}
