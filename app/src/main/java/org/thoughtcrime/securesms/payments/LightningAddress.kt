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

//    @Throws(AddressException::class)
//    fun fromPublicAddress(publicAddress: PublicAddress?): LightningAddress {
//      if (publicAddress == null) {
//        throw AddressException("Does not contain a public address")
//      }
//      return LightningAddress(publicAddress)
//    }

//    fun fromBytes(bytes: ByteArray?): LightningAddress? {
//      if (bytes == null) {
//        return null
//      }
//
//      try {
//        return LightningAddress(PublicAddress.fromBytes(bytes))
//      } catch (e: SerializationException) {
//        w(TAG, e)
//        return null
//      }
//    }

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

//    @Throws(AddressException::class)
//    fun fromQr(data: String): LightningAddress {
//      try {
//        val printableWrapper = PrintableWrapper.fromUri(Uri.parse(data))
//        return fromPublicAddress(printableWrapper.getPublicAddress())
//      } catch (e: SerializationException) {
//        return fromBase58(data)
//      } catch (e: InvalidUriException) {
//        return fromBase58(data)
//      }
//    }
  }
}
