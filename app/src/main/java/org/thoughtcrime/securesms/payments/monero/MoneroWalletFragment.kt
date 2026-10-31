package org.thoughtcrime.securesms.payments.monero

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.Manifest
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import im.molly.monero.sdk.Ledger
import im.molly.monero.sdk.MoneroCurrency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.thoughtcrime.securesms.R
import java.time.LocalDate
import java.text.DateFormat
import java.util.Date
import java.util.Calendar
import java.security.MessageDigest
import kotlin.coroutines.resume

class MoneroWalletFragment : Fragment(R.layout.payments_monero_fragment) {
  private companion object {
    const val AUTO_REFRESH_INTERVAL_MS = 30_000L
  }

  private lateinit var status: TextView
  private lateinit var balance: TextView
  private lateinit var balanceDetail: TextView
  private lateinit var transactions: LinearLayout
  private lateinit var transactionCount: TextView
  private lateinit var createButton: Button
  private lateinit var restoreButton: Button
  private lateinit var refresh: SwipeRefreshLayout
  private lateinit var progressBar: ProgressBar
  private lateinit var syncDetail: TextView
  private var receiveAddress: String? = null
  private var sending = false
  private var pendingSendAmount: String? = null
  private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) openPaymentScanner()
    else Toast.makeText(requireContext(), R.string.PaymentsTransferFragment__to_scan_a_qr_code_signal_needs_access_to_the_camera, Toast.LENGTH_LONG).show()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val embedded = parentFragment is org.thoughtcrime.securesms.payments.preferences.PaymentsHomeFragment
    view.findViewById<Toolbar>(R.id.monero_toolbar).apply {
      visibility = if (embedded) View.GONE else View.VISIBLE
      setNavigationIcon(R.drawable.ic_arrow_left_24)
      setNavigationOnClickListener { findNavController().popBackStack() }
      menu.add(R.string.PaymentsMonero__node_details).setOnMenuItemClickListener {
        val node = MoneroWalletRepository.selectedNode()
        if (node == null) {
          showError(IllegalStateException(getString(R.string.PaymentsMonero__node_not_configured)))
          return@setOnMenuItemClickListener true
        }
        MaterialAlertDialogBuilder(requireContext())
          .setTitle(R.string.PaymentsMonero__node_details)
          .setMessage(getString(R.string.PaymentsMonero__node, node.name, node.endpoint))
          .setPositiveButton(android.R.string.ok, null).show()
        true
      }
    }
    status = view.findViewById(R.id.monero_status)
    view.findViewById<View>(R.id.monero_currency_bitcoin).setOnClickListener {
      val home = parentFragment as? org.thoughtcrime.securesms.payments.preferences.PaymentsHomeFragment
      if (home != null) home.showBitcoinWallet() else findNavController().popBackStack()
    }
    balance = view.findViewById(R.id.monero_balance)
    balanceDetail = view.findViewById(R.id.monero_balance_detail)
    transactions = view.findViewById(R.id.monero_transactions)
    transactionCount = view.findViewById(R.id.monero_transaction_count)
    createButton = view.findViewById(R.id.monero_create)
    restoreButton = view.findViewById(R.id.monero_restore)
    refresh = view.findViewById(R.id.monero_refresh)
    progressBar = view.findViewById(R.id.monero_sync_progress)
    syncDetail = view.findViewById(R.id.monero_sync_detail)
    view.findViewById<TextView>(R.id.button_start).setText(R.string.PaymentsMonero__receive)
    view.findViewById<View>(R.id.button_start_frame).setOnClickListener { showReceive() }
    view.findViewById<View>(R.id.button_end_frame).setOnClickListener { showSend() }
    createButton.setOnClickListener { createWallet() }
    restoreButton.setOnClickListener { restoreWallet() }
    refresh.setOnRefreshListener {
      viewLifecycleOwner.lifecycleScope.launch { synchronize(showRefreshIndicator = true) }
    }
    parentFragmentManager.setFragmentResultListener(
      org.thoughtcrime.securesms.payments.preferences.transfer.PaymentsTransferQrScanFragment.REQUEST_KEY_SCANNED_DESTINATION,
      viewLifecycleOwner,
    ) { _, result ->
      val scanned = result.getString(org.thoughtcrime.securesms.payments.preferences.transfer.PaymentsTransferQrScanFragment.RESULT_DESTINATION)
        ?.let(MoneroPaymentQr::parse)
      if (scanned == null) {
        Toast.makeText(requireContext(), R.string.PaymentsMonero__invalid_qr, Toast.LENGTH_LONG).show()
      } else {
        showSend(scanned.address, scanned.amount ?: pendingSendAmount)
      }
      pendingSendAmount = null
    }

    if (!MoneroProcess.isSupportedDevice()) {
      status.setText(R.string.PaymentsMonero__unsupported)
      refresh.isEnabled = false
      return
    }
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          MoneroWalletRepository.creationStage.collect { stage ->
            when (stage) {
              MoneroWalletRepository.CreationStage.STARTING_SERVICE -> status.setText(R.string.PaymentsMonero__starting_service)
              MoneroWalletRepository.CreationStage.CREATING_KEYS -> status.setText(R.string.PaymentsMonero__creating)
              MoneroWalletRepository.CreationStage.SAVING -> status.setText(R.string.PaymentsMonero__saving)
              else -> Unit
            }
          }
        }
        launch { MoneroWalletRepository.syncProgress.collect(::renderSyncProgress) }
        launch { MoneroWalletRepository.currentLedger.collect { it?.let(::renderLedger) } }
        launch {
          while (true) {
            delay(AUTO_REFRESH_INTERVAL_MS)
            // The first open/sync is handled below. Don't race it with an automatic
            // refresh, and don't keep reopening a wallet whose SDK open just failed.
            if (MoneroWalletRepository.isWalletOpen()) synchronize()
          }
        }
      }
    }
    viewLifecycleOwner.lifecycleScope.launch {
      try {
        val exists = MoneroWalletRepository.hasWallet(requireContext())
        renderWalletAvailable(exists)
        if (exists) {
          MoneroWalletRepository.showOpeningWallet()
          val wallet = MoneroWalletRepository.openWallet(requireContext())
          receiveAddress = wallet.publicAddress.address
          MoneroWalletRepository.clearOpeningWallet()
          MoneroWalletRepository.takePendingRecoveryPhrase()?.let(::showRecoveryPhrase)
          // refresh() starts wallet2's balance callbacks; don't wait for ledger().first()
          // here because a reopened wallet may not emit until that refresh is underway.
          synchronize()
        } else if (MoneroWalletRepository.hasPendingCreation()) {
          createWallet()
        } else {
          status.setText(R.string.PaymentsMonero__local_creation)
        }
      } catch (error: Exception) {
        MoneroWalletRepository.clearOpeningWallet()
        showError(error)
      }
    }
  }

  private fun renderWalletAvailable(exists: Boolean) {
    createButton.visibility = if (exists) View.GONE else View.VISIBLE
    restoreButton.visibility = if (exists) View.GONE else View.VISIBLE
    view?.findViewById<View>(R.id.monero_actions)?.visibility = if (exists) View.VISIBLE else View.GONE
    refresh.isEnabled = exists
  }

  private fun createWallet() {
    createButton.isEnabled = false
    restoreButton.isEnabled = false
    status.setText(R.string.PaymentsMonero__creating)
    val operation = MoneroWalletRepository.createWalletInBackground(requireContext())
    viewLifecycleOwner.lifecycleScope.launch {
      try {
        val result = operation.await()
        receiveAddress = result.wallet.publicAddress.address
        renderWalletAvailable(true)
        showRecoveryPhrase(MoneroWalletRepository.takePendingRecoveryPhrase() ?: result.recoveryPhrase)
        synchronize()
      } catch (error: Exception) {
        showError(error)
      } finally {
        createButton.isEnabled = true
        restoreButton.isEnabled = true
      }
    }
  }

  private fun showRecoveryPhrase(phrase: String) {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PaymentsMonero__phrase_title)
      .setMessage(getString(R.string.PaymentsMonero__phrase_warning) + "\n\n" + phrase)
      .setCancelable(false)
      .setPositiveButton(R.string.PaymentsMonero__phrase_saved, null)
      .show()
  }

  private fun restoreWallet() {
    val genesis = LocalDate.of(2014, 4, 18)
    val datePicker = DatePicker(requireContext()).apply {
      minDate = Calendar.getInstance().apply {
        clear()
        set(genesis.year, genesis.monthValue - 1, genesis.dayOfMonth)
      }.timeInMillis
      maxDate = System.currentTimeMillis()
      updateDate(genesis.year, genesis.monthValue - 1, genesis.dayOfMonth)
    }
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PaymentsMonero__restore_date_title)
      .setMessage(R.string.PaymentsMonero__restore_date_message)
      .setView(datePicker)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.PaymentsMonero__restore_date_confirm) { _, _ ->
        promptForRecoveryPhrase(LocalDate.of(datePicker.year, datePicker.month + 1, datePicker.dayOfMonth))
      }.show()
  }

  private fun promptForRecoveryPhrase(creationDate: LocalDate) {
    val input = EditText(requireContext()).apply {
      hint = getString(R.string.PaymentsMonero__restore_hint)
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
      minLines = 3
    }
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PaymentsMonero__restore)
      .setView(input)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.PaymentsMonero__restore) { _, _ ->
        val phrase = input.text.toString().trim().toCharArray()
        input.text.clear()
        createButton.isEnabled = false
        restoreButton.isEnabled = false
        val operation = MoneroWalletRepository.restoreWalletInBackground(requireContext(), phrase, creationDate)
        viewLifecycleOwner.lifecycleScope.launch {
          try {
            val wallet = operation.await()
            receiveAddress = wallet.publicAddress.address
            renderWalletAvailable(true)
            synchronize()
          } catch (error: Exception) {
            showError(error)
          } finally {
            createButton.isEnabled = true
            restoreButton.isEnabled = true
          }
        }
      }.show()
  }

  private suspend fun synchronize() {
    synchronize(showRefreshIndicator = false)
  }

  private suspend fun synchronize(showRefreshIndicator: Boolean) {
    val operation = MoneroWalletRepository.refreshInBackground(requireContext())
    if (showRefreshIndicator) refresh.isRefreshing = true
    try {
      renderLedger(operation.await())
      status.setText(R.string.PaymentsMonero__ready)
    } catch (error: Exception) {
      showError(error)
    } finally {
      if (showRefreshIndicator) refresh.isRefreshing = false
    }
  }

  private fun renderSyncProgress(progress: MoneroWalletRepository.SyncProgress) {
    val shouldShowProgress = progress.isSyncing || progress.isOpeningWallet
    progressBar.visibility = if (shouldShowProgress) View.VISIBLE else View.GONE
    syncDetail.visibility = if (shouldShowProgress) View.VISIBLE else View.GONE
    progress.error?.let { status.text = it }
    if (progress.isOpeningWallet) {
      status.setText(R.string.PaymentsMonero__opening_wallet)
      progressBar.isIndeterminate = true
      syncDetail.setText(R.string.PaymentsMonero__opening_wallet_detail)
      return
    }
    if (!progress.isSyncing) return
    val target = progress.targetHeight
    if (progress.isNodeConnected == true && target != null && progress.currentHeight >= target) {
      status.setText(R.string.PaymentsMonero__sync_finalizing)
      progressBar.isIndeterminate = true
      syncDetail.setText(R.string.PaymentsMonero__sync_finalizing_detail)
      return
    }
    status.setText(
      if (progress.isNodeConnected == false) R.string.PaymentsMonero__node_reconnecting
      else R.string.PaymentsMonero__syncing,
    )
    progressBar.isIndeterminate = target == null || progress.isNodeConnected == false
    if (progress.isNodeConnected == false) {
      syncDetail.setText(R.string.PaymentsMonero__node_reconnecting_detail)
    } else if (target != null) {
      progressBar.max = target
      progressBar.progress = progress.currentHeight.coerceAtMost(target)
      syncDetail.text = getString(R.string.PaymentsMonero__blocks, progress.currentHeight, target)
    } else {
      syncDetail.text = getString(R.string.PaymentsMonero__scanned, progress.currentHeight)
    }
  }

  private fun renderLedger(ledger: Ledger) {
    val funds = ledger.getBalance()
    val available = funds.unlockedAmountAt(ledger.checkedAt)
    balance.text = getString(R.string.PaymentsMonero__amount, MoneroAmountFormatter.format(funds.totalAmount))
    balanceDetail.text = getString(
      R.string.PaymentsMonero__available_locked,
      MoneroAmountFormatter.format(available),
      MoneroAmountFormatter.format(funds.totalAmount - available),
    )
    val seenPreferences = requireContext().getSharedPreferences("radar_monero_tx_seen", Context.MODE_PRIVATE)
    val sortTimes = ledger.transactions.associate { tx ->
      tx.txId to (tx.blockTimestamp?.toEpochMilli() ?: firstSeenMillis(tx.txId, seenPreferences))
    }
    val recentTransactions = ledger.transactions
      .sortedWith(compareByDescending<im.molly.monero.sdk.Transaction> {
        sortTimes.getValue(it.txId)
      }.thenByDescending { it.txId })
      .take(12)
    transactionCount.text = resources.getQuantityString(
      R.plurals.PaymentsMonero__transaction_count,
      recentTransactions.size,
      recentTransactions.size,
    )
    transactions.removeAllViews()
    if (recentTransactions.isEmpty()) {
      transactions.addView(TextView(requireContext()).apply {
        setText(R.string.PaymentsMonero__no_transactions)
        setTextAppearance(R.style.Signal_Text_BodyMedium)
        setTextColor(com.google.android.material.color.MaterialColors.getColor(
          this,
          com.google.android.material.R.attr.colorOnSurfaceVariant,
        ))
        val inset = (16 * resources.displayMetrics.density).toInt()
        setPadding(inset, inset, inset, inset)
      })
      return
    }

    val inflater = LayoutInflater.from(requireContext())
    recentTransactions.forEach { tx ->
      val item = inflater.inflate(R.layout.payments_monero_transaction_item, transactions, false)
      val incoming = tx.amount.atomicUnits >= 0
      item.findViewById<ImageView>(R.id.monero_transaction_direction_icon).setImageResource(
        if (incoming) R.drawable.symbol_arrow_down_24 else R.drawable.symbol_arrow_up_24,
      )
      item.findViewById<TextView>(R.id.monero_transaction_title).setText(
        if (incoming) R.string.PaymentsMonero__received_xmr else R.string.PaymentsMonero__sent_xmr,
      )
      item.findViewById<TextView>(R.id.monero_transaction_date).text = transactionDateLabel(tx, seenPreferences)
      val state = when (tx.state) {
        is im.molly.monero.sdk.TxState.OnChain -> R.string.PaymentsMonero__confirmed
        im.molly.monero.sdk.TxState.BeingProcessed, im.molly.monero.sdk.TxState.InMemoryPool -> R.string.PaymentsMonero__pending
        im.molly.monero.sdk.TxState.Failed -> R.string.PaymentsMonero__failed
        im.molly.monero.sdk.TxState.OffChain -> R.string.PaymentsMonero__pending
      }
      item.findViewById<TextView>(R.id.monero_transaction_status).setText(state)
      val shortId = if (tx.txId.length > 22) "${tx.txId.take(10)}…${tx.txId.takeLast(8)}" else tx.txId
      item.findViewById<TextView>(R.id.monero_transaction_id).text =
        getString(R.string.PaymentsMonero__transaction_id_short, shortId)
      val absoluteAmount = im.molly.monero.sdk.MoneroAmount(kotlin.math.abs(tx.amount.atomicUnits))
      item.findViewById<TextView>(R.id.monero_transaction_amount).text = getString(
        R.string.PaymentsMonero__amount,
        (if (incoming) "+" else "−") + MoneroAmountFormatter.format(absoluteAmount),
      )
      transactions.addView(item)
    }
  }

  private fun transactionDateLabel(
    tx: im.molly.monero.sdk.Transaction,
    seenPreferences: SharedPreferences,
  ): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    tx.blockTimestamp?.let { return formatter.format(Date.from(it)) }

    // Pool transactions do not have a block timestamp yet. Show a clearly labeled
    // local observation time instead of pretending it is the on-chain confirmation time.
    return getString(
      R.string.PaymentsMonero__seen_by_wallet,
      formatter.format(Date(firstSeenMillis(tx.txId, seenPreferences))),
    )
  }

  private fun firstSeenMillis(txId: String, preferences: SharedPreferences): Long {
    val keyDigest = MessageDigest.getInstance("SHA-256")
      .digest(txId.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
    val key = "first_seen.$keyDigest"
    return preferences.getLong(key, 0L).takeIf { it > 0L } ?: System.currentTimeMillis().also {
      preferences.edit().putLong(key, it).apply()
    }
  }

  private fun showReceive() {
    val address = receiveAddress ?: return
    val content = dialogContent()
    val image = ImageView(requireContext()).apply {
      contentDescription = getString(R.string.PaymentsMonero__address)
      val size = (224 * resources.displayMetrics.density).toInt()
      layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
    }
    val matrix = QRCodeWriter().encode(address, BarcodeFormat.QR_CODE, 512, 512, mapOf(EncodeHintType.MARGIN to 2))
    val pixels = IntArray(512 * 512) { i -> if (matrix[i % 512, i / 512]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    image.setImageBitmap(android.graphics.Bitmap.createBitmap(pixels, 512, 512, android.graphics.Bitmap.Config.ARGB_8888))
    content.addView(image)
    content.addView(TextView(requireContext()).apply { text = address; setTextIsSelectable(true) })
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PaymentsMonero__receive)
      .setView(content)
      .setPositiveButton(R.string.PaymentsMonero__copy) { _, _ ->
        (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
          .setPrimaryClip(ClipData.newPlainText("Monero address", address))
      }.setNegativeButton(android.R.string.cancel, null).show()
  }

  private fun dialogContent() = LinearLayout(requireContext()).apply {
    orientation = LinearLayout.VERTICAL
    val padding = (24 * resources.displayMetrics.density).toInt()
    setPadding(padding, padding / 2, padding, padding / 2)
  }

  private fun showSend(prefillAddress: String? = null, prefillAmount: String? = null) {
    if (sending) return
    val content = dialogContent()
    content.addView(TextView(requireContext()).apply {
      text = balanceDetail.text
      setTextAppearance(R.style.Signal_Text_BodyMedium)
      val inset = (12 * resources.displayMetrics.density).toInt()
      setPadding(inset, inset, inset, inset)
      setBackgroundResource(R.drawable.payment_available_balance_pill)
    })
    val recipientLayout = TextInputLayout(requireContext()).apply {
      hint = getString(R.string.PaymentsMonero__recipient_hint)
      boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
      endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
      layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val recipient = TextInputEditText(recipientLayout.context).apply {
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
      isSingleLine = true
      textSize = 14f
      setText(prefillAddress)
    }
    recipientLayout.addView(recipient)
    val amountLayout = TextInputLayout(requireContext()).apply {
      hint = getString(R.string.PaymentsMonero__amount_hint)
      suffixText = "XMR"
      boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
    }
    val amountText = TextInputEditText(amountLayout.context).apply {
      inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
      isSingleLine = true
      textSize = 20f
      setText(prefillAmount)
    }
    amountLayout.addView(amountText)
    var dialog: androidx.appcompat.app.AlertDialog? = null
    val scanButton = com.google.android.material.button.MaterialButton(requireContext()).apply {
      text = getString(R.string.PaymentsMonero__scan)
      setIconResource(R.drawable.symbol_qrcode_24)
      iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
      minWidth = 0
      setOnClickListener {
        pendingSendAmount = amountText.text?.toString()
        dialog?.dismiss()
        requestPaymentScan()
      }
    }
    val recipientRow = LinearLayout(requireContext()).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER_VERTICAL
      addView(recipientLayout, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
      addView(scanButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        marginStart = (8 * resources.displayMetrics.density).toInt()
      })
    }
    content.addView(recipientRow)
    content.addView(amountLayout)
    dialog = MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.PaymentsMonero__send_title).setView(content)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.PaymentsMonero__send, null).create()
    dialog?.setOnShowListener {
      dialog?.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
        val value = try { MoneroAmountParser.parse(amountText.text?.toString().orEmpty()) } catch (_: IllegalArgumentException) {
          amountLayout.error = getString(R.string.PaymentsMonero__amount_hint)
          return@setOnClickListener
        }
        val destination = recipient.text?.toString()?.trim().orEmpty()
        if (destination.isEmpty()) {
          recipientLayout.error = getString(R.string.PaymentsMonero__recipient_hint)
          return@setOnClickListener
        }
        recipientLayout.error = null
        amountLayout.error = null
        sending = true
        dialog?.dismiss()
        viewLifecycleOwner.lifecycleScope.launch {
          try {
            MoneroWalletRepository.send(requireContext(), destination, value) { actual, fee ->
              suspendCancellableCoroutine { continuation ->
                val confirmation = MaterialAlertDialogBuilder(requireContext())
                  .setMessage(getString(R.string.PaymentsMonero__confirm_send,
                    MoneroAmountFormatter.format(actual), destination, MoneroAmountFormatter.format(fee)))
                  .setNegativeButton(android.R.string.cancel) { _, _ -> if (continuation.isActive) continuation.resume(false) }
                  .setPositiveButton(R.string.PaymentsMonero__send) { _, _ -> if (continuation.isActive) continuation.resume(true) }
                  .setOnCancelListener { if (continuation.isActive) continuation.resume(false) }.show()
                continuation.invokeOnCancellation { confirmation.dismiss() }
              }
            }
            Toast.makeText(requireContext(), R.string.PaymentsMonero__sent, Toast.LENGTH_LONG).show()
            synchronize()
          } catch (error: Exception) {
            showError(error)
          } finally {
            sending = false
          }
        }
      }
    }
    dialog?.show()
  }

  private fun requestPaymentScan() {
    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      openPaymentScanner()
    } else {
      cameraPermission.launch(Manifest.permission.CAMERA)
    }
  }

  private fun openPaymentScanner() {
    findNavController().navigate(R.id.paymentRecipientScanQr)
  }

  private fun showError(error: Exception) {
    if (error is CancellationException) throw error
    MoneroWalletRepository.clearOpeningWallet()
    progressBar.visibility = View.GONE
    syncDetail.visibility = View.GONE
    if (MoneroWalletRepository.currentLedger.value == null) {
      balance.setText(R.string.PaymentsMonero__balance_unavailable)
    }
    status.text = error.localizedMessage ?: error.javaClass.simpleName
  }
}
