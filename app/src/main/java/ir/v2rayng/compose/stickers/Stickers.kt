package ir.v2rayng.compose.stickers

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Immutable
enum class StickerMood { NORMAL, HAPPY, WOBBLE, SLEEP, SAD }

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
data class ImageArt(
    val color: Color,
    val emoji: String = "",
    val imageB64: String = "",
    val a: Float = 1f,
    val b: Float = 1f,
    val eyes: Boolean = false,
    val phase: Float = 0f,
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
        "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Rose", "Storm", "Lime", "Tutti",
        "Rain", "Moonbeam", "Cloudy", "Sunny", "Whisper", "Nightingale", "Basil", "Saffron", "Tangerine", "Mango",
        "Guava", "Kiwi", "Fig", "Melon", "Honeydew", "Barberry", "Blossom", "Rosewater", "Speck", "Echo",
        "Wave", "Shell", "Coral", "Pebble", "Tree", "Cave", "Aurora", "Breeze", "Spring", "Mirage",
        "Nova", "Varia", "Typhoon", "Thunder", "Bolt", "Dew", "Ice", "Snow", "Aura", "Pinwheel",
        "Coconut", "Almond", "Pistachio", "Walnut", "Hazel", "Raisin", "Rice", "Yogurt", "Honey", "Nectar",
        "Bubbles", "Comet", "Pudding", "Mochi", "Waffle", "Cookie", "Muffin", "Donut", "Candy", "Jelly",
        "Peachy", "Cherry", "Berry", "Plum", "Olive", "Cocoa", "Minty", "Latte", "Mocha", "Crema",
        "Sunny-D", "Fizzy", "Poppy", "Dandy", "Ziggy", "Bouncy", "Wobbly", "Squishy", "Squeezy", "Stretchy",
        "Wiggles", "Jiggles", "Bloop", "Bleep", "Blip", "Pip", "Pop", "Boop", "Bounce", "Boing",
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

    private val shapeNames = listOf(
        "Drop", "Triangle", "Cloud", "Ball", "Capsule", "Bean",
        "Fire", "Star", "Heart", "Bolt", "Moon", "Cup", "Rocket", "Balloon", "Shroom", "Gem",
    )

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

    private val fireOps: List<ToyOp> = listOf(
        ToyPoly(chaikin(listOf(
            Offset(0.50f, 0.05f), Offset(0.63f, 0.15f), Offset(0.58f, 0.26f), Offset(0.74f, 0.33f),
            Offset(0.86f, 0.51f), Offset(0.80f, 0.71f), Offset(0.65f, 0.88f), Offset(0.50f, 0.93f),
            Offset(0.35f, 0.88f), Offset(0.20f, 0.71f), Offset(0.14f, 0.51f), Offset(0.26f, 0.33f),
            Offset(0.42f, 0.26f), Offset(0.37f, 0.15f),
        ))),
    )

    private val starOps: List<ToyOp> = listOf(
        ToyPoly(chaikin(listOf(
            Offset(0.50f, 0.05f), Offset(0.61f, 0.37f), Offset(0.95f, 0.37f), Offset(0.68f, 0.57f),
            Offset(0.78f, 0.92f), Offset(0.50f, 0.72f), Offset(0.22f, 0.92f), Offset(0.32f, 0.57f),
            Offset(0.05f, 0.37f), Offset(0.39f, 0.37f),
        ), 2)),
    )

    private val heartOps: List<ToyOp> = listOf(
        ToyPoly(chaikin(listOf(
            Offset(0.50f, 0.90f), Offset(0.31f, 0.78f), Offset(0.15f, 0.61f), Offset(0.11f, 0.43f),
            Offset(0.18f, 0.27f), Offset(0.34f, 0.21f), Offset(0.50f, 0.32f), Offset(0.66f, 0.21f),
            Offset(0.82f, 0.27f), Offset(0.89f, 0.43f), Offset(0.85f, 0.61f), Offset(0.69f, 0.78f),
        ))),
    )

    private val boltOps: List<ToyOp> = listOf(
        ToyPoly(chaikin(listOf(
            Offset(0.62f, 0.04f), Offset(0.30f, 0.50f), Offset(0.46f, 0.50f),
            Offset(0.36f, 0.96f), Offset(0.72f, 0.42f), Offset(0.55f, 0.42f), Offset(0.68f, 0.04f),
        ), 1)),
    )

    private val moonOps: List<ToyOp> = listOf(
        ToyPoly(chaikin(listOf(
            Offset(0.62f, 0.06f), Offset(0.42f, 0.10f), Offset(0.24f, 0.26f), Offset(0.16f, 0.50f),
            Offset(0.24f, 0.74f), Offset(0.42f, 0.90f), Offset(0.62f, 0.94f),
            Offset(0.52f, 0.82f), Offset(0.44f, 0.64f), Offset(0.42f, 0.50f), Offset(0.44f, 0.36f), Offset(0.52f, 0.18f),
        ), 2)),
    )

    private val cupOps: List<ToyOp> = listOf(
        ToyPoly(chaikin(listOf(
            Offset(0.24f, 0.30f), Offset(0.76f, 0.30f), Offset(0.80f, 0.40f),
            Offset(0.78f, 0.84f), Offset(0.68f, 0.92f), Offset(0.32f, 0.92f), Offset(0.22f, 0.84f), Offset(0.20f, 0.40f),
        ), 2)),
        ToyCircle(0.86f, 0.56f, 0.11f),
    )

    private val rocketOps: List<ToyOp> = listOf(
        ToyPoly(chaikin(listOf(
            Offset(0.50f, 0.04f), Offset(0.62f, 0.16f), Offset(0.66f, 0.34f), Offset(0.66f, 0.60f),
            Offset(0.80f, 0.82f), Offset(0.62f, 0.76f), Offset(0.56f, 0.86f), Offset(0.44f, 0.86f),
            Offset(0.38f, 0.76f), Offset(0.20f, 0.82f), Offset(0.34f, 0.60f), Offset(0.34f, 0.34f), Offset(0.38f, 0.16f),
        ), 2)),
    )

    private val balloonOps: List<ToyOp> = listOf(
        ToyCircle(0.50f, 0.36f, 0.30f),
        ToyPoly(chaikin(listOf(
            Offset(0.47f, 0.65f), Offset(0.53f, 0.65f), Offset(0.56f, 0.76f), Offset(0.50f, 0.88f),
            Offset(0.53f, 0.98f), Offset(0.47f, 0.98f), Offset(0.44f, 0.88f), Offset(0.50f, 0.76f),
        ), 1)),
    )

    private val shroomOps: List<ToyOp> = listOf(
        ToyPoly(chaikin(listOf(
            Offset(0.50f, 0.08f), Offset(0.70f, 0.14f), Offset(0.84f, 0.32f), Offset(0.86f, 0.46f),
            Offset(0.14f, 0.46f), Offset(0.16f, 0.32f), Offset(0.30f, 0.14f),
        ), 1)),
        ToyPoly(chaikin(listOf(
            Offset(0.34f, 0.46f), Offset(0.66f, 0.46f), Offset(0.62f, 0.90f), Offset(0.38f, 0.90f),
        ), 2)),
    )

    private val gemOps: List<ToyOp> = listOf(
        ToyPoly(chaikin(listOf(
            Offset(0.50f, 0.05f), Offset(0.73f, 0.10f), Offset(0.90f, 0.30f), Offset(0.68f, 0.58f),
            Offset(0.50f, 0.95f), Offset(0.32f, 0.58f), Offset(0.10f, 0.30f), Offset(0.27f, 0.10f),
        ), 1)),
    )

    private val shapeOps = listOf(
        dropOps, triangleOps, cloudOps, ballOps, pillOps, beanOps,
        fireOps, starOps, heartOps, boltOps, moonOps, cupOps, rocketOps, balloonOps, shroomOps, gemOps,
    )

    private val eyeSets = listOf(
        listOf(ToyEye(0.40f, 0.50f, 0.11f, 0.32f, -10f), ToyEye(0.60f, 0.50f, 0.11f, 0.32f, -4f)),
        listOf(ToyEye(0.42f, 0.56f, 0.10f, 0.30f, 0f), ToyEye(0.58f, 0.56f, 0.10f, 0.30f, 0f)),
        listOf(ToyEye(0.42f, 0.44f, 0.12f, 0.20f, 22f), ToyEye(0.60f, 0.48f, 0.12f, 0.20f, 26f)),
        listOf(ToyEye(0.42f, 0.40f, 0.10f, 0.30f, -22f), ToyEye(0.56f, 0.43f, 0.10f, 0.30f, 20f)),
        listOf(ToyEye(0.42f, 0.50f, 0.12f, 0.32f, 0f), ToyEye(0.58f, 0.50f, 0.12f, 0.32f, 0f)),
        listOf(ToyEye(0.45f, 0.50f, 0.10f, 0.30f, -28f), ToyEye(0.64f, 0.50f, 0.10f, 0.30f, 28f)),
        listOf(ToyEye(0.40f, 0.58f, 0.10f, 0.18f, -6f), ToyEye(0.60f, 0.58f, 0.10f, 0.18f, 6f)),
        listOf(ToyEye(0.41f, 0.50f, 0.09f, 0.16f, 0f), ToyEye(0.59f, 0.50f, 0.09f, 0.16f, 0f)),
        listOf(ToyEye(0.40f, 0.48f, 0.10f, 0.18f, -4f), ToyEye(0.60f, 0.48f, 0.10f, 0.18f, 4f)),
        listOf(ToyEye(0.46f, 0.30f, 0.07f, 0.12f, 0f), ToyEye(0.57f, 0.33f, 0.07f, 0.12f, 0f)),
        listOf(ToyEye(0.24f, 0.44f, 0.07f, 0.14f, 8f), ToyEye(0.35f, 0.46f, 0.07f, 0.14f, 8f)),
        listOf(ToyEye(0.40f, 0.58f, 0.09f, 0.16f, 0f), ToyEye(0.57f, 0.58f, 0.09f, 0.16f, 0f)),
        listOf(ToyEye(0.42f, 0.38f, 0.08f, 0.15f, 0f), ToyEye(0.58f, 0.38f, 0.08f, 0.15f, 0f)),
        listOf(ToyEye(0.40f, 0.31f, 0.09f, 0.16f, -4f), ToyEye(0.60f, 0.31f, 0.09f, 0.16f, 4f)),
        listOf(ToyEye(0.44f, 0.62f, 0.08f, 0.14f, 0f), ToyEye(0.58f, 0.62f, 0.08f, 0.14f, 0f)),
        listOf(ToyEye(0.38f, 0.36f, 0.09f, 0.15f, -6f), ToyEye(0.62f, 0.36f, 0.09f, 0.15f, 6f)),
    )

    private val toyColors = intArrayOf(
        0xFF8A8A8A.toInt(), 0xFF1E86F5.toInt(), 0xFFFA6E14.toInt(), 0xFF14171A.toInt(),
        0xFF0FCB6E.toInt(), 0xFF9C6233.toInt(), 0xFFE05C5C.toInt(), 0xFFF7CE1B.toInt(),
        0xFF7FD8E8.toInt(), 0xFFB39BF5.toInt(), 0xFF23262B.toInt(), 0xFFED8936.toInt(),
    )

    val toys: List<Sticker> = (0 until (shapeOps.size * toyColors.size)).map { idx ->
        val kind = idx % shapeOps.size
        val row = idx / shapeOps.size
        val colorIdx = (kind * 5 + row * 3 + 2) % toyColors.size
        val color = Color(toyColors[colorIdx].toLong())
        Sticker(
            "toy-$idx",
            shapeNames[kind],
            ToyArt(color, shapeOps[kind], eyeSets[kind], (idx * 0.09f) % 1f),
        )
    }

    private val sets = listOf(blobs, toys, ir.v2rayng.compose.stickers.CountryFlags.list)

    fun toyOps(kind: Int): List<ToyOp> = shapeOps[((kind % shapeOps.size) + shapeOps.size) % shapeOps.size]

    fun toyEyes(kind: Int): List<ToyEye> = eyeSets[((kind % eyeSets.size) + eyeSets.size) % eyeSets.size]

    fun setFor(style: Int): List<Sticker> = when (((style % 4) + 4) % 4) {
        0 -> blobs
        1 -> toys
        2 -> ir.v2rayng.compose.stickers.CountryFlags.list
        else -> CustomStickers.snapshot().ifEmpty { blobs }
    }

    fun byIndex(style: Int, i: Int): Sticker {
        val set = setFor(style)
        val n = set.size
        return set[((i % n) + n) % n]
    }

    /** pack -1 = follow the fallback style with fallbackIndex (legacy/default slots). */
    fun byPack(pack: Int, index: Int, fallbackStyle: Int, fallbackIndex: Int): Sticker {
        return if (pack in 0..3) {
            byIndex(pack, index)
        } else {
            byIndex(fallbackStyle, fallbackIndex)
        }
    }
}

fun tinted(sticker: Sticker, color: Long): Sticker {
    if (color < 0L) return sticker
    val c = Color(color)
    return when (val a = sticker.art) {
        is BlobArt -> sticker.copy(art = a.copy(color = c))
        is ToyArt -> sticker.copy(art = a.copy(color = c))
        is ImageArt -> sticker.copy(art = a.copy(color = c))
        is FlagArt -> sticker
    }
}
