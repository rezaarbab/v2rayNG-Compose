package ir.v2rayng.compose.data

import android.content.Context
import ir.v2rayng.compose.core.sec.SecurePrefs
import org.json.JSONArray
import org.json.JSONObject

data class StickerFx(
    val alpha: Float = 1f,
    val blur: Float = 0f,
    val scale: Float = 1f,
)

fun StickerFx.isDefault(): Boolean = alpha >= 0.995f && blur <= 0.05f && scale in 0.995f..1.005f

/**
 * Slots packed as "alpha,blur,scale" triplets joined by ";" (one triplet per slot; single-slot
 * groups hold exactly one).
 */
fun packFx(l: StickerFx): String = l.let { "%.2f,%.1f,%.2f".format(it.alpha, it.blur, it.scale) }

data class Settings(
    val dark: Boolean = true,
    val accent: Int = 0,
    val customAccent: Long = -1L,
    val stickerStyle: Int = 0,
    val mainPack: Int = -1,
    val mainFx: String = "",
    val wallpaperStyle: Int = 0,
    val wallpaperIndex: Int = -1,
    val wallpaperColor: Long = -1L,
    val wallpaperSize: Int = 0,
    val wallFx: String = "",
    val mainColor: Long = -1L,
    val mainSize: Int = 0,
    val mainIndex: Int = -1,
    val followFinger: Boolean = true,
    val animate: Boolean = true,
    val motionGlobal: Boolean = true,
    val stateMotion: Boolean = true,
    val showNotification: Boolean = true,
    val notifSticker: Boolean = true,
    val notifColor: Long = -1L,
    val flagAvatars: Boolean = true,
    val connectFlagDancer: Boolean = true,
    val flagStretch: Float = 0.66f,
    val flagRound: Float = 0.45f,
    val speed: Float = 1f,
    val bgSpeed: Float = 1f,
    val navStickers: Boolean = true,
    val autoFlag: Boolean = true,
    val navOrder: String = "0,1,2,3,4",
    val navIcons: String = "0,1,2,3,4",
    val upIcon: Int = 5,
    val downIcon: Int = 6,
    val upPack: Int = -1,
    val downPack: Int = -1,
    val navPacks: String = "-1,-1,-1,-1,-1",
    val routePacks: String = "-1,-1,-1",
    val upColor: Long = -1L,
    val downColor: Long = -1L,
    val upSpeed: Float = 1f,
    val downSpeed: Float = 1f,
    val navColors: String = "-1,-1,-1,-1,-1",
    val routeColors: String = "-1,-1,-1",
    val navSpeeds: String = "1,1,1,1,1",
    val routeSpeeds: String = "1,1,1",
    val routeIcons: String = "0,1,2",
    val upFx: String = "",
    val downFx: String = "",
    val navFx: String = "",
    val routeFx: String = "",
    val antiFilter: Boolean = false,
    val antiFilterApps: String = "",
    val homeOrder: String = "0,1,2,3,4",
    val pingOnStart: Boolean = true,
    val autoPingNew: Boolean = true,
    val flagOnStart: Boolean = true,
    val autoFlagNew: Boolean = true,
    val bubbleOn: Boolean = true,
    val bubbleColor: Long = -1L,
)

fun parseList(s: String, def: List<Int>): List<Int> {
    return s.split(",").mapNotNull { it.trim().toIntOrNull() }.ifEmpty { def }
}

fun packList(l: List<Int>): String = l.joinToString(",")

fun parseLongList(s: String, def: List<Long>): List<Long> {
    return s.split(",").mapNotNull { it.trim().toLongOrNull() }.ifEmpty { def }
}

fun packLongList(l: List<Long>): String = l.joinToString(",")

fun parseFloatList(s: String, def: List<Float>): List<Float> {
    return s.split(",").mapNotNull { it.trim().toFloatOrNull() }.ifEmpty { def }
}

fun packFloatList(l: List<Float>): String = l.joinToString(",")

fun packStickerFx(alpha: Float, blur: Float, scale: Float): String = packFx(StickerFx(alpha, blur, scale))

fun parseFxPack(s: String, def: StickerFx = StickerFx()): StickerFx {
    val seg = s.trim().split(",").mapNotNull { it.trim().toFloatOrNull() }
    return StickerFx(
        alpha = seg.getOrNull(0)?.coerceIn(0.05f, 1f) ?: def.alpha,
        blur = seg.getOrNull(1)?.coerceIn(0f, 40f) ?: def.blur,
        scale = seg.getOrNull(2)?.coerceIn(0.5f, 2.5f) ?: def.scale,
    )
}

