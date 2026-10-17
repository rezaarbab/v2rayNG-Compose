package ir.v2rayng.compose.stickers

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

data class StickerPreset(
    val id: String,
    val color: Color,
    val amp1: Float,
    val amp2: Float,
    val phase1: Float,
    val phase2: Float,
    val phaseOffset: Float,
)

object V2Stickers {
    val zephyr = StickerPreset("zephyr", Color(0xFFF09258), 0.8f, 0.5f, 1.3f, 0.2f, 0.00f)
    val puck = StickerPreset("puck", Color(0xFF96B6F5), 1.0f, 0.6f, 0.4f, 2.1f, 0.17f)
    val charon = StickerPreset("charon", Color(0xFFB2A0F5), 0.6f, 0.9f, 2.0f, 1.0f, 0.34f)
    val kore = StickerPreset("kore", Color(0xFF8CDDB2), 1.0f, 0.4f, 2.6f, 0.7f, 0.51f)
    val fenrir = StickerPreset("fenrir", Color(0xFFEC6E6E), 0.7f, 0.8f, 1.1f, 2.9f, 0.68f)
    val leda = StickerPreset("leda", Color(0xFFF4D27A), 0.9f, 0.5f, 0.1f, 1.6f, 0.85f)

    val all = listOf(zephyr, puck, charon, kore, fenrir, leda)

    fun byIndex(i: Int): StickerPreset {
        val n = all.size
        return all[((i % n) + n) % n]
    }
}
