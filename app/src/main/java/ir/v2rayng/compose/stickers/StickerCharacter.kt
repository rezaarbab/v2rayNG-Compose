package ir.v2rayng.compose.stickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
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

val EyeDark = Color(0xFF241E1E)
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

private fun moodAmp(mood: StickerMood): Float = when (mood) {
    StickerMood.HAPPY -> 0.085f
    StickerMood.WOBBLE -> 0.05f
    else -> 0.03f
}

@Composable
fun StickerCharacter(
    sticker: Sticker,
    mood: StickerMood = StickerMood.NORMAL,
    sizeDp: Dp = 96.dp,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    gaze: Offset? = null,
    bg: Boolean = false,
) {
    val env = LocalStickerEnv.current
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var side by remember { mutableStateOf(IntSize.Zero) }
    val frozenPhase = remember(sticker) { ((sticker.id.hashCode() and 0x3FF) % 997) / 997f }

    val tables: BlobTables? = remember(sticker) {
        val a = sticker.art as? BlobArt ?: return@remember null
        val n = 48
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
    val gestures = if (onTap == null && onLongPress == null) null else Modifier.pointerInput(onTap, onLongPress) {
        detectTapGestures(
            onTap = { onTap?.invoke() },
            onLongPress = { onLongPress?.invoke() },
        )
    }

    Canvas(
        modifier = modifier
            .size(sizeDp)
            .onGloballyPositioned { rootPos = it.positionInRoot(); side = it.size }
            .then(gestures ?: Modifier)
    ) {
        val s = size.width
        val animOn = env.enabled.value
        val us = if (bg) env.bgSpeed.floatValue else env.speed.floatValue
        val static = mood == StickerMood.SLEEP || !animOn
        val phase = when (val a = sticker.art) {
            is BlobArt -> a.phase
            is ToyArt -> a.phase
            is FlagArt -> a.phase
        }
        val t = if (static) {
            frozenPhase
        } else {
            StickerTicker.ms.longValue / 1000f * us * moodSpeed(mood) + phase
        }
        val breathe = sin(2.0 * PI * t).toFloat()
        val hop = -moodAmp(mood) * sin(PI * t).let { (it * it).toFloat() }
        val blink: Float
        val cycle = t % 1f
        val dip = (1f - abs(cycle - 0.90f) / 0.06f).coerceIn(0f, 1f)
        blink = if (mood == StickerMood.SLEEP) 0.14f else 1f - 0.88f * dip
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
            color = Color.Black.copy(alpha = 0.20f),
            topLeft = Offset((0.5f - 0.27f * shScale) * s, 0.885f * s),
            size = Size(0.54f * s * shScale, 0.08f * s * shScale),
        )

        fun drawPolyBand(pts: List<Offset>, color: Color) {
            shapePath.rewind()
            var first = true
            for (pt in pts) {
                val p = px(pt.x, pt.y)
                if (first) {
                    shapePath.moveTo(p.x, p.y)
                    first = false
                } else {
                    shapePath.lineTo(p.x, p.y)
                }
            }
            shapePath.close()
            drawPath(shapePath, color)
        }

        fun drawOps(ops: List<ToyOp>, color: Color) {
            for (op in ops) {
                when (op) {
                    is ToyCircle -> {
                        val c = px(op.cx, op.cy)
                        val rx = op.r * sx * s
                        val ry = op.r * sy * s
                        drawOval(
                            color = color,
                            topLeft = Offset(c.x - rx, c.y - ry),
                            size = Size(2 * rx, 2 * ry),
                        )
                    }
                    is ToyPoly -> drawPolyBand(op.pts, color)
                }
            }
        }

        when (val art = sticker.art) {
            is BlobArt -> {
                val tb = tables
                val bodyColor = art.color
                if (tb == null) {
                    drawCircle(bodyColor, radius = 0.34f * s, center = px(0.5f, 0.5f))
                } else {
                    val wt = 2.0 * PI * t
                    val cw = cos(wt).toFloat()
                    val sw = sin(wt).toFloat()
                    val n = 48
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
                    drawPath(shapePath, bodyColor)
                }

                val gloss = px(0.42f, 0.37f)
                drawCircle(Color.White.copy(alpha = 0.16f), radius = 0.11f * s, center = gloss)

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
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f),
                        radius = hw * 0.36f,
                        center = c + Offset(-hw * 0.34f, -hh * 0.4f),
                    )
                }
            }

            is ToyArt -> {
                drawOps(art.ops, art.color)

                val gloss = px(0.42f, 0.37f)
                drawCircle(Color.White.copy(alpha = 0.16f), radius = 0.11f * s, center = gloss)

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

            is FlagArt -> {
                for (band in art.bands) {
                    drawOps(band.ops, band.color)
                }

                val gloss = px(0.42f, 0.37f)
                drawCircle(Color.White.copy(alpha = 0.14f), radius = 0.10f * s, center = gloss)

                val hw = 0.05f * s
                val hh = 0.15f * blink * s
                val eyes = listOf(0.40f to 0.50f, 0.60f to 0.50f)
                for ((i, eye) in eyes.withIndex()) {
                    val c = px(eye.first, eye.second) + Offset(gx, gy)
                    rotate(if (i == 0) -8f else 8f, pivot = c) {
                        drawOval(
                            color = EyeDark,
                            topLeft = Offset(c.x - hw, c.y - hh),
                            size = Size(2 * hw, 2 * hh),
                        )
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = hw * 0.34f,
                        center = c + Offset(-hw * 0.34f, -hh * 0.4f),
                    )
                }
            }
        }
    }
}
