package ir.v2rayng.compose.core.sec

import android.content.Context
import ir.v2rayng.compose.AppCtx

/**
 * Every value persisted in SharedPreferences is sealed with the AndroidKeyStore
 * master key (AES-256-GCM, StrongBox when available) - plaintext never touches
 * prefs files. Broken/unrecognized seals still read as legacy plaintext so old
 * installs migrate seamlessly; every freshly written value is sealed.
 */
object SecurePrefs {

    private const val PREFS = "v2settings"

    /** keys whose legacy plaintext has already been re-sealed this process */
    @Volatile
    private var migratedKeys: MutableSet<String>? = null

    private fun migratedSet(): MutableSet<String> {
        migratedKeys?.let { return it }
        return synchronized(this) {
            migratedKeys ?: HashSet<String>().also { migratedKeys = it }
        }
    }

    private fun sp(context: Context?): android.content.SharedPreferences =
        (context ?: AppCtx.app).getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    fun set(key: String, value: String?, context: Context? = null) {
        try {
            if (value == null) {
                sp(context).edit().remove(key).apply()
            } else {
                val sealed = BlobVault.seal(value.toByteArray(Charsets.UTF_8)) ?: return
                sp(context).edit().putString(key, sealed).apply()
            }
        } catch (_: Exception) {
        }
    }

    /** null = absent (or unreadable); falls back to legacy plaintext and migrates it */
    fun get(key: String, context: Context? = null): String? {
        return try {
            val s = sp(context)
            val raw = s.getString(key, null)
            if (raw != null) {
                if (raw.isEmpty()) return raw
                BlobVault.unseal(raw)?.let { return String(it, Charsets.UTF_8) }
                // old install plaintext -> read it, then seal it once with the master key
                legacyMigrate(s, key, raw)
                return raw
            }
            // old installs stored real typed primitives -> serialize once to a sealed string
            val v = s.all[key] ?: return null
            (v as? String ?: v.toString()).also { legacyMigrate(s, key, it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Old installs hold plaintext/typed values - on first successful read the
     * value is re-sealed with the AndroidKeyStore master key so the prefs file
     * becomes fully ciphertext over time.
     */
    private fun legacyMigrate(s: android.content.SharedPreferences, key: String, plain: String) {
        if (plain.isEmpty()) return
        if (!migratedSet().add(key)) return
        try {
            if (plain.length > 256 * 1024) return
            val sealed = BlobVault.seal(plain.toByteArray(Charsets.UTF_8)) ?: return
            s.edit().putString(key, sealed).apply()
        } catch (_: Exception) {
        }
    }

    fun getString(key: String, def: String, context: Context? = null): String = get(key, context) ?: def
    fun getStringOrNull(key: String, context: Context? = null): String? = get(key, context)
    fun getBoolean(key: String, def: Boolean, context: Context? = null): Boolean = get(key, context)?.toBoolean() ?: def
    fun getInt(key: String, def: Int, context: Context? = null): Int = get(key, context)?.toIntOrNull() ?: def
    fun getLong(key: String, def: Long, context: Context? = null): Long = get(key, context)?.toLongOrNull() ?: def
    fun getFloat(key: String, def: Float, context: Context? = null): Float = get(key, context)?.toFloatOrNull() ?: def
    fun contains(key: String, context: Context? = null): Boolean =
        try { sp(context).contains(key) } catch (_: Exception) { false }
    fun remove(key: String, context: Context? = null) {
        try {
            sp(context).edit().remove(key).apply()
        } catch (_: Exception) {
        }
    }
}
