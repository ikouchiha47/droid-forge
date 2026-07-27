package com.forge.skeleton.crypto

import com.forge.skeleton.network.interfaces.ICipher

class NoopCipher : ICipher {
    override fun encrypt(plaintext: ByteArray): ByteArray = plaintext
    override fun decrypt(ciphertext: ByteArray): ByteArray = ciphertext
}
