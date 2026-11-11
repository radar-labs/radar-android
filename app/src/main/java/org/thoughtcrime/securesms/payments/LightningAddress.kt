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

    /**
     * Whether [input] looks like a BOLT11 invoice: the bech32 human-readable part for Lightning is
     * `ln` plus a network prefix (`lnbc`, `lntb`, `lntbs`, `lnbcrt`). Deliberately loose — the SDK
     * does the real parsing; this only decides which shape to build.
     */
    @JvmStatic
    fun looksLikeBolt11Invoice(input: String): Boolean {
      val trimmed = input.trim().removePrefix("lightning:").removePrefix("LIGHTNING:")
      return trimmed.length > 20 && trimmed.lowercase().startsWith("ln") && !trimmed.contains("@")
    }

    /**
     * A destination that is already a BOLT11 invoice. The invoice *is* the payment request, so
     * there is no LNURL to derive: both fields carry it, which keeps `lightning:<invoice>` correct
     * for the share/QR path and lets the whole send pipeline continue to carry a single type.
     */
    @JvmStatic
    fun fromBolt11(invoice: String): LightningAddress {
      val trimmed = invoice.trim().removePrefix("lightning:").removePrefix("LIGHTNING:")
      return LightningAddress(trimmed, trimmed)
    }

    /**
     * Parses a user-supplied destination into a [LightningAddress], accepting either a
     * `user@host` lightning address or a BOLT11 invoice.
     *
     * This is the single entry point every deserialization path already funnels through
     * (PaymentSendJob's job data, PayeeParcelable, PaymentTable rows), so teaching it about
     * invoices is what lets a BOLT11 destination survive a round trip through any of them.
     */
    @Throws(AddressException::class)
    fun fromLightningAddress(lightningAddress: String): LightningAddress {
      if (looksLikeBolt11Invoice(lightningAddress)) {
        return fromBolt11(lightningAddress)
      }
      try {
        val lnParts = lightningAddress.split("@")
        if (lnParts.size != 2 || lnParts[0].isEmpty() || lnParts[1].isEmpty()) {
          throw AddressException("Not a lightning address: expected user@host")
        }
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
          // a BOLT11 invoice — the payment request itself, nothing to derive
          looksLikeBolt11Invoice(serialized) -> return fromBolt11(serialized)
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
