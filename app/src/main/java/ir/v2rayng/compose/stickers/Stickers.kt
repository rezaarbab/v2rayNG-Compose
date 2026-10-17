package ir.v2rayng.compose.stickers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class StickerMood { NORMAL, HAPPY, WOBBLE, SLEEP }

data class BlobArt(
    val color: Color,
    val a: Float,
    val b: Float,
    val p: Float,
    val q: Float,
    val phase: Float,
)

sealed interface ToyOp
data class ToyCircle(val cx: Float, val cy: Float, val r: Float) : ToyOp
data class ToyPoly(val pts: List<Offset>) : ToyOp

data class ToyEye(
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val deg: Float,
)

data class ToyArt(
    val color: Color,
    val ops: List<ToyOp>,
    val eyes: List<ToyEye>,
    val phase: Float,
)

sealed interface StickerArt
data class Sticker(
    val id: String,
    val name: String,
    val art: StickerArt,
)

object V2Stickers {

    val blobs = listOf(
        Sticker("zephyr", "زفیر", BlobArt(Color(0xFFF09258), 0.8f, 0.5f, 1.3f, 0.2f, 0.00f)),
        Sticker("puck", "پاک", BlobArt(Color(0xFF96B6F5), 1.0f, 0.6f, 0.4f, 2.1f, 0.17f)),
        Sticker("charon", "شارون", BlobArt(Color(0xFFB2A0F5), 0.6f, 0.9f, 2.0f, 1.0f, 0.34f)),
        Sticker("kore", "کوری", BlobArt(Color(0xFF8CDDB2), 1.0f, 0.4f, 2.6f, 0.7f, 0.51f)),
        Sticker("fenrir", "فنریر", BlobArt(Color(0xFFEC6E6E), 0.7f, 0.8f, 1.1f, 2.9f, 0.68f)),
        Sticker("leda", "لدا", BlobArt(Color(0xFFF4D27A), 0.9f, 0.5f, 0.1f, 1.6f, 0.85f)),
        Sticker("rose", "رز", BlobArt(Color(0xFFE689C3), 0.9f, 0.6f, 0.9f, 2.4f, 0.31f)),
        Sticker("storm", "استورم", BlobArt(Color(0xFF7FD8E8), 0.75f, 0.45f, 3.2f, 0.9f, 0.62f)),
        Sticker("lime", "لیمو", BlobArt(Color(0xFFC9E87F), 0.85f, 0.7f, 0.6f, 1.9f, 0.78f)),
    )

    private val dropPoly = listOf(
        Offset(0.5f, 0.05f), Offset(0.72f, 0.28f), Offset(0.86f, 0.55f),
        Offset(0.7f, 0.85f), Offset(0.5f, 0.96f), Offset(0.3f, 0.85f),
        Offset(0.14f, 0.55f), Offset(0.28f, 0.28f),
    )

    private val trianglePoly = listOf(
        Offset(0.5f, 0.07f), Offset(0.92f, 0.83f), Offset(0.9f, 0.94f),
        Offset(0.1f, 0.94f), Offset(0.08f, 0.83f),
    )

    private val cloudOrangeOps = listOf(
        ToyCircle(0.32f, 0.52f, 0.20f), ToyCircle(0.5f, 0.42f, 0.24f),
        ToyCircle(0.68f, 0.47f, 0.20f), ToyCircle(0.55f, 0.66f, 0.18f),
        ToyCircle(0.34f, 0.68f, 0.18f),
    )

    private val beanOps = listOf(
        ToyCircle(0.5f, 0.5f, 0.30f), ToyCircle(0.25f, 0.4f, 0.20f),
        ToyCircle(0.72f, 0.3f, 0.20f), ToyCircle(0.72f, 0.7f, 0.20f),
        ToyCircle(0.3f, 0.75f, 0.18f),
    )

    private val pillOps = listOf(
        ToyCircle(0.3f, 0.5f, 0.3f), ToyCircle(0.7f, 0.5f, 0.3f),
        ToyPoly(listOf(Offset(0.3f, 0.2f), Offset(0.7f, 0.2f), Offset(0.7f, 0.8f), Offset(0.3f, 0.8f))),
    )

