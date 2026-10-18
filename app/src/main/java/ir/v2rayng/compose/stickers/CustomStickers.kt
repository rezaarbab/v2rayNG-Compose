package ir.v2rayng.compose.stickers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

data class CustomStickerDef(
    val id: String,
    val name: String,
    val kind: Int,
    val color: Long = 0xFF2C5BD6,
    val blobA: Float = 1f,
    val blobB: Float = 1f,
    val toyShape: Int = 0,
    val flagBands: String = "0xFF1F9D55,0xFFF7F7F2,0xFFE03C31",
    val flagVertical: Boolean = false,
    val emblem: Int = 0,
    val emblemColor: Long = 0xFFF7CE1B,
    val speed: Float = 1f,
    val size: Int = 190,
) {
    fun bandColors(): List<Long> = flagBands.split(",").mapNotNull { it.trim().toLongOrNull() }

    fun toSticker(): Sticker {
        val phase = ((id.hashCode() and 0x3FF) % 997) / 997f
        return when (kind) {
            1 -> Sticker(
                "cus-$id",
                name,
                ToyArt(Color(color), V2Stickers.toyOps(toyShape), V2Stickers.toyEyes(toyShape), phase),
            )
            2 -> Sticker("cus-$id", name, FlagArt(Color(bandColors().firstOrNull() ?: 0xFF23262B), flagBands(), phase))
            else -> Sticker(
                "cus-$id",
                name,
                BlobArt(Color(color), blobA, blobB, (id.hashCode() % 628) / 100f, (id.hashCode() % 314) / 50f, phase),
            )
        }
    }

    private fun flagBands(): List<FlagBand> {
        val colors = bandColors().ifEmpty { listOf(0xFF23262B) }
        val bands = ArrayList<FlagBand>()
        val n = colors.size
        for (i in 0 until n) {
            val c = Color(colors[i])
            val ops = if (flagVertical) {
                val x0 = 0.03f + i * (0.94f / n)
                val x1 = 0.03f + (i + 1) * (0.94f / n)
                listOf(ToyPoly(straightRect(x0, 0.03f, x1, 0.97f)))
            } else {
                val y0 = 0.03f + i * (0.94f / n)
                val y1 = 0.03f + (i + 1) * (0.94f / n)
                listOf(ToyPoly(straightRect(0.03f, y0, 0.97f, y1)))
            }
            bands.add(FlagBand(c, ops))
        }
        if (emblem > 0) {
            val hole = Color(colors[colors.size / 2])
            bands.add(FlagBand(Color(emblemColor), emblemOps(emblem, hole)))
        }
        return bands
    }
}

internal fun straightRect(x0: Float, y0: Float, x1: Float, y1: Float): List<Offset> = listOf(
    Offset(x0, y0), Offset(x1, y0), Offset(x1, y1), Offset(x0, y1),
)

internal fun starPts(cx: Float, cy: Float, r: Float): List<Offset> {
    val pts = ArrayList<Offset>(10)
    for (i in 0 until 10) {
        val ang = Math.PI * i / 5.0 - Math.PI / 2.0
        val rr = if (i % 2 == 0) r.toDouble() else (r * 0.42f).toDouble()
        pts.add(Offset((cx + rr * Math.cos(ang)).toFloat(), (cy + rr * Math.sin(ang)).toFloat()))
    }
    return pts
}

internal fun scaledMoon(cx: Float, cy: Float, s: Float): List<Offset> {
    val src = listOf(
        Offset(0.62f, 0.06f), Offset(0.42f, 0.10f), Offset(0.24f, 0.26f), Offset(0.16f, 0.50f),
        Offset(0.24f, 0.74f), Offset(0.42f, 0.90f), Offset(0.62f, 0.94f),
        Offset(0.52f, 0.82f), Offset(0.44f, 0.64f), Offset(0.42f, 0.50f), Offset(0.44f, 0.36f), Offset(0.52f, 0.18f),
    )
    return src.map { Offset(cx + (it.x - 0.5f) * s, cy + (it.y - 0.5f) * s) }
}

