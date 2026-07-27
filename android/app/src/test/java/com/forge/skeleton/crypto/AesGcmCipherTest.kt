package com.forge.skeleton.crypto

import org.junit.Test
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AesGcmCipherTest {

    private fun key(bytes: Int): ByteArray =
        ByteArray(bytes).also { SecureRandom().nextBytes(it) }

    @Test
    fun `round trips plaintext with 256 bit key`() {
        val cipher = AesGcmCipher(key(32))
        val plaintext = "hello skeleton".toByteArray()
        val restored = cipher.decrypt(cipher.encrypt(plaintext))
        assertContentEquals(plaintext, restored)
    }

    @Test
    fun `round trips with 128 bit key`() {
        val cipher = AesGcmCipher(key(16))
        val plaintext = ByteArray(1024).also { SecureRandom().nextBytes(it) }
        val restored = cipher.decrypt(cipher.encrypt(plaintext))
        assertContentEquals(plaintext, restored)
    }

    @Test
    fun `nonce randomization yields distinct ciphertexts`() {
        val cipher = AesGcmCipher(key(32))
        val plaintext = "same input".toByteArray()
        val a = cipher.encrypt(plaintext)
        val b = cipher.encrypt(plaintext)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `wrong key fails to decrypt`() {
        val encryptor = AesGcmCipher(key(32))
        val decryptor = AesGcmCipher(key(32))
        val ciphertext = encryptor.encrypt("secret".toByteArray())
        assertFailsWith<AEADBadTagException> { decryptor.decrypt(ciphertext) }
    }
}
