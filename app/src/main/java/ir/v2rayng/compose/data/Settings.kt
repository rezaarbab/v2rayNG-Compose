package ir.v2rayng.compose.data

import android.content.Context

data class Settings(
    val dark: Boolean = true,
    val accent: Int = 0,
    val stickerStyle: Int = 0,
    val wallpaperIndex: Int = -1,
    val followFinger: Boolean = true,
)

class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("v2settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        dark = sp.getBoolean("dark", true),
        accent = sp.getInt("accent", 0),
        stickerStyle = sp.getInt("style", 0),
        wallpaperIndex = sp.getInt("wallpaper", -1),
        followFinger = sp.getBoolean("followFinger", true),
    )

    fun save(s: Settings) {
        sp.edit()
            .putBoolean("dark", s.dark)
            .putInt("accent", s.accent)
            .putInt("style", s.stickerStyle)
            .putInt("wallpaper", s.wallpaperIndex)
            .putBoolean("followFinger", s.followFinger)
            .apply()
    }
}
