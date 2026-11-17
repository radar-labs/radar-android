package org.thoughtcrime.securesms.payments

import org.junit.Assert.assertTrue
import org.junit.Test

class LightningAddressBitcoinTest {
  @Test
  fun `accepts a mainnet bitcoin address as a payment destination`() {
    val address = "1BoatSLRHtKNngkdXEeobR76b53LETtpyT"

    assertTrue(LightningAddress.looksLikeBitcoinDestination(address))
  }

  @Test
  fun `accepts a BIP-21 URI and retains its amount`() {
    val uri = "bitcoin:1BoatSLRHtKNngkdXEeobR76b53LETtpyT?amount=0.0001"

    assertTrue(LightningAddress.looksLikeBitcoinDestination(uri))
  }
}
