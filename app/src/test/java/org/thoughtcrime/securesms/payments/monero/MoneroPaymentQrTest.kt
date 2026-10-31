package org.thoughtcrime.securesms.payments.monero

import org.junit.Assert.assertNull
import org.junit.Test

class MoneroPaymentQrTest {
  @Test
  fun `rejects malformed and non-Monero QR payloads`() {
    assertNull(MoneroPaymentQr.parse("https://example.org/pay"))
    assertNull(MoneroPaymentQr.parse("monero:not-an-address?tx_amount=0.01"))
    assertNull(MoneroPaymentQr.parse("monero:address?tx_amount=not-a-number"))
  }
}
