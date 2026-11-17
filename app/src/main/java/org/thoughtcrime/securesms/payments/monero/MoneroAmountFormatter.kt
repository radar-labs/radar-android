package org.thoughtcrime.securesms.payments.monero

import im.molly.monero.sdk.MoneroAmount
import java.math.BigDecimal

internal object MoneroAmountFormatter {
  fun format(amount: MoneroAmount): String = BigDecimal.valueOf(amount.atomicUnits, MoneroAmount.ATOMIC_UNIT_SCALE)
    .stripTrailingZeros().toPlainString()
}
