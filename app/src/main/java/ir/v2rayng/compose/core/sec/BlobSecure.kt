package ir.v2rayng.compose.core.sec

import android.util.Log
import ir.v2rayng.compose.AppCtx
import org.json.JSONObject

/** debug tag - visible with: adb logcat -s BlobSec */
fun blog(msg: String) {
    try { Log.d("BlobSec", msg) } catch (_: Exception) {}
}

/**
 * Server-issued rotating keys - the app owns no shared secret:
 *   POST /api/key -> the handset proves itself with a signature from a NON-EXPORTABLE
 *   AndroidKeyStore EC key (BlobIdentity); the worker memorises that public key on first
 *   sight and answers with fresh RANDOM kc (payload key) + kt (token key) for the next
 *   60-minute bucket; the session dies at the bucket boundary.
 *   every request carries a one-shot HMAC token; every admin->app payload is
 *   JSON -> zlib -> AES-256-GCM(Kc, AAD BLBv1) inside a homoglyph stego carrier.
 * Nothing crypto-related is baked into the APK any more: BLOB_ROOT used to let anyone who
 * decompiled it derive any device's keys, and it is gone.
 */
object BlobSecure {

    private const val PREFS = "v2settings"
    private const val KEY = "blob-sec"

    data class Session(val kc: ByteArray, val kt: ByteArray, val exp: Long) {
        fun wrap(): JSONObject = JSONObject()
            .put("kc", BlobB64.encode(kc))
            .put("kt", BlobB64.encode(kt))
            .put("exp", exp)
    }

    /** Human-readable reason of the last secure-path failure (no URLs, log/toast safe). */
    @Volatile
    var lastReason: String? = null

    /** True only while the payload just returned came out of an AES-GCM/stego envelope that this
     *  session's key opened. Server data that was not encrypted must never be applied. */
    @Volatile
    var lastPayloadSecure: Boolean = false

    fun fail(r: String): String {
        lastReason = r
        return r
    }

    @Volatile
    private var live: Session? = null

    fun load(): Session? {
        live?.let { return it }
        return try {
            val sp = AppCtx.app.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            val sealed = sp.getString(KEY, null) ?: return null
            val rawJson = BlobVault.unseal(sealed) ?: return null
            val o = JSONObject(String(rawJson, Charsets.UTF_8))
            val s = Session(
                BlobB64.decode(o.getString("kc")) ?: return null,
                BlobB64.decode(o.getString("kt")) ?: return null,
                o.optLong("exp", 0L),
            )
            if (System.currentTimeMillis() >= s.exp || s.kt.size != 32 || s.kc.size != 32) null
            else s.also { live = it }
        } catch (_: Exception) {
            null
        }
    }

    private fun save(s: Session?) {
        live = s
        try {
            val sp = AppCtx.app.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            if (s == null) sp.edit().remove(KEY).apply()
            else sp.edit().putString(KEY, BlobVault.seal(s.wrap().toString().toByteArray(Charsets.UTF_8)) ?: return).apply()
        } catch (_: Exception) {
        }
    }

    fun clear() {
        save(null)
    }

    fun liveSession(): Boolean = load() != null

    /** one-shot token: b64( ts4BE | nonce | hmac(kt, msg) ), msg = "blb"|device|0x00|ts4BE|nonce
     *  the worker enforces a strictly-monotonic per-device timestamp, so tokens minted in the
     *  same second are nudged +1 tick to keep consecutive one-shot tokens unique. */
    @Volatile
    private var lastTokTs: Int = 0

    private fun nextTs(): Int {
        var now = (System.currentTimeMillis() / 1000L).toInt()
        synchronized(this) {
            if (now <= lastTokTs) now = lastTokTs + 1
            lastTokTs = now
        }
        return now
    }

    private fun tsBytes(ts: Int): ByteArray =
        byteArrayOf((ts shr 24).toByte(), (ts shr 16).toByte(), (ts shr 8).toByte(), ts.toByte())

    private fun newNonce(): ByteArray {
        val n = ByteArray(8)
        java.security.SecureRandom().nextBytes(n)
        return n
    }

