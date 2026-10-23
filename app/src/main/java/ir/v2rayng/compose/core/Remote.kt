package ir.v2rayng.compose.core

import android.os.Build
import ir.v2rayng.compose.AppCtx
import ir.v2rayng.compose.core.sec.BlobSecure
import ir.v2rayng.compose.core.sec.SecurePrefs
import org.json.JSONObject

/**
 * Cloudflare Worker (cloudflare/worker.js) backend client.
 * Set the worker URL here after `wrangler deploy`:
 *   Remote.BASE = "https://your-worker.workers.dev"
 */
object Remote {
    var BASE: String = "https://god-panel.lastofanarchy.workers.dev"

    data class ServerEntry(
        val id: String,
        val name: String,
        val address: String,
        val port: Int,
        val link: String?,
        val category: String,
        val msg: String = "",
    )

    data class PopupCfg(
        val id: Long,
        val title: String,
        val body: String,
        val btnText: String,
        val btnUrl: String,
        val btnEnabled: Boolean,
        val cancelEnabled: Boolean,
    )

    data class UpdateCfg(
        val forced: Boolean,
        val text: String,
        val btnText: String,
        val url: String,
    )

    data class NotifLine(
        val id: Long,
        val title: String,
        val body: String,
        val ts: Long,
    )

    /** Admin raw content (file/image) delivered inside the encrypted stego carrier. */
    data class DataMsg(
        val id: Long,
        val title: String,
        val name: String,
        val mime: String,
        val b64: String,
    )
    data class Result(
        val enabled: Boolean,
        val servers: List<ServerEntry>,
        val popup: PopupCfg?,
        val update: UpdateCfg?,
        val notifications: List<NotifLine>,
        val flags: Map<String, String> = emptyMap(),
        val unchanged: Boolean = false,
        val dataFile: DataMsg? = null,
    )

    /** Persisted stable device id - one unique stats entry per device. */
    @Volatile
    private var cachedDevice: String = ""

    fun deviceId(context: android.content.Context): String {
        cachedDevice.let { if (it.isNotBlank()) return it }
        synchronized(this) {
            if (cachedDevice.isNotBlank()) return cachedDevice
            var v = SecurePrefs.get("deviceId", context)
            if (v.isNullOrBlank()) {
                v = java.util.UUID.randomUUID().toString()
                SecurePrefs.set("deviceId", v, context)
            }
            cachedDevice = v
            return v
        }
    }

    private fun deviceModel(): String =
        Build.MANUFACTURER.ifBlank { "?" } + " " + Build.MODEL

    /** Extra fingerprint info the admin sees in the panel (percent-encoded, browser/runtime-safe). */
    fun deviceInfo(): JSONObject {
        val o = JSONObject()
        o.put("brand", Build.BRAND)
        o.put("device", Build.DEVICE)
        o.put("model", Build.MODEL)
        o.put("sdk", android.os.Build.VERSION.SDK_INT)
        o.put("os", android.os.Build.VERSION.RELEASE)
        return o
    }

    fun appVersion(): Long = try {
        AppCtx.app.packageManager.getPackageInfo(AppCtx.app.packageName, 0).longVersionCode
    } catch (_: Exception) {
        0L
    }

    /** Build the standard sync body. Pending acks (events, popup/notif/payload receipts)
     *  ride inside this body so they only ever cross the wire encrypted - there is no
     *  separate plaintext /api/beat or /api/ack call anymore. */
    private fun buildSyncBody(device: String, type: String, connected: Boolean): JSONObject = JSONObject()
        .put("device", device)
        .put("model", deviceModel())
        .put("ver", appVersion())
        .put("type", type)
        .put("connected", connected)
        .put("info", deviceInfo())
        .put("acks", peekAckQueue())

    /* ---- encrypted ack queue ----
     * Everything the app wants to confirm (connect/disconnect events, popup/notif
     * seen, payload received) is queued locally and delivered inside the encrypted
     * sync body. The queue is cleared only after a successful encrypted exchange;
     * the worker consumes acks idempotently, so a redelivery after a lost response
     * is harmless. */
    private const val ACK_KEY = "sec-ackq"
    private val ackMu = Any()

    /** Queue a receipt for the next encrypted sync (replaces the old plaintext calls). */
    fun queueAck(kind: String, id: Long = 0L, name: String = "") {
        try {
            synchronized(ackMu) {
                val arr = ackQueue()
                val o = JSONObject()
                o.put("kind", kind)
                if (id != 0L) o.put("id", id)
                if (name.isNotBlank()) o.put("name", name)
                arr.put(o)
                while (arr.length() > 64) arr.remove(0) // bounded - drop oldest
                SecurePrefs.set(ACK_KEY, arr.toString(), AppCtx.app)
            }
        } catch (_: Exception) {
        }
    }

