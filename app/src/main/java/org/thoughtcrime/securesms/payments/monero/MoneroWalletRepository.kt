package org.thoughtcrime.securesms.payments.monero

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import im.molly.monero.sdk.Ledger
import im.molly.monero.sdk.MoneroAmount
import im.molly.monero.sdk.MoneroNetwork
import im.molly.monero.sdk.MoneroNodeClient
import im.molly.monero.sdk.MoneroWallet
import im.molly.monero.sdk.PaymentDetail
import im.molly.monero.sdk.PaymentRequest
import im.molly.monero.sdk.PublicAddress
import im.molly.monero.sdk.RemoteNode
import im.molly.monero.sdk.RestorePoint
import im.molly.monero.sdk.SecretKey as MoneroSecretKey
import im.molly.monero.sdk.WalletDataStore
import im.molly.monero.sdk.loadbalancer.RoundRobinRule
import im.molly.monero.sdk.mnemonics.MoneroMnemonic
import im.molly.monero.sdk.service.SandboxedWalletService
import org.thoughtcrime.securesms.BuildConfig
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.thoughtcrime.securesms.util.RemoteConfig
import java.io.FilterOutputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.Instant
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import java.security.KeyStore
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Owns the Android wallet2 service, encrypted wallet storage, and the currently open wallet. */
object MoneroWalletRepository {
  private const val WALLET_CHECKPOINT_INTERVAL_MS = 60_000L
  private const val WALLET_OPEN_TIMEOUT_MS = 60_000L
  private const val PREFERENCES = "radar_monero_wallet"
  private const val WALLET_ID_KEY = "mainnet_wallet_id"
  private const val WALLET_ID = "radar-monero-mainnet-v1"
  private const val KEY_ALIAS = "org.radarlabs.radar.monero.wallet.v1"
  private const val FILE_MAGIC = "RDXMR01"
  private const val FILE_VERSION = 1
  private const val GCM_TAG_BITS = 128
  private const val GCM_IV_BYTES = 12

  private val mutex = Mutex()
  private val walletOpenLock = Any()
  // wallet2's native refresh is non-reentrant. Serialize even if a caller bypasses
  // refreshInBackground's shared Deferred (for example during screen recreation).
  private val nativeRefreshMutex = Mutex()
  private val receiveAddressMutex = Mutex()
  // Wallet creation and chain refresh must survive navigation away from the wallet screen.
  private val operationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  data class SyncProgress(
    val isSyncing: Boolean,
    val currentHeight: Int,
    val startedAtMillis: Long? = null,
    val targetHeight: Int? = null,
    val error: String? = null,
    val isOpeningWallet: Boolean = false,
    val isNodeConnected: Boolean? = null,
  )
  private val _syncProgress = MutableStateFlow(SyncProgress(false, 0))
  val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()
  private val _currentLedger = MutableStateFlow<Ledger?>(null)
  val currentLedger: StateFlow<Ledger?> = _currentLedger.asStateFlow()

  fun showOpeningWallet() {
    _syncProgress.value = SyncProgress(
      isSyncing = false,
      currentHeight = _currentLedger.value?.checkedAt?.height ?: 0,
      startedAtMillis = System.currentTimeMillis(),
      isOpeningWallet = true,
    )
  }

  fun clearOpeningWallet() {
    if (_syncProgress.value.isOpeningWallet) {
      _syncProgress.value = _syncProgress.value.copy(isOpeningWallet = false, startedAtMillis = null)
    }
  }
  private val refreshLock = Any()
  private val creationLock = Any()
  private var creationOperation: Deferred<CreatedWallet>? = null
  enum class CreationStage { IDLE, STARTING_SERVICE, CREATING_KEYS, SAVING, READY }
  private val _creationStage = MutableStateFlow(CreationStage.IDLE)
  val creationStage: StateFlow<CreationStage> = _creationStage.asStateFlow()
  @Volatile private var refreshOperation: Deferred<Ledger>? = null
  @Volatile private var walletOpenOperation: Deferred<MoneroWallet>? = null
  private var provider: im.molly.monero.sdk.WalletProvider? = null
  private var nodeClient: MoneroNodeClient? = null
  @Volatile private var wallet: MoneroWallet? = null
  @Volatile private var pendingRecoveryPhrase: String? = null