    /** the exact bytes the worker recomputes its mac / verifies its signature over */
    private fun tokMsg(device: String, ts: ByteArray, nonce: ByteArray): ByteArray =
        "blb".toByteArray(Charsets.UTF_8) +
            device.toByteArray(Charsets.UTF_8) + byteArrayOf(0x00) + ts + nonce

    fun token(device: String, kt: ByteArray): String {
        val ts = tsBytes(nextTs())
        val nonce = newNonce()
        return BlobB64.encode(ts + nonce + BlobStego.hmac(kt, tokMsg(device, ts, nonce)))
    }

    /** What the last proof attempt looked like, so one log line says whether the keystore pair is
     *  self-consistent here or whether the worker is reading something else. */
    @Volatile
    var lastProofNote: String = ""
        private set

    /** Proof that this install owns its hardware key: the same message, signed instead of MACed.
     *  Sent only on /api/key, where the worker checks it against the public key stored for this
     *  device. null = the keystore refused to sign, so no session can be opened. */
    fun deviceProof(device: String): String? {
        val ts = tsBytes(nextTs())
        val nonce = newNonce()
        val msg = tokMsg(device, ts, nonce)
        val sig = BlobIdentity.sign(msg) ?: return null
        val pubDer = BlobB64.decode(BlobIdentity.publicKeyB64() ?: return null) ?: return null
        // verify our own signature with the bytes we export as the public key: REJECTED here means
        // this device's exported key is not the pair that signed (keystore/format), while ok here
        // plus a refusal from the worker means the two sides read the encoding differently
        val self: String = try {
            val pk = java.security.KeyFactory.getInstance("EC").generatePublic(
                java.security.spec.X509EncodedKeySpec(pubDer),
            )
            val v = java.security.Signature.getInstance("SHA256withECDSA")
            v.initVerify(pk)
            v.update(msg)
            if (v.verify(sig)) "self-verify ok" else "self-verify REJECTED"
        } catch (e: Exception) {
            "self-verify error " + e.javaClass.simpleName
        }
        val md = hex3(msg)
        lastProofNote = self + ", fp " + BlobIdentity.fingerprint() + ", msg " + md + " (" + msg.size + "B), pub " +
            pubDer.size + "B#" + hex3(pubDer) + ", sig " + sig.size + "B#" + hex3(sig)
        blog("deviceProof: " + lastProofNote)
        return BlobB64.encode(ts + nonce + sig)
    }

    /** First three bytes of the SHA-256 of these bytes, hex - enough to prove two blobs are the
     *  same without putting the key material in a log. */
    private fun hex3(b: ByteArray): String = try {
        java.security.MessageDigest.getInstance("SHA-256").digest(b).take(3)
            .joinToString("") { String.format("%02x", it) }
    } catch (_: Exception) {
        "?"
    }

