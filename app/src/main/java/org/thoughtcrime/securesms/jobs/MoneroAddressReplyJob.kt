package org.thoughtcrime.securesms.jobs

import kotlinx.coroutines.runBlocking
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.payments.monero.MoneroAddressExchange
import org.thoughtcrime.securesms.payments.monero.MoneroWalletRepository
import org.thoughtcrime.securesms.recipients.RecipientUtil
import org.thoughtcrime.securesms.sms.MessageSender
import java.io.IOException

class MoneroAddressReplyJob private constructor(parameters: Parameters, private val messageId: Long) : BaseJob(parameters) {
  constructor(messageId: Long) : this(Parameters.Builder().setQueue(KEY).setMaxAttempts(5).build(), messageId)

  override fun getFactoryKey() = KEY
  override fun serialize(): ByteArray? = JsonJobData.Builder().putLong("messageId", messageId).serialize()

  override fun onRun() {
    if (!org.thoughtcrime.securesms.payments.monero.MoneroProcess.isSupportedDevice() || !SignalStore.account.isPrimaryDevice) return
    val message = SignalDatabase.messages.getMessageRecordOrNull(messageId) ?: return
    if (!message.isSecure || message.isOutgoing) return
    val request = MoneroAddressExchange.parseRequest(message.body) ?: return
    val recipient = SignalDatabase.threads.getRecipientForThreadId(message.threadId) ?: return
    if (recipient.isGroup || recipient.isSelf || recipient.isBlocked || recipient.id != message.fromRecipient.id ||
      !RecipientUtil.isMessageRequestAccepted(message.threadId, recipient)) return
    val preferences = context.getSharedPreferences("radar_monero_address_replies", android.content.Context.MODE_PRIVATE)
    val key = "reply.${message.threadId}.${request.id}"
    if (preferences.getBoolean(key, false)) return
    val last = preferences.getLong("last.${message.threadId}", 0)
    if (System.currentTimeMillis() - last < 5000) throw IOException("Monero address reply rate limited")

    val address = runBlocking {
      if (request.network == 0 && MoneroWalletRepository.hasWallet(context)) {
        MoneroWalletRepository.chatReceiveAddress(context, recipient.id.serialize())
      } else null
    }
    // Recheck acceptance after the asynchronous wallet operation, before sharing an address.
    val current = recipient.fresh()
    if (current.isBlocked || !RecipientUtil.isMessageRequestAccepted(message.threadId, current)) return
    if (MoneroAddressExchange.parseRequest(message.body) == null) return
    var inserted = false
    MessageSender.send(context, OutgoingMessage.text(current, request.reply(address), current.expiresInSeconds * 1000L),
      message.threadId, MessageSender.SendType.SIGNAL, null) { inserted = true }
    if (!inserted) throw IOException("Could not queue the Monero address reply")
    preferences.edit().putBoolean(key, true).putLong("last.${message.threadId}", System.currentTimeMillis()).commit()
  }

  override fun onShouldRetry(e: Exception) = e is IOException || e is android.os.RemoteException
  override fun onFailure() = Unit

  class Factory : Job.Factory<MoneroAddressReplyJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): MoneroAddressReplyJob =
      MoneroAddressReplyJob(parameters, JsonJobData.deserialize(serializedData).getLong("messageId"))
  }

  companion object { const val KEY = "MoneroAddressReplyJob" }
}
