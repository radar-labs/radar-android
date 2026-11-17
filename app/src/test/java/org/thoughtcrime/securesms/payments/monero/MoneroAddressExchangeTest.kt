package org.thoughtcrime.securesms.payments.monero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class MoneroAddressExchangeTest {
  private val mainnetAddress = "42ey1afDFnn4886T7196doS9GPMzexD9gXpsZJDwVjeRVdFCSoHnv7KPbBeGpzJBzHRCAs9UxqeoyFQMYbqSWYTfJJQAWDm"

  private fun iosReply(id: String, network: Int = 0, expiresAt: Double = 2000.0): String {
    val payload = """{"version":1,"kind":"address","id":"$id","network":$network,"expiresAt":$expiresAt,"address":"$mainnetAddress"}"""
    return "Monero receiving address shared\n\n${MoneroAddressExchange.PREFIX}${Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))}"
  }

  @Test fun `ios uppercase UUID matches Android request`() {
    val id = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    assertEquals(mainnetAddress, MoneroAddressExchange.parseAddressReply(iosReply(id.uppercase()), id, 1000.0))
    assertEquals(mainnetAddress, MoneroAddressExchange.parseAddressReply(iosReply(id), id, 1000.0))
  }

  @Test fun `unavailable response is recognized without accepting unrelated replies`() {
    val request = MoneroAddressExchange.createRequest()
    val reply = request.reply(null)
    assertTrue(MoneroAddressExchange.isUnavailableReply(reply, request.id.uppercase()))
    assertFalse(MoneroAddressExchange.isUnavailableReply(reply, "3f2504e0-4f89-41d3-9a0c-0305e82c3301"))
    assertFalse(MoneroAddressExchange.isUnavailableReply(reply, request.id, Double.MAX_VALUE))
    assertFalse(MoneroAddressExchange.isUnavailableReply(iosReply(request.id), request.id, 1000.0))
  }

  @Test fun `reply must match request network and expiry`() {
    val id = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    assertNull(MoneroAddressExchange.parseAddressReply(iosReply(id), "3f2504e0-4f89-41d3-9a0c-0305e82c3302", 1000.0))
    assertNull(MoneroAddressExchange.parseAddressReply(iosReply(id, network = 2), id, 1000.0))
    assertNull(MoneroAddressExchange.parseAddressReply(iosReply(id, expiresAt = 999.0), id, 1000.0))
    assertNull(MoneroAddressExchange.parseAddressReply(iosReply("1-1-1-1-1"), "1-1-1-1-1", 1000.0))
  }

  @Test fun `published address works without a live request`() {
    val id = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    val published = iosReply(id)

    assertEquals(mainnetAddress, MoneroAddressExchange.parseSharedAddress(published))
    assertTrue(MoneroAddressExchange.isControlMessage(published))
    assertEquals("", MoneroAddressExchange.displayText(published))
    assertNull(MoneroAddressExchange.parseSharedAddress(iosReply(id, network = 2)))
  }

  @Test
  fun `generated request round trips with an expiring id`() {
    val (request, body) = MoneroAddressExchange.requestMessage()

    val parsed = MoneroAddressExchange.parseRequest(body)

    assertNotNull(parsed)
    assertEquals(request.id, parsed?.id)
    assertEquals(0, parsed?.network)
    assertTrue(MoneroAddressExchange.isControlMessage(body))
    assertTrue(MoneroAddressExchange.isControlMessage(request.reply("address-placeholder")))
    assertTrue(MoneroAddressExchange.isControlMessage(request.reply(null)))
    assertFalse(MoneroAddressExchange.isControlMessage("ordinary chat message"))
  }

  @Test
  fun `request parser rejects a message that is not an address request`() {
    val (request, body) = MoneroAddressExchange.requestMessage()
    val requestPayload = body.substringAfter(MoneroAddressExchange.PREFIX)
    val json = String(Base64.getDecoder().decode(requestPayload), Charsets.UTF_8)
      .replace("\"kind\":\"request\"", "\"kind\":\"address\"")
    val malformed = "${MoneroAddressExchange.PREFIX}${Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))}"

    assertNull(MoneroAddressExchange.parseRequest(malformed))
    assertNotNull(request)
  }

  @Test
  fun `payment sent from ios displays only the human notice`() {
    val payload = """{"version":1,"kind":"paymentSent","id":"3f2504e0-4f89-41d3-9a0c-0305e82c3301","network":0,"expiresAt":${System.currentTimeMillis() / 1000.0 + 600},"address":null,"amount":1000000000}"""
    val body = "A contact says they sent you 0.01 XMR on Mainnet. Check your Monero wallet to verify receipt.\n\n${MoneroAddressExchange.PREFIX}${Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))}"

    assertEquals("A contact says they sent you 0.01 XMR on Mainnet. Check your Monero wallet to verify receipt.", MoneroAddressExchange.displayText(body))
    assertFalse(MoneroAddressExchange.isControlMessage(body))
  }
}
