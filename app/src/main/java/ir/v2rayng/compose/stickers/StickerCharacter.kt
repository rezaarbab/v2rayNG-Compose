package ir.v2rayng.compose.stickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.sp
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

private fun buildTables(a: Float, b: Float, p: Float, q: Float): BlobTables {
    val n = 48
    val A = FloatArray(n)
    val B = FloatArray(n)
    val C = FloatArray(n)
    for (i in 0 until n) {
        val th = 2.0 * PI * i / n
        val b1 = (0.08f * a) * sin(2 * th + p).toFloat()
        val c1 = (0.08f * a) * cos(2 * th + p).toFloat()
        val b2 = (0.06f * b) * sin(3 * th + q).toFloat()
        val c2 = (0.06f * b) * cos(3 * th + q).toFloat()
        A[i] = 1f + b1 + b2
        B[i] = b1 + b2
        C[i] = c1 - c2
    }
    return BlobTables(A, B, C)
}

private fun moodSpeed(mood: StickerMood): Float = when (mood) {
    StickerMood.SLEEP -> 0.55f
    StickerMood.WOBBLE -> 2.1f
    StickerMood.SAD -> 2.4f
    StickerMood.HAPPY -> 1.5f
    else -> 1f
}

private fun moodAmp(mood: StickerMood): Float = when (mood) {
    StickerMood.HAPPY -> 0.115f
    StickerMood.WOBBLE -> 0.055f
    StickerMood.SAD -> 0.02f
    else -> 0.045f
}

private fun moodTilt(mood: StickerMood): Float = when (mood) {
    StickerMood.WOBBLE -> 3.0f
    StickerMood.SAD -> 5.0f
    StickerMood.HAPPY -> 2.2f
    StickerMood.SLEEP -> 0.4f
    else -> 1.3f
}

