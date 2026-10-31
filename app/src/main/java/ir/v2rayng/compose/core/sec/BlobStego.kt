package ir.v2rayng.compose.core.sec

import ir.v2rayng.compose.core.NativeSec

/**
 * Blob secure layer - app side. Wire format MUST stay byte-identical with
 * cloudflare/worker.js (blob secure layer v2):
 *   token    = b64url( ts(4BE) | nonce(8) | hmac(32) ), msg = "blb"|device|0x00|ts4BE|nonce
 *   cipher   = iv(12) || AES-256-GCM(key=Kc, aad="BLBv1")   (no PBKDF2 - key is random)
 *   envelope = [mode 1B || raw | zlib]
 *   carrier  = magic16(0xB10B) + len32 + cipher bits, scattered by key-derived xorshift64*
 */
object BlobStego {

    private const val MAGIC16 = 0xB10B
    private const val MAX_CIPHER = 2_000_000
    private val AAD = byteArrayOf(0x42, 0x4C, 0x42, 0x76, 0x31) // "BLBv1"

    private val CH_ZWSP = '\u200B'
    private val CH_ZWNJ = '\u200C'
    private val CH_ZWJ = '\u200D'
    private val CH_WJ = '\u2060'
    private val CH_BOM = '\uFEFF'
    private val CH_SHY = '\u00AD'
    val STRIP_ALL = setOf(CH_ZWSP, CH_ZWNJ, CH_ZWJ, CH_WJ, CH_BOM)
    val GLUE_CHARS = setOf(CH_ZWSP, CH_ZWNJ, CH_ZWJ, CH_WJ, CH_BOM, CH_SHY)

    val PAIRS = listOf(
        'ی' to 'ي', 'ک' to 'ك',
        'a' to 'а', 'A' to 'А', 'c' to 'с', 'C' to 'С', 'e' to 'е', 'E' to 'Е',
        'o' to 'о', 'O' to 'О', 'p' to 'р', 'P' to 'Р', 'x' to 'х', 'X' to 'Х',
        'y' to 'у', 'Y' to 'У', 'k' to 'к', 'K' to 'К', 'm' to 'м', 'M' to 'М',
        'j' to 'ј', 'J' to 'Ј', 'B' to 'В',
    )
    val TWINS: Map<Char, Char> = buildMap {
        PAIRS.forEach { (a, b) -> put(a, b); put(b, a) }
    }
    val PRIMARY: Set<Char> = PAIRS.map { it.first }.toSet()

    /** HMAC-SHA256 is in C (blobsec.c) behind a known-answer self test; null = library not usable. */
    fun hmac(key: ByteArray, msg: ByteArray): ByteArray = NativeSec.hmacSha256(key, msg) ?: ByteArray(0)

    /** stego bit-stream seed: HMAC-SHA256(key=Kc, msg="BLB1") first 8 bytes big endian. */
    fun orderSeed(kc: ByteArray): Long {
        val d = hmac(kc, byteArrayOf(0x42, 0x4C, 0x42, 0x31))
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (d[i].toLong() and 0xFF)
        return v
    }

    class RevealedBlob(val json: String)

    /** Why the last reveal/open failed - short, log/toast safe (no URLs). */
    @Volatile
    var lastWhy: String? = null

    /** Reveal an admin response carrier into plaintext (null = wrong keys / damaged). */
    fun reveal(stego: String, kc: ByteArray): String? {
        lastWhy = null
        if (kc.size != 32) return why("stego: bad key size " + kc.size)
        val slots = buildSlots(stego, true)
        if (slots.size < 56) return why("stego: carrier too small for a header (" + slots.size + " slots)")
        val rnd = KeyedRandom(orderSeed(kc))
        val shuffled = keyShuffle(slots.size, rnd)
        val head = BooleanArray(56)
        for (i in 0 until 56) head[i] = evalSlot(stego, slots[shuffled[i]])
        val magic = readIntBits(head, 0, 16)
        // a wrong Kc scrambles the header first, so this is the key-mismatch signature
        if (magic != MAGIC16) return why("stego: header not recognized (keys out of sync, or worker format differs)")
        val cipherLen = readIntBits(head, 16, 32)
        if (cipherLen <= 0 || cipherLen > MAX_CIPHER) return why("stego: bad cipher len " + cipherLen)
        if (56 + cipherLen * 8 > slots.size) return why("stego: carrier shorter than payload (need " + (56 + cipherLen * 8) + ", got " + slots.size + ")")
        val data = BooleanArray(cipherLen * 8)
        for (i in 0 until cipherLen * 8) data[i] = evalSlot(stego, slots[shuffled[56 + i]])
        val cipher = bytesOfBits(data)
        val plain = blobOpen(cipher, kc)
            ?: return why("stego: GCM tag fail (sec=" + NativeSec.note() + ", step=" + NativeSec.errInfo() + ")")
        val jsonU8 = BlobZip.open(plain)
        blog("reveal: OK cipherLen=" + cipherLen)
        return String(jsonU8, Charsets.UTF_8)
    }

