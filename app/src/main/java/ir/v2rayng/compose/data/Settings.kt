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
)

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
            .apply()
    }
}
