package ir.v2rayng.compose.stickers

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Immutable
enum class StickerMood { NORMAL, HAPPY, WOBBLE, SLEEP }

@Immutable
data class BlobArt(
    val color: Color,
    val a: Float,
    val b: Float,
    val p: Float,
    val q: Float,
    val phase: Float,
) : StickerArt

@Immutable
sealed interface ToyOp

@Immutable
data class ToyCircle(val cx: Float, val cy: Float, val r: Float) : ToyOp

@Immutable
data class ToyPoly(val pts: List<Offset>) : ToyOp

@Immutable
data class ToyEye(
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val deg: Float,
)

@Immutable
data class ToyArt(
    val color: Color,
    val ops: List<ToyOp>,
    val eyes: List<ToyEye>,
    val phase: Float,
) : StickerArt

@Immutable
data class FlagBand(
    val color: Color,
    val ops: List<ToyOp>,
)

@Immutable
data class FlagArt(
    val base: Color,
    val bands: List<FlagBand>,
    val phase: Float,
) : StickerArt

@Immutable
sealed interface StickerArt

@Immutable
data class Sticker(
    val id: String,
    val name: String,
    val art: StickerArt,
)

internal fun chaikin(base: List<Offset>, iters: Int = 3): List<Offset> {
    var cur = base
    repeat(iters) {
        val next = ArrayList<Offset>(cur.size * 2)
        for (i in cur.indices) {
            val a = cur[i]
            val b = cur[(i + 1) % cur.size]
            next.add(Offset(a.x * 0.75f + b.x * 0.25f, a.y * 0.75f + b.y * 0.25f))
            next.add(Offset(a.x * 0.25f + b.x * 0.75f, a.y * 0.25f + b.y * 0.75f))
        }
        cur = next
    }
    return cur
}

object V2Stickers {

    private val blobNames = listOf(
        "زفیر", "پاک", "شارون", "کوری", "فنریر", "لدا", "رز", "استورم", "لیمو", "توتی",
        "باران", "مهتاب", "ابر", "آفتاب", "نجوا", "بلبل", "ریحان", "زعفران", "نارنگی", "انبه",
        "گواوا", "کیوی", "انجیر", "طالبی", "خربزه", "زرشک", "بهارنارنج", "گلاب", "لکه", "بوم",
        "موج", "صدف", "مرجان", "صخره", "درخت", "غار", "شفق", "باد", "چشمه", "سراب",
        "نوا", "واریا", "طوفان", "رعد", "برق", "شبنم", "یخ", "برف", "اورا", "فرفره",
    )

    val blobs: List<Sticker> = blobNames.mapIndexed { idx, name ->
        val classic = floatArrayOf(26f, 218f, 258f, 150f, 4f, 42f)
        val hue = if (idx < classic.size) classic[idx] else (((idx - classic.size) * 137.508f) % 360f)
        val art = BlobArt(
            color = Color.hsl(hue, 0.55f + 0.25f * (idx % 4) / 3f, 0.60f + 0.10f * (idx % 3) / 2f),
            a = 0.4f + 0.6f * (idx % 5) / 4f,
            b = 0.3f + 0.7f * (idx % 7) / 6f,
            p = (idx * 1.17f) % 6.2832f,
            q = (idx * 2.31f) % 6.2832f,
            phase = (idx * 0.053f) % 1f,
        )
        Sticker("blob-$idx", name, art)
    }

    private val shapeNames = listOf("اشک", "مثلث", "ابر", "شب", "کپسول", "لوبیا")

    private val dropOps: List<ToyOp> = listOf(
        ToyPoly(chaikin(listOf(Offset(0.5f, 0.05f), Offset(0.73f, 0.27f), Offset(0.87f, 0.56f), Offset(0.72f, 0.84f), Offset(0.5f, 0.96f), Offset(0.28f, 0.84f), Offset(0.13f, 0.56f), Offset(0.27f, 0.27f)))),
    )

    private val triangleOps: List<ToyOp> = listOf(
        ToyPoly(chaikin(listOf(Offset(0.5f, 0.09f), Offset(0.9f, 0.8f), Offset(0.93f, 0.92f), Offset(0.07f, 0.92f), Offset(0.1f, 0.8f)), 4)),
    )

