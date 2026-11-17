package org.thoughtcrime.securesms.payments.monero

import im.molly.monero.sdk.MoneroNetwork
import im.molly.monero.sdk.PublicAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Parses Monero addresses and standard monero: payment URIs without trusting QR contents. */
internal data class MoneroPaymentQr(val address: String, val amount: String?) {
  val scannerValue: String
    get() = if (amount == null) address else "monero:$address?tx_amount=$amount"

  companion object {
    @JvmStatic
    fun parse(value: String): MoneroPaymentQr? = runCatching {
      val trimmed = value.trim()
      require(trimmed.isNotEmpty() && trimmed.length <= 4096)
      val uri = if (trimmed.startsWith("monero:", ignoreCase = true)) URI(trimmed) else null
      if (uri != null) require(uri.scheme.equals("monero", ignoreCase = true))
      val schemeSpecificPart = uri?.rawSchemeSpecificPart
      val rawAddress = schemeSpecificPart?.substringBefore('?') ?: trimmed
      val address = URLDecoder.decode(rawAddress, StandardCharsets.UTF_8.name()).trim()
      require(PublicAddress.parse(address).network == MoneroNetwork.Mainnet)

      val rawQuery = schemeSpecificPart?.substringAfter('?', "").orEmpty()
      val query = rawQuery.split('&').filter(String::isNotBlank)
        .associate { part ->
          val separator = part.indexOf('=')
          require(separator > 0)
          URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8.name()) to
            URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8.name())
        }
      val amount = query["tx_amount"]?.also {
        MoneroAmountParser.parse(it)
      }
      MoneroPaymentQr(address, amount)
    }.getOrNull()
  }
}
