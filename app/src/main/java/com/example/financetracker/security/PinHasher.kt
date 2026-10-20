package com.example.financetracker.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {
    fun valid(pin: CharArray) = pin.size == 4 && pin.all { it in '0'..'9' }
    fun salt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }
    fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        require(valid(pin)) { "Enter exactly four digits" }
        require(salt.size == 16)
        val spec = PBEKeySpec(pin, salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun matches(pin: CharArray, salt: ByteArray, expected: ByteArray): Boolean {
        if (!valid(pin) || expected.size != 32) return false
        val actual = derive(pin, salt)
        return try { MessageDigest.isEqual(actual, expected) } finally { actual.fill(0) }
    }
}

object PinAttemptPolicy {
    fun delayMillis(failures: Int): Long = if (failures < 5) 0 else
        (30_000L * (1L shl ((failures - 5) / 5).coerceIn(0, 4))).coerceAtMost(300_000L)
}
