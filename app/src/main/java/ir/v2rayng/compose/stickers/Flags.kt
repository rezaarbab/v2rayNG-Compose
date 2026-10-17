package ir.v2rayng.compose.stickers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

object CountryFlags {

    val list: List<Sticker> = build().let { it }

    fun byCode(code: String): Int {
        val key = code.lowercase().trim()
        return list.indexOfFirst { it.id == key }
    }
}

private fun hband(y0: Float, y1: Float, color: Color) = FlagBand(
    color,
    listOf(ToyPoly(chaikin(listOf(Offset(0.05f, y0), Offset(0.95f, y0), Offset(0.95f, y1), Offset(0.05f, y1)), 2))),
)

private fun vband(x0: Float, x1: Float, color: Color) = FlagBand(
    color,
    listOf(ToyPoly(chaikin(listOf(Offset(x0, 0.10f), Offset(x1, 0.10f), Offset(x1, 0.90f), Offset(x0, 0.90f)), 2))),
)

private fun rect(x0: Float, y0: Float, x1: Float, y1: Float, color: Color) = FlagBand(
    color,
    listOf(ToyPoly(listOf(Offset(x0, y0), Offset(x1, y0), Offset(x1, y1), Offset(x0, y1)))),
)

private fun cband(color: Color, vararg cs: ToyCircle) = FlagBand(color, cs.toList())

private fun build(): List<Sticker> {
    val baseRounded = ToyPoly(chaikin(listOf(Offset(0.06f, 0.10f), Offset(0.94f, 0.10f), Offset(0.94f, 0.90f), Offset(0.06f, 0.90f)), 4))

    fun flag(id: String, name: String, base: Color, bands: List<FlagBand>, phase: Float): Sticker {
        val art = FlagArt(base, listOf(FlagBand(base, listOf(baseRounded))) + bands, phase)
        return Sticker(id, name, art)
    }

    val red = Color(0xFFE03C31)
    val blue = Color(0xFF2C5BD6)
    val yellow = Color(0xFFF7CE1B)
    val white = Color(0xFFF7F7F2)
    val green = Color(0xFF1F9D55)
    val navy = Color(0xFF12203A)

    return listOf(
        flag("tr", "ترکیه", red, listOf(
            cband(white, ToyCircle(0.42f, 0.5f, 0.20f)),
            cband(red, ToyCircle(0.485f, 0.5f, 0.205f)),
            cband(white, ToyCircle(0.68f, 0.5f, 0.055f)),
        ), 0.07f),
        flag("de", "آلمان", Color(0xFF23262B), listOf(
            hband(0.366f, 0.634f, red),
            hband(0.634f, 0.90f, yellow),
        ), 0.14f),
        flag("fr", "فرانسه", white, listOf(
            vband(0.05f, 0.35f, blue),
            vband(0.65f, 0.95f, red),
        ), 0.21f),
        flag("it", "ایتالیا", white, listOf(
            vband(0.05f, 0.35f, green),
            vband(0.65f, 0.95f, red),
        ), 0.28f),
        flag("nl", "هلند", white, listOf(
            hband(0.10f, 0.366f, red),
            hband(0.634f, 0.90f, navy),
        ), 0.35f),
        flag("ru", "روسیه", white, listOf(
            hband(0.366f, 0.634f, blue),
            hband(0.634f, 0.90f, red),
        ), 0.42f),
        flag("jp", "ژاپن", white, listOf(
            cband(Color(0xFFD9344A), ToyCircle(0.5f, 0.5f, 0.26f)),
        ), 0.49f),
        flag("cn", "چین", red, listOf(
            cband(yellow, ToyCircle(0.33f, 0.38f, 0.13f)),
            cband(yellow, ToyCircle(0.52f, 0.24f, 0.05f)),
            cband(yellow, ToyCircle(0.56f, 0.36f, 0.05f)),
            cband(yellow, ToyCircle(0.56f, 0.50f, 0.05f)),
        ), 0.56f),
        flag("us", "آمریکا", red, listOf(
            hband(0.243f, 0.377f, white),
            hband(0.511f, 0.645f, white),
            hband(0.779f, 0.90f, white),
            rect(0.06f, 0.105f, 0.46f, 0.50f, blue),
            cband(white, ToyCircle(0.14f, 0.22f, 0.035f), ToyCircle(0.28f, 0.22f, 0.035f), ToyCircle(0.21f, 0.36f, 0.035f), ToyCircle(0.36f, 0.36f, 0.035f)),
        ), 0.63f),
        flag("ae", "امارات", green, listOf(
            hband(0.366f, 0.634f, white),
            hband(0.634f, 0.90f, Color(0xFF23262B)),
            vband(0.05f, 0.28f, red),
        ), 0.70f),
        flag("in", "هند", Color(0xFFFF9933), listOf(
            hband(0.366f, 0.634f, white),
            hband(0.634f, 0.90f, green),
            cband(navy, ToyCircle(0.5f, 0.5f, 0.09f)),
        ), 0.77f),
        flag("ir", "ایران", white, listOf(
            hband(0.10f, 0.366f, green),
            hband(0.634f, 0.90f, red),
            cband(red, ToyCircle(0.5f, 0.5f, 0.08f)),
        ), 0.84f),
        flag("se", "سوئد", blue, listOf(
            hband(0.40f, 0.60f, yellow),
            vband(0.30f, 0.44f, yellow),
        ), 0.91f),
    )
}
