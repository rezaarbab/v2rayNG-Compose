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
    val speed: Float = 1f,
    val bgSpeed: Float = 1f,
    val navStickers: Boolean = true,
    val autoFlag: Boolean = true,
    val navOrder: String = "0,1,2,3,4",
    val navIcons: String = "0,1,2,3,4",
    val upIcon: Int = 5,
    val downIcon: Int = 6,
    val routeIcons: String = "0,1,2",
    val antiFilter: Boolean = false,
    val homeOrder: String = "0,1,2,3,4",
)

fun parseList(s: String, def: List<Int>): List<Int> {
    return s.split(",").mapNotNull { it.trim().toIntOrNull() }.ifEmpty { def }
}

fun packList(l: List<Int>): String = l.joinToString(",")

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
        speed = sp.getFloat("speed", 1f),
        bgSpeed = sp.getFloat("bgSpeed", 1f),
        navStickers = sp.getBoolean("navStickers", true),
        autoFlag = sp.getBoolean("autoFlag", true),
        navOrder = sp.getString("navOrder", "0,1,2,3,4")!!,
        navIcons = sp.getString("navIcons", "0,1,2,3,4")!!,
        upIcon = sp.getInt("upIcon", 5),
        downIcon = sp.getInt("downIcon", 6),
        routeIcons = sp.getString("routeIcons", "0,1,2")!!,
        antiFilter = sp.getBoolean("antiFilter", false),
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
            .putFloat("speed", s.speed)
            .putFloat("bgSpeed", s.bgSpeed)
            .putBoolean("navStickers", s.navStickers)
            .putBoolean("autoFlag", s.autoFlag)
            .putString("navOrder", s.navOrder)
            .putString("navIcons", s.navIcons)
            .putInt("upIcon", s.upIcon)
            .putInt("downIcon", s.downIcon)
            .putString("routeIcons", s.routeIcons)
            .putBoolean("antiFilter", s.antiFilter)
            .putString("homeOrder", s.homeOrder)
            .apply()
    }
}
