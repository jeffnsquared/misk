package misk.crypto

import com.google.crypto.tink.Aead
import java.util.Base64

@Deprecated("Use misk-crypto-testing instead",
  replaceWith = ReplaceWith("FakeMasterEncryptionKey", imports = ["misk.crypto.testing"]))
internal class FakeMasterEncryptionKey : Aead {

  override fun encrypt(plaintext: ByteArray?, associatedData: ByteArray?): ByteArray {
    return Base64.getEncoder().encode(plaintext)
  }

  override fun decrypt(ciphertext: ByteArray?, associatedData: ByteArray?): ByteArray {
    return Base64.getDecoder().decode(ciphertext)
  }
}