private fun phaseOf(art: StickerArt): Float = when (art) {
    is BlobArt -> art.phase
    is ToyArt -> art.phase
    is FlagArt -> art.phase
    is ImageArt -> art.phase
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
    decorative: Boolean = false,
    speedScale: Float = 1f,
) {
    val env = LocalStickerEnv.current
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var side by remember { mutableStateOf(IntSize.Zero) }
    val frozenPhase = remember(sticker) { ((sticker.id.hashCode() and 0x3FF) % 997) / 997f }

    val tables: BlobTables? = remember(sticker) {
        when (val art = sticker.art) {
            is BlobArt -> buildTables(art.a, art.b, art.p, art.q)
            is ImageArt -> buildTables(art.a, art.b, 0f, 0f)
            else -> null
        }
    }

    val shapePath = remember { Path() }
    val gestures = if (onTap == null && onLongPress == null) null else Modifier.pointerInput(onTap, onLongPress) {
        detectTapGestures(
            onTap = { onTap?.invoke() },
            onLongPress = { onLongPress?.invoke() },
        )
    }
    val positionMod = if (gaze != null) {
        Modifier.onGloballyPositioned { rootPos = it.positionInRoot(); side = it.size }
    } else {
        Modifier
    }

    val blinkCur = remember(mood) { mutableFloatStateOf(if (mood == StickerMood.SLEEP) 0.14f else 1f) }
    val textMeasurer = rememberTextMeasurer()

    val dance = Modifier.graphicsLayer {
        if (mood == StickerMood.SLEEP || decorative || !env.enabled.value) {
            translationX = 0f; translationY = 0f
            scaleX = 1f; scaleY = 1f; rotationZ = 0f
            return@graphicsLayer
        }
        val tiny = sizeDp.value <= 48f
        val s = sizeDp.toPx()
        val clock = StickerTicker.anim.longValue
        val us = if (bg) env.bgSpeed.floatValue else env.speed.floatValue
        val customSpeed = CustomStickers.speedOf(sticker)
        val spMul = if (bg) 0.6f else 1f
        val t = clock / 1000f * us * spMul * (moodSpeed(mood) * speedScale * customSpeed) * (if (tiny) 1.8f else 1f) + phaseOf(sticker.art)
        val w = 2.0 * PI * t
        val breathe = sin(w).toFloat()
        val sp = ((1.0 - cos(w)) / 2.0).toFloat()
        val amp = (moodAmp(mood) * (if (tiny) 2.6f else 1f)).coerceAtMost(0.14f)
        val hop = -amp * sp
        val sad = mood == StickerMood.SAD
        val swayAmp = if (sad) 0.05f else (amp * 0.35f).coerceAtMost(if (tiny) 0.018f else 0.008f)
        val sway = swayAmp * sin(if (sad) w * 2.5 + 1.7 else w + 1.7).toFloat()
        val tilt = moodTilt(mood) * (if (tiny) 1.3f else 1f) * sin(if (sad) w * 2.5 + 0.6 else w + 0.6).toFloat()
        val sqx = 1f + 0.026f * breathe

        val cycle = t % 1f
        val dip = (1f - abs(cycle - 0.90f) / 0.06f).coerceIn(0f, 1f)
        val blinkVal = 1f - 0.88f * dip
        if (abs(blinkVal - blinkCur.floatValue) > 0.015f) {
            blinkCur.floatValue = blinkVal
        }

        translationX = sway * s
        translationY = hop * s
        scaleX = sqx
        scaleY = 1f - 0.026f * breathe
        rotationZ = tilt
        transformOrigin = TransformOrigin(0.5f, 0.86f)
    }

    Canvas(
        modifier = modifier
            .size(sizeDp)
            .then(positionMod)
            .then(gestures ?: Modifier)
            .then(dance)
    ) {
        val s = size.width
        val customSpeed = CustomStickers.speedOf(sticker)
        val animOn = env.enabled.value
        val static = mood == StickerMood.SLEEP || !animOn || decorative
        val tImg = if (static) {
            frozenPhase
        } else {
            val clock = if (bg || sizeDp.value <= 48f) StickerTicker.wave.longValue else StickerTicker.anim.longValue
            val us = if (bg) env.bgSpeed.floatValue else env.speed.floatValue
            clock / 1000f * us * (if (bg) 0.6f else 1f) * (moodSpeed(mood) * speedScale * customSpeed) + phaseOf(sticker.art)
        }

        fun p(x: Float, y: Float): Offset = Offset(x * s, y * s)

        drawOval(
            color = Color.Black.copy(alpha = 0.20f),
            topLeft = Offset(0.23f * s, 0.885f * s),
            size = Size(0.54f * s, 0.08f * s),
        )

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

        fun drawPolyBand(pts: List<Offset>, color: Color) {
            shapePath.rewind()
            var first = true
            for (pt in pts) {
                val q = p(pt.x, pt.y)
                if (first) {
                    shapePath.moveTo(q.x, q.y)
                    first = false
                } else {
                    shapePath.lineTo(q.x, q.y)
                }
            }
            shapePath.close()
            drawPath(shapePath, color)
        }

        fun drawOps(ops: List<ToyOp>, color: Color) {
            for (op in ops) {
                when (op) {
                    is ToyCircle -> {
                        val c = p(op.cx, op.cy)
                        val r = op.r * s
                        drawOval(
                            color = color,
                            topLeft = Offset(c.x - r, c.y - r),
                            size = Size(2 * r, 2 * r),
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
                    drawCircle(bodyColor, radius = 0.34f * s, center = p(0.5f, 0.5f))
                } else {
                    val t = if (static) {
                        frozenPhase
                    } else if (sizeDp.value <= 48f) {
                        val us = env.speed.floatValue
                        StickerTicker.wave.longValue / 1000f * us * (moodSpeed(mood) * speedScale * customSpeed * 1.35f) + phaseOf(art)
                    } else {
                        val clock = StickerTicker.anim.longValue
                        val us = if (bg) env.bgSpeed.floatValue else env.speed.floatValue
                        clock / 1000f * us * (if (bg) 0.6f else 1f) * (moodSpeed(mood) * speedScale * customSpeed) + phaseOf(art)
                    }
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
                        val q = p(x, y)
                        if (i == 0) shapePath.moveTo(q.x, q.y) else shapePath.lineTo(q.x, q.y)
                    }
                    shapePath.close()
                    drawPath(shapePath, bodyColor)
                }

                drawCircle(Color.White.copy(alpha = 0.16f), radius = 0.11f * s, center = p(0.42f, 0.37f))

                val hw = 0.036f * s
                val hh = 0.07f * blinkCur.floatValue * s
                val eyes = listOf(0.38f to 0.47f, 0.62f to 0.47f)
                for ((ex, ey) in eyes) {
                    val c = p(ex, ey) + Offset(gx, gy)
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

                drawCircle(Color.White.copy(alpha = 0.16f), radius = 0.11f * s, center = p(0.42f, 0.37f))

                for (e in art.eyes) {
                    val c = p(e.cx, e.cy) + Offset(gx, gy)
                    val w = e.w * s
                    val h = e.h * s * blinkCur.floatValue
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

                drawCircle(Color.White.copy(alpha = 0.14f), radius = 0.10f * s, center = p(0.42f, 0.37f))

                val hw = 0.045f * s
                val hh = 0.13f * blinkCur.floatValue * s
                val eyes = listOf(0.40f to 0.50f, 0.60f to 0.50f)
                for ((i, eye) in eyes.withIndex()) {
                    val c = p(eye.first, eye.second) + Offset(gx, gy)
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

            is ImageArt -> {
                val tb = tables
                if (tb != null && !static) {
                    val wt = 2.0 * PI * tImg
                    val cw = cos(wt).toFloat()
                    val sw = sin(wt).toFloat()
                    val n = 48
                    shapePath.rewind()
                    for (i in 0 until n) {
                        val th = 2.0 * PI * i / n
                        val r = 0.34f * (tb.A[i] + tb.B[i] * cw + tb.C[i] * sw)
                        val x = 0.5f + r * cos(th).toFloat()
                        val y = 0.5f + r * sin(th).toFloat()
                        val q = p(x, y)
                        if (i == 0) shapePath.moveTo(q.x, q.y) else shapePath.lineTo(q.x, q.y)
                    }
                    shapePath.close()
                    drawPath(shapePath, art.color)
                } else {
                    drawCircle(art.color, radius = 0.34f * s, center = p(0.5f, 0.5f))
                }

                clipPath(shapePath) {
                    if (art.imageB64.isNotBlank()) {
                        val bmp = ImageStickerCache.get(art.imageB64)
                        if (bmp != null) {
                            val box = 0.56f * s
                            val sc = maxOf(box / bmp.width, box / bmp.height)
                            val dw = bmp.width * sc
                            val dh = bmp.height * sc
                            drawImage(
                                bmp.asImageBitmap(),
                                dstOffset = androidx.compose.ui.unit.IntOffset(
                                    (0.5f * s - dw / 2f).toInt(),
                                    (0.5f * s - dh / 2f).toInt(),
                                ),
                                dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()),
                            )
                        }
                    } else if (art.emoji.isNotBlank()) {
                        drawText(
                            textMeasurer,
                            art.emoji,
                            topLeft = Offset(0.5f * s - 0.30f * s, 0.5f * s - 0.36f * s),
                            style = TextStyle(fontSize = (0.52f * s).toSp(), textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        )
                    }
                }

                drawCircle(Color.White.copy(alpha = 0.16f), radius = 0.11f * s, center = p(0.42f, 0.37f))

                if (art.eyes) {
                    val hw = 0.036f * s
                    val hh = 0.07f * blinkCur.floatValue * s
                    val eyes = listOf(0.38f to 0.47f, 0.62f to 0.47f)
                    for ((ex, ey) in eyes) {
                        val c = p(ex, ey) + Offset(gx, gy)
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
            }
        }
    }
}
