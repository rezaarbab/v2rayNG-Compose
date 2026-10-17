package ir.v2rayng.compose.stickers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

object CountryFlags {

    val list: List<Sticker> = build()

    fun byCode(code: String): Int {
        val key = code.lowercase().trim()
        return list.indexOfFirst { it.id == key }
    }
}

private val RED = Color(0xFFE03C31)
private val BLUE = Color(0xFF2C5BD6)
private val NAVY = Color(0xFF12203A)
private val WHITE = Color(0xFFF7F7F2)
private val GOLD = Color(0xFFF7CE1B)
private val GREEN = Color(0xFF1F9D55)

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

private fun triH(a: Color, m: Color, b: Color): List<FlagBand> = listOf(
    hband(0.366f, 0.634f, m),
    hband(0.634f, 0.90f, b),
)

private fun triV(l: Color, m: Color, r: Color, base: Color): List<FlagBand> = listOf(
    vband(0.05f, 0.35f, l),
    vband(0.65f, 0.95f, r),
)

private fun nordic(base: Color, cross: Color): List<FlagBand> = listOf(
    hband(0.40f, 0.60f, cross),
    vband(0.28f, 0.42f, cross),
)

private fun miniJack(): List<FlagBand> = listOf(
    rect(0.06f, 0.105f, 0.40f, 0.42f, WHITE),
    rect(0.06f, 0.105f, 0.40f, 0.42f, BLUE),
    rect(0.205f, 0.105f, 0.255f, 0.42f, WHITE),
    rect(0.06f, 0.235f, 0.40f, 0.285f, WHITE),
    rect(0.215f, 0.105f, 0.245f, 0.42f, RED),
    rect(0.06f, 0.245f, 0.40f, 0.275f, RED),
)

