package ir.v2rayng.compose.stickers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
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
) : StickerArt

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
) : StickerArt

sealed interface StickerArt
data class Sticker(
    val id: String,
    val name: String,
    val art: StickerArt,
)

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

    private val toyNames = listOf(
        "حباب", "توپ", "الماس", "برگ", "ستاره", "قلب", "نخود", "برنجک", "لوبیا", "نخیل",
        "نعناع", "زیتون", "آلبالو", "گیلاس", "انار", "انگور", "توت‌فرنگی", "نارنج", "هلو", "زردآلو",
        "آفتابگردان", "نیلوفر", "لاله", "شقایق", "بنفشه", "یاس", "نرگس", "مریم", "سنبل", "گلبرگ",
        "قارچ", "مکعب", "بیضی", "لوزی", "سپر", "کپسول", "کدو", "شیشه", "سبد", "زنگ",
        "چتر", "کلاه", "دکمه", "قیچی", "کلید", "ساعت", "تاب", "توتم", "بادبزن", "فانوس",
    )

    val toys: List<Sticker> = toyNames.mapIndexed { idx, name ->
        val classic = floatArrayOf(0f, 218f, 30f, 260f, 158f, 28f)
        val hue = if (idx < classic.size) classic[idx] else (((idx - classic.size) * 137.508f) % 360f)
        val color = Color.hsl(hue, 0.62f + 0.2f * (idx % 4) / 3f, 0.56f + 0.12f * (idx % 3) / 2f)
        val eyes = listOf(
            ToyEye(0.40f, 0.47f, 0.12f, 0.34f, if (idx % 2 == 0) -8f else 8f),
            ToyEye(0.60f, 0.47f, 0.12f, 0.34f, if (idx % 2 == 0) 8f else -8f),
        )
        val ops: List<ToyOp> = when (idx % 10) {
            0 -> cloudOps(4 + idx % 3)
            1 -> flowerOps(5 + idx % 4)
            2 -> listOf(ToyPoly(starPts(5 + idx % 4)))
            3 -> listOf(ToyPoly(ngonPts(5 + idx % 5)))
            4 -> pillOps(idx % 2 == 0)
            5 -> listOf(ToyPoly(gemPts()))
            6 -> eggOps()
            7 -> listOf(ToyPoly(dropPts()))
            8 -> listOf(ToyPoly(heartPts()))
            else -> listOf(ToyPoly(shieldPts()))
        }
        Sticker("toy-$idx", name, ToyArt(color, ops, eyes, (idx * 0.071f) % 1f))
    }

    private val sets = listOf(blobs, toys)

    fun setFor(style: Int): List<Sticker> = sets[((style % 2) + 2) % 2]

    fun byIndex(style: Int, i: Int): Sticker {
        val set = setFor(style)
        val n = set.size
        return set[((i % n) + n) % n]
    }
}

private fun cloudOps(n: Int): List<ToyOp> {
    val ops = ArrayList<ToyOp>(n)
    for (k in 0 until n) {
        val f = k.toFloat() / (n - 1).coerceAtLeast(1)
        val bump = sin((f * PI).toFloat())
        ops.add(ToyCircle(0.30f + 0.40f * f, 0.58f - 0.16f * bump, 0.18f + 0.08f * bump))
    }
    return ops
}

private fun flowerOps(k: Int): List<ToyOp> {
    val ops = ArrayList<ToyOp>(k + 1)
    for (i in 0 until k) {
        val th = ((2 * PI * i / k) + PI / 2).toFloat()
        ops.add(ToyCircle(0.5f + 0.28f * cos(th), 0.52f + 0.28f * sin(th), 0.15f))
    }
    ops.add(ToyCircle(0.5f, 0.52f, 0.17f))
    return ops
}

private fun starPts(spikes: Int = 5): List<Offset> {
    val pts = ArrayList<Offset>(spikes * 2)
    for (k in 0 until spikes * 2) {
        val ang = (-90.0 + (360.0 / (spikes * 2)) * k) * PI / 180.0
        val rad = if (k % 2 == 0) 0.46f else 0.20f
        pts.add(Offset(0.5f + (rad * cos(ang)).toFloat(), 0.52f + (rad * sin(ang)).toFloat()))
    }
    return pts
}

private fun ngonPts(n: Int): List<Offset> {
    val pts = ArrayList<Offset>(n)
    for (k in 0 until n) {
        val ang = (-90.0 + 360.0 * k / n) * PI / 180.0
        pts.add(Offset(0.5f + (0.42f * cos(ang)).toFloat(), 0.52f + (0.42f * sin(ang)).toFloat()))
    }
    return pts
}

private fun pillOps(horizontal: Boolean): List<ToyOp> = if (horizontal) {
    listOf(
        ToyCircle(0.32f, 0.5f, 0.28f), ToyCircle(0.68f, 0.5f, 0.28f),
        ToyPoly(listOf(Offset(0.32f, 0.22f), Offset(0.68f, 0.22f), Offset(0.68f, 0.78f), Offset(0.32f, 0.78f))),
    )
} else {
    listOf(
        ToyCircle(0.5f, 0.3f, 0.26f), ToyCircle(0.5f, 0.7f, 0.26f),
        ToyPoly(listOf(Offset(0.26f, 0.3f), Offset(0.74f, 0.3f), Offset(0.74f, 0.7f), Offset(0.26f, 0.7f))),
    )
}

private fun gemPts(): List<Offset> = listOf(
    Offset(0.5f, 0.08f), Offset(0.88f, 0.52f), Offset(0.5f, 0.96f), Offset(0.12f, 0.52f),
)

private fun eggOps(): List<ToyOp> = listOf(
    ToyCircle(0.5f, 0.40f, 0.30f),
    ToyCircle(0.5f, 0.62f, 0.30f),
    ToyCircle(0.5f, 0.72f, 0.26f),
)

private fun dropPts(): List<Offset> = listOf(
    Offset(0.5f, 0.05f), Offset(0.72f, 0.28f), Offset(0.86f, 0.55f),
    Offset(0.7f, 0.85f), Offset(0.5f, 0.96f), Offset(0.3f, 0.85f),
    Offset(0.14f, 0.55f), Offset(0.28f, 0.28f),
)

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

private fun shieldPts(): List<Offset> = listOf(
    Offset(0.5f, 0.05f), Offset(0.9f, 0.2f), Offset(0.9f, 0.55f),
    Offset(0.5f, 0.95f), Offset(0.1f, 0.55f), Offset(0.1f, 0.2f),
)
