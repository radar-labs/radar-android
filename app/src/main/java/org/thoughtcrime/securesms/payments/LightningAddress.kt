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

    @Throws(AddressException::class)
    fun fromLNURL(lnurl: String): LightningAddress {
      try {
        // `lnurl` is the bech32-encoded LNURL string that [serialize] stored (e.g. "lnurl1..."). Decode
        // it back to the LNURLP URL and derive the "user@host" lightning address — the form every
        // consumer needs: Breez's `parse` (fee + send), and PaymentSendJob, which reconstructs the
        // address via [fromLightningAddress] (which splits on "@") when its Factory recreates the job.
        //
        // It must NOT be `Uri.parse`d as-is: a bech32 string has no host/path, which previously yielded
        // a malformed "<bech32>@null" address that Breez rejected with InvalidInput on the fee request.
        // LNURLs exceed bech32's standard 90-char cap, so decode with no length limit.
        val decoded = Bech32.decode(lnurl, Int.MAX_VALUE)
        val url = Uri.parse(String(Bech32.convert(decoded.data, 5, 8, false), Charsets.UTF_8))
        val lightningAddress = "${url.pathSegments.last()}@${url.host}"

        return LightningAddress(lightningAddress, lnurl)
      } catch (e: Exception) {
        throw AddressException(e)
      }
    }
  }
}
