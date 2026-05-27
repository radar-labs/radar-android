/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments

import breez_sdk_spark.LogEntry
import breez_sdk_spark.Logger
import breez_sdk_spark.initLogging
import org.signal.core.util.logging.Log

/**
 * Buffers Breez SDK log lines in memory for display in the Lightning logs screen.
 * Mirrors iOS LightningLogger. Install once via [installIfNeeded] before the SDK connects.
 */
object LightningLogger : Logger {

  private val TAG = Log.tag(LightningLogger::class.java)
  private const val MAX_ENTRIES = 10_000

  private val buffer = ArrayDeque<String>()
  private var installed = false

  @Synchronized
  fun installIfNeeded() {
    if (installed) return
    installed = true
    try {
      initLogging(null, this, null)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to install Lightning logger", e)
    }
  }

  @Synchronized
  override fun log(l: LogEntry) {
    buffer.addLast("[${l.level}] ${l.line}")
    while (buffer.size > MAX_ENTRIES) {
      buffer.removeFirst()
    }
  }

  @Synchronized
  fun currentText(): String = buffer.joinToString("\n")
}
