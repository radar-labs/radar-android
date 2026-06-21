package org.thoughtcrime.securesms.payments

import android.net.Uri
import com.mobilecoin.lib.exceptions.SerializationException
import org.signal.core.util.logging.Log.tag

class LightningAddress internal constructor(val paymentAddress: String, val lnurlAddress: String) {
  val paymentAddressUri: Uri

  init {
    try {
      this.paymentAddressUri = Uri.parse("lightning:${lnurlAddress}")
    } catch (e: SerializationException) {
      throw AssertionError(e)
    }
  }

  fun serialize(): ByteArray {
    return lnurlAddress.toByteArray()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LightningAddress) return false

    return this.paymentAddress == other.paymentAddress
  }

  override fun hashCode(): Int {
    return paymentAddress.hashCode()
  }

  override fun toString(): String {
    return this.paymentAddress
  }

  class AddressException : Exception {
    constructor(e: Throwable?) : super(e)

    constructor(message: String?) : super(message)
  }

  companion object {
    private val TAG = tag(LightningAddress::class.java)

    fun fromLightningAddressNullableOrThrow(lightningAddress: String?): LightningAddress? {
      return if (lightningAddress != null) fromLightningAddressOrThrow(lightningAddress) else null
    }

    fun fromLightningAddressOrThrow(lightningAddress: String): LightningAddress {
      try {
        return fromLightningAddress(lightningAddress)
      } catch (e: AddressException) {
        throw AssertionError(e)
      }
    }

    @Throws(AddressException::class)
    fun fromLightningAddress(lightningAddress: String): LightningAddress {
      try {
        val lnParts = lightningAddress.split("@")
        val lnurl = Bech32.encode(Bech32.Bech32Data("lnurl",
          Bech32.convert(
            "https://${lnParts[1]}/.well-known/lnurlp/${lnParts[0]}".toByteArray(Charsets.UTF_8), 8, 5, true)
        ))

        return LightningAddress(lightningAddress, lnurl)
      } catch (e: SerializationException) {
        throw AddressException(e)
      }
    }

    /**
     * Builds a [LightningAddress] from a recipient's published payment-address string (decrypted from
     * their profile). Clients serialize this differently, so accept all known forms and normalize to
     * the "user@host" lightning address:
     *  - Android stores the bech32 LNURL (`lnurl1...`).
     *  - iOS stores the decoded LNURLP URL (`https://host/.well-known/lnurlp/user`).
     *  - a plain `user@host` lightning address is also accepted.
     *
     * "user@host" is the form every consumer needs: Breez's `parse` (fee + send), and PaymentSendJob,
     * which reconstructs the address via [fromLightningAddress] (splitting on "@"). It must NOT be
     * `Uri.parse`d as-is when bech32: a bech32 string has no host/path and previously produced a
     * malformed "<bech32>@null" address that Breez rejected with InvalidInput.
     */
    @Throws(AddressException::class)
    fun fromLNURL(serialized: String): LightningAddress {
      try {
        val lightningAddress = when {
          // bech32 LNURL → decode to the LNURLP URL, then derive user@host. LNURLs exceed bech32's
          // standard 90-char cap, so decode with no length limit.
          serialized.startsWith("lnurl1", ignoreCase = true) -> {
            val url = Uri.parse(String(Bech32.convert(Bech32.decode(serialized, Int.MAX_VALUE).data, 5, 8, false), Charsets.UTF_8))
            "${url.pathSegments.last()}@${url.host}"
          }
          // a LNURLP URL (e.g. published by iOS)
          serialized.contains("://") -> {
            val url = Uri.parse(serialized)
            "${url.pathSegments.last()}@${url.host}"
          }
          // already a "user@host" lightning address
          serialized.contains("@") -> serialized
          else -> throw AddressException("Unrecognized lightning payment address")
        }

        return LightningAddress(lightningAddress, serialized)
      } catch (e: AddressException) {
        throw e
      } catch (e: Exception) {
        throw AddressException(e)
      }
    }
  }
}