internal fun emblemOps(kind: Int, hole: Color): List<ToyOp> = when (kind) {
    1 -> listOf(ToyCircle(0.5f, 0.5f, 0.17f))
    2 -> listOf(ToyPoly(chaikin(starPts(0.5f, 0.5f, 0.22f), 1)))
    3 -> listOf(ToyPoly(chaikin(scaledMoon(0.5f, 0.5f, 0.5f), 1)))
    4 -> listOf(
        ToyPoly(straightRect(0.44f, 0.24f, 0.56f, 0.76f)),
        ToyPoly(straightRect(0.30f, 0.44f, 0.70f, 0.56f)),
    )
    5 -> listOf(
        ToyPoly(
            chaikin(
                listOf(
                    Offset(0.5f, 0.26f), Offset(0.61f, 0.29f), Offset(0.68f, 0.38f),
                    Offset(0.57f, 0.51f), Offset(0.5f, 0.74f), Offset(0.43f, 0.51f),
                    Offset(0.32f, 0.38f), Offset(0.39f, 0.29f),
                ),
                1,
            ),
        ),
    )
    else -> emptyList()
}

fun CustomStickerDef.suggestion(): String = when (kind) {
    1 -> "Toy pack"
    2 -> "Flag pack"
    else -> "Blob pack"
}

object CustomStickers {
    @Volatile
    private var defs: List<CustomStickerDef> = emptyList()

    @Volatile
    private var snap: List<Sticker> = emptyList()

    private var store: ir.v2rayng.compose.data.SettingsStore? = null

    fun init(context: android.content.Context) {
        if (store == null) {
            store = ir.v2rayng.compose.data.SettingsStore(context.applicationContext)
            reload()
        }
    }

    fun snapshot(): List<Sticker> = snap

    fun all(): List<CustomStickerDef> = defs

    fun speedOf(sticker: Sticker): Float =
        defs.firstOrNull { "cus-${it.id}" == sticker.id }?.speed ?: 1f

    fun sizeOf(sticker: Sticker): Int =
        defs.firstOrNull { "cus-${it.id}" == sticker.id }?.size ?: 0

    fun upsert(def: CustomStickerDef) {
        val cur = defs.toMutableList()
        val i = cur.indexOfFirst { it.id == def.id }
        if (i >= 0) cur[i] = def else cur.add(def)
        defs = cur
        rebuild()
        save()
    }

    fun delete(defId: String) {
        defs = defs.filter { it.id != defId }
        rebuild()
        save()
    }

    private fun rebuild() {
        snap = defs.map { it.toSticker() }
    }

    private fun save() {
        val arr = JSONArray()
        for (d in defs) {
            arr.put(
                JSONObject()
                    .put("id", d.id)
                    .put("name", d.name)
                    .put("kind", d.kind)
                    .put("color", d.color)
                    .put("blobA", d.blobA.toDouble())
                    .put("blobB", d.blobB.toDouble())
                    .put("toyShape", d.toyShape)
                    .put("flagBands", d.flagBands)
                    .put("flagVertical", d.flagVertical)
                    .put("emblem", d.emblem)
                    .put("emblemColor", d.emblemColor)
                    .put("speed", d.speed.toDouble())
                    .put("size", d.size),
            )
        }
        store?.saveCustom(arr.toString())
    }

    private fun reload() {
        val raw = store?.loadCustom() ?: return
        try {
            val arr = JSONArray(raw)
            val out = ArrayList<CustomStickerDef>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    CustomStickerDef(
                        id = o.optString("id"),
                        name = o.optString("name", "Mine"),
                        kind = o.optInt("kind", 0),
                        color = o.optLong("color", 0xFF2C5BD6),
                        blobA = o.optDouble("blobA", 1.0).toFloat(),
                        blobB = o.optDouble("blobB", 1.0).toFloat(),
                        toyShape = o.optInt("toyShape", 0),
                        flagBands = o.optString("flagBands", "0xFF1F9D55,0xFFF7F7F2,0xFFE03C31"),
                        flagVertical = o.optBoolean("flagVertical", false),
                        emblem = o.optInt("emblem", 0),
                        emblemColor = o.optLong("emblemColor", 0xFFF7CE1B),
                        speed = o.optDouble("speed", 1.0).toFloat(),
                        size = o.optInt("size", 190),
                    ),
                )
            }
            defs = out
            rebuild()
        } catch (_: Exception) {
        }
    }
}
