package ir.v2rayng.compose.core

import android.os.Build
import ir.v2rayng.compose.AppCtx
import ir.v2rayng.compose.core.sec.BlobSecure
import org.json.JSONObject
import kotlin.concurrent.thread

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
    data class Result(
        val enabled: Boolean,
        val servers: List<ServerEntry>,
        val popup: PopupCfg?,
        val update: UpdateCfg?,
        val notifications: List<NotifLine>,
        val flags: Map<String, String> = emptyMap(),
        val unchanged: Boolean = false,
    )

    /** Persisted stable device id - one unique stats entry per device. */
    @Volatile
    private var cachedDevice: String = ""

    fun deviceId(context: android.content.Context): String {
        cachedDevice.let { if (it.isNotBlank()) return it }
        synchronized(this) {
            if (cachedDevice.isNotBlank()) return cachedDevice
            val sp = context.getSharedPreferences("v2settings", android.content.Context.MODE_PRIVATE)
            var v = sp.getString("deviceId", null)
            if (v.isNullOrBlank()) {
                v = java.util.UUID.randomUUID().toString()
                sp.edit().putString("deviceId", v).apply()
            }
            cachedDevice = v
            return v
        }
    }

    private fun httpPost(path: String, body: JSONObject): JSONObject? {
        if (BASE.isBlank()) return null
        return try {
            val url = java.net.URL(BASE.trimEnd('/') + path)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }.let {
                JSONObject(it)
            }
        } catch (_: Exception) {
            null
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

    /** Build the standard sync body (identical for the plain and encrypted protocols). */
    private fun buildSyncBody(device: String, type: String, connected: Boolean): JSONObject = JSONObject()
        .put("device", device)
        .put("model", deviceModel())
        .put("ver", appVersion())
        .put("type", type)
        .put("connected", connected)
        .put("info", deviceInfo())

    /**
     * POST /api/sync - strongest mode first: the encrypted blob protocol (ECDH session,
     * AES-256-GCM payload hidden inside a homoglyph stego carrier). Graceful fallback to
     * the old plaintext protocol when the worker has no BLOB secrets or transport fails.
     */
    fun sync(device: String, type: String, connected: Boolean): Result? {
        val body = buildSyncBody(device, type, connected)
        val secure = try { BlobSecure.syncSecure(BASE, body) } catch (_: Exception) { null }
        if (secure != null) {
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
        val plain = plainSync(body) ?: return null
        return mapSyncPayload(plain)
    }

    private fun plainSync(body: JSONObject): JSONObject? {
        if (BASE.isBlank()) return null
        return try {
            val url = java.net.URL(BASE.trimEnd('/') + "/api/sync")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        }
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
        return Result(res.optBoolean("enabled", true), servers, popup, upd, notifications, flags)
    }

    fun beat(device: String, connected: Boolean) {
        thread(name = "remote-beat") {
            try {
                httpPost(
                    "/api/beat",
                    JSONObject().put("device", device).put("connected", connected),
                )
            } catch (_: Exception) {
            }
        }
    }

    /** Send a connect/disconnect stat event. */
    fun event(device: String, name: String) {
        thread(name = "remote-event") {
            try {
                httpPost("/api/ack", JSONObject().put("device", device).put("kind", "event").put("name", name))
            } catch (_: Exception) {
            }
        }
    }

    /** Declare this device has seen a popup / notification (worker counts shows once). */
    fun ack(device: String, kind: String, id: Long) {
        thread(name = "remote-ack") {
            try {
                httpPost("/api/ack", JSONObject().put("device", device).put("kind", kind).put("id", id))
            } catch (_: Exception) {
            }
        }
    }
}
