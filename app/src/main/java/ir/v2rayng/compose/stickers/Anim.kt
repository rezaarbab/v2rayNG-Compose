package ir.v2rayng.compose.stickers

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

object StickerTicker {
    val anim = mutableLongStateOf(0L)
    val wave = mutableLongStateOf(0L)
}

class StickerEnv {
    val enabled = mutableStateOf(true)
    val speed = mutableFloatStateOf(1f)
    val bgSpeed = mutableFloatStateOf(1f)
    // flag look, user-controlled: aspect (square..long) and corner rounding (sharp..semicircle)
    val flagStretch = mutableFloatStateOf(0.66f)
    val flagRound = mutableFloatStateOf(0.45f)
}

val LocalStickerEnv = staticCompositionLocalOf { StickerEnv() }
