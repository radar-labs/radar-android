package org.thoughtcrime.securesms.payments.monero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneroNodeCatalogTest {
  @Test
  fun `only valid HTTPS managed nodes are accepted`() {
    val nodes = MoneroNodeCatalog.parseManagedNodes(
      """[
        {"id":"radar-1","name":"Radar node","url":"https://node.example:443"},
        {"id":"insecure","name":"HTTP node","url":"http://node.example"},
        {"id":"credentials","name":"Credential URL","url":"https://user:pass@node.example"}
      ]"""
    )

    assertEquals(1, nodes.size)
    assertEquals("radar-1", nodes.single().id)
    assertTrue(nodes.single().managed)
  }

  @Test
  fun `custom nodes merge without duplicate endpoints`() {
    val nodes = MoneroNodeCatalog.availableNodes(
      """[{"id":"radar-1","name":"Radar node","url":"https://node.example"}]""",
      listOf("https://node.example", "https://custom.example", "http://insecure.example"),
      includePublicFallbacks = true,
    )

    assertEquals(
      listOf(
        "https://node.example",
        MoneroNodeCatalog.PUBLIC_MAINNET_FALLBACK_ENDPOINT,
        MoneroNodeCatalog.SECONDARY_PUBLIC_MAINNET_FALLBACK_ENDPOINT,
        "https://custom.example",
      ),
      nodes.map { it.endpoint }
    )
    assertTrue(nodes.first().managed)
    assertFalse(nodes[1].managed)
    assertFalse(nodes[2].managed)
    assertFalse(nodes.last().managed)
  }

  @Test
  fun `public Monero mainnet fallbacks are only included when explicitly enabled`() {
    val nodes = MoneroNodeCatalog.availableNodes(null, emptyList())

    assertTrue(nodes.isEmpty())

    val testingNodes = MoneroNodeCatalog.availableNodes(null, emptyList(), includePublicFallbacks = true)

    assertEquals(2, testingNodes.size)
    assertEquals(MoneroNodeCatalog.PUBLIC_MAINNET_FALLBACK_ENDPOINT, testingNodes[0].endpoint)
    assertEquals(MoneroNodeCatalog.SECONDARY_PUBLIC_MAINNET_FALLBACK_ENDPOINT, testingNodes[1].endpoint)
    assertTrue(testingNodes.all { it.endpoint.startsWith("https://") })
  }

  @Test
  fun `managed nodes are preferred and public node remains available as fallback`() {
    val nodes = MoneroNodeCatalog.availableNodes(
      """[{"id":"radar-1","name":"Radar Mainnet","url":"https://xmr.radar.example"}]""",
      emptyList(),
      includePublicFallbacks = true,
    )

    assertEquals("Radar Mainnet", nodes.first().name)
    assertTrue(nodes.first().managed)
    assertEquals(MoneroNodeCatalog.PUBLIC_MAINNET_FALLBACK_ENDPOINT, nodes[1].endpoint)
    assertEquals(MoneroNodeCatalog.SECONDARY_PUBLIC_MAINNET_FALLBACK_ENDPOINT, nodes[2].endpoint)
  }

  @Test
  fun `release node list contains only explicitly configured Radar nodes`() {
    val nodes = MoneroNodeCatalog.availableNodes(
      """[{"id":"radar-1","name":"Radar Mainnet","url":"https://xmr.radar.example"}]""",
      emptyList(),
    )

    assertEquals(1, nodes.size)
    assertEquals("radar-1", nodes.single().id)
    assertTrue(nodes.single().managed)
  }

  @Test
  fun `empty and malformed remote config fail closed`() {
    assertTrue(MoneroNodeCatalog.parseManagedNodes(null).isEmpty())
    assertTrue(MoneroNodeCatalog.parseManagedNodes("not-json").isEmpty())
  }

  @Test
  fun `node chain length is converted to the latest zero based block index`() {
    assertEquals(0, MoneroNodeCatalog.latestBlockIndexFromChainLength(1))
    assertEquals(2_345_678, MoneroNodeCatalog.latestBlockIndexFromChainLength(2_345_679))
    assertEquals(null, MoneroNodeCatalog.latestBlockIndexFromChainLength(0))
  }
}
