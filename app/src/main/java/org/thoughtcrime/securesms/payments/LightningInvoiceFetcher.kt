/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.signal.core.util.logging.Log
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Resolves a Lightning address (`user@domain`) to a **BOLT11 invoice** via LNURL-pay.
 *
 * Mirrors iOS `getBolt11FromLightningAddress(_:amount:)` from
 * `PaymentsTransferInViewController.swift` (commit 4f069a4e30 / Cake-compat work).
 *
 * Flow:
 *   1. `GET https://<domain>/.well-known/lnurlp/<name>` → JSON with a `callback` URL.
 *   2. `GET <callback>?amount=<msat>` → JSON with a `pr` field (the BOLT11 invoice).
 *
 * Throws [IOException] (or its subclasses) on any network / parsing failure so callers
 * can fall back to `lightning:<address>`.
 */
object LightningInvoiceFetcher {

  private val TAG = Log.tag(LightningInvoiceFetcher::class.java)

  private val httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

  /**
   * Fetches a BOLT11 invoice for the given Lightning address.
   * @param lightningAddress in the form `user@domain` (e.g. `alice@radar.cash`).
   * @param amountMillisats invoice amount in millisats (`0` for an open / minimum-amount invoice,
   *                       matching iOS which passes `amount=0`).
   */
  @JvmStatic
  @JvmOverloads
  @Throws(IOException::class)
  fun fetchBolt11(lightningAddress: String, amountMillisats: Long = 0): String {
    val parts = lightningAddress.split("@")
    if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
      throw IOException("Invalid lightning address: $lightningAddress")
    }
    val name = parts[0]
    val domain = parts[1]

    val callback = readJsonString(
      url = "https://$domain/.well-known/lnurlp/$name",
      jsonKey = "callback"
    )

    val sep = if (callback.contains("?")) "&" else "?"
    return readJsonString(
      url = "$callback${sep}amount=$amountMillisats",
      jsonKey = "pr"
    )
  }

  private fun readJsonString(url: String, jsonKey: String): String {
    val request = Request.Builder().url(url).build()
    httpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw IOException("Non-2xx response from $url: ${response.code}")
      }
      val body = response.body?.string()
        ?: throw IOException("Empty response body from $url")
      val value = try {
        JSONObject(body).optString(jsonKey, "")
      } catch (e: Exception) {
        throw IOException("Failed to parse JSON from $url", e)
      }
      if (value.isEmpty()) {
        throw IOException("Missing '$jsonKey' in response from $url")
      }
      return value
    }
  }
}
