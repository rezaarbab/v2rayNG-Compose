package ir.v2rayng.compose.stickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

val EyeDark = Color(0xFF231E1E)
private val EyeLight = Color.White

private data class BlobTables(
    val A: FloatArray,
    val B: FloatArray,
    val C: FloatArray,
)

private fun moodSpeed(mood: StickerMood): Float = when (mood) {
    StickerMood.SLEEP -> 0.55f
    StickerMood.WOBBLE -> 2.1f
    StickerMood.HAPPY -> 1.7f
    else -> 1f
}

private fun moodHop(mood: StickerMood, t: Float): Float = when (mood) {
    StickerMood.SLEEP -> 0f
    StickerMood.HAPPY -> -0.085f * abs(sin(PI * t * 2.0)).toFloat()
    StickerMood.WOBBLE -> -0.05f * abs(sin(PI * t * 2.0)).toFloat()
    else -> -0.03f * abs(sin(PI * t)).toFloat()
}

private fun moodBlink(mood: StickerMood, t: Float): Float {
    if (mood == StickerMood.SLEEP) return 0.14f
    val cycle = t % 1f
    return if (cycle in 0.85f..0.93f) 0.12f else 1f
}

@Composable
fun StickerCharacter(
    sticker: Sticker,
    mood: StickerMood = StickerMood.NORMAL,
    sizeDp: Dp = 96.dp,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    gaze: Offset? = null,
) {
    var frame by remember { mutableLongStateOf(0L) }
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var side by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frame = it }
        }
    }

    val tables: BlobTables? = remember(sticker) {
        val a = sticker.art as? BlobArt ?: return@remember null
        val n = 64
        val A = FloatArray(n)
        val B = FloatArray(n)
        val C = FloatArray(n)
        for (i in 0 until n) {
            val th = 2.0 * PI * i / n
            val b1 = (0.08f * a.a) * sin(2 * th + a.p).toFloat()
            val c1 = (0.08f * a.a) * cos(2 * th + a.p).toFloat()
            val b2 = (0.06f * a.b) * sin(3 * th + a.q).toFloat()
            val c2 = (0.06f * a.b) * cos(3 * th + a.q).toFloat()
            A[i] = 1f + b1 + b2
            B[i] = b1 + b2
            C[i] = c1 - c2
        }
        BlobTables(A, B, C)
    }

    val shapePath = remember { Path() }

    Canvas(
        modifier = modifier
            .size(sizeDp)
            .onGloballyPositioned { rootPos = it.positionInRoot(); side = it.size }
            .then(if (onTap != null) Modifier.clickable { onTap() } else Modifier)
    ) {
        val s = size.width
        val phase = when (val a = sticker.art) {
            is BlobArt -> a.phase
            is ToyArt -> a.phase
        }
        val t = frame / 1000f * moodSpeed(mood) + phase
        val breathe = sin(2.0 * PI * t).toFloat()
        val hop = moodHop(mood, t)
        val blink = moodBlink(mood, t)
        val sx = 1f + 0.03f * breathe
        val sy = 1f - 0.03f * breathe

        var gx = 0f
        var gy = 0f
        if (gaze != null) {
            val dirX = gaze.x - (rootPos.x + side.width / 2f)
            val dirY = gaze.y - (rootPos.y + side.height / 2f)
            val len = sqrt(dirX * dirX + dirY * dirY)
            if (len > 1f) {
                val cap = s * 0.055f
                val gain = cap * (len / (len + 400f)) / len
                gx = dirX * gain
                gy = dirY * gain
            }
        }

        fun px(x: Float, y: Float): Offset =
            Offset((0.5f + (x - 0.5f) * sx) * s, (0.5f + (y - 0.5f) * sy + hop) * s)

        val shScale = (1f - abs(hop) * 3f).coerceIn(0.45f, 1f)
        drawOval(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset((0.5f - 0.27f * shScale) * s, 0.885f * s),
            size = Size(0.54f * s * shScale, 0.08f * s * shScale),
        )

        when (val art = sticker.art) {
            is BlobArt -> {
                val tb = tables
                if (tb == null) {
                    drawCircle(art.color, radius = 0.34f * s, center = px(0.5f, 0.5f))
                } else {
                    val wt = 2.0 * PI * t
                    val cw = cos(wt).toFloat()
                    val sw = sin(wt).toFloat()
                    val n = 64
                    shapePath.rewind()
                    for (i in 0 until n) {
                        val th = 2.0 * PI * i / n
                        val r = 0.34f * (tb.A[i] + tb.B[i] * cw + tb.C[i] * sw)
                        val x = 0.5f + r * cos(th).toFloat()
                        val y = 0.5f + r * sin(th).toFloat()
                        val p = px(x, y)
                        if (i == 0) shapePath.moveTo(p.x, p.y) else shapePath.lineTo(p.x, p.y)
                    }
                    shapePath.close()
                    drawPath(shapePath, art.color)
                }

                val hw = 0.036f * s
                val hh = 0.07f * blink * s
                val eyes = listOf(0.38f to 0.47f, 0.62f to 0.47f)
                for ((ex, ey) in eyes) {
                    val c = px(ex, ey) + Offset(gx, gy)
                    drawOval(
                        color = EyeDark,
                        topLeft = Offset(c.x - hw, c.y - hh),
                        size = Size(2 * hw, 2 * hh),
                    )
                }
            }

            is ToyArt -> {
                shapePath.rewind()
                for (op in art.ops) {
                    when (op) {
                        is ToyCircle -> {
                            val c = px(op.cx, op.cy)
                            val rx = op.r * sx * s
                            val ry = op.r * sy * s
                            drawOval(
                                color = art.color,
                                topLeft = Offset(c.x - rx, c.y - ry),
                                size = Size(2 * rx, 2 * ry),
                            )
                        }
                        is ToyPoly -> {
                            var first = true
                            for (pt in op.pts) {
                                val p = px(pt.x, pt.y)
                                if (first) {
                                    shapePath.moveTo(p.x, p.y)
                                    first = false
                                } else {
                                    shapePath.lineTo(p.x, p.y)
                                }
                            }
                            shapePath.close()
                        }
                    }
                }
                drawPath(shapePath, art.color)

                for (e in art.eyes) {
                    val c = px(e.cx, e.cy) + Offset(gx, gy)
                    val w = e.w * s
                    val h = e.h * s * blink
                    rotate(e.deg, pivot = c) {
                        drawOval(
                            color = EyeLight,
                            topLeft = Offset(c.x - w / 2f, c.y - h / 2f),
                            size = Size(w, h),
                        )
                    }
                }
            }
        }
    }
}