    /** /api/key - get fresh 60-minute keys from the worker, persist via BlobVault.
     *  Returns the newly stored session, or null when the worker refuses. */
    private fun issueKeys(base: String, body: JSONObject): Session? {
        return try {
            val device = body.getString("device")
            // proof FIRST: sign() replaces an unreadable keystore key and retries, and a key that
            // was just replaced has a different public half - reading pub before that happened
            // sends the dead key with a signature made by the new one, which the worker can only
            // report as "signature does not match"
            val proof = deviceProof(device) ?: run {
                blog("issueKeys: keystore signing failed")
                fail("device identity signature failed (keystore)")
                return null
            }
            val pub = BlobIdentity.publicKeyB64() ?: run {
                blog("issueKeys: this device has no keystore identity key")
                fail("no device identity key (" + (BlobIdentity.note ?: "keystore unavailable") + ")")
                return null
            }
            val req = JSONObject()
                .put("device", device)
                .put("model", body.optString("model", ""))
                .put("ver", body.opt("ver") ?: 0)
                .put("ts", System.currentTimeMillis() / 1000L)
                .put("connected", body.optBoolean("connected", false))
                .put("info", body.opt("info") ?: JSONObject())
                .put("alg", "ec")
                .put("pub", pub)
                .put("sig", proof)
                .put("att", JSONObject(BlobIdentity.attestation()))
            val r = httpPost(base, "/api/key", req)
            if (r.body == null) {
                blog("issueKeys: /api/key got nothing (status " + r.status + ")")
                fail(if (r.status == 0) "key request: no answer from the server (network)" else "key request: HTTP " + r.status + " with no body")
                return null
            }
            val resp = try {
                JSONObject(r.body)
            } catch (_: Exception) {
                blog("issueKeys: /api/key replied HTTP " + r.status + " as: " + r.body.take(60))
                fail("key request: server replied HTTP " + r.status + " and not json")
                return null
            }
            if (resp.optInt("v", 0) < 2 || resp.optString("enc") != "k") {
                val why = refused("session keys", r, resp) + (if (lastProofNote.isNotBlank()) " [" + lastProofNote + "]" else "")
                blog("issueKeys: " + why)
                fail(why)
                return null
            }
            val kc = BlobB64.decode(resp.getString("kc")) ?: run { blog("issueKeys: bad b64 kc"); fail("session keys: invalid response (protocol)"); return null }
            val kt = BlobB64.decode(resp.getString("kt")) ?: run { blog("issueKeys: bad b64 kt"); fail("session keys: invalid response (protocol)"); return null }
            if (kc.size != 32 || kt.size != 32) { blog("issueKeys: bad key sizes " + kc.size + "/" + kt.size); fail("session keys: invalid response (protocol)"); return null }
            val expSec = resp.optLong("exp", 0L)
            val expMs = if (expSec > 0) expSec * 1000L else System.currentTimeMillis() + 3600_000L
            val s = Session(kc, kt, expMs)
            save(s)
            blog("issueKeys: OK, keys live until epoch-ms " + expMs)
            s
        } catch (e: Exception) {
            blog("issueKeys: exception " + e.javaClass.simpleName)
            fail("key request failed (network)")
            null
        }
    }

    fun syncSecure(base: String, body: JSONObject): JSONObject? {
        lastReason = null
        lastPayloadSecure = false
        var session: Session? = load()
        var issued = false
        for (attempt in 0 until 2) {
            val sNow = session
            if (sNow == null) {
                if (issued) return null
                issued = true
                blog("no session -> issuing keys from worker")
                // worker hands us fresh keys (device + 60-min bucket) and stores them too
                session = issueKeys(base, body) ?: return null
                continue
            }
            val r = httpPost(
                base,
                "/api/sync",
                JSONObject(body.toString()),
                "v1",
                token(body.getString("device"), sNow.kt),
            )
            if (r.body == null) {
                blog("sync: no answer (status " + r.status + ")")
                // transport failed - keep the session, let the caller retry on the next sync
                fail(if (r.status == 0) "server unreachable (network or timeout)" else "server answered HTTP " + r.status + " with no body")
                return null
            }
            val wire = r.body ?: return null
            val resp = try {
                JSONObject(wire)
            } catch (_: Exception) {
                blog("sync: HTTP " + r.status + " body is not json: " + wire.take(60))
                fail(if (r.status in 200..299) "unexpected server response (protocol)" else "server answered HTTP " + r.status + " and not json - old worker?")
                return null
            }
            when (val enc = resp.optString("enc")) {
                "s", "c" -> {
                    val opened = if (enc == "s") revealPayload(resp.getString("st"), sNow.kc) else openCy(resp.getString("cy"), sNow.kc)
                    if (opened != null) {
                        lastPayloadSecure = true
                        return opened
                    }
                    val why = BlobStego.lastWhy ?: "payload unreadable"
                    blog("enc=" + enc + " open failed: " + why)
                    // the token was accepted, so the worker holds keys this session cannot open -
                    // drop it and re-issue once instead of failing every sync from now on
                    if (issued) {
                        fail("payload verification failed ($why)")
                        return null
                    }
                    issued = true
                    clear()
                    session = issueKeys(base, body) ?: return null
                }
                "n" -> {
                    // "unchanged" carries no server data, and it only arrives after this session's
                    // one-shot token was accepted, so it counts as a secure answer
                    lastPayloadSecure = true
                    return JSONObject().put("__nc", true)
                }
                "h" -> {
                    // keys expired/rotated server-side - clear and issue fresh
                    blog("enc=h (expired/rotated) -> re-issuing")
                    clear()
                    if (issued) { fail("session rotated twice in a row"); return null }
                    issued = true
                    session = issueKeys(base, body) ?: return null
                }
                "x" -> {
                    blog("enc=x (auth rejected) -> clearing")
                    fail(refused("request", r, resp))
                    clear()
                    return null
                }
                else -> {
                    blog("unexpected enc=" + enc + " -> clearing (worker has no blob layer?)")
                    fail(refused("server reply", r, resp))
                    clear()
                    return null
                }
            }
        }
        return null
    }

