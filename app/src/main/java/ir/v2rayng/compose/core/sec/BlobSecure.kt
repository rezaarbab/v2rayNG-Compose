package ir.v2rayng.compose.core.sec

import android.util.Base64
import ir.v2rayng.compose.AppCtx
import org.json.JSONObject

/**
 * Option C - the strongest wiring:
 *   Handshake = ECDH P-256 (app generates an ephemeral secret; the STATIC public
 *   key of the worker is baked into the app so no trusted key ever ships).
 *   Session keys = HKDF(shared secret, "blobv1" + deviceId) hardware-sealed
 *   through BlobVault. Every request carries a one-shot HMAC token; every
 *   admin->app payload is JSON -> zlib -> AES-256-GCM inside a stego carrier.
 */
object BlobSecure {

    private const val PREFS = "v2settings"
    private const val KEY = "blob-sec"

    /** root secret (hex32) baked in the app - equal to worker secret BLOB_ROOT */
    private const val ROOT = "02943399ed750ba5128c5df37ee79ed2314c2ee32a04206fae632b3528c0ed93"

    /** worker's static P-256 public key (SPKI, base64url) */
    private const val STATIC_PUB =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE2Ipq7zpde4tZDch7pSEpfggaMj5NgJ23PyriGZe09a01ieqnE4n1STm3ih3U9cm63mejLBD3gUJEdoYsvRZG_w"

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

    private fun leftPad32(b: ByteArray): ByteArray = if (b.size >= 32) b.copyOf() else {
        val o = ByteArray(32)
        b.copyInto(o, 32 - b.size)
        o
    }

    /** HKDF-SHA256(salt=32 zero bytes) -> Kc(32) + Kt(32) - identical to the worker side */
    private fun derive(raw: ByteArray, device: String): Pair<ByteArray, ByteArray> {
        val salt = ByteArray(32)
        val prk = BlobStego.hmac(salt, raw)
        val info = "blobv1$device".toByteArray(Charsets.UTF_8)
        val hmac = javax.crypto.Mac.getInstance("HmacSHA256")
        hmac.init(javax.crypto.spec.SecretKeySpec(prk, "HmacSHA256"))
        val out = java.io.ByteArrayOutputStream()
        var prev = ByteArray(0)
        var counter = 1
        while (out.size() < 64) {
            prev = hmac.doFinal(prev + info + byteArrayOf(counter.toByte()))
            out.write(prev)
            counter++
        }
        val all = out.toByteArray()
        return all.copyOfRange(0, 32) to all.copyOfRange(32, 64)
    }

    fun handshake(base: String, body: JSONObject): JSONObject? {
        return try {
            val kgp = java.security.KeyPairGenerator.getInstance("EC")
            kgp.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
            val kp = kgp.generateKeyPair()
            val ka = javax.crypto.KeyAgreement.getInstance("ECDH")
            ka.init(kp.getPrivate())
            val staticPub = java.security.KeyFactory.getInstance("EC")
                .generatePublic(java.security.spec.X509EncodedKeySpec(urllib64Decode(STATIC_PUB)))
            ka.doPhase(staticPub, true)
            val (kc, kt) = derive(leftPad32(ka.generateSecret()), body.getString("device"))
            val rootB = hexToBytes(ROOT)
            val req = JSONObject()
                .put("device", body.getString("device"))
                .put("model", body.optString("model", ""))
                .put("ver", body.opt("ver") ?: 0)
                .put("ts", System.currentTimeMillis() / 1000L)
                .put("eph", BlobB64.encode(kp.getPublic().encoded))
                .put("connected", body.optBoolean("connected", false))
                .put("info", body.opt("info") ?: JSONObject())
                .put("tok", token(body.getString("device"), rootB))
            val text = httpPost(base, "/api/handshake", req) ?: return null
            val resp = JSONObject(text)
            if (resp.optInt("v", 0) < 2) return null
            // persist the session (worker returns exp in seconds; default 30 days)
            val expMs = resp.optLong("exp", 0L) * 1000L
            save(Session(kc, kt, if (expMs > System.currentTimeMillis()) expMs else System.currentTimeMillis() + 30L * 86400_000L))
            when (resp.optString("enc")) {
                "s" -> revealPayload(resp.getString("st"), kc)
                "c" -> openCy(resp.getString("cy"), kc)
                "n" -> JSONObject().put("__nc", true)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun syncSecure(base: String, body: JSONObject): JSONObject? {
        var session: Session? = load()
        for (attempt in 0 until 2) {
            val sNow = session
            if (sNow == null) {
                // no usable session - handshake also stores the keys and returns the payload
                return handshake(base, body)
            }
            val text = httpPost(
                base,
                "/api/sync",
                JSONObject(body.toString()),
                "v1",
                token(body.getString("device"), sNow.kt),
            )
            if (text == null) {
                // transport failed - keep the session and let the loop retry/end
                session = null
                continue
            }
            val resp = JSONObject(text)
            when (resp.optString("enc")) {
                "s" -> return revealPayload(resp.getString("st"), sNow.kc)
                "c" -> return openCy(resp.getString("cy"), sNow.kc)
                "n" -> return JSONObject().put("__nc", true)
                "h" -> {
                    // session expired/rotated server-side - clear keys; next attempt handshakes
                    clear()
                    session = null
                    continue
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
        return handshake(base, body)
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

    private fun urllib64Decode(b64: String): ByteArray {
        val d = b64.replace('-', '+').replace('_', '/')
        return Base64.decode(d + "=".repeat((4 - d.length % 4) % 4), Base64.DEFAULT)
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