private fun build(): List<Sticker> {
    val baseRounded = FlagBand(WHITE, listOf(ToyPoly(chaikin(listOf(Offset(0.06f, 0.10f), Offset(0.94f, 0.10f), Offset(0.94f, 0.90f), Offset(0.06f, 0.90f)), 4))))

    fun flag(id: String, name: String, base: Color, bands: List<FlagBand>, phase: Float): Sticker {
        val art = FlagArt(base, listOf(baseRounded) + bands, phase)
        return Sticker(id, name, art)
    }

    var i = 0
    fun ph(): Float = ((i++ * 0.069f) % 1f)

    return listOf(
        flag("tr", "ترکیه", RED, listOf(
            cband(WHITE, ToyCircle(0.42f, 0.5f, 0.20f)),
            cband(RED, ToyCircle(0.485f, 0.5f, 0.205f)),
            cband(WHITE, ToyCircle(0.68f, 0.5f, 0.055f)),
        ), ph()),
        flag("de", "آلمان", Color(0xFF23262B), listOf(
            hband(0.366f, 0.634f, RED),
            hband(0.634f, 0.90f, GOLD),
        ), ph()),
        flag("fr", "فرانسه", WHITE, triV(BLUE, WHITE, RED, WHITE), ph()),
        flag("it", "ایتالیا", WHITE, triV(GREEN, WHITE, RED, WHITE), ph()),
        flag("nl", "هلند", RED, triH(RED, WHITE, NAVY).drop(0), ph()),
        flag("ru", "روسیه", WHITE, triH(WHITE, BLUE, RED), ph()),
        flag("jp", "ژاپن", WHITE, listOf(
            cband(Color(0xFFD9344A), ToyCircle(0.5f, 0.5f, 0.26f)),
        ), ph()),
        flag("cn", "چین", RED, listOf(
            cband(GOLD, ToyCircle(0.33f, 0.38f, 0.13f)),
            cband(GOLD, ToyCircle(0.52f, 0.24f, 0.05f)),
            cband(GOLD, ToyCircle(0.56f, 0.36f, 0.05f)),
            cband(GOLD, ToyCircle(0.56f, 0.50f, 0.05f)),
        ), ph()),
        flag("us", "آمریکا", RED, listOf(
            hband(0.243f, 0.377f, WHITE),
            hband(0.511f, 0.645f, WHITE),
            hband(0.779f, 0.90f, WHITE),
            rect(0.06f, 0.105f, 0.46f, 0.50f, BLUE),
            cband(WHITE, ToyCircle(0.14f, 0.22f, 0.035f), ToyCircle(0.28f, 0.22f, 0.035f), ToyCircle(0.21f, 0.36f, 0.035f), ToyCircle(0.36f, 0.36f, 0.035f)),
        ), ph()),
        flag("ae", "امارات", GREEN, listOf(
            hband(0.366f, 0.634f, WHITE),
            hband(0.634f, 0.90f, Color(0xFF23262B)),
            vband(0.05f, 0.28f, RED),
        ), ph()),
        flag("in", "هند", Color(0xFFFF9933), listOf(
            hband(0.366f, 0.634f, WHITE),
            hband(0.634f, 0.90f, GREEN),
            cband(NAVY, ToyCircle(0.5f, 0.5f, 0.09f)),
        ), ph()),
        flag("ir", "ایران", WHITE, listOf(
            hband(0.10f, 0.366f, GREEN),
            hband(0.634f, 0.90f, RED),
            cband(RED, ToyCircle(0.5f, 0.5f, 0.08f)),
        ), ph()),
        flag("se", "سوئد", BLUE, nordic(BLUE, GOLD), ph()),
        flag("gr", "یونان", BLUE, listOf(
            hband(0.10f, 0.26f, WHITE), hband(0.42f, 0.58f, WHITE),
            hband(0.74f, 0.90f, WHITE),
            rect(0.05f, 0.10f, 0.36f, 0.58f, BLUE),
            rect(0.16f, 0.10f, 0.24f, 0.58f, WHITE),
            rect(0.05f, 0.28f, 0.36f, 0.38f, WHITE),
        ), ph()),
        flag("es", "اسپانیا", RED, listOf(
            hband(0.28f, 0.72f, GOLD),
        ), ph()),
        flag("ua", "اوکراین", BLUE, listOf(
            hband(0.5f, 0.90f, GOLD),
        ), ph()),
        flag("pl", "لهستان", WHITE, listOf(
            hband(0.5f, 0.90f, RED),
        ), ph()),
        flag("cz", "چک", WHITE, listOf(
            hband(0.5f, 0.90f, RED),
            FlagBand(BLUE, listOf(ToyPoly(listOf(Offset(0.05f, 0.10f), Offset(0.34f, 0.50f), Offset(0.05f, 0.90f))))),
        ), ph()),
        flag("ro", "رومانی", NAVY, triV(BLUE, GOLD, RED, NAVY), ph()),
        flag("hu", "مجارستان", RED, triH(RED, WHITE, GREEN), ph()),
        flag("at", "اتریش", WHITE, listOf(
            hband(0.366f, 0.634f, RED),
        ), ph()),
        flag("ch", "سوئیس", RED, listOf(
            rect(0.435f, 0.22f, 0.565f, 0.78f, WHITE),
            rect(0.24f, 0.435f, 0.76f, 0.565f, WHITE),
        ), ph()),
        flag("dk", "دانمارک", RED, nordic(RED, WHITE), ph()),
        flag("fi", "فنلاند", WHITE, nordic(WHITE, BLUE), ph()),
        flag("pt", "پرتغال", RED, listOf(
            vband(0.05f, 0.38f, GREEN),
            cband(GOLD, ToyCircle(0.33f, 0.5f, 0.10f)),
        ), ph()),
        flag("be", "بلژیک", Color(0xFF23262B), triV(Color(0xFF23262B), GOLD, RED, Color(0xFF23262B)), ph()),
        flag("id", "اندونزی", RED, listOf(
            hband(0.5f, 0.90f, WHITE),
        ), ph()),
        flag("pk", "پاکستان", GREEN, listOf(
            vband(0.05f, 0.24f, WHITE),
            cband(WHITE, ToyCircle(0.55f, 0.5f, 0.16f)),
            cband(GREEN, ToyCircle(0.60f, 0.5f, 0.17f)),
            cband(WHITE, ToyCircle(0.70f, 0.36f, 0.05f)),
        ), ph()),
        flag("bd", "بنگلادش", GREEN, listOf(
            cband(RED, ToyCircle(0.46f, 0.5f, 0.22f)),
        ), ph()),
        flag("kr", "کره", WHITE, listOf(
            cband(NAVY, ToyCircle(0.5f, 0.5f, 0.17f)),
            cband(RED, ToyCircle(0.5f, 0.44f, 0.13f)),
        ), ph()),
        flag("ca", "کانادا", WHITE, listOf(
            vband(0.05f, 0.30f, RED),
            vband(0.70f, 0.95f, RED),
            cband(RED, ToyCircle(0.5f, 0.5f, 0.10f)),
        ), ph()),
        flag("au", "استرالیا", NAVY, miniJack() + listOf(
            cband(WHITE, ToyCircle(0.64f, 0.26f, 0.05f), ToyCircle(0.78f, 0.40f, 0.05f), ToyCircle(0.64f, 0.60f, 0.05f), ToyCircle(0.82f, 0.78f, 0.05f)),
        ), ph()),
        flag("nz", "نیوزیلند", NAVY, miniJack() + listOf(
            cband(RED, ToyCircle(0.64f, 0.26f, 0.055f), ToyCircle(0.78f, 0.42f, 0.055f), ToyCircle(0.66f, 0.60f, 0.055f)),
        ), ph()),
        flag("mx", "مکزیک", WHITE, triV(GREEN, WHITE, RED, WHITE) + listOf(
            cband(Color(0xFF7A5A2B), ToyCircle(0.5f, 0.5f, 0.10f)),
        ), ph()),
        flag("br", "برزیل", GREEN, listOf(
            FlagBand(GOLD, listOf(ToyPoly(chaikin(listOf(Offset(0.5f, 0.14f), Offset(0.90f, 0.5f), Offset(0.5f, 0.86f), Offset(0.10f, 0.5f)), 3)))),
            cband(BLUE, ToyCircle(0.5f, 0.5f, 0.16f)),
            cband(WHITE, ToyCircle(0.46f, 0.44f, 0.04f)),
        ), ph()),
        flag("ar", "آرژانتین", Color(0xFF75AADB), listOf(
            hband(0.366f, 0.634f, WHITE),
            cband(GOLD, ToyCircle(0.5f, 0.5f, 0.09f)),
        ), ph()),
        flag("sg", "سنگاپور", RED, listOf(
            hband(0.5f, 0.90f, WHITE),
            cband(WHITE, ToyCircle(0.34f, 0.30f, 0.12f)),
            cband(RED, ToyCircle(0.40f, 0.30f, 0.13f)),
        ), ph()),
    )
}
