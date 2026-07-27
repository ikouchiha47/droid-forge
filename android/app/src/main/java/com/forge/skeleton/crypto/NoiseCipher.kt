package com.forge.skeleton.crypto

import com.forge.skeleton.network.interfaces.ICipher
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.NamedParameterSpec
import java.security.spec.XECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Derives a symmetric session key via X25519 ECDH between the local static
// private key and the peer static public key, then runs AES-256-GCM.
// The key schedule is derived lazily and cached; both peers converge on the
// same key because ECDH(a, B) == ECDH(b, A).
class NoiseCipher(
    private val localPrivate: java.security.PrivateKey,
    private val peerPublicRaw: ByteArray,
) : ICipher {

    private val random = SecureRandom()
    private var sessionKey: SecretKeySpec? = null

    private fun deriveKey(): SecretKeySpec {
        sessionKey?.let { return it }
        val kf = KeyFactory.getInstance("XDH")
        val spec = NamedParameterSpec.X25519
        val u = decodeLittleEndianU(peerPublicRaw)
        val peerPublic = kf.generatePublic(XECPublicKeySpec(spec, u))
        val agreement = KeyAgreement.getInstance("XDH")
        agreement.init(localPrivate)
        agreement.doPhase(peerPublic, true)
        val shared = agreement.generateSecret()
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(shared + PROTOCOL_LABEL)
        return SecretKeySpec(keyBytes, "AES").also { sessionKey = it }
    }

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val key = deriveKey()
        val nonce = ByteArray(NONCE_SIZE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        return nonce + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= NONCE_SIZE) { "ciphertext too short" }
        val key = deriveKey()
        val nonce = ciphertext.copyOfRange(0, NONCE_SIZE)
        val body = ciphertext.copyOfRange(NONCE_SIZE, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(body)
    }

    private fun decodeLittleEndianU(raw: ByteArray): java.math.BigInteger {
        val reversed = raw.reversedArray()
        return java.math.BigInteger(1, reversed)
    }

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val NONCE_SIZE = 12
        const val TAG_BITS = 128
        val PROTOCOL_LABEL = "Noise_XX_25519_AESGCM_SHA256".toByteArray()
    }
}