    private fun ackQueue(): org.json.JSONArray = try {
        val s = SecurePrefs.get(ACK_KEY, AppCtx.app)
        if (s.isNullOrBlank()) org.json.JSONArray() else org.json.JSONArray(s)
    } catch (_: Exception) {
        org.json.JSONArray()
    }

    /** The queue WITHOUT draining - cleared only after a confirmed encrypted sync. */
    private fun peekAckQueue(): org.json.JSONArray = synchronized(ackMu) { ackQueue() }

    private fun clearAckQueue() {
        try {
            synchronized(ackMu) { SecurePrefs.set(ACK_KEY, "[]", AppCtx.app) }
        } catch (_: Exception) {
        }
    }

    /**
     * POST /api/sync - the ONLY data channel: the encrypted blob protocol
     * (server-issued rotating keys, AES-256-GCM payload hidden inside a homoglyph
     * stego carrier, one-shot HMAC token per request). There is deliberately NO
     * plaintext fallback: when the secure path fails the app keeps its local state
     * and retries on the next sync.
     */
    fun sync(device: String, type: String, connected: Boolean): Result? {
        val body = buildSyncBody(device, type, connected)
        val secure = try {
            BlobSecure.syncSecure(BASE, body)
        } catch (e: Exception) {
            try { android.util.Log.d("BlobSec", "secure path exception: " + e.javaClass.simpleName) } catch (_: Exception) {}
            null
        }
        if (secure == null) {
            try { android.util.Log.d("BlobSec", "secure path failed -> keeping local state, retry next sync") } catch (_: Exception) {}
            return null
        }
        clearAckQueue() // worker consumed the acks (idempotent, safe to re-send on loss)
        if (secure.optBoolean("__nc", false)) {
            // worker says the payload is unchanged since the last sync; apply nothing
            return Result(
                enabled = true,
                servers = emptyList(),
                popup = null,
                update = null,
                notifications = emptyList(),
                unchanged = true,
            )
        }
        return mapSyncPayload(secure)
    }

    /** Map a syncPayloadObj response (plain or decrypted) into the app model. */
    private fun mapSyncPayload(res: JSONObject): Result {
        val servers = ArrayList<ServerEntry>()
        val arr = res.optJSONArray("servers") ?: org.json.JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            servers.add(
                ServerEntry(
                    id = "rem-" + o.optString("id"),
                    name = o.optString("name", "srv"),
                    address = o.optString("address", ""),
                    port = o.optInt("port", 443),
                    link = o.optString("link", "").takeIf { it.isNotBlank() },
                    category = o.optString("category", ""),
                    msg = o.optString("msg", ""),
                ),
            )
        }
        val popup = res.optJSONObject("popup")?.let {
            PopupCfg(
                id = it.optLong("id", 0L),
                title = it.optString("title", "\u067E\u06CC\u0627\u0645"),
                body = it.optString("body", ""),
                btnText = it.optString("btnText", ""),
                btnUrl = it.optString("btnUrl", ""),
                btnEnabled = it.optBoolean("btnEnabled", false),
                cancelEnabled = it.optBoolean("cancelEnabled", true),
            )
        }
        val upd = res.optJSONObject("update")?.let {
            UpdateCfg(it.optBoolean("forced", false), it.optString("text", ""), it.optString("btnText", "Update"), it.optString("url", ""))
        }
        val notifications = ArrayList<NotifLine>()
        val narr = res.optJSONArray("notifications") ?: org.json.JSONArray()
        for (i in 0 until narr.length()) {
            val o = narr.optJSONObject(i) ?: continue
            notifications.add(NotifLine(o.optLong("id"), o.optString("title", ""), o.optString("body", ""), o.optLong("ts", 0L)))
        }
        val flags = HashMap<String, String>()
        res.optJSONObject("pflags")?.let { pf ->
            for (k in pf.keys()) flags[k] = pf.optString(k, "auto")
        }
        var dataFile: DataMsg? = null
        res.optJSONObject("file")?.let { f ->
            val b64 = f.optString("b64", "")
            if (b64.isNotBlank()) {
                dataFile = DataMsg(
                    id = f.optLong("id", 0L),
                    title = f.optString("title", ""),
                    name = f.optString("name", ""),
                    mime = f.optString("mime", ""),
                    b64 = b64,
                )
            }
        }
        return Result(res.optBoolean("enabled", true), servers, popup, upd, notifications, flags, false, dataFile)
    }

    // Note: there are intentionally no other network calls - no /api/beat, /api/ack or
    // /api/handshake. Receipts and events ride inside the encrypted sync body
    // (queueAck) and keys come from /api/key inside BlobSecure.
}
