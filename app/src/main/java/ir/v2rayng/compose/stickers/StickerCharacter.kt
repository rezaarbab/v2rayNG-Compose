package ir.v2rayng.compose.stickers

import androidx.compose.animation.core.withFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val EyeDark = Color(0xFF231E1E)

@Composable
fun StickerCharacter(
    preset: StickerPreset,
    mood: StickerMood = StickerMood.NORMAL,
    sizeDp: Dp = 96.dp,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frame = it }
        }
    }

    val speed = when (mood) {
        StickerMood.SLEEP -> 0.55f
        StickerMood.WOBBLE -> 2.1f
        StickerMood.HAPPY -> 1.7f
        else -> 1f
    }
    val t = frame / 1000f * speed + preset.phaseOffset
    val breathe = sin(2.0 * PI * t).toFloat()
    val hop = when (mood) {
        StickerMood.SLEEP -> 0f
        StickerMood.HAPPY -> -0.085f * abs(sin(PI * t * 2.0)).toFloat()
        StickerMood.WOBBLE -> -0.05f * abs(sin(PI * t * 2.0)).toFloat()
        else -> -0.03f * abs(sin(PI * t)).toFloat()
    }
    val blink: Float
    if (mood == StickerMood.SLEEP) {
        blink = 0.14f
    } else {
        val cycle = t % 1f
        blink = if (cycle in 0.85f..0.93f) 0.12f else 1f
    }

    Canvas(
        modifier = modifier
            .size(sizeDp)
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
    ) {
        val s = size.width
        val sx = 1f + 0.03f * breathe
        val sy = 1f - 0.03f * breathe

        fun px(x: Float, y: Float): Offset =
            Offset((0.5f + (x - 0.5f) * sx) * s, (0.5f + (y - 0.5f) * sy + hop) * s)

        val shScale = (1f - abs(hop) * 3f).coerceIn(0.45f, 1f)
        drawOval(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset((0.5f - 0.27f * shScale) * s, 0.885f * s),
            size = Size(0.54f * s * shScale, 0.08f * s * shScale),
        )

        val path = Path()
        val n = 160
        for (i in 0 until n) {
            val th = 2.0 * PI * i / n
            val r = 0.34f * (
                1f + 0.08f * preset.amp1 * sin(2 * th + preset.phase1 + 2 * PI * t).toFloat()
                    + 0.06f * preset.amp2 * sin(3 * th + preset.phase2 - 2 * PI * t).toFloat()
                )
            val x = 0.5f + r * cos(th).toFloat()
            val y = 0.5f + r * sin(th).toFloat()
            val p = px(x, y)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, preset.color)

        val hw = 0.036f * s
        val eyePairs = listOf(0.38f to 0.47f, 0.62f to 0.47f)
        for ((ex, ey) in eyePairs) {
            val c = px(ex, ey)
            val hh = 0.07f * blink * s
            drawOval(
                color = EyeDark,
                topLeft = Offset(c.x - hw, c.y - hh),
                size = Size(2 * hw, 2 * hh),
            )
        }
    }
}