    fun revealPayload(carrier: String, kc: ByteArray): JSONObject? {
        val text = BlobStego.reveal(carrier, kc) ?: return null
        return try {
            JSONObject(text)
        } catch (_: Exception) {
            BlobStego.lastWhy = "stego: plaintext is not json (" + text.length + " chars, head=" + preview(text) + ")"
            blog("revealPayload: json parse failed, " + text.length + " chars")
            null
        }
    }

    fun openCy(hexCy: String, kc: ByteArray): JSONObject? {
        val blob = BlobB64.decode(hexCy) ?: return cyWhy("cy: not base64")
        val plain = BlobStego.blobOpen(blob, kc) ?: return cyWhy("cy: GCM tag fail (wrong key)")
        // mode byte says which envelope scheme the worker used: 0 raw, 2 zlib, 1 = raw deflate
        val mode = if (plain.isEmpty()) -1 else plain[0].toInt() and 0xFF
        val jsonU8 = BlobZip.open(plain)
        return try {
            JSONObject(String(jsonU8, Charsets.UTF_8))
        } catch (_: Exception) {
            cyWhy(
                "cy: not json - sealed " + plain.size + "B mode=" + mode + " -> " + jsonU8.size +
                    "B head=" + preview(String(jsonU8, Charsets.ISO_8859_1))
            )
        }
    }

    /** First bytes as text + hex, to tell envelope schemes apart without logging the payload. */
    private fun preview(s: String): String {
        val n = minOf(8, s.length)
        val hx = StringBuilder()
        val asc = StringBuilder()
        for (i in 0 until n) {
            val v = s[i].code
            hx.append(String.format("%02x ", v))
            asc.append(if (v in 33..126) s[i] else '.')
        }
        return "\"" + asc + "\"][" + hx.toString().trim() + "]"
    }

    private fun cyWhy(w: String): JSONObject? {
        blog("openCy: " + w)
        BlobStego.lastWhy = w
        return null
    }

    /** A POST and whatever came back. An HTTP error still carries an answer, and reading that
     *  answer is the difference between "this phone has no network" and "the worker in the cloud
     *  is an old one" - which both used to be reported as "server unreachable". */
    data class Http(val status: Int, val body: String?)

    private fun httpPost(base: String, path: String, body: JSONObject, blobHeader: String? = null, btok: String? = null): Http {
        return try {
            val url = java.net.URL(base.trimEnd('/') + path)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            // stego carriers are large (up to a few MB when the admin delivers a file) -
            // a short read timeout here looks exactly like "failed to connect to server"
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            if (blobHeader != null) conn.setRequestProperty("x-blob", blobHeader)
            if (btok != null) conn.setRequestProperty("x-btok", btok)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }
            if (code !in 200..299) blog("http " + path + " -> " + code + " " + (text?.take(90) ?: "no body"))
            Http(code, text)
        } catch (e: Exception) {
            blog("http " + path + " -> " + e.javaClass.simpleName)
            Http(0, null)
        }
    }

    /** Turn a refused response into one readable sentence: status, the enc code the worker chose
     *  and the reason the worker itself gave (which is protocol text, never server config). */
    private fun refused(what: String, r: Http, resp: JSONObject): String {
        val enc = resp.optString("enc", "")
        val why = resp.optString("why", "")
        val tail = when {
            why.isNotBlank() -> ": " + why
            enc == "x" -> " (auth refused - worker has a different key for this device?)"
            resp.optInt("v", 0) < 2 -> " - the worker in the cloud is an old build (deploy it)"
            else -> ""
        }
        return what + " refused (HTTP " + r.status + (if (enc.isNotBlank()) ", enc " + enc else "") + ")" + tail
    }
}
