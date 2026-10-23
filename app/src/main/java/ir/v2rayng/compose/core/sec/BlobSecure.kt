package ir.v2rayng.compose.core.sec

import ir.v2rayng.compose.AppCtx
import org.json.JSONObject

/**
 * Server-issued rotating keys - the app never owns a crypto key:
 *   POST /api/key -> worker derives kc (payload key) + kt (token key) fresh for
 *   every 60-minute bucket (HMAC of BLOB_ROOT over deviceId + wall time) and
 *   hands them over the TLS channel. Sessions expire at the bucket boundary;
 *   every request carries a one-shot HMAC token; every admin->app payload is
 *   JSON -> zlib -> AES-256-GCM(Kc, AAD BLBv1) inside a homoglyph stego carrier.
 * The only embedded constant is ROOT (the app-auth secret, not a decrypt key).
 */
object BlobSecure {

    private const val PREFS = "v2settings"
    private const val KEY = "blob-sec"

    /** app-auth secret (hex32) baked in the app - equal to worker secret BLOB_ROOT */
    private const val ROOT = "02943399ed750ba5128c5df37ee79ed2314c2ee32a04206fae632b3528c0ed93"

    data class Session(val kc: ByteArray, val kt: ByteArray, val exp: Long) {
        fun wrap(): JSONObject = JSONObject()
            .put("kc", BlobB64.encode(kc))
            .put("kt", BlobB64.encode(kt))
            .put("exp", exp)
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

    fun token(device: String, kt: ByteArray): String {
        var now = (System.currentTimeMillis() / 1000L).toInt()
        synchronized(this) {
            if (now <= lastTokTs) now = lastTokTs + 1
            lastTokTs = now
        }
        val ts = byteArrayOf((now shr 24).toByte(), (now shr 16).toByte(), (now shr 8).toByte(), now.toByte())
        val nonce = ByteArray(8)
        val mac = BlobStego.hmac(
            kt,
            "blb".toByteArray(Charsets.UTF_8) +
                device.toByteArray(Charsets.UTF_8) + byteArrayOf(0x00) + ts + nonce,
        )
        return BlobB64.encode(ts + nonce + mac)
    }

    /** /api/key - get fresh 60-minute keys from the worker, persist via BlobVault.
     *  Returns the newly stored session, or null when the worker refuses. */
    private fun issueKeys(base: String, body: JSONObject): Session? {
        return try {
            val req = JSONObject()
                .put("device", body.getString("device"))
                .put("model", body.optString("model", ""))
                .put("ver", body.opt("ver") ?: 0)
                .put("ts", System.currentTimeMillis() / 1000L)
                .put("connected", body.optBoolean("connected", false))
                .put("info", body.opt("info") ?: JSONObject())
                .put("tok", token(body.getString("device"), hexToBytes(ROOT)))
            val text = httpPost(base, "/api/key", req) ?: return null
            val resp = JSONObject(text)
            if (resp.optInt("v", 0) < 2 || resp.optString("enc") != "k") return null
            val kc = BlobB64.decode(resp.getString("kc")) ?: return null
            val kt = BlobB64.decode(resp.getString("kt")) ?: return null
            if (kc.size != 32 || kt.size != 32) return null
            val expSec = resp.optLong("exp", 0L)
            val expMs = if (expSec > 0) expSec * 1000L else System.currentTimeMillis() + 3600_000L
            val s = Session(kc, kt, expMs)
            save(s)
            s
        } catch (_: Exception) {
            null
        }
    }

    fun syncSecure(base: String, body: JSONObject): JSONObject? {
        var session: Session? = load()
        var issued = false
        for (attempt in 0 until 2) {
            val sNow = session
            if (sNow == null) {
                if (issued) return null
                issued = true
                // worker hands us fresh keys (device + 60-min bucket) and stores them too
                session = issueKeys(base, body) ?: return null
                continue
            }
            val text = httpPost(
                base,
                "/api/sync",
                JSONObject(body.toString()),
                "v1",
                token(body.getString("device"), sNow.kt),
            )
            if (text == null) {
                // transport failed - keep the session, let the caller fall back
                return null
            }
            val resp = JSONObject(text)
            when (resp.optString("enc")) {
                "s" -> return revealPayload(resp.getString("st"), sNow.kc)
                "c" -> return openCy(resp.getString("cy"), sNow.kc)
                "n" -> return JSONObject().put("__nc", true)
                "h" -> {
                    // keys expired/rotated server-side - clear and issue fresh
                    clear()
                    if (issued) return null
                    issued = true
                    session = issueKeys(base, body) ?: return null
                }
                "x" -> {
                    clear()
                    return null
                }
                else -> {
                    clear()
                    return null
                }
            }
        }
        return null
    }

    fun revealPayload(carrier: String, kc: ByteArray): JSONObject? {
        val text = BlobStego.reveal(carrier, kc) ?: return null
        return try { JSONObject(text) } catch (_: Exception) { null }
    }

    fun openCy(hexCy: String, kc: ByteArray): JSONObject? {
        val blob = BlobB64.decode(hexCy) ?: return null
        val plain = BlobStego.blobOpen(blob, kc) ?: return null
        val jsonU8 = BlobZip.open(plain)
        return try { JSONObject(String(jsonU8, Charsets.UTF_8)) } catch (_: Exception) { null }
    }

    fun hexToBytes(hex: String): ByteArray {
        val o = ByteArray(hex.length / 2)
        for (i in o.indices) {
            o[i] = ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
        return o
    }

    private fun httpPost(base: String, path: String, body: JSONObject, blobHeader: String? = null, btok: String? = null): String? {
        return try {
            val url = java.net.URL(base.trimEnd('/') + path)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            if (blobHeader != null) conn.setRequestProperty("x-blob", blobHeader)
            if (btok != null) conn.setRequestProperty("x-btok", btok)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }
}
