package kttp.security

import java.security.MessageDigest
import java.util.Base64

object Sha1 {
    fun hash(input: String): ByteArray {
        return MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
    }

    fun hash(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-1").digest(input)
    }

    fun hashToBase64(input: String): String {
        return Base64.getEncoder().encodeToString(hash(input))
    }
}