  data class CreatedWallet(val wallet: MoneroWallet, val recoveryPhrase: String)

  suspend fun hasWallet(context: Context): Boolean =
    context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
      .contains(WALLET_ID_KEY)

  fun createWalletInBackground(context: Context): Deferred<CreatedWallet> = synchronized(creationLock) {
    creationOperation?.takeIf { !it.isCancelled } ?: operationScope.async {
      try {
        createWallet(context.applicationContext)
      } finally {
        if (_creationStage.value != CreationStage.READY) _creationStage.value = CreationStage.IDLE
      }
    }.also { creationOperation = it }
  }

  fun hasPendingCreation(): Boolean = synchronized(creationLock) {
    creationOperation?.let { !it.isCancelled } == true
  }

  fun restoreWalletInBackground(context: Context, recoveryPhrase: CharArray, walletCreationDate: LocalDate) =
    operationScope.async { restoreWallet(context.applicationContext, recoveryPhrase, walletCreationDate) }

  fun refreshInBackground(context: Context): Deferred<Ledger> = synchronized(refreshLock) {
    refreshOperation?.takeIf { it.isActive } ?: operationScope.async {
      try {
        refresh(context.applicationContext)
      } catch (error: Exception) {
        _syncProgress.value = _syncProgress.value.copy(
          isSyncing = false,
          isOpeningWallet = false,
          startedAtMillis = null,
          error = error.localizedMessage,
        )
        throw error
      } finally {
        synchronized(refreshLock) { refreshOperation = null }
      }
    }.also { refreshOperation = it }
  }

  fun takePendingRecoveryPhrase(): String? = synchronized(this) {
    pendingRecoveryPhrase.also { pendingRecoveryPhrase = null }
  }

  suspend fun createWallet(context: Context): CreatedWallet = mutex.withLock {
    check(!hasWallet(context)) { "A Monero wallet already exists on this device." }
    val appContext = context.applicationContext
    _creationStage.value = CreationStage.STARTING_SERVICE
    val walletProvider = getProvider(appContext)
    _creationStage.value = CreationStage.CREATING_KEYS
    val activeNodeClient = getNodeClient()
    // Key generation and persistence must work offline. The SDK applies a conservative
    // time buffer when estimating this new key's scan height. Never change existing wallets.
    val createdAt = Instant.now()
    val store = EncryptedWalletDataStore(appContext)
    val spendKey = MoneroSecretKey()
    val entropy = spendKey.bytes
    val phrase = try {
      MoneroMnemonic.generateMnemonic(entropy).use { mnemonic ->
        val words = mnemonic.words
        try {
          words.concatToString()
        } finally {
          words.fill('\u0000')
        }
      }
    } finally {
      entropy.fill(0)
    }

    try {
      val created = walletProvider.restoreWallet(
        network = MoneroNetwork.Mainnet,
        dataStore = store,
        client = activeNodeClient,
        secretSpendKey = spendKey,
        restorePoint = RestorePoint.creationTime(createdAt),
      )
      try {
        _creationStage.value = CreationStage.SAVING
        created.save()
        persistWalletId(appContext)
        wallet = created
        pendingRecoveryPhrase = phrase
        _creationStage.value = CreationStage.READY
        refreshInBackground(appContext)
        CreatedWallet(created, phrase)
      } catch (error: Throwable) {
        created.close()
        throw error
      }
    } finally {
      spendKey.close()
    }
  }

  suspend fun restoreWallet(
    context: Context,
    recoveryPhrase: CharArray,
    walletCreationDate: LocalDate,
  ): MoneroWallet = mutex.withLock {
    check(!hasWallet(context)) { "A Monero wallet already exists on this device." }
    val appContext = context.applicationContext
    val mnemonic = MoneroMnemonic.recoverEntropy(recoveryPhrase)
      ?: throw IllegalArgumentException("The Monero recovery phrase is not valid.")

    try {
      val entropy = mnemonic.entropy
      val spendKey = try {
        MoneroSecretKey(entropy)
      } finally {
        entropy.fill(0)
      }

      spendKey.use { key ->
        val restored = getProvider(appContext).restoreWallet(
          network = MoneroNetwork.Mainnet,
          dataStore = EncryptedWalletDataStore(appContext),
          client = getNodeClient(),
          secretSpendKey = key,
          // Scan earlier than the estimate so a slightly inaccurate date cannot silently
          // hide prior incoming transactions. Users can always select an older date.
          restorePoint = if (walletCreationDate.minusDays(14).isBefore(LocalDate.of(2014, 6, 8))) {
            RestorePoint.Genesis
          } else {
            RestorePoint.creationTime(walletCreationDate.minusDays(14))
          },
        )
        try {
          restored.save()
          persistWalletId(appContext)
          wallet = restored
          refreshInBackground(appContext)
          restored
        } catch (error: Throwable) {
          restored.close()
          throw error
        }
      }
    } finally {
      mnemonic.close()
      recoveryPhrase.fill('\u0000')
    }
  }