    private fun why(w: String): String? {
        blog("reveal: " + w)
        lastWhy = w
        return null
    }

    /**
     * cipher = iv(12) || GCM-ct||tag ; key = the random session bytes. The opening itself is in
     * blobsec.c - this file only checks the shape and hands the bytes over.
     */
    fun blobOpen(cipher: ByteArray, key: ByteArray): ByteArray? {
        if (cipher.size < 29 || key.size != 32) return null
        return NativeSec.gcmDecrypt(cipher, key, AAD)
    }

    /**
     * Letter definition for the slot engine - must be IDENTICAL to cloudflare/worker.js parity rules:
     *  - the sender (bl_slots) counts every glyph in the 0x0600-0x06FF block as a "letter"
     *    (including punctuation like ؟ ، ؛ that Character.isLetter rejects), plus A-Z / a-z;
     *  - the receiver must ALSO keep homoglyph swap targets (Cyrillic twins like х ј Є)
     *    as letters so slot positions survive the swap - hence isLetter() ∪ [0600..06FF].
     */
    fun isLetterSlot(c: Char): Boolean = c.isLetter() || c.code in 0x0600..0x06FF

    fun isGlue(c: Char): Boolean = !isLetterSlot(c) && GLUE_CHARS.contains(c)

    private fun family(c: Char): Int {
        val code = c.code
        return when {
            code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0x08A0..0x08FF ||
                code in 0xFB50..0xFDFF || code in 0xFE70..0xFEFF -> 1
            code in 0x0400..0x052F || code in 0x0041..0x005A || code in 0x0061..0x007A -> 2
            code in 0x0370..0x03FF || code in 0x1F00..0x1FFF -> 3
            code in 0x3040..0x30FF || code in 0x4E00..0x9FFF || code in 0xAC00..0xD7AF -> 4
            else -> 0
        }
    }

    internal class Slot {
        var swap = false
        var at = 0
        var prev = 0
        var ch = ' '
        var twin = ' '
    }

    /** Robust build -  exact port of StegaText slots (morph + space + swap). */
    internal fun buildSlots(s: String, robustOnly: Boolean): List<Slot> {
        var count = 0
        for (c in s) if (isLetterSlot(c)) count++
        val n = count
        if (n < 2) return emptyList()

        val lch = CharArray(n)
        val lidx = IntArray(n)
        var t = 0
        for (i in s.indices) {
            val c = s[i]
            if (isLetterSlot(c)) { lch[t] = c; lidx[t] = i; t++ }
        }

        val sameToken = BooleanArray(if (n > 0) n - 1 else 0)
        for (j in 0 until n - 1) {
            var cont = true
            for (u in lidx[j] + 1 until lidx[j + 1]) {
                if (!isGlue(s[u])) { cont = false; break }
            }
            sameToken[j] = cont
        }

        val morphK = HashSet<Int>()
        val morphSlots = ArrayList<Slot>()
        val gapSlots = ArrayList<Slot>()

        var k = 0
        while (k < n) {
            var end = k
            while (end + 1 < n && sameToken[end]) end++
            val word = StringBuilder()
            for (u in k..end) word.append(primaryOfChar(lch[u]))
            val w = word.toString()
            var slotK = -1
            if (w.endsWith("هایی") && w.length >= 8) slotK = end - 3
            else if (w.endsWith("ترین") && w.length >= 8) slotK = end - 3
            else if (w.endsWith("های") && w.length >= 7) slotK = end - 2
            else if (w.endsWith("ها") && w.length >= 5) slotK = end - 1
            else if (w.endsWith("تر") && w.length >= 6) slotK = end - 1
            else if (w.startsWith("نمی") && w.length >= 6) slotK = k + 3
            else if (w.startsWith("می") && w.length >= 5) slotK = k + 2
            if (slotK > 0 && slotK > k && slotK <= end) {
                morphK.add(slotK)
                morphSlots.add(newSlot(false, lidx[slotK], lidx[slotK - 1], CH_ZWNJ, ' '))
            }
            k = end + 1
        }

        for (j in 0 until n - 1) {
            val target = j + 1
            if (morphK.contains(target)) continue
            val f = family(lch[j])
            if (f != 0 && f == family(lch[target])) {
                gapSlots.add(newSlot(false, lidx[target], lidx[j], CH_ZWJ, ' '))
            }
        }

        val spaceSlots = ArrayList<Slot>()
        for (j in 0 until n - 1) {
            val target = j + 1
            if (morphK.contains(target)) continue
            var spaceLike = true
            for (u in lidx[j] + 1 until lidx[target]) {
                if (s[u] != ' ' && !isGlue(s[u])) { spaceLike = false; break }
            }
            if (spaceLike && lidx[target] > lidx[j] + 1) {
                spaceSlots.add(newSlot(false, lidx[target], lidx[j], ' ', ' '))
            }
        }

        val swapSlots = ArrayList<Slot>()
        for (j in 0 until n) {
            val twin = TWINS[lch[j]]
            if (twin != null) {
                val sN = newSlot(true, lidx[j], 0, ' ', ' ')
                sN.twin = twin
                swapSlots.add(sN)
            }
        }

        return if (robustOnly) morphSlots + spaceSlots + swapSlots
        else morphSlots + gapSlots + spaceSlots + swapSlots
    }

