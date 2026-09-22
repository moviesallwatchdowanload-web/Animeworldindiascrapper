package com.outcome.watchanimeworld

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesDecrypt {

    fun decryptCryptoJs(cipherText: String, ckEncoded: String): String? {
        return try {
            val ckBytes = Regex("\\\\x([0-9a-fA-F]{2})").findAll(ckEncoded).map { m ->
                m.groupValues[1].toInt(16).toByte()
            }.toList().toByteArray()

            val b64 = String(ckBytes, Charsets.UTF_8)
            val keyBytes = Base64.decode(b64, Base64.DEFAULT)
            val keyHex = String(keyBytes, Charsets.UTF_8)

            val key = SecretKeySpec(keyHex.toByteArray(), "AES")
            val cipherBytes = Base64.decode(cipherText, Base64.DEFAULT)

            val iv = IvParameterSpec(cipherBytes.copyOfRange(0, 16))
            val ct = cipherBytes.copyOfRange(16, cipherBytes.size)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, iv)
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }
}
