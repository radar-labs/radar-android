package org.thoughtcrime.securesms.payments.monero

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Coordinates the encrypted in-chat address exchange without requiring the recipient to open the thread. */
internal object MoneroChatPaymentFlow {
  private const val ADDRESS_CACHE_PREFERENCES = "radar_monero_chat_addresses"

  suspend fun requestRecipientAddress(context: Context, recipient: Recipient, threadId: Long): String {
    check(!recipient.isGroup && !recipient.isSelf && !recipient.isBlocked) {
      "Monero chat payments are only available in accepted one-to-one conversations."
    }
    check(MoneroWalletRepository.hasWallet(context)) { "Create a Monero wallet in Payments before sending XMR." }

    val appContext = context.applicationContext
    val addressKey = "mainnet.$threadId"
    // Prefer the newest authenticated address in the conversation. This permits
    // the recipient to publish one while online and receive later while offline.
    MessageTable.mmsReaderFor(SignalDatabase.messages.getConversation(threadId, 0, 100)).use { reader ->
      for (message in reader) {
        if (message == null || message.isOutgoing || !message.isSecure || message.isRemoteDelete ||
          message.threadId != threadId || message.fromRecipient.id != recipient.id) continue
        MoneroAddressExchange.parseSharedAddress(message.body.orEmpty())?.let { address ->
          appContext.getSharedPreferences(ADDRESS_CACHE_PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(addressKey, address).apply()
          return address
        }
      }
    }
    appContext.getSharedPreferences(ADDRESS_CACHE_PREFERENCES, Context.MODE_PRIVATE)
      .getString(addressKey, null)
      ?.takeIf(::isValidMainnetAddress)
      ?.let { return it }

    val (request, body) = MoneroAddressExchange.requestMessage()
    val response = CompletableDeferred<String>()
    fun readReplies() {
      if (response.isCompleted) return
      try {
        MessageTable.mmsReaderFor(SignalDatabase.messages.getConversation(threadId, 0, 100)).use { reader ->
          for (message in reader) {
            if (message == null || message.isOutgoing || !message.isSecure || message.isRemoteDelete ||
              message.threadId != threadId || message.fromRecipient.id != recipient.id) continue
            val body = message.body.orEmpty()
            val address = MoneroAddressExchange.parseAddressReply(body, request.id)
            if (address != null) {
              appContext.getSharedPreferences(ADDRESS_CACHE_PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(addressKey, address).apply()
              response.complete(address)
              return
            }
            if (MoneroAddressExchange.isUnavailableReply(body, request.id)) {
              response.completeExceptionally(IOException("Your contact's Monero wallet is unavailable. They need to set up or open their mainnet wallet."))
              return
            }
          }
        }
      } catch (error: Exception) {
        response.completeExceptionally(error)
      }
    }
    // Incoming messages notify conversation observers. Insert observers are used
    // for outgoing messages and do not deliver the recipient's address reply.
    val observer = DatabaseObserver.Observer { readReplies() }

    AppDependencies.databaseObserver.registerConversationObserver(threadId, observer)
    try {
      MessageSender.send(
        context,
        OutgoingMessage.text(recipient, body, recipient.expiresInSeconds * 1000L),
        threadId,
        MessageSender.SendType.SIGNAL,
        null,
        null
      )
      withContext(Dispatchers.IO) { readReplies() }
      return withTimeout(TimeUnit.MINUTES.toMillis(2)) { response.await() }
    } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
      throw IOException("The recipient's Monero wallet did not return an address. Make sure the chat is accepted and try again.", error)
    } finally {
      AppDependencies.databaseObserver.unregisterObserver(observer)
    }
  }

  private fun isValidMainnetAddress(address: String): Boolean = runCatching {
    im.molly.monero.sdk.PublicAddress.parse(address).network == im.molly.monero.sdk.MoneroNetwork.Mainnet
  }.getOrDefault(false)
}
