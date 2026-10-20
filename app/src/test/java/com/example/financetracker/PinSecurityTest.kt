package com.example.financetracker

import com.example.financetracker.security.PinAttemptPolicy
import com.example.financetracker.security.PinHasher
import org.junit.Assert.*
import org.junit.Test

class PinSecurityTest {
    @Test fun exactlyFourAsciiDigitsIncludingLeadingZeros() {
        assertTrue(PinHasher.valid("0000".toCharArray()))
        assertTrue(PinHasher.valid("0123".toCharArray()))
        listOf("", "123", "12345", "12 3", "-123", "１２３４", "١٢٣٤").forEach {
            assertFalse(it, PinHasher.valid(it.toCharArray()))
        }
    }
    @Test fun saltedVerifierAcceptsOnlyTheCorrectPin() {
        val salt = PinHasher.salt()
        val hash = PinHasher.derive("0123".toCharArray(), salt)
        assertEquals(32, hash.size)
        assertTrue(PinHasher.matches("0123".toCharArray(), salt, hash))
        assertFalse(PinHasher.matches("0124".toCharArray(), salt, hash))
        assertFalse(PinHasher.matches("123".toCharArray(), salt, hash))
        assertFalse(PinHasher.matches("0123".toCharArray(), salt, byteArrayOf()))
        assertFalse(hash.contentEquals(PinHasher.derive("0123".toCharArray(), PinHasher.salt())))
    }
    @Test fun verifierUsesTheSpecifiedPbkdf2Parameters() {
        val salt = ByteArray(16) { it.toByte() }
        val hex = PinHasher.derive("0123".toCharArray(), salt).joinToString("") { "%02x".format(it) }
        // Independently calculated with Python hashlib.pbkdf2_hmac('sha256', ..., 120000, 32).
        assertEquals("b805e7dfbdf7abf7aec4a2833ee9c08bea63108b111f523e412b835b62913846", hex)
    }
    @Test fun attemptsApplyIncreasingBoundedCooldowns() {
        (0..4).forEach { assertEquals(0L, PinAttemptPolicy.delayMillis(it)) }
        assertEquals(30_000L, PinAttemptPolicy.delayMillis(5))
        assertEquals(30_000L, PinAttemptPolicy.delayMillis(9))
        assertEquals(60_000L, PinAttemptPolicy.delayMillis(10))
        assertEquals(120_000L, PinAttemptPolicy.delayMillis(15))
        assertEquals(240_000L, PinAttemptPolicy.delayMillis(20))
        assertEquals(300_000L, PinAttemptPolicy.delayMillis(25))
        assertEquals(300_000L, PinAttemptPolicy.delayMillis(Int.MAX_VALUE))
    }
}
