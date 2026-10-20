package com.example.financetracker.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The verifier is salted, stretched, and encrypted with a non-exportable device key. */
class PinStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("pin_security", Context.MODE_PRIVATE)
    val enabled: Boolean get() = prefs.contains("ciphertext")
    val ownerEmail: String? get() = prefs.getString("owner", null)
    private fun keys() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)
    private fun boot() = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun remainingMillis(): Long = synchronized(monitor) {
        val delay = PinAttemptPolicy.delayMillis(prefs.getInt("failures", 0))
        if (delay == 0L) return@synchronized 0L
        // Reboot must not erase a persisted cooldown; wall-clock changes cannot shorten it.
        val now = SystemClock.elapsedRealtime()
        if (prefs.getInt("boot", -2) != boot() || prefs.getLong("until", 0) > now + 300_000L) {
            check(prefs.edit().putInt("boot", boot()).putLong("until", now + delay).commit())
        }
        (prefs.getLong("until", 0) - now).coerceAtLeast(0L)
    }

    fun recordAttempt(success: Boolean) = synchronized(monitor) {
        val failures = if (success) 0 else (prefs.getInt("failures", 0) + 1).coerceAtMost(100)
        check(prefs.edit().putInt("failures", failures).putInt("boot", boot())
            .putLong("until", SystemClock.elapsedRealtime() + PinAttemptPolicy.delayMillis(failures)).commit())
    }

    fun verify(pin: CharArray): Boolean = synchronized(monitor) {
        if (remainingMillis() > 0) return@synchronized false
        check(enabled) { "PIN lock is not enabled" }
        val alias = requireNotNull(prefs.getString("alias", null))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keys().getKey(alias, null) as SecretKey,
            GCMParameterSpec(128, decode(requireNotNull(prefs.getString("iv", null)))))
        val bytes = cipher.doFinal(decode(requireNotNull(prefs.getString("ciphertext", null))))
        try {
            check(bytes.size == 48) { "PIN verifier unavailable" }
            val salt = bytes.copyOfRange(0, 16)
            val hash = bytes.copyOfRange(16, 48)
            val matches = try { PinHasher.matches(pin, salt, hash) } finally { salt.fill(0); hash.fill(0) }
            recordAttempt(matches)
            matches
        } finally { bytes.fill(0) }
    }

    fun verifyRecoveryPassword(password: CharArray, expected: String?): Boolean = synchronized(monitor) {
        if (!enabled || remainingMillis() > 0) return@synchronized false
        val actual = String(password).toByteArray(Charsets.UTF_8)
        val stored = expected?.toByteArray(Charsets.UTF_8)
        val valid = try { stored != null && MessageDigest.isEqual(actual, stored) }
            finally { actual.fill(0); stored?.fill(0) }
        recordAttempt(valid)
        valid
    }

    fun save(pin: CharArray, owner: String) = synchronized(monitor) {
        require(PinHasher.valid(pin) && owner.isNotBlank())
        val alias = "swallet_pin_${UUID.randomUUID()}"
        val oldAlias = prefs.getString("alias", null)
        var committed = false
        try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, generator.generateKey())
            val salt = PinHasher.salt()
            val hash = PinHasher.derive(pin, salt)
            val payload = salt + hash
            val encrypted = try { cipher.doFinal(payload) } finally { salt.fill(0); hash.fill(0); payload.fill(0) }
            check(prefs.edit().clear().putString("alias", alias).putString("owner", owner)
                .putString("iv", encode(cipher.iv)).putString("ciphertext", encode(encrypted)).commit())
            committed = true
        } finally {
            runCatching { if (!committed) keys().deleteEntry(alias) else oldAlias?.let { keys().deleteEntry(it) } }
        }
    }

    fun disable() = synchronized(monitor) {
        val alias = prefs.getString("alias", null)
        check(prefs.edit().clear().commit())
        runCatching { alias?.let { keys().deleteEntry(it) } }
        Unit
    }

    companion object { private val monitor = Any() }
}