    private val cloudOps: List<ToyOp> = listOf(
        ToyCircle(0.31f, 0.55f, 0.19f), ToyCircle(0.5f, 0.42f, 0.235f),
        ToyCircle(0.69f, 0.5f, 0.20f), ToyCircle(0.55f, 0.68f, 0.18f),
        ToyCircle(0.33f, 0.68f, 0.17f),
    )

    private val ballOps: List<ToyOp> = listOf(ToyCircle(0.5f, 0.5f, 0.46f))

    private val pillOps: List<ToyOp> = listOf(
        ToyCircle(0.3f, 0.5f, 0.3f), ToyCircle(0.7f, 0.5f, 0.3f),
        ToyPoly(listOf(Offset(0.3f, 0.2f), Offset(0.7f, 0.2f), Offset(0.7f, 0.8f), Offset(0.3f, 0.8f))),
    )

    private val beanOps: List<ToyOp> = listOf(
        ToyCircle(0.5f, 0.5f, 0.30f), ToyCircle(0.26f, 0.4f, 0.20f),
        ToyCircle(0.72f, 0.30f, 0.20f), ToyCircle(0.72f, 0.70f, 0.20f),
        ToyCircle(0.30f, 0.76f, 0.18f),
    )

    private val shapeOps = listOf(dropOps, triangleOps, cloudOps, ballOps, pillOps, beanOps)

    private val eyeSets = listOf(
        listOf(ToyEye(0.40f, 0.50f, 0.11f, 0.32f, -10f), ToyEye(0.60f, 0.50f, 0.11f, 0.32f, -4f)),
        listOf(ToyEye(0.42f, 0.56f, 0.10f, 0.30f, 0f), ToyEye(0.58f, 0.56f, 0.10f, 0.30f, 0f)),
        listOf(ToyEye(0.42f, 0.44f, 0.12f, 0.20f, 22f), ToyEye(0.60f, 0.48f, 0.12f, 0.20f, 26f)),
        listOf(ToyEye(0.42f, 0.40f, 0.10f, 0.30f, -22f), ToyEye(0.56f, 0.43f, 0.10f, 0.30f, 20f)),
        listOf(ToyEye(0.42f, 0.50f, 0.12f, 0.32f, 0f), ToyEye(0.58f, 0.50f, 0.12f, 0.32f, 0f)),
        listOf(ToyEye(0.45f, 0.50f, 0.10f, 0.30f, -28f), ToyEye(0.64f, 0.50f, 0.10f, 0.30f, 28f)),
    )

    val toys: List<Sticker> = (0 until 48).map { idx ->
        val kind = idx % 6
        val classic = intArrayOf(0xFF8A8A8A.toInt(), 0xFF1E86F5.toInt(), 0xFFFA6E14.toInt(), 0xFF14171A.toInt(), 0xFF0FCB6E.toInt(), 0xFF9C6233.toInt())
        val color = if (idx < 6) {
            Color(classic[kind].toLong())
        } else {
            val hue = (((idx - 6) * 137.508f) % 360f + 360f) % 360f
            Color.hsl(hue, 0.66f, 0.55f)
        }
        Sticker(
            "toy-$idx",
            shapeNames[kind],
            ToyArt(color, shapeOps[kind], eyeSets[kind], (idx * 0.09f) % 1f),
        )
    }

    private val sets = listOf(blobs, toys, ir.v2rayng.compose.stickers.CountryFlags.list)

    fun setFor(style: Int): List<Sticker> = sets[((style % 3) + 3) % 3]

    fun byIndex(style: Int, i: Int): Sticker {
        val set = setFor(style)
        val n = set.size
        return set[((i % n) + n) % n]
    }
}

fun tinted(sticker: Sticker, color: Long): Sticker {
    if (color < 0L) return sticker
    val c = Color(color)
    return when (val a = sticker.art) {
        is BlobArt -> sticker.copy(art = a.copy(color = c))
        is ToyArt -> sticker.copy(art = a.copy(color = c))
        is FlagArt -> sticker
    }
}