    private fun newSlot(swap: Boolean, at: Int, prev: Int, ch: Char, twin: Char): Slot {
        val sX = Slot()
        sX.swap = swap
        sX.at = at
        sX.prev = prev
        sX.ch = ch
        sX.twin = twin
        return sX
    }

    private fun primaryOfChar(c: Char): Char = if (PRIMARY.contains(c)) c else TWINS[c] ?: c

    internal fun evalSlot(s: String, slot: Slot): Boolean = when {
        slot.swap -> slot.at in s.indices && !PRIMARY.contains(s[slot.at])
        slot.ch == ' ' && slot.twin == ' ' -> betweenCount(s, slot.prev, slot.at, ' ') >= 2
        else -> betweenHas(s, slot.prev, slot.at, slot.ch)
    }

    private fun betweenHas(s: String, from: Int, to: Int, ch: Char): Boolean {
        if (from < 0 || to >= s.length || to <= from) return false
        for (i in from + 1 until to) if (s[i] == ch) return true
        return false
    }

    private fun betweenCount(s: String, from: Int, to: Int, ch: Char): Int {
        if (from < 0 || to >= s.length || to <= from) return 0
        var c = 0
        for (i in from + 1 until to) if (s[i] == ch) c++
        return c
    }

    fun writeIntBits(v: Int, width: Int, bits: BooleanArray, offset: Int) {
        for (i in 0 until width) bits[offset + i] = ((v shr (width - 1 - i)) and 1) == 1
    }

    fun readIntBits(bits: BooleanArray, offset: Int, width: Int): Int {
        var v = 0
        for (i in 0 until width) v = (v shl 1) or (if (bits[offset + i]) 1 else 0)
        return v
    }

    fun bitsOfBytes(b: ByteArray): BooleanArray {
        val bits = BooleanArray(b.size * 8)
        for (i in b.indices) {
            for (j in 0 until 8) bits[i * 8 + j] = ((b[i].toInt() shr (7 - j)) and 1) == 1
        }
        return bits
    }

    fun bytesOfBits(bits: BooleanArray): ByteArray {
        val out = ByteArray(bits.size / 8)
        for (i in out.indices) {
            var v = 0
            for (j in 0 until 8) v = (v shl 1) or (if (bits[i * 8 + j]) 1 else 0)
            out[i] = v.toByte()
        }
        return out
    }
}

/** bit-exact port of KeyedRandom (all ops on the raw 64-bit pattern, like Java). */
class KeyedRandom(seed: Long) {
    private var state: Long

    init {
        val s0 = seed xor (-0x61C8864680B583EBL)
        state = if (s0 == 0L) 0xB504F32DL else s0
    }

    fun next(): Long {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return x * 0x2545F4914F6CDD1DL
    }

    fun below(bound: Int): Int {
        if (bound <= 1) return 0
        val lim = (0x4000000000000000L / bound) * bound
        var v: Long
        do {
            v = next() and 0x3FFFFFFFFFFFFFFFL
        } while (v >= lim)
        return (v % bound).toInt()
    }
}

fun keyShuffle(count: Int, rnd: KeyedRandom): IntArray {
    val arr = IntArray(count) { it }
    for (i in count - 1 downTo 1) {
        val j = rnd.below(i + 1)
        val t = arr[i]
        arr[i] = arr[j]
        arr[j] = t
    }
    return arr
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
