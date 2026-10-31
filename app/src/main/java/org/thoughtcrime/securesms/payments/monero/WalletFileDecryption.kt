package org.thoughtcrime.securesms.payments.monero

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import javax.crypto.Cipher

internal object WalletFileDecryption {
  fun open(input: InputStream, ciphertextSize: Int, cipher: Cipher): InputStream {
    require(ciphertextSize >= 16)
    val ciphertext = ByteArray(ciphertextSize)
    val plaintext = try {
      var offset = 0
      while (offset < ciphertext.size) {
        val count = input.read(ciphertext, offset, ciphertext.size - offset)
        if (count < 0) throw EOFException("Truncated encrypted Monero wallet file.")
        offset += count
      }
      // One authenticated operation avoids CipherInputStream's repeated large
      // output allocations with Android Keystore's buffered GCM decryption.
      cipher.doFinal(ciphertext)
    } finally {
      ciphertext.fill(0)
    }
    return object : ByteArrayInputStream(plaintext) {
      override fun close() {
        plaintext.fill(0)
        pos = count
        mark = count
        super.close()
      }
    }
  }
}
