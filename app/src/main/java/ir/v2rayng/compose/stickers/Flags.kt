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
private val BLACK = Color(0xFF23262B)
private val SKY = Color(0xFF75AADB)
private val ORANGE = Color(0xFFE77A1E)

private fun hb(y0: Float, y1: Float, color: Color) = FlagBand(
    color,
    listOf(ToyPoly(chaikin(listOf(Offset(0.03f, y0), Offset(0.97f, y0), Offset(0.97f, y1), Offset(0.03f, y1)), 2))),
)

private fun vb(x0: Float, x1: Float, color: Color) = FlagBand(
    color,
    listOf(ToyPoly(chaikin(listOf(Offset(x0, 0.08f), Offset(x1, 0.08f), Offset(x1, 0.92f), Offset(x0, 0.92f)), 2))),
)

private fun rc(x0: Float, y0: Float, x1: Float, y1: Float, color: Color) = FlagBand(
    color,
    listOf(ToyPoly(listOf(Offset(x0, y0), Offset(x1, y0), Offset(x1, y1), Offset(x0, y1)))),
)

private fun cb(color: Color, vararg cs: ToyCircle) = FlagBand(color, cs.toList())

private fun H3(t: Color, m: Color, b: Color) = listOf(hb(0.366f, 0.634f, m), hb(0.634f, 0.90f, b))
private fun V3(l: Color, m: Color, r: Color) = listOf(vb(0.05f, 0.35f, l), vb(0.65f, 0.95f, r))
private fun H2(t: Color) = listOf(hb(0.10f, 0.50f, t))
private fun V2(l: Color) = listOf(vb(0.50f, 0.95f, l))
private fun NV(base: Color, cross: Color) = listOf(hb(0.40f, 0.60f, cross), vb(0.28f, 0.42f, cross))
private fun DISK(d: Color, r: Float = 0.22f) = listOf(cb(d, ToyCircle(0.5f, 0.5f, r)))
private fun CROSS(c: Color) = listOf(rc(0.435f, 0.22f, 0.565f, 0.78f, c), rc(0.24f, 0.435f, 0.76f, 0.565f, c))
private fun TRIH(a: Color, m: Color, b: Color) = listOf(hb(0.366f, 0.634f, m), hb(0.634f, 0.90f, b))
private fun TRIV(l: Color, m: Color, r: Color) = listOf(vb(0.05f, 0.35f, l), vb(0.65f, 0.95f, r))

private fun miniUK(): List<FlagBand> = listOf(
    rc(0.06f, 0.105f, 0.40f, 0.42f, WHITE),
    rc(0.06f, 0.105f, 0.40f, 0.42f, BLUE),
    rc(0.205f, 0.105f, 0.255f, 0.42f, WHITE),
    rc(0.06f, 0.235f, 0.40f, 0.285f, WHITE),
    rc(0.215f, 0.105f, 0.245f, 0.42f, RED),
    rc(0.06f, 0.245f, 0.40f, 0.275f, RED),
)