class SettingsStore(context: Context) {
    private val ctx: Context = context.applicationContext

    companion object {
        /** Each value used to be sealed on its own, so a cold load() cost 58 StrongBox round trips
         *  (~200ms each on the phone in the APP_SCOUT traces) and the app sat on a black screen for
         *  about a minute after every restart. The whole object is now one sealed blob: one unseal. */
        private const val BLOB_KEY = "sec-all"
        private const val UI_PREFS = "v2ui"

        @Volatile
        private var cache: Settings? = null

        @Volatile
        private var blobJson: String? = null

        /** Set when a stored blob exists but cannot be opened this session - nothing may be
         *  written over it, or the real settings would be lost permanently. */
        @Volatile
        private var blobUnreadable = false

        /** Same rule for the server list: a stored value that will not open must not be replaced. */
        @Volatile
        private var serversUnreadable = false

        /** Never blocks: null until someone has actually loaded the settings once. */
        fun peek(): Settings? = cache
    }

    fun load(): Settings = cache ?: readBlob().also { cache = it; if (!blobUnreadable) mirror(it) }

    /** The one value needed before the first frame. Reading it through SecurePrefs would cost a
     *  StrongBox unseal inside onCreate, which is exactly what made the app start slowly. */
    fun darkFast(): Boolean = try {
        ctx.getSharedPreferences(UI_PREFS, android.content.Context.MODE_PRIVATE).getBoolean("dark", true)
    } catch (_: Exception) {
        dark()
    }

    /** The palette the very first frame must paint with. These three values are mirrored in
     *  plaintext for exactly this reason - waiting for the sealed blob is what showed the default
     *  theme for a second or two on every cold start. */
    fun themeSeed(): Settings = try {
        val p = ctx.getSharedPreferences(UI_PREFS, android.content.Context.MODE_PRIVATE)
        Settings(
            dark = p.getBoolean("dark", true),
            accent = p.getInt("accent", 0),
            customAccent = p.getLong("customAccent", -1L),
        )
    } catch (_: Exception) {
        Settings(dark = darkFast())
    }

    fun dark(): Boolean {
        val raw = blobRaw()
        if (!raw.isNullOrBlank()) {
            try { return JSONObject(raw).optString("dark", "true").toBoolean() } catch (_: Exception) {}
        }
        return SecurePrefs.getBoolean("dark", true, ctx)
    }

    private fun blobRaw(): String? = blobJson ?: SecurePrefs.get(BLOB_KEY, ctx).also { blobJson = it }

    private fun readBlob(): Settings {
        val sealed = blobRaw()?.takeIf { it.isNotBlank() }
        if (sealed != null) {
            try {
                val j = JSONObject(sealed)
                return fieldsFrom { k -> j.optString(k, "").takeIf { v -> v.isNotEmpty() } }
            } catch (_: Exception) {
                // The sealed value is there but this session cannot open it. Handing back defaults
                // is survivable; writing those defaults over the blob would erase the user's
                // settings for good, which is exactly the "everything reset" report.
                blobUnreadable = true
                return Settings(dark = darkFast())
            }
        }
        // first start after the per-key layout: read the old keys once, then fold them into the blob
        return fieldsFrom { k -> SecurePrefs.get(k, ctx) }.also { save(it) }
    }

