package ir.v2rayng.compose.data

import android.content.Context

data class Settings(
    val dark: Boolean = true,
    val accent: Int = 0,
    val customAccent: Long = -1L,
    val stickerStyle: Int = 0,
    val wallpaperStyle: Int = 0,
    val wallpaperIndex: Int = -1,
    val wallpaperColor: Long = -1L,
    val wallpaperSize: Int = 0,
    val mainColor: Long = -1L,
    val mainSize: Int = 0,
    val mainIndex: Int = -1,
    val followFinger: Boolean = true,
    val animate: Boolean = true,
    val stateMotion: Boolean = true,
    val showNotification: Boolean = true,
    val speed: Float = 1f,
    val bgSpeed: Float = 1f,
    val navStickers: Boolean = true,
    val autoFlag: Boolean = true,
    val navOrder: String = "0,1,2,3,4",
    val navIcons: String = "0,1,2,3,4",
    val upIcon: Int = 5,
    val downIcon: Int = 6,
    val upColor: Long = -1L,
    val downColor: Long = -1L,
    val upSpeed: Float = 1f,
    val downSpeed: Float = 1f,
    val navColors: String = "-1,-1,-1,-1,-1",
    val routeColors: String = "-1,-1,-1",
    val navSpeeds: String = "1,1,1,1,1",
    val routeSpeeds: String = "1,1,1",
    val routeIcons: String = "0,1,2",
    val antiFilter: Boolean = false,
    val antiFilterApps: String = "",
    val homeOrder: String = "0,1,2,3,4",
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

class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("v2settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        dark = sp.getBoolean("dark", true),
        accent = sp.getInt("accent", 0),
        customAccent = java.lang.Long.valueOf(sp.getString("customAccent", "-1")!!),
        stickerStyle = sp.getInt("style", 0),
        wallpaperStyle = sp.getInt("wallStyle", 0),
        wallpaperIndex = sp.getInt("wallpaper", -1),
        wallpaperColor = java.lang.Long.valueOf(sp.getString("wallColor", "-1")!!),
        wallpaperSize = sp.getInt("wallSize", 0),
        mainColor = java.lang.Long.valueOf(sp.getString("mainColor", "-1")!!),
        mainSize = sp.getInt("mainSize", 0),
        mainIndex = sp.getInt("mainIndex", -1),
        followFinger = sp.getBoolean("followFinger", true),
        animate = sp.getBoolean("animate", true),
        stateMotion = sp.getBoolean("stateMotion", true),
        showNotification = sp.getBoolean("showNotification", true),
        speed = sp.getFloat("speed", 1f),
        bgSpeed = sp.getFloat("bgSpeed", 1f),
        navStickers = sp.getBoolean("navStickers", true),
        autoFlag = sp.getBoolean("autoFlag", true),
        navOrder = sp.getString("navOrder", "0,1,2,3,4")!!,
        navIcons = sp.getString("navIcons", "0,1,2,3,4")!!,
        upIcon = sp.getInt("upIcon", 5),
        downIcon = sp.getInt("downIcon", 6),
        upColor = java.lang.Long.valueOf(sp.getString("upColor", "-1")!!),
        downColor = java.lang.Long.valueOf(sp.getString("downColor", "-1")!!),
        upSpeed = sp.getFloat("upSpeed", 1f),
        downSpeed = sp.getFloat("downSpeed", 1f),
        navColors = sp.getString("navColors", "-1,-1,-1,-1,-1")!!,
        routeColors = sp.getString("routeColors", "-1,-1,-1")!!,
        navSpeeds = sp.getString("navSpeeds", "1,1,1,1,1")!!,
        routeSpeeds = sp.getString("routeSpeeds", "1,1,1")!!,
        routeIcons = sp.getString("routeIcons", "0,1,2")!!,
        antiFilter = sp.getBoolean("antiFilter", false),
        antiFilterApps = sp.getString("antiFilterApps", "")!!,
        homeOrder = sp.getString("homeOrder", "0,1,2,3,4")!!,
    )

    fun save(s: Settings) {
        sp.edit()
            .putBoolean("dark", s.dark)
            .putInt("accent", s.accent)
            .putString("customAccent", s.customAccent.toString())
            .putInt("style", s.stickerStyle)
            .putInt("wallStyle", s.wallpaperStyle)
            .putInt("wallpaper", s.wallpaperIndex)
            .putString("wallColor", s.wallpaperColor.toString())
            .putInt("wallSize", s.wallpaperSize)
            .putString("mainColor", s.mainColor.toString())
            .putInt("mainSize", s.mainSize)
            .putInt("mainIndex", s.mainIndex)
            .putBoolean("followFinger", s.followFinger)
            .putBoolean("animate", s.animate)
            .putBoolean("stateMotion", s.stateMotion)
            .putBoolean("showNotification", s.showNotification)
            .putFloat("speed", s.speed)
            .putFloat("bgSpeed", s.bgSpeed)
            .putBoolean("navStickers", s.navStickers)
            .putBoolean("autoFlag", s.autoFlag)
            .putString("navOrder", s.navOrder)
            .putString("navIcons", s.navIcons)
            .putInt("upIcon", s.upIcon)
            .putInt("downIcon", s.downIcon)
            .putString("upColor", s.upColor.toString())
            .putString("downColor", s.downColor.toString())
            .putFloat("upSpeed", s.upSpeed)
            .putFloat("downSpeed", s.downSpeed)
            .putString("navColors", s.navColors)
            .putString("routeColors", s.routeColors)
            .putString("navSpeeds", s.navSpeeds)
            .putString("routeSpeeds", s.routeSpeeds)
            .putString("routeIcons", s.routeIcons)
            .putBoolean("antiFilter", s.antiFilter)
            .putString("antiFilterApps", s.antiFilterApps)
            .putString("homeOrder", s.homeOrder)
            .apply()
    }
}
