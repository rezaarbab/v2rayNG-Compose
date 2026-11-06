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
    // flat everything: no faces, no gloss, no shadow, no morph. The default look of the app.
    val plain = mutableStateOf(true)
    val plainFlags = mutableStateOf(true)
    /** The dance belongs to the finger: a sticker moves while it is being touched and holds still
     *  the moment it is not. Nothing in the settings turns this back into an all-the-time wobble,
     *  because all-the-time wobble is the battery cost this was written to remove. */
    val touchOnly = mutableStateOf(true)
}

val LocalStickerEnv = staticCompositionLocalOf { StickerEnv() }
