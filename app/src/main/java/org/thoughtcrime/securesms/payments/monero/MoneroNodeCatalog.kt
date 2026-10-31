package org.thoughtcrime.securesms.payments.monero

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

data class MoneroNode(
  val id: String,
  val name: String,
  val endpoint: String,
  val managed: Boolean
)

object MoneroNodeCatalog {
  const val PUBLIC_MAINNET_FALLBACK_ENDPOINT = "https://xmr-node.cakewallet.com:18081"
  const val SECONDARY_PUBLIC_MAINNET_FALLBACK_ENDPOINT = "https://xmr.cryptostorm.is:18081"

  /** `get_info.height` is a block count; wallet scan height is a zero-based block index. */
  fun latestBlockIndexFromChainLength(chainLength: Int): Int? =
    chainLength.takeIf { it > 0 }?.minus(1)

  fun availableNodes(
    remoteConfigJson: String?,
    customEndpoints: List<String>,
    includePublicFallbacks: Boolean = false,
  ): List<MoneroNode> {
    val managed = parseManagedNodes(remoteConfigJson)
    val publicNodes = if (includePublicFallbacks) {
      listOf(
        MoneroNode("radar-public-mainnet-cake", "Cake Wallet public node", PUBLIC_MAINNET_FALLBACK_ENDPOINT, managed = false),
        MoneroNode("radar-secondary-mainnet-fallback", "Public Monero node", SECONDARY_PUBLIC_MAINNET_FALLBACK_ENDPOINT, managed = false),
      )
    } else {
      emptyList()
    }
    val custom = customEndpoints.mapIndexedNotNull { index, endpoint ->
      val normalized = endpoint.trim()
      if (isSecureEndpoint(normalized)) MoneroNode("custom-$index", normalized, normalized, managed = false) else null
    }
    val seen = mutableSetOf<String>()
    return (managed + publicNodes + custom)
      .filter { seen.add(it.endpoint.lowercase()) }
  }

  fun parseManagedNodes(json: String?): List<MoneroNode> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
      Json.parseToJsonElement(json).jsonArray.mapNotNull { element ->
        val item = element.jsonObject
        val id = item["id"]?.jsonPrimitive?.content?.trim().orEmpty()
        val name = item["name"]?.jsonPrimitive?.content?.trim().orEmpty()
        val endpoint = item["url"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (id.isNotEmpty() && name.isNotEmpty() && isSecureEndpoint(endpoint)) {
          MoneroNode(id, name, endpoint, managed = true)
        } else {
          null
        }
      }
    }.getOrDefault(emptyList())
  }

  fun isSecureEndpoint(endpoint: String): Boolean {
    return runCatching {
      val uri = URI(endpoint)
      uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null &&
        uri.fragment == null
    }.getOrDefault(false)
  }
}
