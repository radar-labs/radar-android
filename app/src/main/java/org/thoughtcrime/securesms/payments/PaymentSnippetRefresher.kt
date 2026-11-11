/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.payments

import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.SignalDatabase

/**
 * Recomputes the cached chat-list snippet (the `body` column on each thread) for every
 * thread that contains at least one payment message. Used when the user toggles a payment
 * display preference (`SignalStore.payments.showInSats`, `balanceHidden`) so chat-list rows
 * pick up the new unit / mask immediately, rather than waiting for the next payment-message
 * arrival to retrigger `ThreadTable.update`.
 *
 * Mirrors iOS's NotificationCenter-driven chat-list refresh path
 * (`PaymentsDisplayPreferences.amountTypeDidChange` / `balanceHiddenDidChange` from
 * `c90dd2d790` / `8f92be19fb`).
 */
object PaymentSnippetRefresher {

  private val TAG = Log.tag(PaymentSnippetRefresher::class.java)

  /** Refresh on a background executor — never blocks the toggle interaction. */
  @JvmStatic
  fun refreshAsync() {
    SignalExecutors.BOUNDED.execute(::refresh)
  }

  private fun refresh() {
    val ids = paymentThreadIds()
    if (ids.isEmpty()) return

    val threads = SignalDatabase.threads
    var updated = 0
    for (id in ids) {
      try {
        threads.update(id, false)
        updated++
      } catch (t: Throwable) {
        Log.w(TAG, "Failed to refresh thread $id snippet", t)
      }
    }
    Log.i(TAG, "Refreshed payment-message snippets in $updated thread(s).")
  }

  /** SELECT DISTINCT thread_id FROM message WHERE message is a payment notification or tombstone. */
  private fun paymentThreadIds(): List<Long> {
    val mask = MessageTypes.SPECIAL_TYPES_MASK
    val notification = MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION
    val tombstone = MessageTypes.SPECIAL_TYPE_PAYMENTS_TOMBSTONE

    val ids = mutableListOf<Long>()
    SignalDatabase.rawDatabase.rawQuery(
      "SELECT DISTINCT ${MessageTable.THREAD_ID} FROM ${MessageTable.TABLE_NAME} " +
        "WHERE (${MessageTable.TYPE} & $mask) = $notification OR (${MessageTable.TYPE} & $mask) = $tombstone",
      null
    ).use { cursor ->
      while (cursor.moveToNext()) ids.add(cursor.getLong(0))
    }
    return ids
  }
}
