package ir.v2rayng.compose.stickers

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

object StickerTicker {
    val ms = mutableLongStateOf(0L)
}

class StickerEnv {
    val enabled = mutableStateOf(true)
    val speed = mutableFloatStateOf(1f)
}

val LocalStickerEnv = staticCompositionLocalOf { StickerEnv() }
