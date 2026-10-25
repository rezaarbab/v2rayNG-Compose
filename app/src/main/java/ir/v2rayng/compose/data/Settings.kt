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
        /** load() unseals every value separately, and each unseal is ~200ms of StrongBox time on
         *  the phone captured in the APP_SCOUT traces, so the parsed object is shared in memory.
         *  Every write goes through save() here and keeps it current. */
        @Volatile
        private var cache: Settings? = null

        /** Never blocks: null until someone has actually loaded the settings once. */
        fun peek(): Settings? = cache
    }

    fun load(): Settings = cache ?: build().also { cache = it }

    private fun build(): Settings = Settings(
        dark = SecurePrefs.getBoolean("dark", true, ctx),
        accent = SecurePrefs.getInt("accent", 0, ctx),
        customAccent = SecurePrefs.getLong("customAccent", -1L, ctx),
        stickerStyle = SecurePrefs.getInt("style", 0, ctx),
        mainPack = SecurePrefs.getInt("mainPack", -1, ctx),
        mainFx = SecurePrefs.getString("mainFx", "", ctx),
        wallpaperStyle = SecurePrefs.getInt("wallStyle", 0, ctx),
        wallpaperIndex = SecurePrefs.getInt("wallpaper", -1, ctx),
        wallpaperColor = SecurePrefs.getLong("wallColor", -1L, ctx),
        wallpaperSize = SecurePrefs.getInt("wallSize", 0, ctx),
        wallFx = SecurePrefs.getString("wallFx", "", ctx),
        mainColor = SecurePrefs.getLong("mainColor", -1L, ctx),
        mainSize = SecurePrefs.getInt("mainSize", 0, ctx),
        mainIndex = SecurePrefs.getInt("mainIndex", -1, ctx),
        followFinger = SecurePrefs.getBoolean("followFinger", true, ctx),
        animate = SecurePrefs.getBoolean("animate", true, ctx),
        stateMotion = SecurePrefs.getBoolean("stateMotion", true, ctx),
        showNotification = SecurePrefs.getBoolean("showNotification", true, ctx),
        notifSticker = SecurePrefs.getBoolean("notifSticker", true, ctx),
        notifColor = SecurePrefs.getLong("notifColor", -1L, ctx),
        flagAvatars = SecurePrefs.getBoolean("flagAvatars", true, ctx),
        connectFlagDancer = SecurePrefs.getBoolean("connectFlagDancer", true, ctx),
        speed = SecurePrefs.getFloat("speed", 1f, ctx),
        bgSpeed = SecurePrefs.getFloat("bgSpeed", 1f, ctx),
        navStickers = SecurePrefs.getBoolean("navStickers", true, ctx),
        autoFlag = SecurePrefs.getBoolean("autoFlag", true, ctx),
        navOrder = SecurePrefs.getString("navOrder", "0,1,2,3,4", ctx),
        navIcons = SecurePrefs.getString("navIcons", "0,1,2,3,4", ctx),
        upIcon = SecurePrefs.getInt("upIcon", 5, ctx),
        downIcon = SecurePrefs.getInt("downIcon", 6, ctx),
        upPack = SecurePrefs.getInt("upPack", -1, ctx),
        downPack = SecurePrefs.getInt("downPack", -1, ctx),
        navPacks = SecurePrefs.getString("navPacks", "-1,-1,-1,-1,-1", ctx),
        routePacks = SecurePrefs.getString("routePacks", "-1,-1,-1", ctx),
        upColor = SecurePrefs.getLong("upColor", -1L, ctx),
        downColor = SecurePrefs.getLong("downColor", -1L, ctx),
        upSpeed = SecurePrefs.getFloat("upSpeed", 1f, ctx),
        downSpeed = SecurePrefs.getFloat("downSpeed", 1f, ctx),
        navColors = SecurePrefs.getString("navColors", "-1,-1,-1,-1,-1", ctx),
        routeColors = SecurePrefs.getString("routeColors", "-1,-1,-1", ctx),
        navSpeeds = SecurePrefs.getString("navSpeeds", "1,1,1,1,1", ctx),
        routeSpeeds = SecurePrefs.getString("routeSpeeds", "1,1,1", ctx),
        routeIcons = SecurePrefs.getString("routeIcons", "0,1,2", ctx),
        upFx = SecurePrefs.getString("upFx", "", ctx),
        downFx = SecurePrefs.getString("downFx", "", ctx),
        navFx = SecurePrefs.getString("navFx", "", ctx),
        routeFx = SecurePrefs.getString("routeFx", "", ctx),
        antiFilter = SecurePrefs.getBoolean("antiFilter", false, ctx),
        antiFilterApps = SecurePrefs.getString("antiFilterApps", "", ctx),
        homeOrder = SecurePrefs.getString("homeOrder", "0,1,2,3,4", ctx),
        pingOnStart = SecurePrefs.getBoolean("pingOnStart", true, ctx),
        autoPingNew = SecurePrefs.getBoolean("autoPingNew", true, ctx),
        flagOnStart = SecurePrefs.getBoolean("flagOnStart", true, ctx),
        autoFlagNew = SecurePrefs.getBoolean("autoFlagNew", true, ctx),
        flagStretch = SecurePrefs.getFloat("flagStretch", 0.66f, ctx),
        flagRound = SecurePrefs.getFloat("flagRound", 0.45f, ctx),
        bubbleOn = SecurePrefs.getBoolean("bubbleOn", true, ctx),
        bubbleColor = SecurePrefs.getLong("bubbleColor", -1L, ctx),
    )

    fun loadServers(): List<ServerProfile>? {
        return try {
            if (!SecurePrefs.contains("servers", ctx)) return null
            val raw = SecurePrefs.getStringOrNull("servers", ctx) ?: return null
            if (raw.isBlank()) return null
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
                    ),
                )
            }
            out.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    fun saveServers(list: List<ServerProfile>, selectedId: String) {
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

    /**
     * One SecurePrefs write is a StrongBox AES-GCM seal, and MIUI devices take ~200ms for it.
     * save() covers 58 keys, so rewriting unchanged values is what froze the UI on a toggle.
     */
    private val lastSaved = HashMap<String, String>()

    private fun put(key: String, value: String) {
        if (lastSaved[key] == value) return
        lastSaved[key] = value
        SecurePrefs.set(key, value, ctx)
    }

    fun save(s: Settings) {
        cache = s
        put("dark", s.dark.toString())
        put("accent", s.accent.toString())
        put("customAccent", s.customAccent.toString())
        put("style", s.stickerStyle.toString())
        put("mainPack", s.mainPack.toString())
        put("mainFx", s.mainFx)
        put("wallStyle", s.wallpaperStyle.toString())
        put("wallpaper", s.wallpaperIndex.toString())
        put("wallColor", s.wallpaperColor.toString())
        put("wallSize", s.wallpaperSize.toString())
        put("wallFx", s.wallFx)
        put("mainColor", s.mainColor.toString())
        put("mainSize", s.mainSize.toString())
        put("mainIndex", s.mainIndex.toString())
        put("followFinger", s.followFinger.toString())
        put("animate", s.animate.toString())
        put("stateMotion", s.stateMotion.toString())
        put("showNotification", s.showNotification.toString())
        put("notifSticker", s.notifSticker.toString())
        put("notifColor", s.notifColor.toString())
        put("flagAvatars", s.flagAvatars.toString())
        put("connectFlagDancer", s.connectFlagDancer.toString())
        put("speed", s.speed.toString())
        put("bgSpeed", s.bgSpeed.toString())
        put("navStickers", s.navStickers.toString())
        put("autoFlag", s.autoFlag.toString())
        put("navOrder", s.navOrder)
        put("navIcons", s.navIcons)
        put("upIcon", s.upIcon.toString())
        put("downIcon", s.downIcon.toString())
        put("upPack", s.upPack.toString())
        put("downPack", s.downPack.toString())
        put("navPacks", s.navPacks)
        put("routePacks", s.routePacks)
        put("upColor", s.upColor.toString())
        put("downColor", s.downColor.toString())
        put("upSpeed", s.upSpeed.toString())
        put("downSpeed", s.downSpeed.toString())
        put("navColors", s.navColors)
        put("routeColors", s.routeColors)
        put("navSpeeds", s.navSpeeds)
        put("routeSpeeds", s.routeSpeeds)
        put("routeIcons", s.routeIcons)
        put("upFx", s.upFx)
        put("downFx", s.downFx)
        put("navFx", s.navFx)
        put("routeFx", s.routeFx)
        put("antiFilter", s.antiFilter.toString())
        put("antiFilterApps", s.antiFilterApps)
        put("homeOrder", s.homeOrder)
        put("pingOnStart", s.pingOnStart.toString())
        put("autoPingNew", s.autoPingNew.toString())
        put("flagOnStart", s.flagOnStart.toString())
        put("autoFlagNew", s.autoFlagNew.toString())
        put("flagStretch", s.flagStretch.toString())
        put("flagRound", s.flagRound.toString())
        put("bubbleOn", s.bubbleOn.toString())
        put("bubbleColor", s.bubbleColor.toString())
    }
}
