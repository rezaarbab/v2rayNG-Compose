package ir.v2rayng.compose.core.sec

import ir.v2rayng.compose.core.NativeSec

/**
 * Hardware-backed storage: every sensitive preference blob (server list, session keys) is sealed
 * with an AES-256-GCM key that lives inside AndroidKeyStore - plaintext never touches
 * SharedPreferences, and the key material is not exportable.
 *
 * The key used to be created with setIsStrongBoxBacked(true). The device in the app log showed why
 * that has to go: a 1094-byte value took 3.8s, 4.3s and 8.5s to unseal, and every operation queued
 * behind every other one, because StrongBox is a small co-processor with its own lock. The TEE
 * keystore is still hardware-backed and still non-exportable, so the alias moved and the old
 * StrongBox key stays reachable purely so values sealed with it can migrate on the next write
 * instead of being lost.
 *
 * This file no longer holds a keystore, a cipher, a key parameter or an alias - that whole path is
 * in blobsec.c behind NativeSec. What is left here is only the judgement about the bytes: retry
 * before replacing anything, fall back to the old key once, serialize access, and say what happened.
 */
object BlobVault {

    /** which keystore alias: 0 is the one in use, 1 the StrongBox key this app used to write with */
    private const val CURRENT = 0

    /** What happened to the key, for the app log - "cannot read" and "no key at all" are very
     *  different states and the user sees only "nothing saves" without this. */
    @Volatile
    var recoveryNote: String? = null
        private set

    @Volatile
    private var usable = false

    /**
     * A live but unreadable key entry must not be left in place: sealing then returns nothing, every
     * write is dropped and every read yields defaults - the "nothing saves anymore" state. But the
     * keystore also refuses for a moment under load, and replacing the key on that first refusal
     * would destroy everything already stored, so the entry is asked three times before it is
     * replaced, and the replacement is reported instead of happening silently.
     */
    private fun ready(): Boolean {
        if (usable) return true
        val t0 = android.os.SystemClock.elapsedRealtime()
        if (NativeSec.vaultOk(CURRENT)) {
            usable = true
            return true
        }
        for (i in 1..3) {
            try {
                Thread.sleep(70L)
            } catch (_: Exception) {
            }
            if (NativeSec.vaultOk(CURRENT)) {
                usable = true
                return true
            }
        }
        // only an entry that is really there may be replaced: a keystore that did not answer is not
        // evidence of a dead key, and dropping a live one would destroy every stored value
        if (NativeSec.hasKey(CURRENT) && NativeSec.dropKey(CURRENT) && NativeSec.vaultOk(CURRENT)) {
            recoveryNote = "keystore key exists but this device cannot read it - it was replaced, so values stored earlier are gone"
            usable = true
            return true
        }
        recoveryNote = "the keystore gives no key at all on this device - saving cannot work" +
            " (" + NativeSec.note() + ", " + ir.v2rayng.compose.core.NativeKit.note() + ")"
        SecJournal.note("keystore key UNAVAILABLE ${android.os.SystemClock.elapsedRealtime() - t0}ms")
        return false
    }

    /** Where the last operation stopped, for the log page - "cannot be opened" alone does not say
     *  whether the base64, the length, the keystore or the tag is what refused. */
    @Volatile
    var lastStage: String = ""
        private set

    /** Seal and store. The plaintext goes in and nothing but a verdict comes back - the sealed
     *  bytes and their base64 form used to cross through Kotlin on the way to SharedPreferences,
     *  which put a ciphertext in every method return a tracer prints. */
    fun put(prefsFile: String, key: String, plain: String, commit: Boolean): Boolean {
        if (!ready()) {
            lastStage = "no key [selfcheck=" + NativeSec.vaultSelfCheck() + " " + NativeSec.note() +
                " step=" + NativeSec.errInfo() + "]"
            return false
        }
        val t0 = android.os.SystemClock.elapsedRealtime()
        // one operation at a time: concurrent use of a keystore key fails on some devices
        val ok = synchronized(this) { NativeSec.kvPut(prefsFile, key, plain, commit) }
        lastStage = NativeSec.kvReason()
        if (!ok) usable = false
        SecJournal.note((if (ok) "put " else "put FAIL ") + key + " " + plain.length + "ch " +
            (if (commit) "commit" else "apply") + " ${android.os.SystemClock.elapsedRealtime() - t0}ms" +
            (if (ok) "" else " (${lastStage.take(90)})"))
        return ok
    }

    /** Read and open. null means absent or unreadable; lastStage says which, and "plaintext" means
     *  the value predates sealing and should be written back sealed. */
    fun get(prefsFile: String, key: String): String? {
        val t0 = android.os.SystemClock.elapsedRealtime()
        // asked for, but its answer is not used to refuse: an old install's plaintext has to stay
        // readable on a device whose keystore is refusing today, and the sealed form simply
        // follows it into the same call
        ready()
        val out = synchronized(this) { NativeSec.kvGet(prefsFile, key) }
        lastStage = NativeSec.kvReason()
        SecJournal.note("get " + key + " " + (out?.length ?: -1) + "ch " +
            "${android.os.SystemClock.elapsedRealtime() - t0}ms (${lastStage.take(70)})")
        return out
    }
}
