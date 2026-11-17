package org.thoughtcrime.securesms.payments.monero

import kotlinx.serialization.json.*
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class MoneroAddressExchange(val payload: JsonObject) {
  val id: String get() = payload.getValue("id").jsonPrimitive.content
  val network: Int get() = payload.getValue("network").jsonPrimitive.int

  fun reply(address: String?): String {
    val reply = JsonObject(payload + mapOf(
      "kind" to JsonPrimitive(if (address == null) "unavailable" else "address"),
      "address" to (address?.let(::JsonPrimitive) ?: JsonNull),
      "amount" to JsonNull,
    ))
    val fallbackText = when (reply["kind"]?.jsonPrimitive?.content) {
      "address" -> "Monero receiving address shared"
      "unavailable" -> "Monero address unavailable"
      else -> "Monero payment update"
    }
    return "$fallbackText\n\n$PREFIX" + Base64.getEncoder().encodeToString(reply.toString().toByteArray(Charsets.UTF_8))
  }

  companion object {
    const val PREFIX = "radar-monero-exchange-v1:"
    private const val LIFETIME = 7 * 24 * 60 * 60

    fun createRequest(network: Int = 0): MoneroAddressExchange {
      require(network in 0..2)
      val payload = buildJsonObject {
        put("id", UUID.randomUUID().toString())
        put("expiresAt", System.currentTimeMillis() / 1000.0 + TimeUnit.MINUTES.toSeconds(5))
        put("version", 1)
        put("kind", "request")
        put("network", network)
      }
      return MoneroAddressExchange(payload)
    }

    fun requestMessage(network: Int = 0): Pair<MoneroAddressExchange, String> {
      val request = createRequest(network)
      val encoded = Base64.getEncoder().encodeToString(request.payload.toString().toByteArray(Charsets.UTF_8))
      return request to "Monero payment request\n\n$PREFIX$encoded"
    }

    fun parseRequest(body: String, nowSeconds: Double = System.currentTimeMillis() / 1000.0): MoneroAddressExchange? = runCatching {
      require(body.toByteArray(Charsets.UTF_8).size <= 2048)
      val marker = body.indexOf(PREFIX)
      require(marker >= 0 && body.indexOf(PREFIX, marker + PREFIX.length) == -1)
      val encoded = body.substring(marker + PREFIX.length).trim()
      val value = Json.parseToJsonElement(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)).jsonObject
      require(value["version"]?.jsonPrimitive?.int == 1)
      require(value["kind"]?.jsonPrimitive?.content == "request")
      val id = value.getValue("id").jsonPrimitive.content
      require(UUID.fromString(id).toString().equals(id, ignoreCase = true))
      require(value.getValue("network").jsonPrimitive.int in 0..2)
      val expiry = value.getValue("expiresAt").jsonPrimitive.double
      require(expiry.isFinite() && expiry > nowSeconds && expiry <= nowSeconds + LIFETIME + 30)
      require(value["address"] == null || value["address"] == JsonNull)
      require(value["amount"] == null || value["amount"] == JsonNull)
      MoneroAddressExchange(value)
    }.getOrNull()

    fun parseAddressReply(body: String, expectedId: String, nowSeconds: Double = System.currentTimeMillis() / 1000.0): String? = runCatching {
      require(body.toByteArray(Charsets.UTF_8).size <= 4096)
      val marker = body.indexOf(PREFIX)
      require(marker >= 0 && body.indexOf(PREFIX, marker + PREFIX.length) == -1)
      val encoded = body.substring(marker + PREFIX.length).trim()
      val value = Json.parseToJsonElement(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)).jsonObject
      require(value["version"]?.jsonPrimitive?.int == 1)
      require(value["kind"]?.jsonPrimitive?.content == "address")
      // Swift encodes UUIDs uppercase; compare canonical identifiers, not casing.
      val replyId = value["id"]?.jsonPrimitive?.content ?: error("Missing request id")
      require(UUID.fromString(replyId).toString().equals(replyId, ignoreCase = true))
      require(UUID.fromString(expectedId).toString().equals(expectedId, ignoreCase = true))
      require(UUID.fromString(replyId) == UUID.fromString(expectedId))
      require(value["network"]?.jsonPrimitive?.int == 0)
      val expiry = value["expiresAt"]?.jsonPrimitive?.double ?: error("Missing expiry")
      require(expiry.isFinite() && expiry > nowSeconds)
      value["address"]?.jsonPrimitive?.content?.also { address ->
        require(im.molly.monero.sdk.PublicAddress.parse(address).network == im.molly.monero.sdk.MoneroNetwork.Mainnet)
      } ?: error("Missing Monero address")
    }.getOrNull()

    fun parseSharedAddress(body: String): String? = runCatching {
      require(body.toByteArray(Charsets.UTF_8).size <= 4096)
      val marker = body.indexOf(PREFIX)
      require(marker >= 0 && body.indexOf(PREFIX, marker + PREFIX.length) == -1)
      val value = Json.parseToJsonElement(
        String(Base64.getDecoder().decode(body.substring(marker + PREFIX.length).trim()), Charsets.UTF_8)
      ).jsonObject
      require(value["version"]?.jsonPrimitive?.int == 1)
      require(value["kind"]?.jsonPrimitive?.content == "address")
      require(UUID.fromString(value.getValue("id").jsonPrimitive.content).toString()
        .equals(value.getValue("id").jsonPrimitive.content, ignoreCase = true))
      require(value["network"]?.jsonPrimitive?.int == 0)
      require(value["expiresAt"]?.jsonPrimitive?.double?.isFinite() == true)
      require(value["amount"] == null || value["amount"] == JsonNull)
      value.getValue("address").jsonPrimitive.content.also { address ->
        require(im.molly.monero.sdk.PublicAddress.parse(address).network == im.molly.monero.sdk.MoneroNetwork.Mainnet)
      }
    }.getOrNull()

    @JvmStatic
    fun isControlMessage(body: String?): Boolean = runCatching {
      if (body == null || body.toByteArray(Charsets.UTF_8).size > 4096) return false
      val marker = body.indexOf(PREFIX)
      require(marker >= 0 && body.indexOf(PREFIX, marker + PREFIX.length) == -1)
      val encoded = body.substring(marker + PREFIX.length).trim()
      val value = Json.parseToJsonElement(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)).jsonObject
      require(value["version"]?.jsonPrimitive?.int == 1)
      val kind = value["kind"]?.jsonPrimitive?.content ?: error("Missing kind")
      require(kind in setOf("request", "address", "unavailable"))
      require(UUID.fromString(value.getValue("id").jsonPrimitive.content).toString().equals(value.getValue("id").jsonPrimitive.content, true))
      require(value.getValue("network").jsonPrimitive.int in 0..2)
      require(value.getValue("expiresAt").jsonPrimitive.double.isFinite())
      require(value["amount"] == null || value["amount"] == JsonNull)
      when (kind) {
        "request", "unavailable" -> require(value["address"] == null || value["address"] == JsonNull)
        "address" -> require(!value["address"]?.jsonPrimitive?.content.isNullOrBlank())
      }
      true
    }.getOrDefault(false)

    fun isUnavailableReply(body: String, expectedId: String, nowSeconds: Double = System.currentTimeMillis() / 1000.0): Boolean = runCatching {
      require(isControlMessage(body))
      val encoded = body.substringAfter(PREFIX).trim()
      val value = Json.parseToJsonElement(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)).jsonObject
      require(value.getValue("kind").jsonPrimitive.content == "unavailable")
      require(UUID.fromString(value.getValue("id").jsonPrimitive.content) == UUID.fromString(expectedId))
      require(value.getValue("network").jsonPrimitive.int == 0)
      require(value.getValue("expiresAt").jsonPrimitive.double > nowSeconds)
      true
    }.getOrDefault(false)

    /** Returns a clean chat rendering for Monero protocol messages, or null for ordinary text. */
    @JvmStatic
    fun displayText(body: String?): String? = runCatching {
      if (body == null || body.toByteArray(Charsets.UTF_8).size > 4096) return null
      val marker = body.indexOf(PREFIX)
      require(marker >= 0 && body.indexOf(PREFIX, marker + PREFIX.length) == -1)
      val encoded = body.substring(marker + PREFIX.length).trim()
      val value = Json.parseToJsonElement(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)).jsonObject
      require(value["version"]?.jsonPrimitive?.int == 1)
      val kind = value["kind"]?.jsonPrimitive?.content ?: error("Missing kind")
      require(kind in setOf("request", "address", "unavailable", "paymentSent"))
      require(UUID.fromString(value.getValue("id").jsonPrimitive.content).toString().equals(value.getValue("id").jsonPrimitive.content, true))
      require(value.getValue("network").jsonPrimitive.int in 0..2)
      require(value.getValue("expiresAt").jsonPrimitive.double.isFinite())
      when (kind) {
        "request", "address", "unavailable" -> ""
        "paymentSent" -> {
          val amount = value["amount"]?.jsonPrimitive?.long ?: error("Missing amount")
          require(amount > 0 && (value["address"] == null || value["address"] == JsonNull))
          body.substring(0, marker).trim().takeIf(String::isNotEmpty) ?: error("Missing payment notice")
        }
        else -> error("Unsupported Monero message")
      }
    }.getOrNull()
  }
}
