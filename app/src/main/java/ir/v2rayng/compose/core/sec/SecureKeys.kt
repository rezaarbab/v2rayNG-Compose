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

    /** Reading a value is a StrongBox unseal (~200ms on the phone in the APP_SCOUT traces), and
     *  startup touches the same keys repeatedly, so decoded values are memoized for the process
     *  and dropped the moment the key is written. */
    private val memo = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun set(key: String, value: String?, context: Context? = null) {
        memo.remove(key)
        try {
            val ed = sp(context).edit()
            if (value == null) {
                ed.remove(key)
            } else {
                val sealed = BlobVault.seal(value.toByteArray(Charsets.UTF_8))
                if (sealed == null) {
                    // silent no-op is what made "nothing saves" undiagnosable
                    sealFailSet().add(key)
                    return
                }
                ed.putString(key, sealed)
                memo[key] = value
            }
            // apply() only hands the write to a background thread, and MIUI kills the process when
            // the app is swiped away - on a worker thread the write is committed instead, so a
            // setting saved a second ago is still there when the app opens again
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) ed.apply() else ed.commit()
        } catch (_: Exception) {
        }
    }

    @Volatile
    private var sealFails: MutableSet<String>? = null

    @Volatile
    private var unreadable: MutableSet<String>? = null

    private fun sealFailSet(): MutableSet<String> {
        sealFails?.let { return it }
        return synchronized(this) { sealFails ?: HashSet<String>().also { sealFails = it } }
    }

    private fun unreadableSet(): MutableSet<String> {
        unreadable?.let { return it }
        return synchronized(this) { unreadable ?: HashSet<String>().also { unreadable = it } }
    }

    /** Keys the keystore refused to seal this session - writes for these are lost. */
    fun sealFailureReport(): String? = sealFailSet().takeIf { it.isNotEmpty() }?.joinToString(",")

    /** Keys whose stored value this device can no longer decrypt (its keystore key changed). */
    fun unreadableReport(): String? = unreadableSet().takeIf { it.isNotEmpty() }?.joinToString(",")

    /** Prove a write landed instead of assuming it - reads prefs directly, bypassing the memo. */
    fun verify(key: String, expected: String, context: Context? = null): Boolean = try {
        val raw = sp(context).getString(key, null) ?: return false
        BlobVault.unseal(raw)?.toString(Charsets.UTF_8) == expected
    } catch (_: Exception) {
        false
    }

    /** null = absent (or unreadable); falls back to legacy plaintext and migrates it */
    fun get(key: String, context: Context? = null): String? {
        memo[key]?.let { return it }
        val v = readStored(key, context) ?: return null
        memo[key] = v
        return v
    }

    private fun readStored(key: String, context: Context?): String? {
        return try {
            val s = sp(context)
            val raw = s.getString(key, null)
            if (raw != null) {
                if (raw.isEmpty()) return raw
                BlobVault.unseal(raw)?.let { return String(it, Charsets.UTF_8) }
                if (looksPlaintext(raw)) {
                    // old install plaintext -> read it, then seal it once with the master key
                    legacyMigrate(s, key, raw)
                    return raw
                }
                // Sealed with a key this device no longer has. Handing it back as if it were a
                // value (and re-sealing it over itself) poisoned the stored data and made every
                // later read fail, which is what looked like "nothing saves anymore".
                unreadableSet().add(key)
                return null
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
    /** Our ciphertext is base64url without padding, so it is only ever [A-Za-z0-9-_] and at least
     *  38 characters (12-byte IV + 16-byte tag for an empty payload). Anything else - JSON, a
     *  boolean, a number, a short id, a uuid, a URL - can only be legacy plaintext. */
    private fun looksPlaintext(v: String): Boolean {
        if (v.length < 38) return true
        var i = 0
        while (i < v.length) {
            val c = v[i]
            val ok = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_'
            if (!ok) return true
            i++
        }
        return false
    }

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
        memo.remove(key)
        try {
            sp(context).edit().remove(key).apply()
        } catch (_: Exception) {
        }
    }
}
