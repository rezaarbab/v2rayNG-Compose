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

    /** The routing page is the admin's own HTML: the app renders it, and shows nothing at all when
     *  the admin has not written anything. */
    data class RoutingCfg(val html: String)

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
        val routing: RoutingCfg? = null,
    )

    /** Persisted stable device id - one unique stats entry per device. */
    @Volatile
    private var cachedDevice: String = ""

    /**
     * Stable device key. ANDROID_ID is per handset+signing-key and survives an uninstall, so one
     * phone stays one row in the panel; the old random uuid was destroyed together with the
     * keystore key, which is why a single user showed up dozens of times.
     */
    fun deviceId(context: android.content.Context): String {
        cachedDevice.let { if (it.isNotBlank()) return it }
        synchronized(this) {
            if (cachedDevice.isNotBlank()) return cachedDevice
            val hardware = try {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID,
                )
            } catch (_: Exception) {
                null
            }
            val v = if (!hardware.isNullOrBlank() && hardware != "9774d56d682e549c") {
                hardware.lowercase()
            } else {
                // no usable platform id (emulator image, rooted ROM): fall back to a stored uuid
                var s = SecurePrefs.get("deviceId", context)
                if (s.isNullOrBlank()) {
                    s = java.util.UUID.randomUUID().toString()
                    SecurePrefs.set("deviceId", s, context)
                }
                s
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
        fun add(k: String, v: Any?) {
            val s = v?.toString() ?: return
            if (s.isBlank()) return
            try { o.put(k, s) } catch (_: Exception) {}
        }
        add("brand", Build.BRAND)
        add("model", Build.MODEL)
        add("device", Build.DEVICE)
        add("manufacturer", Build.MANUFACTURER)
        add("product", Build.PRODUCT)
        add("board", Build.BOARD)
        add("hardware", Build.HARDWARE)
        add("bootloader", Build.BOOTLOADER)
        add("display", Build.DISPLAY)
        // both fields are newer than minSdk 26, so they are read behind an SDK check
        add("incremental", if (Build.VERSION.SDK_INT >= 34) Build.VERSION.INCREMENTAL else "")
        add("soc", if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "")
        add("tags", Build.TAGS)
        add("abi", Build.SUPPORTED_ABIS.joinToString(","))
        add("cores", Runtime.getRuntime().availableProcessors())
        add("fingerprint", Build.FINGERPRINT.take(120))
        add("sec_patch", Build.VERSION.SECURITY_PATCH)
        add("sdk", Build.VERSION.SDK_INT)
        add("os", Build.VERSION.RELEASE)
        // survives a reinstall and is tied to the signing certificate, so it identifies the
        // handset itself rather than this particular install of the app
        add("android_id", try {
            android.provider.Settings.Secure.getString(
                AppCtx.app.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            )
        } catch (_: Exception) {
            null
        })
        val pi = try {
            AppCtx.app.packageManager.getPackageInfo(AppCtx.app.packageName, 0)
        } catch (_: Exception) {
            null
        }
        add("app_ver", pi?.versionName ?: "?")
        // lastUpdateTime is when this APK was installed/updated - the closest thing to a build stamp
        add("installed", pi?.lastUpdateTime ?: 0L)
        add("inst_first", pi?.firstInstallTime ?: 0L)
        add("target_sdk", AppCtx.app.applicationInfo?.targetSdkVersion)
        add("installer", installer())
        add("sign", sigHash())
        add("ram_mb", try {
            val am = AppCtx.app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            am?.getMemoryInfo(mi)
            if (mi.totalMem > 0) (mi.totalMem / 1048576L).toString() else null
        } catch (_: Exception) {
            null
        })
        add("disk_gb", try {
            val st = android.os.StatFs(android.os.Environment.getDataDirectory().absolutePath)
            (st.blockCountLong * st.blockSizeLong / 1073741824L).toString()
        } catch (_: Exception) {
            null
        })
        add("screen", try {
            val dm = AppCtx.app.resources.displayMetrics
            dm.widthPixels.toString() + "x" + dm.heightPixels + "@" + dm.densityDpi
        } catch (_: Exception) {
            null
        })
        add("battery", batteryBrief())
        add("sim", simBrief())
        add("adb", globalFlag("adb_enabled"))
        add("dev_opts", globalFlag("development_settings_enabled"))
        add("uptime_min", (android.os.SystemClock.elapsedRealtime() / 60000L).toString())
        add("locale", java.util.Locale.getDefault().toString() + "/" + java.util.TimeZone.getDefault().id)
        return o
    }

    /** Which store put the APK on the handset - sideload shows as null/third party. */
    private fun installer(): String? = try {
        val pm = AppCtx.app.packageManager
        val pkg = AppCtx.app.packageName
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            pm.getInstallSourceInfo(pkg).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkg)
        }
    } catch (_: Exception) {
        null
    }

    private fun globalFlag(key: String): String = try {
        android.provider.Settings.Global.getInt(AppCtx.app.contentResolver, key, -1).toString()
    } catch (_: Exception) {
        ""
    }

    /** Sticky battery broadcast: percent plus whether a charger is attached (a VM usually reports
     *  a fixed 100% that is always charging). */
    private fun batteryBrief(): String = try {
        val f = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        // the flags argument is required on targetSdk 34+, and this overload exists since API 26
        val i = AppCtx.app.registerReceiver(null, f, android.content.Context.RECEIVER_NOT_EXPORTED)
        val lvl = i?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val plug = i?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        if (lvl < 0 || scale <= 0) "" else (lvl * 100 / scale).toString() + "%" + (if (plug > 0) "+chg" else "")
    } catch (_: Exception) {
        ""
    }

    private fun simBrief(): String = try {
        val tm = AppCtx.app.getSystemService(android.content.Context.TELEPHONY_SERVICE)
            as? android.telephony.TelephonyManager
        when (tm?.simState ?: -1) {
            android.telephony.TelephonyManager.SIM_STATE_READY -> "ready"
            android.telephony.TelephonyManager.SIM_STATE_ABSENT -> "absent"
            -1 -> ""
            else -> "other"
        }
    } catch (_: Exception) {
        ""
    }

    /** First 16 hex of the APK signing certificate - lets the admin see the build is ours. */
    private fun sigHash(): String = try {
        val pm = AppCtx.app.packageManager
        val pkg = AppCtx.app.packageName
        val sigs: Array<android.content.pm.Signature>? =
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                val si = info.signingInfo
                if (si == null) null else if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNATURES).signatures
            }
        val raw = sigs?.firstOrNull()?.toByteArray() ?: return ""
        val d = java.security.MessageDigest.getInstance("SHA-256").digest(raw)
        StringBuilder().also { sb -> d.take(8).forEach { b -> sb.append(String.format("%02x", b)) } }.toString()
    } catch (_: Exception) {
        ""
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

    /** The queue in memory: reading or writing it through the keystore costs ~200ms per call,
     *  and the popup/notif buttons used to pay that on the UI thread (see APP_SCOUT_HANG). */
    private var ackCache: org.json.JSONArray? = null

    private fun ackQueueLocked(): org.json.JSONArray {
        ackCache?.let { return it }
        val loaded = try {
            val s = SecurePrefs.get(ACK_KEY, AppCtx.app)
            if (s.isNullOrBlank()) org.json.JSONArray() else org.json.JSONArray(s)
        } catch (_: Exception) {
            org.json.JSONArray()
        }
        ackCache = loaded
        return loaded
    }

    /** Queue a receipt for the next encrypted sync (replaces the old plaintext calls). */
    fun queueAck(kind: String, id: Long = 0L, name: String = "") {
        try {
            val snapshot = synchronized(ackMu) {
                val arr = ackQueueLocked()
                val o = JSONObject()
                o.put("kind", kind)
                if (id != 0L) o.put("id", id)
                if (name.isNotBlank()) o.put("name", name)
                arr.put(o)
                while (arr.length() > 64) arr.remove(0) // bounded - drop oldest
                arr.toString()
            }
            Persist.write { SecurePrefs.set(ACK_KEY, snapshot, AppCtx.app) }
        } catch (_: Exception) {
        }
    }

    /** The queue WITHOUT draining - cleared only after a confirmed encrypted sync. */
    private fun peekAckQueue(): org.json.JSONArray =
        synchronized(ackMu) { org.json.JSONArray(ackQueueLocked().toString()) }

    private fun clearAckQueue() {
        try {
            synchronized(ackMu) { ackCache = org.json.JSONArray() }
            Persist.write { SecurePrefs.set(ACK_KEY, "[]", AppCtx.app) }
        } catch (_: Exception) {
        }
    }

    /** Reason of the last failed sync (no URLs - safe for the in-app log). */
    @Volatile
    var lastFail: String? = null

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
            BlobSecure.fail("request failed (network)")
            null
        }
        if (secure == null) {
            lastFail = BlobSecure.lastReason ?: "server unreachable (network)"
            try { android.util.Log.d("BlobSec", "secure path failed -> keeping local state, retry next sync") } catch (_: Exception) {}
            return null
        }
        lastFail = null
        if (!BlobSecure.lastPayloadSecure) {
            // never apply admin/server data that was not opened with this session's key
            lastFail = "rejected: payload did not arrive through the encrypted channel"
            try { android.util.Log.d("BlobSec", "unencrypted payload rejected") } catch (_: Exception) {}
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

    private fun parseRouting(res: JSONObject): RoutingCfg? {
        val html = res.optJSONObject("routing")?.optString("html", "").orEmpty()
        return if (html.isBlank()) null else RoutingCfg(html)
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
        return Result(res.optBoolean("enabled", true), servers, popup, upd, notifications, flags, false, dataFile, parseRouting(res))
    }

    // Note: there are intentionally no other network calls - no /api/beat, /api/ack or
    // /api/handshake. Receipts and events ride inside the encrypted sync body
    // (queueAck) and keys come from /api/key inside BlobSecure.
}
