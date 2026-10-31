package ir.v2rayng.compose.core.sec

import ir.v2rayng.compose.core.NativeSec

/**
 * Blob secure layer - app side. Wire format MUST stay byte-identical with
 * cloudflare/worker.js (blob secure layer v2):
 *   token    = b64url( ts(4BE) | nonce(8) | hmac(32) ), msg = "blb"|device|0x00|ts4BE|nonce
 *   cipher   = iv(12) || AES-256-GCM(key=Kc, aad="BLBv1")   (no PBKDF2 - key is random)
 *   envelope = [mode 1B || raw | zlib]
 *   carrier  = magic16(0xB10B) + len32 + cipher bits, scattered by key-derived xorshift64*
 *
 * The slot engine (morphology insertions, space insertions, homoglyph swaps), the keyed shuffle,
 * the header, the GCM open and the envelope all run in blobsec.c now. This file turns the answer
 * and the recorded step into a sentence for the log - and nothing more.
 */
object BlobStego {

    private val AAD = byteArrayOf(0x42, 0x4C, 0x42, 0x76, 0x31) // "BLBv1"

    /** Why the last reveal/open failed - short, log/toast safe (no URLs). */
    @Volatile
    var lastWhy: String? = null

    /** Reveal an admin response carrier into plaintext (null = wrong keys / damaged). */
    fun reveal(stego: String, kc: ByteArray): String? {
        lastWhy = null
        if (kc.size != 32) return why("stego: bad key size " + kc.size)
        val out = NativeSec.stegoReveal(stego, kc)
        if (out == null) return why(explain(NativeSec.errInfo()))
        return String(out, Charsets.UTF_8)
    }

    /** the library's step, in the words this app has used for these failures */
    private fun explain(step: String): String = when {
        step.startsWith("stego:magic") -> "header not recognized (keys out of sync, or worker format differs)"
        step.startsWith("stego:small") -> "carrier too small for a header"
        step.startsWith("stego:short") -> "carrier shorter than payload"
        step.startsWith("stego:len") -> "bad cipher len"
        step.startsWith("stego:key-size") -> "bad key size"
        step.contains("gcm:") -> "GCM tag fail (sec=" + NativeSec.note() + ", step=" + step + ")"
        else -> "stego: " + step
    }

    private fun why(w: String): String? {
        blog("reveal: " + w)
        lastWhy = w
        return null
    }

    /**
     * cipher = iv(12) || GCM-ct||tag ; key = the random session bytes. Used by the raw "cy" path,
     * which is not a carrier and has no slots - the opening itself is in blobsec.c.
     */
    fun blobOpen(cipher: ByteArray, key: ByteArray): ByteArray? {
        if (cipher.size < 29 || key.size != 32) return null
        return NativeSec.gcmDecrypt(cipher, key, AAD)
    }
}

object BlobZip {
    /**
     * The envelope byte (0 raw, 1 raw-deflate, 2 zlib) and the inflate itself are done in C with
     * the system zlib; if the native side cannot answer, the envelope is returned stripped rather
     * than guessed, so the caller fails on the payload instead of silently mis-reading it.
     */
    fun open(envelope: ByteArray): ByteArray {
        if (envelope.isEmpty()) return envelope
        return NativeSec.openEnv(envelope) ?: envelope.copyOfRange(1, envelope.size)
    }
}

/** base64url helpers, also in C - one alphabet, one padding rule, no java.util.Base64 left here. */
object BlobB64 {
    fun decode(s: String): ByteArray? = NativeSec.b64decode(s)?.takeIf { it.isNotEmpty() || s.isEmpty() }

    fun encode(b: ByteArray): String = NativeSec.b64url(b) ?: ""
}