    private fun fieldsFrom(src: (String) -> String?): Settings {
        fun b(k: String, d: Boolean) = src(k)?.toBoolean() ?: d
        fun i(k: String, d: Int) = src(k)?.toIntOrNull() ?: d
        fun l(k: String, d: Long) = src(k)?.toLongOrNull() ?: d
        fun f(k: String, d: Float) = src(k)?.toFloatOrNull() ?: d
        fun t(k: String, d: String) = src(k) ?: d
        return Settings(
            dark = b("dark", true),
            accent = i("accent", 0),
            customAccent = l("customAccent", -1L),
            stickerStyle = i("style", 0),
            mainPack = i("mainPack", -1),
            mainFx = t("mainFx", ""),
            wallpaperStyle = i("wallStyle", 0),
            wallpaperIndex = i("wallpaper", -1),
            wallpaperColor = l("wallColor", -1L),
            wallpaperSize = i("wallSize", 0),
            wallFx = t("wallFx", ""),
            mainColor = l("mainColor", -1L),
            mainSize = i("mainSize", 0),
            mainIndex = i("mainIndex", -1),
            followFinger = b("followFinger", true),
            animate = b("animate", true),
            motionGlobal = b("motionGlobal", true),
            stateMotion = b("stateMotion", true),
            showNotification = b("showNotification", true),
            notifSticker = b("notifSticker", true),
            notifColor = l("notifColor", -1L),
            flagAvatars = b("flagAvatars", true),
            connectFlagDancer = b("connectFlagDancer", true),
            speed = f("speed", 1f),
            bgSpeed = f("bgSpeed", 1f),
            navStickers = b("navStickers", true),
            autoFlag = b("autoFlag", true),
            navOrder = t("navOrder", "0,1,2,3,4"),
            navIcons = t("navIcons", "0,1,2,3,4"),
            upIcon = i("upIcon", 5),
            downIcon = i("downIcon", 6),
            upPack = i("upPack", -1),
            downPack = i("downPack", -1),
            navPacks = t("navPacks", "-1,-1,-1,-1,-1"),
            routePacks = t("routePacks", "-1,-1,-1"),
            upColor = l("upColor", -1L),
            downColor = l("downColor", -1L),
            upSpeed = f("upSpeed", 1f),
            downSpeed = f("downSpeed", 1f),
            navColors = t("navColors", "-1,-1,-1,-1,-1"),
            routeColors = t("routeColors", "-1,-1,-1"),
            navSpeeds = t("navSpeeds", "1,1,1,1,1"),
            routeSpeeds = t("routeSpeeds", "1,1,1"),
            routeIcons = t("routeIcons", "0,1,2"),
            upFx = t("upFx", ""),
            downFx = t("downFx", ""),
            navFx = t("navFx", ""),
            routeFx = t("routeFx", ""),
            antiFilter = b("antiFilter", false),
            antiFilterApps = t("antiFilterApps", ""),
            homeOrder = t("homeOrder", "0,1,2,3,4"),
            pingOnStart = b("pingOnStart", true),
            autoPingNew = b("autoPingNew", true),
            flagOnStart = b("flagOnStart", true),
            autoFlagNew = b("autoFlagNew", true),
            flagStretch = f("flagStretch", 0.66f),
            flagRound = f("flagRound", 0.45f),
            bubbleOn = b("bubbleOn", true),
            bubbleColor = l("bubbleColor", -1L),
        )
    }