private fun build(): List<Sticker> {
    val baseRounded = FlagBand(WHITE, listOf(ToyPoly(chaikin(listOf(Offset(0.04f, 0.08f), Offset(0.96f, 0.08f), Offset(0.96f, 0.92f), Offset(0.04f, 0.92f)), 3))))
    val out = ArrayList<Sticker>(210)

    fun f(code: String, name: String, base: Color, bands: List<FlagBand>) {
        val nm = EN[code] ?: name
        val art = FlagArt(base, listOf(baseRounded) + bands, (out.size * 0.031f) % 1f)
        out.add(Sticker(code, nm, art))
    }

    f("ad", "Ø¢Ù†Ø¯ÙˆØ±Ø§", BLUE, V3(BLUE, GOLD, RED))
    f("ae", "Ø§Ù…Ø§Ø±Ø§Øª", GREEN, listOf(hb(0.366f, 0.634f, WHITE), hb(0.634f, 0.90f, BLACK), vb(0.05f, 0.28f, RED)))
    f("af", "Ø§ÙØºØ§Ù†Ø³ØªØ§Ù†", BLACK, TRIH(BLACK, RED, GREEN))
    f("ag", "Ø¢Ù†ØªÛŒÚ¯ÙˆØ§ Ùˆ Ø¨Ø§Ø±Ø¨ÙˆØ¯Ø§", NAVY, listOf(hb(0.10f, 0.35f, NAVY), hb(0.35f, 0.55f, BLUE), hb(0.55f, 0.75f, WHITE), hb(0.75f, 0.90f, NAVY)))
    f("ai", "Ø¢Ù†Ú¯ÙˆÛŒÙ„Ø§", BLUE, miniUK())
    f("al", "Ø¢Ù„Ø¨Ø§Ù†ÛŒ", RED, listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.13f))))
    f("am", "Ø§Ø±Ù…Ù†Ø³ØªØ§Ù†", RED, TRIH(RED, BLUE, GOLD))
    f("ao", "Ø¢Ù†Ú¯ÙˆÙ„Ø§", RED, listOf(hb(0.5f, 0.90f, BLACK), cb(GOLD, ToyCircle(0.42f, 0.42f, 0.12f))))
    f("ar", "Ø¢Ø±Ú˜Ø§Ù†ØªÛŒÙ†", SKY, listOf(hb(0.366f, 0.634f, WHITE), cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("at", "Ø§ØªØ±ÛŒØ´", WHITE, listOf(hb(0.366f, 0.634f, RED)))
    f("au", "Ø§Ø³ØªØ±Ø§Ù„ÛŒØ§", NAVY, miniUK() + listOf(cb(WHITE, ToyCircle(0.64f, 0.26f, 0.05f), ToyCircle(0.78f, 0.40f, 0.05f), ToyCircle(0.64f, 0.60f, 0.05f), ToyCircle(0.82f, 0.78f, 0.05f))))
    f("aw", "Ø¢Ø±ÙˆØ¨Ø§", SKY, listOf(hb(0.62f, 0.68f, WHITE), hb(0.68f, 0.74f, RED), cb(WHITE, ToyCircle(0.16f, 0.30f, 0.06f), ToyCircle(0.24f, 0.38f, 0.06f))))
    f("ax", "Ø¬Ø²Ø§ÛŒØ± Ø¢Ù„Ù†Ø¯", BLUE, NV(BLUE, GOLD) + listOf(vb(0.31f, 0.39f, RED), hb(0.43f, 0.57f, RED)))
    f("az", "Ø¢Ø°Ø±Ø¨Ø§ÛŒØ¬Ø§Ù†", SKY, listOf(hb(0.366f, 0.634f, RED), hb(0.634f, 0.90f, GREEN), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("ba", "Ø¨ÙˆØ³Ù†ÛŒ Ùˆ Ù‡Ø±Ø²Ú¯ÙˆÛŒÙ†", NAVY, listOf(vb(0.05f, 0.55f, GOLD), hb(0.66f, 0.90f, NAVY)))
    f("bb", "Ø¨Ø§Ø±Ø¨Ø§Ø¯ÙˆØ³", BLUE, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("bd", "Ø¨Ù†Ú¯Ù„Ø§Ø¯Ø´", GREEN, DISK(RED, 0.24f))
    f("be", "Ø¨Ù„Ú˜ÛŒÚ©", BLACK, TRIV(BLACK, GOLD, RED))
    f("bf", "Ø¨ÙˆØ±Ú©ÛŒÙ†Ø§ÙØ§Ø³Ùˆ", RED, listOf(hb(0.5f, 0.90f, GREEN), cb(GOLD, ToyCircle(0.5f, 0.42f, 0.11f))))
    f("bg", "Ø¨Ù„ØºØ§Ø±Ø³ØªØ§Ù†", WHITE, TRIH(WHITE, GREEN, RED))
    f("bh", "Ø¨Ø­Ø±ÛŒÙ†", WHITE, listOf(vb(0.34f, 0.95f, RED)))
    f("bi", "Ø¨ÙˆØ±ÙˆÙ†Ø¯ÛŒ", GREEN, listOf(hb(0.366f, 0.634f, WHITE), cb(RED, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("bj", "Ø¨Ù†ÛŒÙ†", GREEN, listOf(vb(0.05f, 0.38f, GREEN), hb(0.366f, 0.634f, GOLD), hb(0.634f, 0.90f, RED)))
    f("bl", "Ø³Ù†Øª Ø¨Ø§Ø±ØªÙ„Ù…ÛŒ", NAVY, miniUK())
    f("bm", "Ø¨Ø±Ù…ÙˆØ¯Ø§", RED, miniUK())
    f("bn", "Ø¨Ø±ÙˆÙ†Ø¦ÛŒ", GOLD, listOf(hb(0.28f, 0.52f, WHITE), hb(0.52f, 0.76f, BLACK)))
    f("bo", "Ø¨ÙˆÙ„ÛŒÙˆÛŒ", RED, TRIH(RED, GOLD, GREEN))
    f("br", "Ø¨Ø±Ø²ÛŒÙ„", GREEN, listOf(hb(0.30f, 0.70f, GOLD), cb(BLUE, ToyCircle(0.5f, 0.5f, 0.14f)), cb(WHITE, ToyCircle(0.46f, 0.45f, 0.04f))))
    f("bs", "Ø¨Ø§Ù‡Ø§Ù…Ø§", SKY, listOf(hb(0.366f, 0.634f, GOLD), vb(0.05f, 0.30f, BLACK)))
    f("bt", "Ø¨ÙˆØªØ§Ù†", GOLD, listOf(cb(Color(0xFFE8663C), ToyCircle(0.5f, 0.5f, 0.16f))))
    f("bw", "Ø¨ÙˆØªØ³ÙˆØ§Ù†Ø§", SKY, listOf(hb(0.42f, 0.50f, BLACK), hb(0.50f, 0.58f, WHITE)))
    f("by", "Ø¨Ù„Ø§Ø±ÙˆØ³", RED, listOf(hb(0.66f, 0.90f, GREEN), vb(0.05f, 0.22f, WHITE)))
    f("bz", "Ø¨Ù„ÛŒØ²", NAVY, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.20f)), cb(BLUE, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("ca", "Ú©Ø§Ù†Ø§Ø¯Ø§", WHITE, listOf(vb(0.05f, 0.30f, RED), vb(0.70f, 0.95f, RED), cb(RED, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("cd", "Ú©Ù†Ú¯Ùˆ Ø¯Ù…ÙˆÚ©Ø±Ø§ØªÛŒÚ©", SKY, listOf(vb(0.62f, 0.72f, RED), cb(GOLD, ToyCircle(0.24f, 0.28f, 0.09f))))
    f("cf", "Ø¢ÙØ±ÛŒÙ‚Ø§ÛŒ Ù…Ø±Ú©Ø²ÛŒ", BLUE, listOf(hb(0.10f, 0.30f, BLUE), hb(0.30f, 0.50f, WHITE), hb(0.50f, 0.70f, GREEN), hb(0.70f, 0.90f, GOLD), vb(0.05f, 0.25f, RED)))
    f("cg", "Ú©Ù†Ú¯Ùˆ", GREEN, listOf(vb(0.62f, 0.95f, RED), vb(0.50f, 0.62f, GOLD)))
    f("ch", "Ø³ÙˆØ¦ÛŒØ³", RED, CROSS(WHITE))
    f("ci", "Ø³Ø§Ø­Ù„ Ø¹Ø§Ø¬", WHITE, TRIV(GOLD, WHITE, ORANGE))
    f("ck", "Ø¬Ø²Ø§ÛŒØ± Ú©ÙˆÚ©", BLUE, miniUK() + listOf(cb(WHITE, ToyCircle(0.7f, 0.5f, 0.07f))))
    f("cl", "Ø´ÛŒÙ„ÛŒ", WHITE, listOf(hb(0.5f, 0.90f, RED), rc(0.05f, 0.10f, 0.36f, 0.50f, BLUE), cb(WHITE, ToyCircle(0.20f, 0.30f, 0.07f))))
    f("cm", "Ú©Ø§Ù…Ø±ÙˆÙ†", GREEN, TRIV(GREEN, RED, GOLD) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("cn", "Ú†ÛŒÙ†", RED, listOf(
        cb(GOLD, ToyCircle(0.33f, 0.38f, 0.13f)),
        cb(GOLD, ToyCircle(0.52f, 0.24f, 0.05f)),
        cb(GOLD, ToyCircle(0.56f, 0.36f, 0.05f)),
        cb(GOLD, ToyCircle(0.56f, 0.50f, 0.05f)),
    ))
    f("co", "Ú©Ù„Ù…Ø¨ÛŒØ§", GOLD, listOf(hb(0.10f, 0.50f, GOLD), hb(0.50f, 0.75f, BLUE), hb(0.75f, 0.90f, RED)))
    f("cr", "Ú©Ø§Ø³ØªØ§Ø±ÛŒÚ©Ø§", BLUE, listOf(hb(0.24f, 0.36f, WHITE), hb(0.36f, 0.64f, RED), hb(0.64f, 0.76f, WHITE)))
    f("cu", "Ú©ÙˆØ¨Ø§", BLUE, listOf(hb(0.10f, 0.26f, WHITE), hb(0.42f, 0.58f, WHITE), hb(0.74f, 0.90f, WHITE), rc(0.05f, 0.10f, 0.42f, 0.56f, RED), cb(WHITE, ToyCircle(0.24f, 0.33f, 0.09f))))
    f("cv", "Ú©ÛŒÙ¾â€ŒÙˆØ±Ø¯", NAVY, listOf(hb(0.44f, 0.56f, WHITE), hb(0.56f, 0.68f, RED), cb(GOLD, ToyCircle(0.5f, 0.62f, 0.05f))))
    f("cw", "Ú©ÙˆØ±Ø§Ø³Ø§Ø¦Ùˆ", BLUE, listOf(hb(0.58f, 0.70f, GOLD), vb(0.05f, 0.22f, WHITE)))
    f("cx", "Ø¬Ø²ÛŒØ±Ù‡ Ú©Ø±ÛŒØ³Ù…Ø³", BLUE, listOf(cb(GREEN, ToyCircle(0.72f, 0.62f, 0.13f)), cb(GOLD, ToyCircle(0.28f, 0.32f, 0.10f))))
    f("cy", "Ù‚Ø¨Ø±Ø³", WHITE, listOf(cb(Color(0xFFB8763E), ToyCircle(0.5f, 0.52f, 0.10f))))
    f("cz", "Ú†Ú©", WHITE, listOf(hb(0.5f, 0.90f, RED), FlagBand(BLUE, listOf(ToyPoly(listOf(Offset(0.05f, 0.10f), Offset(0.34f, 0.50f), Offset(0.05f, 0.90f)))))))
    f("de", "Ø¢Ù„Ù…Ø§Ù†", BLACK, TRIH(BLACK, RED, GOLD))
    f("dj", "Ø¬ÛŒØ¨ÙˆØªÛŒ", SKY, listOf(hb(0.5f, 0.90f, GREEN), vb(0.05f, 0.40f, WHITE), cb(RED, ToyCircle(0.20f, 0.32f, 0.08f))))
    f("dk", "Ø¯Ø§Ù†Ù…Ø§Ø±Ú©", RED, NV(RED, WHITE))
    f("dm", "Ø¯ÙˆÙ…ÛŒÙ†ÛŒÚ©Ø§", GREEN, listOf(hb(0.366f, 0.634f, GOLD), hb(0.634f, 0.90f, BLACK), cb(RED, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("do", "Ø¬Ù…Ù‡ÙˆØ±ÛŒ Ø¯ÙˆÙ…ÛŒÙ†ÛŒÚ©Ù†", NAVY, listOf(rc(0.05f, 0.10f, 0.48f, 0.48f, BLUE), rc(0.52f, 0.10f, 0.95f, 0.48f, RED), rc(0.05f, 0.52f, 0.48f, 0.90f, RED), rc(0.52f, 0.52f, 0.95f, 0.90f, BLUE), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("dz", "Ø§Ù„Ø¬Ø²Ø§ÛŒØ±", GREEN, listOf(vb(0.50f, 0.95f, WHITE), cb(WHITE, ToyCircle(0.30f, 0.5f, 0.16f)), cb(RED, ToyCircle(0.34f, 0.5f, 0.13f))))
    f("ec", "Ø§Ú©ÙˆØ§Ø¯ÙˆØ±", GOLD, listOf(hb(0.5f, 0.75f, BLUE), hb(0.75f, 0.90f, RED), cb(GREEN, ToyCircle(0.5f, 0.38f, 0.16f))))
    f("ee", "Ø§Ø³ØªÙˆÙ†ÛŒ", NAVY, TRIH(NAVY, BLACK, WHITE))
    f("eg", "Ù…ØµØ±", RED, TRIH(RED, WHITE, BLACK) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("eh", "ØµØ­Ø±Ø§ÛŒ ØºØ±Ø¨ÛŒ", BLACK, TRIH(BLACK, WHITE, GREEN) + listOf(cb(RED, ToyCircle(0.30f, 0.5f, 0.13f))))
    f("er", "Ø§Ø±ÛŒØªØ±Ù‡", GREEN, listOf(vb(0.05f, 0.55f, BLUE), cb(RED, ToyCircle(0.32f, 0.5f, 0.12f))))
    f("es", "Ø§Ø³Ù¾Ø§Ù†ÛŒØ§", RED, listOf(hb(0.28f, 0.72f, GOLD)))
    f("et", "Ø§ØªÛŒÙˆÙ¾ÛŒ", GREEN, TRIH(GREEN, GOLD, RED) + listOf(cb(BLUE, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("fi", "ÙÙ†Ù„Ø§Ù†Ø¯", WHITE, NV(WHITE, BLUE))
    f("fj", "ÙÛŒØ¬ÛŒ", SKY, miniUK())
    f("fm", "Ù…ÛŒÚ©Ø±ÙˆÙ†Ø²ÛŒ", SKY, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("fo", "Ø¬Ø²Ø§ÛŒØ± ÙØ§Ø±Ùˆ", WHITE, NV(WHITE, RED) + listOf(vb(0.32f, 0.38f, NAVY)))
    f("fr", "ÙØ±Ø§Ù†Ø³Ù‡", WHITE, V3(BLUE, WHITE, RED))
    f("ga", "Ú¯Ø§Ø¨Ù†", GREEN, TRIH(GREEN, GOLD, BLUE))
    f("gb", "Ø¨Ø±ÛŒØªØ§Ù†ÛŒØ§", NAVY, listOf(
        rc(0.05f, 0.10f, 0.95f, 0.90f, WHITE),
        rc(0.42f, 0.10f, 0.58f, 0.90f, WHITE), rc(0.05f, 0.42f, 0.95f, 0.58f, WHITE),
        rc(0.45f, 0.10f, 0.55f, 0.90f, RED), rc(0.05f, 0.45f, 0.95f, 0.55f, RED),
    ))
    f("gd", "Ú¯Ø±Ù†Ø§Ø¯Ø§", RED, listOf(hb(0.24f, 0.44f, GOLD), hb(0.44f, 0.62f, GREEN), hb(0.62f, 0.82f, GOLD), cb(RED, ToyCircle(0.5f, 0.53f, 0.08f))))
    f("ge", "Ú¯Ø±Ø¬Ø³ØªØ§Ù†", WHITE, CROSS(RED) + listOf(rc(0.30f, 0.16f, 0.40f, 0.26f, RED), rc(0.30f, 0.74f, 0.40f, 0.84f, RED), rc(0.60f, 0.16f, 0.70f, 0.26f, RED), rc(0.60f, 0.74f, 0.70f, 0.84f, RED)))
    f("gf", "Ú¯ÙˆÛŒØ§Ù† ÙØ±Ø§Ù†Ø³Ù‡", BLUE, TRIH(BLUE, WHITE, RED) + listOf(cb(RED, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("gh", "ØºÙ†Ø§", RED, TRIH(RED, GOLD, GREEN) + listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("gi", "Ø¬Ø¨Ù„â€ŒØ§Ù„Ø·Ø§Ø±Ù‚", WHITE, listOf(hb(0.62f, 0.90f, RED)))
    f("gl", "Ú¯Ø±ÛŒÙ†Ù„Ù†Ø¯", WHITE, listOf(hb(0.5f, 0.90f, RED), cb(WHITE, ToyCircle(0.38f, 0.5f, 0.20f)), cb(RED, ToyCircle(0.52f, 0.5f, 0.20f))))
    f("gm", "Ú¯Ø§Ù…Ø¨ÛŒØ§", RED, TRIH(RED, BLUE, GREEN) + listOf(hb(0.42f, 0.46f, WHITE), hb(0.54f, 0.58f, WHITE)))
    f("gn", "Ú¯ÛŒÙ†Ù‡", RED, TRIV(RED, GOLD, GREEN))
    f("gp", "Ú¯ÙˆØ§Ø¯Ù„ÙˆÙ¾", NAVY, TRIH(NAVY, WHITE, RED))
    f("gq", "Ú¯ÛŒÙ†Ù‡ Ø§Ø³ØªÙˆØ§ÛŒÛŒ", GREEN, TRIH(GREEN, WHITE, RED) + listOf(cb(SKY, ToyCircle(0.28f, 0.5f, 0.12f))))
    f("gr", "ÛŒÙˆÙ†Ø§Ù†", BLUE, listOf(
        hb(0.10f, 0.26f, WHITE), hb(0.42f, 0.58f, WHITE), hb(0.74f, 0.90f, WHITE),
        rc(0.05f, 0.10f, 0.36f, 0.58f, BLUE), rc(0.16f, 0.10f, 0.24f, 0.58f, WHITE), rc(0.05f, 0.28f, 0.36f, 0.38f, WHITE),
    ))
    f("gs", "Ø¬ÙˆØ±Ø¬ÛŒØ§ÛŒ Ø¬Ù†ÙˆØ¨ÛŒ", NAVY, miniUK())
    f("gt", "Ú¯ÙˆØ§ØªÙ…Ø§Ù„Ø§", SKY, TRIV(SKY, WHITE, SKY) + listOf(cb(GREEN, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("gu", "Ú¯ÙˆØ§Ù…", NAVY, listOf(cb(RED, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("gw", "Ú¯ÛŒÙ†Ù‡ Ø¨ÛŒØ³Ø§Ø¦Ùˆ", RED, listOf(vb(0.05f, 0.38f, RED), hb(0.366f, 0.634f, GOLD), hb(0.634f, 0.90f, GREEN), cb(BLACK, ToyCircle(0.20f, 0.5f, 0.10f))))
    f("gy", "Ú¯ÙˆÛŒØ§Ù†", GREEN, listOf(vb(0.05f, 0.40f, BLACK), vb(0.40f, 0.50f, WHITE), vb(0.50f, 0.60f, GOLD), vb(0.60f, 0.95f, GREEN), cb(RED, ToyCircle(0.35f, 0.5f, 0.11f))))
    f("hk", "Ù‡Ù†Ú¯â€ŒÚ©Ù†Ú¯", RED, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.13f))))
    f("hm", "Ø¬Ø²ÛŒØ±Ù‡ Ù‡Ø±Ø¯", NAVY, miniUK())
    f("hn", "Ù‡Ù†Ø¯ÙˆØ±Ø§Ø³", SKY, TRIH(SKY, WHITE, SKY) + listOf(cb(SKY, ToyCircle(0.5f, 0.5f, 0.08f))))
    f("hr", "Ú©Ø±ÙˆØ§Ø³ÛŒ", RED, TRIH(RED, WHITE, BLUE) + listOf(cb(RED, ToyCircle(0.5f, 0.44f, 0.10f))))
    f("ht", "Ù‡Ø§Ø¦ÛŒØªÛŒ", NAVY, listOf(rc(0.05f, 0.10f, 0.95f, 0.50f, NAVY), rc(0.05f, 0.50f, 0.95f, 0.90f, RED), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("hu", "Ù…Ø¬Ø§Ø±Ø³ØªØ§Ù†", RED, TRIH(RED, WHITE, GREEN))
    f("id", "Ø§Ù†Ø¯ÙˆÙ†Ø²ÛŒ", RED, H2(RED) + listOf(hb(0.5f, 0.90f, WHITE)))
    f("ie", "Ø§ÛŒØ±Ù„Ù†Ø¯", GREEN, TRIV(GREEN, WHITE, ORANGE))
    f("il", "Ø§Ø³Ø±Ø§Ø¦ÛŒÙ„", WHITE, listOf(hb(0.14f, 0.26f, BLUE), hb(0.74f, 0.86f, BLUE), cb(BLUE, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("im", "Ø¬Ø²ÛŒØ±Ù‡ Ù…Ù†", RED, miniUK())
    f("in", "Ù‡Ù†Ø¯", Color(0xFFFF9933), listOf(hb(0.366f, 0.634f, WHITE), hb(0.634f, 0.90f, GREEN), cb(NAVY, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("io", "Ù‚Ù„Ù…Ø±Ùˆ Ø§Ù‚ÛŒØ§Ù†ÙˆØ³ Ù‡Ù†Ø¯", NAVY, miniUK())
    f("iq", "Ø¹Ø±Ø§Ù‚", RED, TRIH(RED, WHITE, BLACK) + listOf(cb(GREEN, ToyCircle(0.5f, 0.56f, 0.05f))))
    f("ir", "Ø§ÛŒØ±Ø§Ù†", WHITE, listOf(hb(0.10f, 0.366f, GREEN), hb(0.634f, 0.90f, RED), cb(RED, ToyCircle(0.5f, 0.5f, 0.08f))))
    f("is", "Ø§ÛŒØ³Ù„Ù†Ø¯", BLUE, NV(BLUE, WHITE) + listOf(vb(0.31f, 0.39f, RED), hb(0.43f, 0.57f, RED)))
    f("it", "Ø§ÛŒØªØ§Ù„ÛŒØ§", WHITE, V3(GREEN, WHITE, RED))
    f("je", "Ø¬Ø±Ø³ÛŒ", WHITE, miniUK())
    f("jm", "Ø¬Ø§Ù…Ø§Ø¦ÛŒÚ©Ø§", GREEN, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.16f)), cb(BLACK, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("jo", "Ø§Ø±Ø¯Ù†", BLACK, TRIH(BLACK, WHITE, GREEN) + listOf(vb(0.05f, 0.38f, RED), cb(WHITE, ToyCircle(0.18f, 0.5f, 0.06f))))
    f("jp", "Ú˜Ø§Ù¾Ù†", WHITE, DISK(Color(0xFFD9344A), 0.26f))
    f("ke", "Ú©Ù†ÛŒØ§", BLACK, TRIH(BLACK, RED, GREEN) + listOf(hb(0.30f, 0.366f, WHITE), hb(0.634f, 0.70f, WHITE), cb(RED, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("kg", "Ù‚Ø±Ù‚ÛŒØ²Ø³ØªØ§Ù†", RED, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("kh", "Ú©Ø§Ù…Ø¨ÙˆØ¬", BLUE, listOf(hb(0.32f, 0.68f, RED), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("ki", "Ú©ÛŒØ±ÛŒØ¨Ø§ØªÛŒ", RED, listOf(hb(0.56f, 0.68f, WHITE), hb(0.68f, 0.78f, BLUE), cb(GOLD, ToyCircle(0.5f, 0.38f, 0.12f))))
    f("km", "Ú©ÙˆÙ…ÙˆØ±", GOLD, listOf(hb(0.10f, 0.325f, GOLD), hb(0.325f, 0.55f, WHITE), hb(0.55f, 0.775f, RED), hb(0.775f, 0.90f, NAVY), vb(0.05f, 0.30f, GREEN), cb(WHITE, ToyCircle(0.18f, 0.5f, 0.09f))))
    f("kn", "Ø³Ù†Øª Ú©ÛŒØªØ³", GREEN, listOf(hb(0.366f, 0.634f, BLACK), hb(0.634f, 0.90f, RED), cb(WHITE, ToyCircle(0.32f, 0.36f, 0.05f))))
    f("kp", "Ú©Ø±Ù‡ Ø´Ù…Ø§Ù„ÛŒ", BLUE, listOf(hb(0.16f, 0.24f, WHITE), hb(0.24f, 0.76f, RED), hb(0.76f, 0.84f, WHITE), cb(WHITE, ToyCircle(0.28f, 0.5f, 0.13f))))
    f("kr", "Ú©Ø±Ù‡ Ø¬Ù†ÙˆØ¨ÛŒ", WHITE, listOf(cb(NAVY, ToyCircle(0.5f, 0.5f, 0.15f)), cb(RED, ToyCircle(0.5f, 0.44f, 0.11f))))
    f("kw", "Ú©ÙˆÛŒØª", GREEN, TRIH(GREEN, WHITE, RED) + listOf(vb(0.05f, 0.30f, BLACK)))
    f("ky", "Ú©ÛŒÙ…Ù†", NAVY, miniUK())
    f("kz", "Ù‚Ø²Ø§Ù‚Ø³ØªØ§Ù†", SKY, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.12f)), vb(0.05f, 0.14f, GOLD)))
    f("la", "Ù„Ø§Ø¦ÙˆØ³", NAVY, listOf(hb(0.28f, 0.72f, RED), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("lb", "Ù„Ø¨Ù†Ø§Ù†", RED, TRIH(RED, WHITE, RED) + listOf(cb(GREEN, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("lc", "Ø³Ù†Øª Ù„ÙˆØ³ÛŒØ§", SKY, listOf(cb(WHITE, ToyCircle(0.5f, 0.38f, 0.10f)), cb(BLACK, ToyCircle(0.42f, 0.62f, 0.10f)), cb(GOLD, ToyCircle(0.58f, 0.62f, 0.10f))))
    f("li", "Ù„ÛŒØ®ØªÙ†â€ŒØ§Ø´ØªØ§ÛŒÙ†", NAVY, listOf(hb(0.5f, 0.90f, RED), cb(GOLD, ToyCircle(0.28f, 0.30f, 0.09f))))
    f("lk", "Ø³Ø±ÛŒâ€ŒÙ„Ø§Ù†Ú©Ø§", GOLD, listOf(vb(0.05f, 0.22f, GREEN), vb(0.22f, 0.28f, ORANGE), rc(0.32f, 0.10f, 0.95f, 0.90f, Color(0xFF8D2029))))
    f("lr", "Ù„ÛŒØ¨Ø±ÛŒØ§", RED, listOf(hb(0.10f, 0.24f, WHITE), hb(0.38f, 0.52f, WHITE), hb(0.66f, 0.80f, WHITE), rc(0.05f, 0.10f, 0.36f, 0.44f, NAVY), cb(WHITE, ToyCircle(0.20f, 0.27f, 0.07f))))
    f("ls", "Ù„Ø³ÙˆØªÙˆ", NAVY, TRIH(NAVY, WHITE, GREEN) + listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("lt", "Ù„ÛŒØªÙˆØ§Ù†ÛŒ", GOLD, TRIH(GOLD, GREEN, RED))
    f("lu", "Ù„ÙˆÚ©Ø²Ø§Ù…Ø¨ÙˆØ±Ú¯", RED, TRIH(RED, WHITE, SKY))
    f("lv", "Ù„ØªÙˆÙ†ÛŒ", Color(0xFF9E3039), listOf(hb(0.40f, 0.60f, WHITE)))
    f("ly", "Ù„ÛŒØ¨ÛŒ", RED, TRIH(RED, BLACK, GREEN) + listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("ma", "Ù…Ø±Ø§Ú©Ø´", RED, listOf(cb(GREEN, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("mc", "Ù…ÙˆÙ†Ø§Ú©Ùˆ", RED, H2(RED) + listOf(hb(0.5f, 0.90f, WHITE)))
    f("md", "Ù…ÙˆÙ„Ø¯Ø§ÙˆÛŒ", NAVY, TRIV(BLUE, GOLD, RED) + listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("me", "Ù…ÙˆÙ†ØªÙ‡â€ŒÙ†Ú¯Ø±Ùˆ", RED, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("mf", "Ø³Ù†Øª Ù…Ø§Ø±ØªÛŒÙ†", NAVY, miniUK())
    f("mg", "Ù…Ø§Ø¯Ø§Ú¯Ø§Ø³Ú©Ø§Ø±", WHITE, listOf(vb(0.05f, 0.34f, RED), hb(0.5f, 0.90f, GREEN)))
    f("mh", "Ø¬Ø²Ø§ÛŒØ± Ù…Ø§Ø±Ø´Ø§Ù„", NAVY, listOf(hb(0.36f, 0.44f, WHITE), hb(0.44f, 0.52f, SKY), cb(WHITE, ToyCircle(0.24f, 0.30f, 0.10f))))
    f("mk", "Ù…Ù‚Ø¯ÙˆÙ†ÛŒÙ‡ Ø´Ù…Ø§Ù„ÛŒ", RED, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.13f))))
    f("ml", "Ù…Ø§Ù„ÛŒ", GREEN, TRIV(GREEN, GOLD, RED))
    f("mm", "Ù…ÛŒØ§Ù†Ù…Ø§Ø±", GOLD, TRIH(GOLD, GREEN, RED) + listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("mn", "Ù…ØºÙˆÙ„Ø³ØªØ§Ù†", RED, TRIV(RED, BLUE, RED) + listOf(cb(GOLD, ToyCircle(0.17f, 0.5f, 0.09f))))
    f("mo", "Ù…Ø§Ú©Ø§Ø¦Ùˆ", GREEN, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.13f))))
    f("mp", "Ù…Ø§Ø±ÛŒØ§Ù†Ø§ÛŒ Ø´Ù…Ø§Ù„ÛŒ", NAVY, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.12f)), cb(SKY, ToyCircle(0.5f, 0.5f, 0.07f))))
    f("mq", "Ù…Ø§Ø±ØªÛŒÙ†ÛŒÚ©", NAVY, TRIH(NAVY, WHITE, RED))
    f("mr", "Ù…ÙˆØ±ÛŒØªØ§Ù†ÛŒ", GREEN, listOf(cb(GOLD, ToyCircle(0.5f, 0.40f, 0.11f)), hb(0.68f, 0.76f, GOLD)))
    f("ms", "Ù…ÙˆÙ†ØªØ³Ø±Ø§Øª", NAVY, miniUK())
    f("mt", "Ù…Ø§Ù„Øª", WHITE, listOf(vb(0.05f, 0.28f, RED)))
    f("mu", "Ù…ÙˆØ±ÛŒØ³", RED, listOf(hb(0.10f, 0.30f, RED), hb(0.30f, 0.50f, BLUE), hb(0.50f, 0.70f, GOLD), hb(0.70f, 0.90f, GREEN)))
    f("mv", "Ù…Ø§Ù„Ø¯ÛŒÙˆ", RED, listOf(rc(0.22f, 0.24f, 0.78f, 0.76f, GREEN), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("mw", "Ù…Ø§Ù„Ø§ÙˆÛŒ", BLACK, TRIH(BLACK, RED, GREEN) + listOf(cb(RED, ToyCircle(0.5f, 0.24f, 0.08f))))
    f("mx", "Ù…Ú©Ø²ÛŒÚ©", WHITE, TRIV(GREEN, WHITE, RED) + listOf(cb(Color(0xFF7A5A2B), ToyCircle(0.5f, 0.5f, 0.10f))))
    f("my", "Ù…Ø§Ù„Ø²ÛŒ", RED, listOf(hb(0.10f, 0.24f, WHITE), hb(0.38f, 0.52f, WHITE), hb(0.66f, 0.80f, WHITE), rc(0.05f, 0.10f, 0.44f, 0.56f, NAVY), cb(GOLD, ToyCircle(0.20f, 0.30f, 0.09f))))
    f("mz", "Ù…ÙˆØ²Ø§Ù…Ø¨ÛŒÚ©", GREEN, TRIH(GREEN, BLACK, GOLD) + listOf(cb(WHITE, ToyCircle(0.32f, 0.5f, 0.11f)), hb(0.30f, 0.366f, WHITE), hb(0.634f, 0.70f, WHITE)))
    f("na", "Ù†Ø§Ù…ÛŒØ¨ÛŒØ§", NAVY, listOf(hb(0.10f, 0.366f, NAVY), hb(0.366f, 0.634f, RED), hb(0.634f, 0.90f, GREEN), cb(GOLD, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("nc", "Ú©Ø§Ù„Ø¯ÙˆÙ†ÛŒØ§ÛŒ Ø¬Ø¯ÛŒØ¯", NAVY, miniUK())
    f("ne", "Ù†ÛŒØ¬Ø±", ORANGE, TRIH(ORANGE, WHITE, GREEN) + listOf(cb(ORANGE, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("nf", "Ø¬Ø²ÛŒØ±Ù‡ Ù†ÙˆØ±ÙÙˆÙ„Ú©", GREEN, listOf(vb(0.40f, 0.60f, WHITE), cb(GREEN, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("ng", "Ù†ÛŒØ¬Ø±ÛŒÙ‡", GREEN, V3(GREEN, WHITE, GREEN))
    f("ni", "Ù†ÛŒÚ©Ø§Ø±Ø§Ú¯ÙˆØ¦Ù‡", SKY, TRIH(SKY, WHITE, SKY) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("nl", "Ù‡Ù„Ù†Ø¯", RED, TRIH(RED, WHITE, NAVY))
    f("no", "Ù†Ø±ÙˆÚ˜", RED, NV(RED, WHITE) + listOf(vb(0.31f, 0.39f, NAVY), hb(0.43f, 0.57f, NAVY)))
    f("np", "Ù†Ù¾Ø§Ù„", NAVY, listOf(cb(RED, ToyCircle(0.4f, 0.5f, 0.18f)), cb(WHITE, ToyCircle(0.4f, 0.5f, 0.11f))))
    f("nr", "Ù†Ø§Ø¦ÙˆØ±Ùˆ", NAVY, listOf(hb(0.58f, 0.66f, GOLD), cb(WHITE, ToyCircle(0.30f, 0.32f, 0.09f))))
    f("nu", "Ù†ÛŒÙˆÙˆÛŒ", GOLD, miniUK())
    f("nz", "Ù†ÛŒÙˆØ²ÛŒÙ„Ù†Ø¯", NAVY, miniUK() + listOf(cb(RED, ToyCircle(0.64f, 0.26f, 0.055f), ToyCircle(0.78f, 0.42f, 0.055f), ToyCircle(0.66f, 0.60f, 0.055f))))
    f("om", "Ø¹Ù…Ø§Ù†", WHITE, TRIH(WHITE, RED, GREEN) + listOf(vb(0.05f, 0.30f, RED)))
    f("pa", "Ù¾Ø§Ù†Ø§Ù…Ø§", WHITE, listOf(rc(0.05f, 0.10f, 0.50f, 0.50f, BLUE), cb(BLUE, ToyCircle(0.28f, 0.30f, 0.10f)), rc(0.50f, 0.50f, 0.95f, 0.90f, RED), cb(RED, ToyCircle(0.72f, 0.70f, 0.10f))))
    f("pe", "Ù¾Ø±Ùˆ", RED, V3(RED, WHITE, RED))
    f("pf", "Ù¾Ù„ÛŒâ€ŒÙ†Ø²ÛŒ ÙØ±Ø§Ù†Ø³Ù‡", RED, TRIH(RED, WHITE, RED) + listOf(cb(GOLD, ToyCircle(0.5f, 0.44f, 0.09f))))
    f("pg", "Ù¾Ø§Ù¾ÙˆØ¢ Ú¯ÛŒÙ†Ù‡ Ù†Ùˆ", BLACK, listOf(hb(0.10f, 0.50f, RED), cb(GOLD, ToyCircle(0.30f, 0.32f, 0.10f))))
    f("ph", "ÙÛŒÙ„ÛŒÙ¾ÛŒÙ†", NAVY, listOf(hb(0.5f, 0.90f, RED), vb(0.05f, 0.42f, WHITE), cb(GOLD, ToyCircle(0.20f, 0.5f, 0.10f))))
    f("pk", "Ù¾Ø§Ú©Ø³ØªØ§Ù†", GREEN, listOf(vb(0.05f, 0.24f, WHITE), cb(WHITE, ToyCircle(0.55f, 0.5f, 0.16f)), cb(GREEN, ToyCircle(0.60f, 0.5f, 0.17f)), cb(WHITE, ToyCircle(0.70f, 0.36f, 0.05f))))
    f("pl", "Ù„Ù‡Ø³ØªØ§Ù†", WHITE, H2(WHITE) + listOf(hb(0.5f, 0.90f, RED)))
    f("pm", "Ø³Ù†Øª Ù¾ÛŒØ± Ùˆ Ù…ÛŒÚ©Ù„ÙˆÙ†", NAVY, miniUK())
    f("pn", "Ø¬Ø²Ø§ÛŒØ± Ù¾ÛŒØªâ€ŒÚ©Ø±Ù†", NAVY, miniUK())
    f("pr", "Ù¾ÙˆØ±ØªÙˆØ±ÛŒÚ©Ùˆ", RED, listOf(hb(0.10f, 0.26f, WHITE), hb(0.42f, 0.58f, WHITE), hb(0.74f, 0.90f, WHITE), rc(0.05f, 0.10f, 0.42f, 0.56f, NAVY), cb(WHITE, ToyCircle(0.24f, 0.33f, 0.09f))))
    f("ps", "ÙÙ„Ø³Ø·ÛŒÙ†", BLACK, TRIH(BLACK, WHITE, GREEN) + listOf(vb(0.05f, 0.36f, RED)))
    f("pt", "Ù¾Ø±ØªØºØ§Ù„", RED, listOf(vb(0.05f, 0.38f, GREEN), cb(GOLD, ToyCircle(0.33f, 0.5f, 0.10f))))
    f("pw", "Ù¾Ø§Ù„Ø§Ø¦Ùˆ", SKY, listOf(cb(GOLD, ToyCircle(0.42f, 0.5f, 0.20f))))
    f("py", "Ù¾Ø§Ø±Ø§Ú¯ÙˆØ¦Ù‡", RED, TRIH(RED, WHITE, BLUE) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("qa", "Ù‚Ø·Ø±", WHITE, listOf(vb(0.36f, 0.95f, Color(0xFF8D1B3D))))
    f("re", "Ø±ÙˆÙ†ÛŒÙˆÙ†", NAVY, TRIH(NAVY, WHITE, RED))
    f("ro", "Ø±ÙˆÙ…Ø§Ù†ÛŒ", NAVY, TRIV(BLUE, GOLD, RED))
    f("rs", "ØµØ±Ø¨Ø³ØªØ§Ù†", RED, TRIH(RED, BLUE, WHITE) + listOf(cb(GOLD, ToyCircle(0.30f, 0.44f, 0.10f))))
    f("ru", "Ø±ÙˆØ³ÛŒÙ‡", WHITE, TRIH(WHITE, BLUE, RED))
    f("rw", "Ø±ÙˆØ§Ù†Ø¯Ø§", SKY, TRIH(SKY, GOLD, GREEN) + listOf(cb(GOLD, ToyCircle(0.5f, 0.32f, 0.11f))))
    f("sa", "Ø¹Ø±Ø¨Ø³ØªØ§Ù†", GREEN, listOf(hb(0.40f, 0.52f, WHITE)))
    f("sb", "Ø¬Ø²Ø§ÛŒØ± Ø³Ù„ÛŒÙ…Ø§Ù†", GREEN, listOf(hb(0.10f, 0.50f, SKY), hb(0.50f, 0.90f, GREEN), cb(GOLD, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("sc", "Ø³ÛŒØ´Ù„", NAVY, listOf(vb(0.05f, 0.40f, NAVY), vb(0.40f, 0.62f, RED), hb(0.62f, 0.90f, GREEN), cb(WHITE, ToyCircle(0.32f, 0.42f, 0.08f))))
    f("sd", "Ø³ÙˆØ¯Ø§Ù†", RED, TRIH(RED, WHITE, BLACK) + listOf(vb(0.05f, 0.34f, GREEN)))
    f("se", "Ø³ÙˆØ¦Ø¯", BLUE, NV(BLUE, GOLD))
    f("sg", "Ø³Ù†Ú¯Ø§Ù¾ÙˆØ±", RED, listOf(hb(0.5f, 0.90f, WHITE), cb(WHITE, ToyCircle(0.34f, 0.30f, 0.12f)), cb(RED, ToyCircle(0.40f, 0.30f, 0.13f))))
    f("sh", "Ø³Ù†Øª Ù‡Ù„Ù†Ø§", NAVY, miniUK())
    f("si", "Ø§Ø³Ù„ÙˆÙˆÙ†ÛŒ", WHITE, TRIH(WHITE, BLUE, RED) + listOf(cb(WHITE, ToyCircle(0.26f, 0.34f, 0.09f))))
    f("sj", "Ø³ÙˆØ§Ù„Ø¨Ø§Ø±Ø¯", NAVY, miniUK())
    f("sk", "Ø§Ø³Ù„ÙˆØ§Ú©ÛŒ", WHITE, TRIH(WHITE, BLUE, RED) + listOf(cb(RED, ToyCircle(0.30f, 0.5f, 0.11f))))
    f("sl", "Ø³ÛŒØ±Ø§Ù„Ø¦ÙˆÙ†", GREEN, TRIH(GREEN, WHITE, SKY))
    f("sm", "Ø³Ø§Ù†â€ŒÙ…Ø§Ø±ÛŒÙ†Ùˆ", WHITE, TRIH(WHITE, SKY, WHITE))
    f("sn", "Ø³Ù†Ú¯Ø§Ù„", GREEN, TRIV(GREEN, GOLD, RED) + listOf(cb(GREEN, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("so", "Ø³ÙˆÙ…Ø§Ù„ÛŒ", SKY, DISK(WHITE, 0.20f))
    f("sr", "Ø³ÙˆØ±ÛŒÙ†Ø§Ù…", GREEN, listOf(hb(0.10f, 0.24f, WHITE), hb(0.24f, 0.76f, RED), hb(0.76f, 0.90f, WHITE), cb(GOLD, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("ss", "Ø³ÙˆØ¯Ø§Ù† Ø¬Ù†ÙˆØ¨ÛŒ", BLACK, TRIH(BLACK, RED, GREEN) + listOf(vb(0.05f, 0.36f, SKY), cb(GOLD, ToyCircle(0.20f, 0.5f, 0.07f))))
    f("st", "Ø³Ø§Ø¦ÙˆØªÙˆÙ…Ù‡", GREEN, TRIH(GREEN, GOLD, RED) + listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("sv", "Ø§Ù„Ø³Ø§Ù„ÙˆØ§Ø¯ÙˆØ±", SKY, TRIH(SKY, WHITE, SKY) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("sx", "Ø³ÛŒÙ†Øª Ù…Ø§Ø±ØªÙ†", NAVY, listOf(hb(0.10f, 0.50f, NAVY), hb(0.50f, 0.90f, RED), vb(0.05f, 0.40f, WHITE)))
    f("sy", "Ø³ÙˆØ±ÛŒÙ‡", RED, TRIH(RED, WHITE, BLACK) + listOf(cb(GREEN, ToyCircle(0.42f, 0.5f, 0.05f), ToyCircle(0.58f, 0.5f, 0.05f))))
    f("sz", "Ø§Ø³ÙˆØ§ØªÛŒÙ†ÛŒ", NAVY, listOf(hb(0.10f, 0.30f, GOLD), hb(0.70f, 0.90f, GOLD), rc(0.05f, 0.30f, 0.95f, 0.70f, RED), cb(BLACK, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("tc", "ØªØ±Ú© Ùˆ Ú©Ø§ÛŒÚ©ÙˆØ³", NAVY, miniUK())
    f("td", "Ú†Ø§Ø¯", NAVY, TRIV(NAVY, GOLD, RED))
    f("tf", "Ø³Ø±Ø²Ù…ÛŒÙ†â€ŒÙ‡Ø§ÛŒ Ø¬Ù†ÙˆØ¨ÛŒ ÙØ±Ø§Ù†Ø³Ù‡", NAVY, miniUK())
    f("tg", "ØªÙˆÚ¯Ùˆ", GREEN, TRIH(GREEN, GOLD, GREEN) + listOf(rc(0.05f, 0.10f, 0.36f, 0.56f, RED), cb(WHITE, ToyCircle(0.20f, 0.33f, 0.09f))))
    f("th", "ØªØ§ÛŒÙ„Ù†Ø¯", RED, listOf(hb(0.24f, 0.38f, WHITE), hb(0.38f, 0.62f, NAVY), hb(0.62f, 0.76f, WHITE)))
    f("tj", "ØªØ§Ø¬ÛŒÚ©Ø³ØªØ§Ù†", RED, TRIH(RED, WHITE, GREEN) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("tk", "ØªÙˆÚ©Ù„Ø§Ø¦Ùˆ", GOLD, miniUK())
    f("tl", "ØªÛŒÙ…ÙˆØ± Ø´Ø±Ù‚ÛŒ", RED, listOf(vb(0.05f, 0.40f, BLACK), vb(0.40f, 0.52f, GOLD), cb(WHITE, ToyCircle(0.22f, 0.5f, 0.09f))))
    f("tm", "ØªØ±Ú©Ù…Ù†Ø³ØªØ§Ù†", GREEN, listOf(vb(0.05f, 0.22f, RED), cb(WHITE, ToyCircle(0.55f, 0.42f, 0.11f))))
    f("tn", "ØªÙˆÙ†Ø³", RED, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.26f)), cb(RED, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("to", "ØªÙˆÙ†Ú¯Ø§", RED, listOf(rc(0.05f, 0.10f, 0.42f, 0.48f, WHITE), cb(RED, ToyCircle(0.24f, 0.29f, 0.06f))))
    f("tr", "ØªØ±Ú©ÛŒÙ‡", Color(0xFFE30A17), listOf(
        cb(Color.White, ToyCircle(0.40f, 0.5f, 0.24f)),
        cb(Color(0xFFE30A17), ToyCircle(0.475f, 0.5f, 0.19f)),
        cb(Color.White, ToyCircle(0.66f, 0.5f, 0.075f)),
    ))
    f("tt", "ØªØ±ÛŒÙ†ÛŒØ¯Ø§Ø¯", RED, listOf(hb(0.366f, 0.634f, WHITE), cb(BLACK, ToyCircle(0.5f, 0.5f, 0.16f))))
    f("tv", "ØªÙˆÙˆØ§Ù„Ùˆ", SKY, miniUK() + listOf(cb(GOLD, ToyCircle(0.7f, 0.6f, 0.06f))))
    f("tw", "ØªØ§ÛŒÙˆØ§Ù†", RED, listOf(rc(0.05f, 0.10f, 0.46f, 0.50f, NAVY), cb(WHITE, ToyCircle(0.25f, 0.30f, 0.11f))))
    f("tz", "ØªØ§Ù†Ø²Ø§Ù†ÛŒØ§", GREEN, listOf(hb(0.36f, 0.64f, BLACK), hb(0.30f, 0.36f, GOLD), hb(0.64f, 0.70f, GOLD), cb(SKY, ToyCircle(0.2f, 0.2f, 0.07f))))
    f("ua", "Ø§ÙˆÚ©Ø±Ø§ÛŒÙ†", BLUE, H2(BLUE) + listOf(hb(0.5f, 0.90f, GOLD)))
    f("ug", "Ø§ÙˆÚ¯Ø§Ù†Ø¯Ø§", BLACK, TRIH(BLACK, GOLD, RED) + listOf(hb(0.30f, 0.366f, WHITE), hb(0.634f, 0.70f, WHITE), cb(GREY, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("um", "Ø¬Ø²Ø§ÛŒØ± Ú©ÙˆÚ†Ú© Ø¯ÙˆØ±Ø§ÙØªØ§Ø¯Ù‡ Ø¢Ù…Ø±ÛŒÚ©Ø§", NAVY, miniUK())
    f("us", "Ø¢Ù…Ø±ÛŒÚ©Ø§", RED, listOf(
        hb(0.243f, 0.377f, WHITE), hb(0.511f, 0.645f, WHITE), hb(0.779f, 0.90f, WHITE),
        rc(0.06f, 0.105f, 0.46f, 0.50f, BLUE),
        cb(WHITE, ToyCircle(0.14f, 0.22f, 0.035f), ToyCircle(0.28f, 0.22f, 0.035f), ToyCircle(0.21f, 0.36f, 0.035f), ToyCircle(0.36f, 0.36f, 0.035f)),
    ))
    f("uy", "Ø§Ø±ÙˆÚ¯ÙˆØ¦Ù‡", WHITE, listOf(hb(0.24f, 0.38f, SKY), hb(0.52f, 0.66f, SKY), hb(0.80f, 0.90f, SKY), cb(GOLD, ToyCircle(0.22f, 0.30f, 0.10f))))
    f("uz", "Ø§Ø²Ø¨Ú©Ø³ØªØ§Ù†", SKY, TRIH(SKY, WHITE, GREEN) + listOf(hb(0.366f, 0.40f, RED), hb(0.60f, 0.634f, RED), cb(SKY, ToyCircle(0.22f, 0.28f, 0.09f))))
    f("va", "ÙˆØ§ØªÛŒÚ©Ø§Ù†", GOLD, TRIH(GOLD, WHITE, GOLD) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("vc", "Ø³Ù†Øª ÙˆÛŒÙ†Ø³Ù†Øª", SKY, TRIV(SKY, GOLD, GREEN) + listOf(cb(GREEN, ToyCircle(0.5f, 0.5f, 0.08f))))
    f("ve", "ÙˆÙ†Ø²ÙˆØ¦Ù„Ø§", GOLD, TRIH(GOLD, BLUE, RED) + listOf(cb(WHITE, ToyCircle(0.5f, 0.40f, 0.09f))))
    f("vg", "Ø¬Ø²Ø§ÛŒØ± ÙˆÛŒØ±Ø¬ÛŒÙ† Ø¨Ø±ÛŒØªÛŒØ´", NAVY, miniUK())
    f("vi", "Ø¬Ø²Ø§ÛŒØ± ÙˆÛŒØ±Ø¬ÛŒÙ† Ø¢Ù…Ø±ÛŒÚ©Ø§", WHITE, listOf(cb(GOLD, ToyCircle(0.42f, 0.5f, 0.12f)), cb(Color(0xFFB8763E), ToyCircle(0.6f, 0.42f, 0.07f))))
    f("vn", "ÙˆÛŒØªÙ†Ø§Ù…", RED, DISK(GOLD, 0.20f))
    f("vu", "ÙˆØ§Ù†ÙˆØ§ØªÙˆ", BLACK, listOf(hb(0.10f, 0.30f, BLACK), hb(0.30f, 0.70f, GREEN), hb(0.70f, 0.90f, RED), cb(GOLD, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("wf", "ÙˆØ§Ù„ÛŒØ³ Ùˆ ÙÙˆØªÙˆÙ†Ø§", NAVY, miniUK())
    f("ws", "Ø³Ø§Ù…ÙˆØ¢", RED, listOf(rc(0.05f, 0.10f, 0.44f, 0.52f, NAVY), cb(WHITE, ToyCircle(0.22f, 0.26f, 0.06f), ToyCircle(0.34f, 0.26f, 0.06f), ToyCircle(0.28f, 0.38f, 0.06f))))
    f("xk", "Ú©ÙˆØ²ÙˆÙˆ", NAVY, listOf(cb(GOLD, ToyCircle(0.5f, 0.44f, 0.13f))))
    f("ye", "ÛŒÙ…Ù†", RED, TRIH(RED, WHITE, BLACK))
    f("yt", "Ù…Ø§ÛŒÙˆØª", NAVY, miniUK())
    f("za", "Ø¢ÙØ±ÛŒÙ‚Ø§ÛŒ Ø¬Ù†ÙˆØ¨ÛŒ", GREEN, listOf(hb(0.42f, 0.58f, GREEN), hb(0.30f, 0.42f, WHITE), hb(0.58f, 0.70f, WHITE), hb(0.10f, 0.30f, RED), hb(0.70f, 0.90f, BLUE), vb(0.05f, 0.30f, BLACK)))
    f("zm", "Ø²Ø§Ù…Ø¨ÛŒØ§", GREEN, listOf(hb(0.10f, 0.30f, ORANGE), hb(0.30f, 0.50f, BLACK), hb(0.50f, 0.70f, RED), cb(GOLD, ToyCircle(0.78f, 0.30f, 0.08f))))
    f("zw", "Ø²ÛŒÙ…Ø¨Ø§Ø¨ÙˆÙ‡", GREEN, TRIH(GREEN, GOLD, RED) + listOf(vb(0.05f, 0.26f, BLACK), cb(GOLD, ToyCircle(0.16f, 0.5f, 0.10f))))

    return out
}

private val GREY = Color(0xFF9BA8B4)

private val EN = mapOf(
    "ad" to "Andorra", "ae" to "United Arab Emirates", "af" to "Afghanistan", "ag" to "Antigua & Barbuda",
    "ai" to "Anguilla", "al" to "Albania", "am" to "Armenia", "ao" to "Angola", "ar" to "Argentina",
    "at" to "Austria", "au" to "Australia", "aw" to "Aruba", "ax" to "Ã…land Islands", "az" to "Azerbaijan",
    "ba" to "Bosnia & Herzegovina", "bb" to "Barbados", "bd" to "Bangladesh", "be" to "Belgium",
    "bf" to "Burkina Faso", "bg" to "Bulgaria", "bh" to "Bahrain", "bi" to "Burundi", "bj" to "Benin",
    "bl" to "St. BarthÃ©lemy", "bm" to "Bermuda", "bn" to "Brunei", "bo" to "Bolivia", "br" to "Brazil",
    "bs" to "Bahamas", "bt" to "Bhutan", "bw" to "Botswana", "by" to "Belarus", "bz" to "Belize",
    "ca" to "Canada", "cd" to "DR Congo", "cf" to "Central African Rep.", "cg" to "Congo",
    "ch" to "Switzerland", "ci" to "CÃ´te d'Ivoire", "ck" to "Cook Islands", "cl" to "Chile",
    "cm" to "Cameroon", "cn" to "China", "co" to "Colombia", "cr" to "Costa Rica", "cu" to "Cuba",
    "cv" to "Cape Verde", "cw" to "CuraÃ§ao", "cx" to "Christmas Island", "cy" to "Cyprus", "cz" to "Czechia",
    "de" to "Germany", "dj" to "Djibouti", "dk" to "Denmark", "dm" to "Dominica", "do" to "Dominican Rep.",
    "dz" to "Algeria", "ec" to "Ecuador", "ee" to "Estonia", "eg" to "Egypt", "eh" to "Western Sahara",
    "er" to "Eritrea", "es" to "Spain", "et" to "Ethiopia", "fi" to "Finland", "fj" to "Fiji",
    "fm" to "Micronesia", "fo" to "Faroe Islands", "fr" to "France", "ga" to "Gabon", "gb" to "United Kingdom",
    "gd" to "Grenada", "ge" to "Georgia", "gf" to "French Guiana", "gg" to "Guernsey", "gh" to "Ghana",
    "gi" to "Gibraltar", "gl" to "Greenland", "gm" to "Gambia", "gn" to "Guinea", "gp" to "Guadeloupe",
    "gq" to "Equatorial Guinea", "gr" to "Greece", "gs" to "South Georgia", "gt" to "Guatemala", "gu" to "Guam",
    "gw" to "Guinea-Bissau", "gy" to "Guyana", "hk" to "Hong Kong", "hm" to "Heard Island", "hn" to "Honduras",
    "hr" to "Croatia", "ht" to "Haiti", "hu" to "Hungary", "id" to "Indonesia", "ie" to "Ireland",
    "il" to "Israel", "im" to "Isle of Man", "in" to "India", "io" to "British Indian Ocean Ter.",
    "iq" to "Iraq", "ir" to "Iran", "is" to "Iceland", "it" to "Italy", "je" to "Jersey", "jm" to "Jamaica",
    "jo" to "Jordan", "jp" to "Japan", "ke" to "Kenya", "kg" to "Kyrgyzstan", "kh" to "Cambodia",
    "ki" to "Kiribati", "km" to "Comoros", "kn" to "St. Kitts & Nevis", "kp" to "North Korea",
    "kr" to "South Korea", "kw" to "Kuwait", "ky" to "Cayman Islands", "kz" to "Kazakhstan", "la" to "Laos",
    "lb" to "Lebanon", "lc" to "St. Lucia", "li" to "Liechtenstein", "lk" to "Sri Lanka", "lr" to "Liberia",
    "ls" to "Lesotho", "lt" to "Lithuania", "lu" to "Luxembourg", "lv" to "Latvia", "ly" to "Libya",
    "ma" to "Morocco", "mc" to "Monaco", "md" to "Moldova", "me" to "Montenegro", "mg" to "Madagascar",
    "mh" to "Marshall Islands", "mk" to "North Macedonia", "ml" to "Mali", "mm" to "Myanmar",
    "mn" to "Mongolia", "mo" to "Macau", "mp" to "N. Mariana Islands", "mq" to "Martinique",
    "mr" to "Mauritania", "ms" to "Montserrat", "mt" to "Malta", "mu" to "Mauritius", "mv" to "Maldives",
    "mw" to "Malawi", "mx" to "Mexico", "my" to "Malaysia", "mz" to "Mozambique", "na" to "Namibia",
    "nc" to "New Caledonia", "ne" to "Niger", "nf" to "Norfolk Island", "ng" to "Nigeria",
    "ni" to "Nicaragua", "nl" to "Netherlands", "no" to "Norway", "np" to "Nepal", "nr" to "Nauru",
    "nu" to "Niue", "nz" to "New Zealand", "om" to "Oman", "pa" to "Panama", "pe" to "Peru",
    "pf" to "French Polynesia", "pg" to "Papua New Guinea", "ph" to "Philippines", "pk" to "Pakistan",
    "pl" to "Poland", "pm" to "St. Pierre & Miquelon", "pn" to "Pitcairn", "pr" to "Puerto Rico",
    "ps" to "Palestine", "pt" to "Portugal", "pw" to "Palau", "py" to "Paraguay", "qa" to "Qatar",
    "re" to "RÃ©union", "ro" to "Romania", "rs" to "Serbia", "ru" to "Russia", "rw" to "Rwanda",
    "sa" to "Saudi Arabia", "sb" to "Solomon Islands", "sc" to "Seychelles", "sd" to "Sudan",
    "se" to "Sweden", "sg" to "Singapore", "sh" to "St. Helena", "si" to "Slovenia", "sj" to "Svalbard",
    "sk" to "Slovakia", "sl" to "Sierra Leone", "sm" to "San Marino", "sn" to "Senegal", "so" to "Somalia",
    "sr" to "Suriname", "ss" to "South Sudan", "st" to "SÃ£o TomÃ© & PrÃ­ncipe", "sv" to "El Salvador",
    "sx" to "Sint Maarten", "sy" to "Syria", "sz" to "Eswatini", "tc" to "Turks & Caicos", "td" to "Chad",
    "tf" to "Fr. Southern Lands", "tg" to "Togo", "th" to "Thailand", "tj" to "Tajikistan", "tk" to "Tokelau",
    "tl" to "Timor-Leste", "tm" to "Turkmenistan", "tn" to "Tunisia", "to" to "Tonga", "tr" to "Turkey",
    "tt" to "Trinidad & Tobago", "tv" to "Tuvalu", "tw" to "Taiwan", "tz" to "Tanzania", "ua" to "Ukraine",
    "ug" to "Uganda", "um" to "US Minor Outlying Is.", "us" to "United States", "uy" to "Uruguay",
    "uz" to "Uzbekistan", "va" to "Vatican City", "vc" to "St. Vincent", "ve" to "Venezuela",
    "vg" to "British Virgin Is.", "vi" to "U.S. Virgin Islands", "vn" to "Vietnam", "vu" to "Vanuatu",
    "wf" to "Wallis & Futuna", "ws" to "Samoa", "xk" to "Kosovo", "ye" to "Yemen", "yt" to "Mayotte",
    "za" to "South Africa", "zm" to "Zambia", "zw" to "Zimbabwe",
)