    val toys = listOf(
        Sticker(
            "drop", "اشک",
            ToyArt(Color(0xFF787878), listOf(ToyPoly(dropPoly)),
                listOf(ToyEye(0.38f, 0.5f, 0.09f, 0.22f, -8f), ToyEye(0.6f, 0.5f, 0.09f, 0.22f, -4f)), 0.11f),
        ),
        Sticker(
            "triangle", "مثلث",
            ToyArt(Color(0xFF1184FF), listOf(ToyPoly(trianglePoly)),
                listOf(ToyEye(0.42f, 0.6f, 0.08f, 0.2f, 0f), ToyEye(0.58f, 0.6f, 0.08f, 0.2f, 0f)), 0.29f),
        ),
        Sticker(
            "cloudy", "ابر",
            ToyArt(Color(0xFFFF6600), cloudOrangeOps,
                listOf(ToyEye(0.42f, 0.45f, 0.16f, 0.07f, 20f), ToyEye(0.6f, 0.5f, 0.16f, 0.07f, 24f)), 0.44f),
        ),
        Sticker(
            "blackie", "شب",
            ToyArt(Color(0xFF111418), listOf(ToyCircle(0.5f, 0.5f, 0.46f)),
                listOf(ToyEye(0.4f, 0.42f, 0.09f, 0.2f, -25f), ToyEye(0.56f, 0.46f, 0.09f, 0.2f, 20f)), 0.58f),
        ),
        Sticker(
            "pill", "کپسول",
            ToyArt(Color(0xFF00C870), pillOps,
                listOf(ToyEye(0.42f, 0.5f, 0.12f, 0.28f, 0f), ToyEye(0.58f, 0.5f, 0.12f, 0.28f, 0f)), 0.71f),
        ),
        Sticker(
            "bean", "لوبیا",
            ToyArt(Color(0xFF94623A), beanOps,
                listOf(ToyEye(0.45f, 0.5f, 0.07f, 0.2f, -30f), ToyEye(0.65f, 0.5f, 0.07f, 0.2f, 30f)), 0.86f),
        ),
        Sticker(
            "star", "ستاره",
            ToyArt(Color(0xFFF4C244), listOf(ToyPoly(starPts())),
                listOf(ToyEye(0.44f, 0.52f, 0.08f, 0.18f, 0f), ToyEye(0.56f, 0.52f, 0.08f, 0.18f, 0f)), 0.2f),
        ),
        Sticker(
            "heart", "قلب",
            ToyArt(Color(0xFFF06D9E), listOf(ToyPoly(heartPts())),
                listOf(ToyEye(0.44f, 0.5f, 0.08f, 0.18f, 0f), ToyEye(0.56f, 0.5f, 0.08f, 0.18f, 0f)), 0.66f),
        ),
    )

    private val sets = listOf(blobs, toys)

    fun setFor(style: Int): List<Sticker> = sets[((style % 2) + 2) % 2]

    fun byIndex(style: Int, i: Int): Sticker {
        val set = setFor(style)
        val n = set.size
        return set[((i % n) + n) % n]
    }
}

private fun starPts(): List<Offset> {
    val pts = ArrayList<Offset>(10)
    for (k in 0 until 10) {
        val ang = (-90.0 + 36.0 * k) * PI / 180.0
        val rad = if (k % 2 == 0) 0.46f else 0.20f
        pts.add(Offset(0.5f + (rad * cos(ang)).toFloat(), 0.52f + (rad * sin(ang)).toFloat()))
    }
    return pts
}

private fun heartPts(): List<Offset> {
    val pts = ArrayList<Offset>(28)
    for (i in 0 until 28) {
        val t = 2.0 * PI * i / 28.0
        val x = 16.0 * sin(t) * sin(t) * sin(t)
        val y = 13.0 * cos(t) - 5.0 * cos(2 * t) - 2.0 * cos(3 * t) - cos(4 * t)
        pts.add(Offset(0.5f + 0.44f * (x / 17.0).toFloat(), 0.52f - 0.40f * (y / 17.0).toFloat()))
    }
    return pts
}

private fun abs(v: Int): Int = if (v < 0) -v else v
