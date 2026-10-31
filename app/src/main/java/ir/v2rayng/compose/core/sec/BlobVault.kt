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

    private const val CURRENT = 0
    private const val LEGACY = 1

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

    /** Where the last unseal stopped, for the log page - "cannot be opened" alone does not say
     *  whether the base64, the length, the keystore or the tag is what refused. */
    @Volatile
    var lastStage: String = ""
        private set

    /** returns base64url(iv || ct) or null when the keystore refuses */
    fun seal(plain: ByteArray): String? {
        if (!ready()) {
            lastStage = "seal: no key [selfcheck=" + NativeSec.vaultSelfCheck() + " " + NativeSec.note() + " step=" + NativeSec.errInfo() + "]"
            return null
        }
        val t0 = android.os.SystemClock.elapsedRealtime()
        // one operation at a time: concurrent use of a keystore key fails on some devices
        val out = synchronized(this) { NativeSec.seal(plain) }
        if (out == null) {
            usable = false
            // -1 = the library did not pass its own known-answer test, so nothing it does can be
            // trusted; -2 = it did not load at all; 2..6 = where the seal/open round trip stopped
            lastStage = "seal: native refused [selfcheck=" + NativeSec.vaultSelfCheck() + " " + NativeSec.note() + " step=" + NativeSec.errInfo() + "]"
            SecJournal.note("seal ERROR ${plain.size}B ${android.os.SystemClock.elapsedRealtime() - t0}ms")
            return null
        }
        SecJournal.note("seal ${plain.size}B ${android.os.SystemClock.elapsedRealtime() - t0}ms")
        return BlobB64.encode(out)
    }

    fun unseal(raw: String): ByteArray? {
        val blob = BlobB64.decode(raw)
        if (blob == null) {
            lastStage = "base64"
            return null
        }
        if (blob.size < 13) {
            lastStage = "too short (${blob.size}B)"
            return null
        }
        val t0 = android.os.SystemClock.elapsedRealtime()
        if (!ready()) {
            lastStage = "no key (step=" + NativeSec.errInfo() + ")"
            return null
        }
        val out = synchronized(this) { NativeSec.unseal(blob, CURRENT) }
        if (out != null) {
            lastStage = "ok"
            SecJournal.note("unseal ${out.size}B ${android.os.SystemClock.elapsedRealtime() - t0}ms")
            return out
        }
        // written before the StrongBox key was dropped - open it with the old key; the next seal
        // rewrites it under the new one, so this path disappears after the first save
        val viaOld = synchronized(this) { NativeSec.unseal(blob, LEGACY) }
        if (viaOld != null) {
            lastStage = "ok (legacy key)"
            SecJournal.note("unseal ${viaOld.size}B with LEGACY key ${android.os.SystemClock.elapsedRealtime() - t0}ms (will be resealed)")
            return viaOld
        }
        // "tag fail" is only true when the step is open:cipher.doFinal; anything earlier means the
        // call itself refused and the stored bytes were never offered to the cipher
        lastStage = "cannot open ${blob.size}B stored [step=" + NativeSec.errInfo() + "]"
        SecJournal.note("unseal BadPadding ${android.os.SystemClock.elapsedRealtime() - t0}ms")
        return null
    }
}
