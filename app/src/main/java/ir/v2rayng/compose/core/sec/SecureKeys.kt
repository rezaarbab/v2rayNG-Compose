package ir.v2rayng.compose.core.sec

import android.content.Context
import ir.v2rayng.compose.AppCtx

/**
 * Temporary diagnostic journal shared by the keystore layer, so the ViewModel can print what the
 * hardware actually did (durations, failures) into the in-app log - the phone has no adb here.
 */
object SecJournal {

    private const val MAX = 160
    private val lines = ArrayDeque<String>()

    fun note(line: String) {
        synchronized(lines) {
            if (lines.size >= MAX) lines.removeFirst()
            lines.addLast(line)
        }
    }

    /** Hands out everything recorded so far and clears it. */
    fun drain(): List<String> = synchronized(lines) {
        val out = lines.toList()
        lines.clear()
        out
    }
}

/**
 * Every value persisted in SharedPreferences is sealed with the AndroidKeyStore master key
 * (AES-256-GCM in the TEE) - plaintext never touches the prefs file, and the sealed bytes never
 * reach this file either: seal, base64, write, read, decode and open all happen in blobsec.c
 * behind BlobVault, because a method tracer prints what a function returns and a ciphertext used
 * to be the return value of every save.
 * A value this device can no longer open reads as absent, never as its own ciphertext, and a
 * legacy plaintext value is read as-is and re-sealed once on the way out.
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

    private fun note(line: String) = SecJournal.note(line)

    /** Reading a value is a keystore open (~200ms on the phone in the APP_SCOUT traces), and
     *  startup touches the same keys repeatedly, so decoded values are memoized for the process
     *  and dropped the moment the key is written. */
    private val memo = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun set(key: String, value: String?, context: Context? = null) {
        memo.remove(key)
        val t0 = android.os.SystemClock.elapsedRealtime()
        try {
            if (value == null) {
                val ed = sp(context).edit()
                ed.remove(key)
                ed.apply()
                return
            }
            // apply() only hands the write to a background thread, and MIUI kills the process when
            // the app is swiped away - off the main thread the same write is committed instead, so
            // a setting saved a second ago is still there when the app opens again. The choice is
            // passed down: the seal, the base64 and the Editor all live in the library now.
            val onMain = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
            if (!BlobVault.put(PREFS, key, value, !onMain)) {
                // silent no-op is what made "nothing saves" undiagnosable
                sealFailSet().add(key)
                note("SEAL FAIL ${key} (${value.length}ch) in ${android.os.SystemClock.elapsedRealtime() - t0}ms (${BlobVault.lastStage.take(90)})")
                return
            }
            memo[key] = value
            note(
                "write ${key} ${value.length}ch " +
                    (if (onMain) "apply" else "commit") + " ${android.os.SystemClock.elapsedRealtime() - t0}ms"
            )
        } catch (e: Exception) {
            note("write FAIL ${key}: ${e.javaClass.simpleName} ${android.os.SystemClock.elapsedRealtime() - t0}ms")
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

    /** Why the last prove-the-write-landed check failed, for the log page: "MISMATCH" alone cannot
     *  be acted on, and logcat is intentionally empty, so the reason has to reach the user's
     *  screen or the next build is another guess. */
    @Volatile
    var verifyNote: String? = null
        private set

    /** Prove a write landed instead of assuming it - opens the stored value through the vault, so
     *  this object's memo cannot make the check agree with itself. */
    fun verify(key: String, expected: String): Boolean {
        val t0 = android.os.SystemClock.elapsedRealtime()
        val out = BlobVault.get(PREFS, key)
        val why = BlobVault.lastStage
        val ok = when {
            out == null -> {
                // "nothing stored" and "stored but this device cannot open it" need different fixes,
                // and the self check is what separates "the native vault is broken" (check != 0)
                // from "this value was sealed under a key that no longer exists" (check == 0)
                verifyNote = if (why == "none") "$key: nothing stored"
                else "stored value cannot be opened ($why; selfcheck=" +
                    ir.v2rayng.compose.core.NativeSec.vaultSelfCheck() + ")"
                false
            }
            out == expected -> true
            else -> {
                verifyNote = "read ${out.length}ch, expected ${expected.length}ch"
                false
            }
        }
        note("verify ${key} ${if (ok) "OK" else "MISMATCH"} ${android.os.SystemClock.elapsedRealtime() - t0}ms")
        return ok
    }

    /** null = absent (or unreadable); falls back to legacy plaintext and migrates it */
    fun get(key: String, context: Context? = null): String? {
        memo[key]?.let { return it }
        val v = readStored(key, context) ?: return null
        memo[key] = v
        return v
    }

    private fun readStored(key: String, context: Context?): String? {
        val v = BlobVault.get(PREFS, key)
        val why = BlobVault.lastStage
        return when {
            v != null -> {
                // "plaintext" is a value an old install wrote in the clear: hand it back as it is
                // and seal it once here, so the prefs file becomes ciphertext over time
                if (why == "plaintext") legacyMigrate(key, v)
                v
            }
            why.startsWith("undecipherable") -> {
                note("read $key $why -> treated as absent")
                // Sealed with a key this device no longer has. Handing it back as if it were a
                // value (and re-sealing it over itself) poisoned the stored data and made every
                // later read fail, which is what looked like "nothing saves anymore".
                unreadableSet().add(key)
                null
            }
            else -> typedLegacy(key, context)
        }
    }

    /** A value an old install stored as a real boolean/int/float rather than as a string. Only for
     *  a "none" verdict, and only when the raw map holds something that is not a String: with the
     *  vault refusing, a String in that map is our own ciphertext and re-sealing it over itself is
     *  exactly the poisoning the branch above exists to avoid. */
    private fun typedLegacy(key: String, context: Context?): String? {
        if (BlobVault.lastStage != "none") return null
        val v = try { sp(context).all[key] } catch (_: Exception) { null } ?: return null
        if (v is String) return null
        note("read $key typed-legacy")
        return v.toString().also { legacyMigrate(key, it) }
    }

    private fun legacyMigrate(key: String, plain: String) {
        if (plain.isEmpty()) return
        if (!migratedSet().add(key)) return
        if (plain.length > 256 * 1024) return
        // applied, not committed: a migration that does not land is retried on the next start,
        // and it is not worth blocking a read on a disk sync
        BlobVault.put(PREFS, key, plain, false)
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
