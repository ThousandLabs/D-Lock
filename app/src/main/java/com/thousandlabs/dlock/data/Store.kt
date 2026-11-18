package com.thousandlabs.dlock.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class LockMethod(val id: String, val label: String, val rule: String, val prompt: String, val wrong: String) {
    PIN("pin", "Code PIN", "De 4 à 8 chiffres.", "Saisissez votre code PIN", "Code PIN incorrect"),
    PATTERN("pattern", "Schéma", "Reliez au moins 4 points.", "Dessinez votre schéma", "Schéma incorrect"),
    PASSWORD("password", "Mot de passe", "Au moins 4 caractères.", "Saisissez votre mot de passe", "Mot de passe incorrect");

    /** Renvoie un message d'erreur si la saisie ne respecte pas la règle, sinon null. */
    fun validate(value: String): String? = when (this) {
        PIN -> if (value.length in 4..8 && value.all { it.isDigit() }) null else "Le code PIN doit faire de 4 à 8 chiffres."
        PATTERN -> if (value.length >= 4) null else "Reliez au moins 4 points."
        PASSWORD -> if (value.length >= 4) null else "Le mot de passe doit faire au moins 4 caractères."
    }

    companion object {
        fun from(id: String?): LockMethod = entries.firstOrNull { it.id == id } ?: PIN
    }
}

/** Tous les réglages de D-Lock, stockés uniquement sur le téléphone. */
object Store {
    const val YOUTUBE = "com.google.android.youtube"
    private const val ITERATIONS = 12000

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences("dlock", Context.MODE_PRIVATE)
        }
    }

    // ---- applis verrouillées par code ----
    var lockedApps: Set<String>
        get() = prefs.getStringSet("locked_apps", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("locked_apps", value.toSet()).apply()

    fun setLocked(pkg: String, locked: Boolean) {
        lockedApps = if (locked) lockedApps + pkg else lockedApps - pkg
    }

    // ---- règle YouTube + VPN ----
    var ytVpnRequired: Boolean
        get() = prefs.getBoolean("yt_vpn", true)
        set(v) = prefs.edit().putBoolean("yt_vpn", v).apply()

    var ytOverride: Boolean
        get() = prefs.getBoolean("yt_override", true)
        set(v) = prefs.edit().putBoolean("yt_override", v).apply()

    var ytAutoReturn: Boolean
        get() = prefs.getBoolean("yt_auto_return", true)
        set(v) = prefs.edit().putBoolean("yt_auto_return", v).apply()

    var vpnPackage: String?
        get() = prefs.getString("vpn_pkg", null)
        set(v) = prefs.edit().putString("vpn_pkg", v).apply()

    // ---- code secret ----
    val method: LockMethod get() = LockMethod.from(prefs.getString("method", null))
    val pinLength: Int get() = prefs.getInt("pin_len", 0)
    fun hasSecret(): Boolean = prefs.contains("hash")

    var biometric: Boolean
        get() = prefs.getBoolean("biometric", false)
        set(v) = prefs.edit().putBoolean("biometric", v).apply()

    fun setSecret(method: LockMethod, value: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("method", method.id)
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(derive(value, salt), Base64.NO_WRAP))
            .putInt("pin_len", if (method == LockMethod.PIN) value.length else 0)
            .apply()
        resetFailures()
    }

    fun check(value: String): Boolean {
        val salt = prefs.getString("salt", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val hash = prefs.getString("hash", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        return MessageDigest.isEqual(derive(value, salt), hash)
    }

    private fun derive(value: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(value.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    // ---- protection contre les essais répétés ----
    // Le temps de blocage est compté depuis le démarrage du téléphone (impossible à contourner en changeant l'heure).
    fun lockoutRemainingMs(): Long {
        val until = prefs.getLong("lockout_until", 0L)
        val now = SystemClock.elapsedRealtime()
        if (until - now > 10 * 60_000L) { // téléphone redémarré entre-temps
            prefs.edit().putLong("lockout_until", 0L).apply(); return 0
        }
        return (until - now).coerceAtLeast(0)
    }

    fun registerFailure() {
        val count = prefs.getInt("fail_count", 0) + 1
        val edit = prefs.edit().putInt("fail_count", count)
        if (count >= 5) {
            val seconds = (30L * (count - 4)).coerceAtMost(300L)
            edit.putLong("lockout_until", SystemClock.elapsedRealtime() + seconds * 1000)
        }
        edit.apply()
    }

    fun resetFailures() {
        prefs.edit().putInt("fail_count", 0).putLong("lockout_until", 0L).apply()
    }

    // ---- accès générique (mises à jour) ----
    fun getBool(key: String, def: Boolean) = prefs.getBoolean(key, def)
    fun putBool(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
    fun getInt(key: String, def: Int) = prefs.getInt(key, def)
    fun putInt(key: String, v: Int) = prefs.edit().putInt(key, v).apply()
}