  fun isWalletOpen(): Boolean = wallet != null

  suspend fun openWallet(context: Context): MoneroWallet {
    val appContext = context.applicationContext
    wallet?.let { return it }
    // A wallet can be requested at the same time by the payments screen and a chat
    // payment flow. Loading the serialized wallet is expensive; share one in-flight
    // native open instead of queueing multiple full decrypt/deserialize operations.
    val operation = synchronized(walletOpenLock) {
      wallet?.let { return@synchronized CompletableDeferred(it) }
      walletOpenOperation?.takeIf { it.isActive }
        ?: operationScope.async { openWalletLocked(appContext) }.also {
          walletOpenOperation = it
        }
    }
    return try {
      operation.await()
    } finally {
      if (operation.isCompleted) {
        synchronized(walletOpenLock) {
          if (walletOpenOperation === operation) walletOpenOperation = null
        }
      }
    }
  }

  private suspend fun openWalletLocked(context: Context): MoneroWallet = try {
    // Bound mutex acquisition too: an opening request can otherwise wait forever
    // behind a previous SDK callback that never completed.
    kotlinx.coroutines.withTimeout(WALLET_OPEN_TIMEOUT_MS) {
      mutex.withLock {
        wallet?.let { return@withLock it }
        check(hasWallet(context)) { "Create or restore a Monero wallet first." }
        val walletProvider = getProvider(context)
        val opened = walletProvider.openWallet(
          network = MoneroNetwork.Mainnet,
          dataStore = EncryptedWalletDataStore(context),
          client = getNodeClient(),
        )
        wallet = opened
        opened
      }
    }
  } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
    val staleProvider = provider
    provider = null
    if (staleProvider != null) runCatching { staleProvider.disconnect() }
    throw IOException(
      "Opening the Monero wallet timed out. Your wallet data is safe; pull down to retry.",
      error,
    )
  }

  suspend fun refresh(context: Context): Ledger {
    showOpeningWallet()
    val activeWallet = openWallet(context)
    return coroutineScope {
      val startedAt = _syncProgress.value.startedAtMillis ?: System.currentTimeMillis()
      // A newly opened wallet's ledger flow is callback-backed and may not emit until
      // wallet2 starts a refresh. Waiting for ledger().first() before awaitRefresh()
      // deadlocks wallet reopening indefinitely at "Loading encrypted wallet data…".
      val initialHeight = _currentLedger.value?.checkedAt?.height ?: 0
      _syncProgress.value = SyncProgress(true, initialHeight, startedAt)
      val firstLedger = CompletableDeferred<Ledger>()
      val nodeHeight = launch {
        while (true) {
          val height = fetchNodeHeight()
          if (height != null) {
            _syncProgress.value = _syncProgress.value.copy(targetHeight = height, isNodeConnected = true)
          } else {
            _syncProgress.value = _syncProgress.value.copy(isNodeConnected = false)
          }
          kotlinx.coroutines.delay(10_000)
        }
      }
      val ledgerUpdates = launch(start = CoroutineStart.UNDISPATCHED) {
        activeWallet.ledger().collect { ledger ->
          _currentLedger.value = ledger
          firstLedger.complete(ledger)
          _syncProgress.value = _syncProgress.value.copy(isSyncing = true, currentHeight = ledger.checkedAt.height, startedAtMillis = startedAt)
        }
      }
      val refreshTask = async { nativeRefreshMutex.withLock { activeWallet.awaitRefresh() } }
      val checkpoints = launch {
        var savedHeight = initialHeight
        while (true) {
          kotlinx.coroutines.delay(WALLET_CHECKPOINT_INTERVAL_MS)
          val height = _syncProgress.value.currentHeight
          if (height != savedHeight) {
            activeWallet.save()
            savedHeight = height
          }
        }
      }
      try {
        val result = refreshTask.await()
        check(!result.isError()) { "The Monero wallet could not synchronize with its node." }
        activeWallet.save()
        firstLedger.await()
        checkNotNull(_currentLedger.value).also { ledger ->
          _syncProgress.value = SyncProgress(false, ledger.checkedAt.height)
        }
      } finally {
        nodeHeight.cancel()
        checkpoints.cancel()
        ledgerUpdates.cancel()
        if (_syncProgress.value.isSyncing) {
          _syncProgress.value = _syncProgress.value.copy(isSyncing = false, startedAtMillis = null)
        }
        clearOpeningWallet()
      }
    }
  }

  private suspend fun fetchNodeHeight(): Int? = withContext(Dispatchers.IO) {
    val endpoints = configuredNodes()
    for (node in endpoints) {
      val height = runCatching {
        val connection = (URL("${node.endpoint.trimEnd('/')}/json_rpc").openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          connectTimeout = 4_000
          readTimeout = 4_000
          doOutput = true
          setRequestProperty("Content-Type", "application/json")
        }
        try {
          connection.outputStream.use {
            it.write("{\"jsonrpc\":\"2.0\",\"id\":\"radar\",\"method\":\"get_info\"}".toByteArray())
          }
          if (connection.responseCode !in 200..299) null
          else {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optJSONObject("result")?.optInt("height")?.let {
              MoneroNodeCatalog.latestBlockIndexFromChainLength(it)
            }
          }
        } finally {
          connection.disconnect()
        }
      }.getOrNull()
      if (height != null) return@withContext height
    }
    null
  }

  suspend fun send(
    context: Context,
    recipient: String,
    amount: MoneroAmount,
    confirmTransfer: suspend (amount: MoneroAmount, fee: MoneroAmount) -> Boolean,
  ) {
    require(amount.atomicUnits > 0) { "Enter an amount greater than zero." }
    val activeWallet = openWallet(context)
    val address = PublicAddress.parse(recipient.trim())
    require(address.network == MoneroNetwork.Mainnet) { "That address is not a Monero mainnet address." }

    activeWallet.createTransfer(
      PaymentRequest(
        paymentDetails = listOf(PaymentDetail(amount, address)),
        spendingAccountIndex = 0,
      )
    ).use { pending ->
      check(confirmTransfer(pending.amount, pending.fee)) { "Payment cancelled." }
      check(pending.commit()) { "The Monero transaction was not submitted." }
      activeWallet.save()
    }
  }

  suspend fun chatReceiveAddress(context: Context, recipientId: String): String = receiveAddressMutex.withLock {
    val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    val key = "receive.mainnet.$recipientId"
    preferences.getString(key, null)?.let { return@withLock it }
    val activeWallet = openWallet(context)
    val address = activeWallet.createSubAddressForAccount().address
    activeWallet.save()
    check(preferences.edit().putString(key, address).commit()) { "Could not save the chat receiving address" }
    address
  }

  fun selectedNode(): MoneroNode? = configuredNodes().firstOrNull()

  fun nodeEndpoint(): String? = selectedNode()?.endpoint

  private fun configuredNodes(): List<MoneroNode> = MoneroNodeCatalog.availableNodes(
    remoteConfigJson = RemoteConfig.moneroNodesJson,
    customEndpoints = emptyList(),
    // Public endpoints are a local developer/testing convenience only. Release builds
    // must use Radar-managed nodes published through Remote Config.
    includePublicFallbacks = BuildConfig.DEBUG,
  )

  private fun allRemoteNodes(nodes: List<MoneroNode>): List<RemoteNode> =
    nodes.map { RemoteNode(it.endpoint, MoneroNetwork.Mainnet) }

  private suspend fun getProvider(context: Context): im.molly.monero.sdk.WalletProvider {
    check(MoneroProcess.isSupportedDevice()) { "This Monero build requires an arm64 Android 8 or newer device." }
    provider?.let { return it }
    return try {
      kotlinx.coroutines.withTimeout(15_000) { SandboxedWalletService.connect(context) }.also { provider = it }
    } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
      throw IOException("The Monero wallet service could not start. Please restart Radar and retry.", error)
    }
  }

  private fun getNodeClient(): MoneroNodeClient {
    nodeClient?.let { return it }
    val nodes = configuredNodes()
    if (nodes.isEmpty()) {
      throw IOException("Monero payments are unavailable because Radar has not configured a trusted node yet.")
    }
    return MoneroNodeClient.create(
      network = MoneroNetwork.Mainnet,
      remoteNodes = flowOf(allRemoteNodes(nodes)),
      loadBalancerRule = RoundRobinRule(),
      httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build(),
      retryBackoff = MoneroRetryPolicy(),
    ).also { nodeClient = it }
  }

  private fun persistWalletId(context: Context) {
    check(
      context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .edit().putString(WALLET_ID_KEY, WALLET_ID).commit()
    ) { "The Monero wallet could not be registered on this device." }
  }

  /** AES-256-GCM wallet-file encryption; the key is non-exportable and held by Android Keystore. */
  private class EncryptedWalletDataStore(context: Context) : WalletDataStore {
    private val file: AtomicFile = AtomicFile(
      File(File(context.noBackupFilesDir, "monero").apply { mkdirs() }, "$WALLET_ID.wallet")
    )

    override suspend fun load(): InputStream {
      val input = file.openRead()
      try {
        val magic = ByteArray(FILE_MAGIC.length)
        readFully(input, magic)
        if (!magic.contentEquals(FILE_MAGIC.toByteArray(Charsets.US_ASCII))) {
          throw IOException("The encrypted Monero wallet file has an unknown format.")
        }
        if (input.read() != FILE_VERSION) throw IOException("Unsupported Monero wallet file version.")
        val ivLength = input.read()
        if (ivLength != GCM_IV_BYTES) throw IOException("Invalid Monero wallet encryption header.")
        val iv = ByteArray(ivLength)
        readFully(input, iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
          ?: throw IOException("The Monero wallet encryption key is unavailable on this device.")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(encryptionHeader(iv))
        // Keystore GCM buffers plaintext until authentication. CipherInputStream's
        // tiny updates repeatedly allocate wallet-sized output buffers on Android.
        // Authenticate once before exposing any plaintext to the native service.
        val remaining = input.channel.size() - input.channel.position()
        if (remaining < GCM_TAG_BITS / 8 || remaining > Int.MAX_VALUE) {
          throw IOException("Invalid encrypted Monero wallet file length.")
        }
        return input.use { WalletFileDecryption.open(it, remaining.toInt(), cipher) }
      } catch (error: Throwable) {
        input.close()
        throw error
      }
    }

    override suspend fun save(writer: (OutputStream) -> Unit, overwrite: Boolean) {
      val output = file.startWrite()
      var cipherOutput: OutputStream? = null
      try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Android Keystore requires a provider-generated IV for randomized encryption.
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        check(iv.size == GCM_IV_BYTES)
        cipher.updateAAD(encryptionHeader(iv))
        output.write(FILE_MAGIC.toByteArray(Charsets.US_ASCII))
        output.write(FILE_VERSION)
        output.write(iv.size)
        output.write(iv)
        val nonClosingOutput = object : FilterOutputStream(output) {
          override fun write(bytes: ByteArray, offset: Int, length: Int) = output.write(bytes, offset, length)
          override fun close() = flush()
        }
        cipherOutput = BufferedOutputStream(CipherOutputStream(nonClosingOutput, cipher), 256 * 1024)
        writer(cipherOutput)
        cipherOutput.close()
        file.finishWrite(output)
      } catch (error: Throwable) {
        runCatching { cipherOutput?.close() }
        file.failWrite(output)
        throw error
      }
    }

    private fun getOrCreateKey(): SecretKey {
      val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
      val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
      generator.init(
        KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .setRandomizedEncryptionRequired(true)
          .build()
      )
      return generator.generateKey()
    }

    private fun readFully(input: InputStream, target: ByteArray) {
      var offset = 0
      while (offset < target.size) {
        val count = input.read(target, offset, target.size - offset)
        if (count < 0) throw IOException("Truncated encrypted Monero wallet file.")
        offset += count
      }
    }

    private fun encryptionHeader(iv: ByteArray): ByteArray =
      FILE_MAGIC.toByteArray(Charsets.US_ASCII) + byteArrayOf(FILE_VERSION.toByte(), iv.size.toByte()) + iv
  }
}