    fun loadServers(): List<ServerProfile>? {
        return try {
            if (!SecurePrefs.contains("servers", ctx)) return null
            val raw = SecurePrefs.getStringOrNull("servers", ctx) ?: return null
            if (raw.isBlank()) return null
            if (!raw.trimStart().startsWith("[")) {
                // sealed value this session cannot open - mark it so saveServers never writes over it
                serversUnreadable = true
                return null
            }
            val out = ArrayList<ServerProfile>()
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val link = o.optString("link", "")
                out.add(
                    ServerProfile(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        host = o.optString("host"),
                        stickerIndex = o.optInt("stickerIndex", i),
                        country = o.optString("country", ""),
                        pinned = o.optBoolean("pinned", false),
                        pack = o.optInt("pack", -1),
                        link = link.takeIf { it.isNotBlank() },
                        category = o.optString("category", ""),
                        note = o.optString("note", ""),
                        bubbleColor = if (o.has("bub")) o.optLong("bub", -1L) else -1L,
                        up = o.optLong("up", 0L),
                        down = o.optLong("down", 0L),
                    ),
                )
            }
            out.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    fun saveServers(list: List<ServerProfile>, selectedId: String) {
        if (serversUnreadable) return
        try {
            val arr = JSONArray()
            for (s in list) {
                val o = JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("host", s.host)
                    .put("stickerIndex", s.stickerIndex)
                    .put("country", s.country)
                    .put("pinned", s.pinned)
                    .put("pack", s.pack)
                if (s.link != null) o.put("link", s.link)
                if (s.category.isNotBlank()) o.put("category", s.category)
                if (s.note.isNotBlank()) o.put("note", s.note)
                if (s.bubbleColor >= 0L) o.put("bub", s.bubbleColor)
                if (s.up > 0L) o.put("up", s.up)
                if (s.down > 0L) o.put("down", s.down)
                arr.put(o)
            }
            SecurePrefs.set("servers", arr.toString(), ctx)
            SecurePrefs.set("selectedId", selectedId, ctx)
        } catch (_: Exception) {
        }
    }

    fun loadSelectedId(): String = SecurePrefs.getString("selectedId", "s1", ctx)

    fun loadRemoteBase(): String = SecurePrefs.getString("remoteBase", "https://god-panel.lastofanarchy.workers.dev", ctx)

    fun saveRemoteBase(raw: String) {
        SecurePrefs.set("remoteBase", raw, ctx)
    }

    fun loadRemote(): String = SecurePrefs.getString("remoteState", "", ctx)

    fun saveRemote(raw: String) {
        SecurePrefs.set("remoteState", raw, ctx)
    }

    fun loadCustom(): String? = SecurePrefs.getStringOrNull("customStickers", ctx)

    fun saveCustom(raw: String) {
        SecurePrefs.set("customStickers", raw, ctx)
    }

    fun saveSelectedId(id: String) {
        SecurePrefs.set("selectedId", id, ctx)
    }

    /** Plaintext copy of the three values the first frame's palette needs, so a cold start never
     *  has to unseal the blob to look right. */
    private fun mirror(s: Settings) {
        try {
            ctx.getSharedPreferences(UI_PREFS, android.content.Context.MODE_PRIVATE).edit()
                .putBoolean("dark", s.dark)
                .putInt("accent", s.accent)
                .putLong("customAccent", s.customAccent)
                .apply()
        } catch (_: Exception) {
        }
    }

    /** One seal for the whole object instead of 58 - that per-key write is what froze the UI. */
    fun save(s: Settings) {
        cache = s
        mirror(s)
        val j = JSONObject()
        fun p(k: String, v: Any) { j.put(k, v.toString()) }
        p("dark", s.dark)
        p("accent", s.accent)
        p("customAccent", s.customAccent)
        p("style", s.stickerStyle)
        p("mainPack", s.mainPack)
        p("mainFx", s.mainFx)
        p("wallStyle", s.wallpaperStyle)
        p("wallpaper", s.wallpaperIndex)
        p("wallColor", s.wallpaperColor)
        p("wallSize", s.wallpaperSize)
        p("wallFx", s.wallFx)
        p("mainColor", s.mainColor)
        p("mainSize", s.mainSize)
        p("mainIndex", s.mainIndex)
        p("followFinger", s.followFinger)
        p("animate", s.animate)
        p("motionGlobal", s.motionGlobal)
        p("stateMotion", s.stateMotion)
        p("showNotification", s.showNotification)
        p("notifSticker", s.notifSticker)
        p("notifColor", s.notifColor)
        p("flagAvatars", s.flagAvatars)
        p("connectFlagDancer", s.connectFlagDancer)
        p("speed", s.speed)
        p("bgSpeed", s.bgSpeed)
        p("navStickers", s.navStickers)
        p("autoFlag", s.autoFlag)
        p("navOrder", s.navOrder)
        p("navIcons", s.navIcons)
        p("upIcon", s.upIcon)
        p("downIcon", s.downIcon)
        p("upPack", s.upPack)
        p("downPack", s.downPack)
        p("navPacks", s.navPacks)
        p("routePacks", s.routePacks)
        p("upColor", s.upColor)
        p("downColor", s.downColor)
        p("upSpeed", s.upSpeed)
        p("downSpeed", s.downSpeed)
        p("navColors", s.navColors)
        p("routeColors", s.routeColors)
        p("navSpeeds", s.navSpeeds)
        p("routeSpeeds", s.routeSpeeds)
        p("routeIcons", s.routeIcons)
        p("upFx", s.upFx)
        p("downFx", s.downFx)
        p("navFx", s.navFx)
        p("routeFx", s.routeFx)
        p("antiFilter", s.antiFilter)
        p("antiFilterApps", s.antiFilterApps)
        p("homeOrder", s.homeOrder)
        p("pingOnStart", s.pingOnStart)
        p("autoPingNew", s.autoPingNew)
        p("flagOnStart", s.flagOnStart)
        p("autoFlagNew", s.autoFlagNew)
        p("flagStretch", s.flagStretch)
        p("flagRound", s.flagRound)
        p("bubbleOn", s.bubbleOn)
        p("bubbleColor", s.bubbleColor)
        val raw = j.toString()
        if (blobUnreadable) return // never overwrite a blob we could not open
        if (raw == blobJson) return
        blobJson = raw
        SecurePrefs.set(BLOB_KEY, raw, ctx)
    }
}
