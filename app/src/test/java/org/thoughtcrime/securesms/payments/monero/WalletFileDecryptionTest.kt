package org.thoughtcrime.securesms.payments.monero

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class WalletFileDecryptionTest {
  private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
  private val iv = ByteArray(12) { it.toByte() }
  private val header = "RDXMR01".toByteArray() + byteArrayOf(1, 12) + iv

  private fun cipher(mode: Int, aad: ByteArray = header) = Cipher.getInstance("AES/GCM/NoPadding").apply {
    init(mode, key, GCMParameterSpec(128, this@WalletFileDecryptionTest.iv))
    updateAAD(aad)
  }

  @Test fun existingFormatRoundTripAndClose() {
    val original = ByteArray(22_418_517) { (it % 251).toByte() }
    val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(original)
    val stream = WalletFileDecryption.open(ByteArrayInputStream(encrypted), encrypted.size, cipher(Cipher.DECRYPT_MODE))
    assertArrayEquals(original, stream.readBytes())
    stream.close()
    stream.reset()
    assertEquals(-1, stream.read())
  }

  @Test fun tamperedCiphertextIsRejectedBeforeReturningStream() {
    val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(ByteArray(4096))
    encrypted[0] = (encrypted[0].toInt() xor 1).toByte()
    assertThrows(AEADBadTagException::class.java) {
      WalletFileDecryption.open(ByteArrayInputStream(encrypted), encrypted.size, cipher(Cipher.DECRYPT_MODE))
    }
  }

  @Test fun changedHeaderIsRejected() {
    val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(ByteArray(4096))
    assertThrows(AEADBadTagException::class.java) {
      WalletFileDecryption.open(ByteArrayInputStream(encrypted), encrypted.size, cipher(Cipher.DECRYPT_MODE, byteArrayOf(0)))
    }
  }

  @Test fun truncatedFileIsRejected() {
    val encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(ByteArray(4096))
    assertThrows(EOFException::class.java) {
      WalletFileDecryption.open(ByteArrayInputStream(encrypted.copyOf(encrypted.size - 1)), encrypted.size, cipher(Cipher.DECRYPT_MODE))
    }
  }

  @Test fun bufferedSaveRemainsCompatible() {
    val original = ByteArray(1_000_003) { (it % 251).toByte() }
    val output = ByteArrayOutputStream()
    BufferedOutputStream(CipherOutputStream(output, cipher(Cipher.ENCRYPT_MODE)), 256 * 1024).use { encrypted ->
      ByteArrayInputStream(original).copyTo(encrypted)
    }
    val ciphertext = output.toByteArray()
    WalletFileDecryption.open(ByteArrayInputStream(ciphertext), ciphertext.size, cipher(Cipher.DECRYPT_MODE)).use {
      assertArrayEquals(original, it.readBytes())
    }
  }
}
