package misk.crypto.testing

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KmsClient

/**
 * This fake implementation of a Key Management Service client is meant to be used
 * for testing and development purposes only.
 *
 * When calling `getAead`, it'll provide a fake [Aead] object that **performs no encryption**.
 * Instead, it only does Base64 encoding/decoding so developers could debug their apps.
 */
@Deprecated("Replace your dependency on misk-crypto-testing with `testImplementation(testFixtures(Dependencies.miskCrypto))`")
internal class FakeKmsClient : KmsClient {
  override fun getAead(keyUri: String?): Aead {
    return FakeMasterEncryptionKey()
  }

  override fun doesSupport(keyUri: String?): Boolean = true
  override fun withCredentials(credentialPath: String?): KmsClient = this
  override fun withDefaultCredentials(): KmsClient = this
}
