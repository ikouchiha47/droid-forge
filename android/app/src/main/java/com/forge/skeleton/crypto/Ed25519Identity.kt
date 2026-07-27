package com.forge.skeleton.crypto

import android.content.Context
import com.forge.skeleton.network.interfaces.IIdentity
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class Ed25519Identity(context: Context) : IIdentity {

    private val keyPair: KeyPair
    private val publicEncoded: ByteArray

    init {
        val file = File(context.filesDir, KEY_FILE)
        keyPair = if (file.exists()) load(file) else generateAndSave(file)
        publicEncoded = keyPair.public.encoded
    }

    override fun publicKey(): ByteArray = publicEncoded

    override fun sign(data: ByteArray): ByteArray {
        val signature = Signature.getInstance(ALGO)
        signature.initSign(keyPair.private)
        signature.update(data)
        return signature.sign()
    }

    override fun verify(data: ByteArray, sig: ByteArray, theirPublicKey: ByteArray): Boolean {
        val kf = KeyFactory.getInstance(ALGO)
        val pub = kf.generatePublic(X509EncodedKeySpec(theirPublicKey))
        val signature = Signature.getInstance(ALGO)
        signature.initVerify(pub)
        signature.update(data)
        return signature.verify(sig)
    }

    private fun generateAndSave(file: File): KeyPair {
        val generator = KeyPairGenerator.getInstance(ALGO)
        val pair = generator.generateKeyPair()
        file.writeBytes(encode(pair.public.encoded, pair.private.encoded))
        return pair
    }

    private fun load(file: File): KeyPair {
        val bytes = file.readBytes()
        val pubLen = readInt(bytes, 0)
        val pubStart = 4
        val pub = bytes.copyOfRange(pubStart, pubStart + pubLen)
        val privStart = pubStart + pubLen + 4
        val privLen = readInt(bytes, pubStart + pubLen)
        val priv = bytes.copyOfRange(privStart, privStart + privLen)
        val kf = KeyFactory.getInstance(ALGO)
        val publicKey: PublicKey = kf.generatePublic(X509EncodedKeySpec(pub))
        val privateKey: PrivateKey = kf.generatePrivate(PKCS8EncodedKeySpec(priv))
        return KeyPair(publicKey, privateKey)
    }

    private fun encode(pub: ByteArray, priv: ByteArray): ByteArray {
        val out = ByteArray(4 + pub.size + 4 + priv.size)
        writeInt(out, 0, pub.size)
        System.arraycopy(pub, 0, out, 4, pub.size)
        writeInt(out, 4 + pub.size, priv.size)
        System.arraycopy(priv, 0, out, 8 + pub.size, priv.size)
        return out
    }

    private fun writeInt(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = (value ushr 24 and 0xFF).toByte()
        dst[offset + 1] = (value ushr 16 and 0xFF).toByte()
        dst[offset + 2] = (value ushr 8 and 0xFF).toByte()
        dst[offset + 3] = (value and 0xFF).toByte()
    }

    private fun readInt(src: ByteArray, offset: Int): Int =
        ((src[offset].toInt() and 0xFF) shl 24) or
            ((src[offset + 1].toInt() and 0xFF) shl 16) or
            ((src[offset + 2].toInt() and 0xFF) shl 8) or
            (src[offset + 3].toInt() and 0xFF)

    private companion object {
        const val ALGO = "Ed25519"
        const val KEY_FILE = "identity.key"
    }
}
