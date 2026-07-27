package com.forge.skeleton.crypto

import com.forge.skeleton.network.interfaces.ICipher
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AesGcmCipher(private val key: ByteArray) : ICipher {

    private val secretKey = SecretKeySpec(key, "AES")
    private val random = SecureRandom()

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= NONCE_SIZE) { "ciphertext too short" }
        val nonce = ciphertext.copyOfRange(0, NONCE_SIZE)
        val body = ciphertext.copyOfRange(NONCE_SIZE, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(body)
    }

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val NONCE_SIZE = 12
        const val TAG_BITS = 128
    }
}
