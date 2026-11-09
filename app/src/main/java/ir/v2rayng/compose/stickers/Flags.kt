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

// lazy so FlagsKt's class init never runs during CountryFlags.<clinit> (circular init crash)
private val flagNameToCode: Map<String, String> by lazy {
    buildMap {
        CountryFlags.list.forEach { put(it.name.lowercase(), it.id.uppercase()) }
        // common aliases admins use in server names
        put("united kingdom", "GB")
        put("uk", "GB")
        put("britain", "GB")
        put("england", "GB")
        put("usa", "US")
        put("united states", "US")
        put("america", "US")
        put("emirates", "AE")
        put("holland", "NL")
        put("south korea", "KR")
        put("russia", "RU")
        put("turkey", "TR")
        put("turkiye", "TR")
    }
}

/** ISO code from a flag emoji pair inside a server name, e.g. "CDN1 🇩🇪" -> "DE". */
fun flagCodeFromEmoji(name: String): String {
    var first = 0
    var run = 0
    for (c in name) {
        val v = c.code
        if (v in 0x1F1E6..0x1F1FF) {
            if (run == 0) first = v
            run++
            if (run == 2) {
                return "" + (first - 0x1F1E6 + 65).toChar() + (v - 0x1F1E6 + 65).toChar()
            }
        } else {
            run = 0
        }
    }
    return ""
}

/** Whole-word country match inside a server name, e.g. "united kingdom" -> "GB" ("oman" never matches "Romania"). */
fun flagCodeFromWords(name: String): String {
    val n = name.lowercase()
    for ((country, code) in flagNameToCode) {
        var at = n.indexOf(country)
        while (at >= 0) {
            val end = at + country.length
            val okStart = at == 0 || !n[at - 1].isLetter()
            val okEnd = end == n.length || !n[end].isLetter()
            if (okStart && okEnd) return code
            at = n.indexOf(country, at + 1)
        }
    }
    return ""
}

/** Best-effort country code from a server name (flag emoji first, then country words). */
fun flagCodeFromName(name: String): String {
    val byEmoji = flagCodeFromEmoji(name)
    if (byEmoji.isNotBlank()) return byEmoji
    return flagCodeFromWords(name)
}

/** The country's own name for a code: "de" -> "Germany". Empty when the code has no flag. */
fun countryNameOf(code: String): String {
    val i = CountryFlags.byCode(code)
    return if (i >= 0) CountryFlags.list[i].name else ""
}

/** Everything a row's flag stands for, as text: the country name, its ISO code and every alias
 *  that points at the same flag. A server named "CDN1 🇩🇪" is found by "ger", "de" or "Germany". */
fun countryWords(code: String): List<String> = wordsByCode[code.lowercase().trim()] ?: emptyList()

// lazy for the same reason as flagNameToCode: it walks CountryFlags.list
private val wordsByCode: Map<String, List<String>> by lazy {
    val out = HashMap<String, ArrayList<String>>()
    CountryFlags.list.forEach { out[it.id] = ArrayList() }
    flagNameToCode.forEach { (word, iso) -> out[iso.lowercase()]?.add(word) }
    out.forEach { (code, list) -> list.add(code.uppercase()) }
    out
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
private val GREY = Color(0xFF9BA8B4)

/* Bands are rectangles. They used to be run through chaikin first, which turns a rectangle into a
 * rounded blob: a tricolour's three straight stripes came out as three capsules, with the base
 * colour showing at the corners as if the band had been cut with a circle. The sticker look is the
 * rounded silhouette and the face, drawn by StickerCharacter - the colour blocks themselves are
 * the flag. */
private fun hb(y0: Float, y1: Float, color: Color) = FlagBand(
    color,
    listOf(ToyPoly(listOf(Offset(0.01f, y0), Offset(0.99f, y0), Offset(0.99f, y1), Offset(0.01f, y1)))),
)

private fun vb(x0: Float, x1: Float, color: Color) = FlagBand(
    color,
    listOf(ToyPoly(listOf(Offset(x0, 0.01f), Offset(x1, 0.01f), Offset(x1, 0.99f), Offset(x0, 0.99f)))),
)

private fun rc(x0: Float, y0: Float, x1: Float, y1: Float, color: Color) = FlagBand(
    color,
    listOf(ToyPoly(listOf(Offset(x0, y0), Offset(x1, y0), Offset(x1, y1), Offset(x0, y1)))),
)

private fun cb(color: Color, vararg cs: ToyCircle) = FlagBand(color, cs.toList())

/* Symbols, not dots. A circle in the middle of a flag read as a smudge wherever the country's
 * emblem is a star, a crescent or a diamond - which is most of them. */
private fun polyPl(cx: Float, cy: Float, r: Float, points: Int, rot: Float, w: Float): List<Offset> =
    (0 until points * 2).map { i ->
        val a = Math.toRadians((rot + i * 180.0 / points))
        val rr = if (i % 2 == 0) r else r * w
        Offset(cx + rr * kotlin.math.cos(a).toFloat(), cy + rr * kotlin.math.sin(a).toFloat())
    }

private fun star(cx: Float, cy: Float, r: Float, points: Int, color: Color, rot: Float = -90f, w: Float = 0.42f) =
    FlagBand(color, listOf(ToyPoly(polyPl(cx, cy, r, points, rot, w))))

private fun diamond(cx: Float, cy: Float, hw: Float, hh: Float, color: Color) = FlagBand(
    color,
    listOf(ToyPoly(listOf(Offset(cx, cy - hh), Offset(cx + hw, cy), Offset(cx, cy + hh), Offset(cx - hw, cy)))),
)

/** Half a disc: the top half or the bottom one, as a polygon. A taegeuk is two of these plus two
 *  half-size ones, which is the only way to get the S without an arc op. */
private fun halfDisc(cx: Float, cy: Float, r: Float, top: Boolean, color: Color, steps: Int = 16) = FlagBand(
    color,
    listOf(ToyPoly((0..steps).map { i ->
        val a = Math.toRadians((if (top) 180.0 else 0.0) + 180.0 * i / steps)
        Offset(cx + r * kotlin.math.cos(a).toFloat(), cy + r * kotlin.math.sin(a).toFloat())
    })),
)

/** The left or right half, for a crescent that is cut across a flag's split field. */
private fun halfDiscSide(cx: Float, cy: Float, r: Float, left: Boolean, color: Color, steps: Int = 16) = FlagBand(
    color,
    listOf(ToyPoly((0..steps).map { i ->
        val a = Math.toRadians((if (left) 90.0 else -90.0) + 180.0 * i / steps)
        Offset(cx + r * kotlin.math.cos(a).toFloat(), cy + r * kotlin.math.sin(a).toFloat())
    })),
)

/** A field of small stars, one band so the op list stays short. */
private fun starGrid(x0: Float, y0: Float, dx: Float, dy: Float, cols: Int, rows: Int, r: Float, color: Color) =
    FlagBand(color, (0 until cols * rows).map { i ->
        ToyPoly(polyPl(x0 + dx * (i % cols), y0 + dy * (i / cols), r, 5, -90f, 0.42f))
    })

/** Every other square of a checkerboard, for a shield that is literally a checkerboard. */
private fun checker(x0: Float, y0: Float, cell: Float, cols: Int, rows: Int, color: Color, parity: Int) = FlagBand(
    color,
    (0 until cols * rows).filter { ((it % cols) + (it / cols)) % 2 == parity }.map { i ->
        val x = x0 + cell * (i % cols)
        val y = y0 + cell * (i / cols)
        ToyPoly(listOf(Offset(x, y), Offset(x + cell, y), Offset(x + cell, y + cell), Offset(x, y + cell)))
    },
)

/** Every other stripe of an n-stripe field, on the face itself: the stripes used to be hand-placed
 *  on the 0.10 grid, which gave flags the wrong number of them and a band of field at both edges. */
private fun stripes(n: Int, color: Color, from: Int = 1): FlagBand = FlagBand(
    color,
    (from until n step 2).map { k ->
        val y0 = 0.04f + 0.92f * k / n
        val y1 = 0.04f + 0.92f * (k + 1) / n
        ToyPoly(listOf(Offset(0.01f, y0), Offset(0.99f, y0), Offset(0.99f, y1), Offset(0.01f, y1)))
    },
)

/* The middle of a tricolour was left to the base colour, so every call that passed a middle
 * different from the base - TRIV(BLACK, GOLD, RED) for Belgium, TRIV(GREEN, WHITE, ORANGE) for
 * Ireland - silently lost that colour: the base painted the whole flag and only the two outer
 * bands were drawn over it. All three bands are drawn now. Where the caller's first colour already
 * equalled the base nothing changes, because the same colour is laid over the same area, and the
 * band edges are the ones the file already used (0.35 / 0.366), so no flag moves. */
private fun TRIH(a: Color, m: Color, b: Color) = listOf(hb(0.01f, 0.366f, a), hb(0.366f, 0.634f, m), hb(0.634f, 0.99f, b))
private fun TRIV(l: Color, m: Color, r: Color) = listOf(vb(0.01f, 0.35f, l), vb(0.35f, 0.65f, m), vb(0.65f, 0.99f, r))
/* Equal thirds of the whole face. The left band used to start at 0.05 while the face starts at
 * 0.02, which left a 0.03 strip of the base colour down the hoist of every vertical tricolour -
 * "a tiny bit of colour sticking out at the side". */
private fun V3(l: Color, m: Color, r: Color) = listOf(vb(0.01f, 0.3367f, l), vb(0.3367f, 0.6633f, m), vb(0.6633f, 0.99f, r))
private fun H2(t: Color) = listOf(hb(0.01f, 0.50f, t))
private fun V2(l: Color) = listOf(vb(0.50f, 0.99f, l))
private fun NV(base: Color, cross: Color) = listOf(hb(0.40f, 0.60f, cross), vb(0.28f, 0.42f, cross))
private fun DISK(d: Color, r: Float = 0.22f) = listOf(cb(d, ToyCircle(0.5f, 0.5f, r)))
private fun CROSS(c: Color) = listOf(rc(0.435f, 0.22f, 0.565f, 0.78f, c), rc(0.24f, 0.435f, 0.76f, 0.565f, c))

/* The canton sits in the corner. It used to start at 0.06/0.105 while the face starts at
 * 0.02/0.04, so the field showed as a strip on two sides of the Union Jack. */
private fun miniUK(): List<FlagBand> = listOf(
    rc(0.02f, 0.04f, 0.36f, 0.355f, WHITE),
    rc(0.02f, 0.04f, 0.36f, 0.355f, BLUE),
    rc(0.165f, 0.04f, 0.215f, 0.355f, WHITE),
    rc(0.02f, 0.17f, 0.36f, 0.22f, WHITE),
    rc(0.175f, 0.04f, 0.205f, 0.355f, RED),
    rc(0.02f, 0.18f, 0.36f, 0.21f, RED),
)

private fun build(): List<Sticker> {
    val out = ArrayList<Sticker>(210)

    fun f(code: String, name: String, base: Color, bands: List<FlagBand>) {
        val baseRounded = FlagBand(base, listOf(ToyPoly(chaikin(listOf(Offset(0.02f, 0.04f), Offset(0.98f, 0.04f), Offset(0.98f, 0.96f), Offset(0.02f, 0.96f)), 3))))
        val art = FlagArt(base, listOf(baseRounded) + bands, (out.size * 0.031f) % 1f)
        out.add(Sticker(code, name, art))
    }

    f("ad", "Andorra", BLUE, V3(BLUE, GOLD, RED))
    f("ae", "United Arab Emirates", GREEN, listOf(hb(0.366f, 0.634f, WHITE), hb(0.634f, 0.99f, BLACK), vb(0.01f, 0.28f, RED)))
    f("af", "Afghanistan", BLACK, TRIH(BLACK, RED, GREEN))
    // red above and below, the blue field inverted-V'd between white fimbriations, gold rising sun
    f("ag", "Antigua and Barbuda", RED, listOf(hb(0.01f, 0.32f, RED), hb(0.32f, 0.36f, WHITE), hb(0.36f, 0.64f, BLUE), hb(0.64f, 0.68f, WHITE), hb(0.68f, 0.99f, RED), cb(GOLD, ToyCircle(0.5f, 0.22f, 0.11f))))
    f("ai", "Anguilla", BLUE, miniUK())
    f("al", "Albania", RED, listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.13f))))
    f("am", "Armenia", RED, TRIH(RED, BLUE, GOLD))
    f("ao", "Angola", RED, listOf(hb(0.5f, 0.99f, BLACK), star(0.42f, 0.42f, 0.10f, 5, GOLD)))
    f("ar", "Argentina", SKY, listOf(hb(0.366f, 0.634f, WHITE), cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("at", "Austria", WHITE, listOf(hb(0.366f, 0.634f, RED)))
    f("au", "Australia", NAVY, miniUK() + listOf(cb(WHITE, ToyCircle(0.64f, 0.26f, 0.05f), ToyCircle(0.78f, 0.40f, 0.05f), ToyCircle(0.64f, 0.60f, 0.05f), ToyCircle(0.82f, 0.78f, 0.05f))))
    f("aw", "Aruba", SKY, listOf(hb(0.62f, 0.68f, WHITE), hb(0.68f, 0.74f, RED), cb(WHITE, ToyCircle(0.16f, 0.30f, 0.06f), ToyCircle(0.24f, 0.38f, 0.06f))))
    f("ax", "Aland Islands", BLUE, NV(BLUE, GOLD) + listOf(vb(0.31f, 0.39f, RED), hb(0.43f, 0.57f, RED)))
    f("az", "Azerbaijan", SKY, listOf(
        hb(0.366f, 0.634f, RED), hb(0.634f, 0.99f, GREEN),
        cb(WHITE, ToyCircle(0.455f, 0.50f, 0.10f)), cb(RED, ToyCircle(0.500f, 0.50f, 0.09f)),
        star(0.585f, 0.50f, 0.06f, 8, WHITE),
    ))
    f("ba", "Bosnia and Herzegovina", NAVY, listOf(vb(0.01f, 0.55f, GOLD), hb(0.66f, 0.99f, NAVY)))
    // three vertical bands (blue-gold-blue) with the black broken trident: head and shaft, the two
    // outer prongs left out. It used to be a gold disc on a blue field, which is not this flag
    // the broken trident: three prongs over a shaft, instead of a dot above a line
    f("bb", "Barbados", BLUE, TRIV(BLUE, GOLD, BLUE) + listOf(
        rc(0.475f, 0.42f, 0.525f, 0.68f, BLACK),
        rc(0.395f, 0.30f, 0.435f, 0.47f, BLACK),
        rc(0.565f, 0.30f, 0.605f, 0.47f, BLACK),
        rc(0.478f, 0.26f, 0.522f, 0.44f, BLACK),
    ))
    f("bd", "Bangladesh", GREEN, DISK(RED, 0.24f))
    f("be", "Belgium", BLACK, TRIV(BLACK, GOLD, RED))
    f("bf", "Burkina Faso", RED, listOf(hb(0.5f, 0.99f, GREEN), star(0.5f, 0.44f, 0.10f, 5, GOLD)))
    f("bg", "Bulgaria", WHITE, TRIH(WHITE, GREEN, RED))
    f("bh", "Bahrain", WHITE, listOf(vb(0.34f, 0.99f, RED)))
    f("bi", "Burundi", GREEN, listOf(hb(0.366f, 0.634f, WHITE), cb(RED, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("bj", "Benin", GREEN, listOf(vb(0.01f, 0.38f, GREEN), hb(0.366f, 0.634f, GOLD), hb(0.634f, 0.99f, RED)))
    f("bl", "St. Barthelemy", NAVY, miniUK())
    f("bm", "Bermuda", RED, miniUK())
    f("bn", "Brunei", GOLD, listOf(hb(0.28f, 0.52f, WHITE), hb(0.52f, 0.76f, BLACK)))
    f("bo", "Bolivia", RED, TRIH(RED, GOLD, GREEN))
    // the yellow diamond and the blue globe, not a yellow stripe and a blue dot
    f("br", "Brazil", GREEN, listOf(diamond(0.5f, 0.5f, 0.31f, 0.31f, GOLD), cb(BLUE, ToyCircle(0.5f, 0.5f, 0.15f)), cb(WHITE, ToyCircle(0.46f, 0.43f, 0.03f))))
    f("bs", "Bahamas", SKY, listOf(hb(0.366f, 0.634f, GOLD), vb(0.01f, 0.30f, BLACK)))
    f("bt", "Bhutan", GOLD, listOf(cb(Color(0xFFE8663C), ToyCircle(0.5f, 0.5f, 0.16f))))
    f("bw", "Botswana", SKY, listOf(hb(0.42f, 0.50f, BLACK), hb(0.50f, 0.58f, WHITE)))
    f("by", "Belarus", RED, listOf(hb(0.66f, 0.99f, GREEN), vb(0.01f, 0.22f, WHITE)))
    // blue field with red borders top and bottom and the white coat of arms between them
    f("bz", "Belize", BLUE, listOf(hb(0.01f, 0.11f, RED), hb(0.89f, 0.99f, RED), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.21f)), cb(GREEN, ToyCircle(0.5f, 0.5f, 0.13f))))
    // the maple leaf: the shape the country is recognised by, instead of a red disc
    f("ca", "Canada", WHITE, listOf(
        vb(0.01f, 0.30f, RED), vb(0.70f, 0.99f, RED),
        FlagBand(RED, listOf(ToyPoly(listOf(
            Offset(0.500f, 0.255f), Offset(0.535f, 0.400f), Offset(0.620f, 0.360f),
            Offset(0.595f, 0.450f), Offset(0.700f, 0.440f), Offset(0.615f, 0.500f),
            Offset(0.665f, 0.580f), Offset(0.545f, 0.560f), Offset(0.560f, 0.685f),
            Offset(0.500f, 0.615f), Offset(0.440f, 0.685f), Offset(0.455f, 0.560f),
            Offset(0.335f, 0.580f), Offset(0.385f, 0.500f), Offset(0.300f, 0.440f),
            Offset(0.405f, 0.450f), Offset(0.380f, 0.360f), Offset(0.465f, 0.400f),
        )))),
    ))
    f("cd", "DR Congo", SKY, listOf(vb(0.62f, 0.72f, RED), star(0.24f, 0.28f, 0.08f, 5, GOLD)))
    f("cf", "Central African Republic", BLUE, listOf(
        hb(0.04f, 0.27f, BLUE), hb(0.27f, 0.50f, WHITE), hb(0.50f, 0.73f, GREEN), hb(0.73f, 0.96f, GOLD),
        vb(0.01f, 0.25f, RED), star(0.13f, 0.19f, 0.05f, 5, GOLD),
    ))
    f("cg", "Congo", GREEN, listOf(vb(0.62f, 0.99f, RED), vb(0.50f, 0.62f, GOLD)))
    f("ch", "Switzerland", RED, CROSS(WHITE))
    f("ci", "Ivory Coast", WHITE, TRIV(GOLD, WHITE, ORANGE))
    f("ck", "Cook Islands", BLUE, miniUK() + listOf(cb(WHITE, ToyCircle(0.7f, 0.5f, 0.07f))))
    f("cl", "Chile", WHITE, listOf(hb(0.5f, 0.99f, RED), rc(0.02f, 0.04f, 0.36f, 0.50f, BLUE), cb(WHITE, ToyCircle(0.20f, 0.30f, 0.07f))))
    f("cm", "Cameroon", GREEN, TRIV(GREEN, RED, GOLD) + listOf(star(0.5f, 0.5f, 0.10f, 5, GOLD)))
    // five stars, not five circles: one large and four small ones opening from it
    f("cn", "China", RED, listOf(
        star(0.30f, 0.32f, 0.14f, 5, GOLD),
        star(0.48f, 0.15f, 0.055f, 5, GOLD),
        star(0.55f, 0.27f, 0.055f, 5, GOLD),
        star(0.55f, 0.41f, 0.055f, 5, GOLD),
        star(0.48f, 0.53f, 0.055f, 5, GOLD),
    ))
    f("co", "Colombia", GOLD, listOf(hb(0.10f, 0.50f, GOLD), hb(0.50f, 0.75f, BLUE), hb(0.75f, 0.99f, RED)))
    f("cr", "Costa Rica", BLUE, listOf(hb(0.24f, 0.36f, WHITE), hb(0.36f, 0.64f, RED), hb(0.64f, 0.76f, WHITE)))
    // five stripes and a red hoist triangle with a white star - the canton was a rectangle
    f("cu", "Cuba", BLUE, listOf(
        stripes(5, WHITE),
        FlagBand(RED, listOf(ToyPoly(listOf(Offset(0.02f, 0.04f), Offset(0.46f, 0.50f), Offset(0.02f, 0.96f))))),
        star(0.16f, 0.50f, 0.07f, 5, WHITE),
    ))
    f("cv", "Cape Verde", NAVY, listOf(hb(0.44f, 0.56f, WHITE), hb(0.56f, 0.68f, RED), starGrid(0.30f, 0.56f, 0.05f, 0.05f, 5, 2, 0.022f, GOLD)))
    f("cw", "Curacao", BLUE, listOf(hb(0.58f, 0.70f, GOLD), vb(0.01f, 0.22f, WHITE)))
    f("cx", "Christmas Island", BLUE, listOf(cb(GREEN, ToyCircle(0.72f, 0.62f, 0.13f)), cb(GOLD, ToyCircle(0.28f, 0.32f, 0.10f))))
    f("cy", "Cyprus", WHITE, listOf(cb(Color(0xFFB8763E), ToyCircle(0.5f, 0.52f, 0.10f))))
    f("cz", "Czechia", WHITE, listOf(hb(0.5f, 0.99f, RED), FlagBand(BLUE, listOf(ToyPoly(listOf(Offset(0.05f, 0.10f), Offset(0.34f, 0.50f), Offset(0.05f, 0.90f)))))))
    f("de", "Germany", BLACK, TRIH(BLACK, RED, GOLD))
    f("dj", "Djibouti", SKY, listOf(hb(0.5f, 0.99f, GREEN), vb(0.01f, 0.40f, WHITE), star(0.20f, 0.32f, 0.07f, 5, RED)))
    f("dk", "Denmark", RED, NV(RED, WHITE))
    f("dm", "Dominica", GREEN, listOf(hb(0.366f, 0.634f, GOLD), hb(0.634f, 0.99f, BLACK), cb(RED, ToyCircle(0.5f, 0.5f, 0.14f))))
    // the cross is drawn first and the four quarters reach it, so every edge belongs to a quarter;
    // inset quarters left the same navy ring here that gb had
    f("do", "Dominican Republic", NAVY, listOf(rc(0.01f, 0.42f, 0.99f, 0.58f, WHITE), rc(0.42f, 0.01f, 0.58f, 0.99f, WHITE), rc(0.01f, 0.01f, 0.45f, 0.45f, BLUE), rc(0.55f, 0.01f, 0.99f, 0.45f, RED), rc(0.01f, 0.55f, 0.45f, 0.99f, RED), rc(0.55f, 0.55f, 0.99f, 0.99f, BLUE), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.10f))))
    // red crescent and star straddling the green/white divide, instead of a dot
    f("dz", "Algeria", GREEN, listOf(
        vb(0.50f, 0.99f, WHITE),
        cb(RED, ToyCircle(0.44f, 0.5f, 0.16f)),
        halfDiscSide(0.50f, 0.5f, 0.13f, true, GREEN),
        halfDiscSide(0.50f, 0.5f, 0.13f, false, WHITE),
        star(0.585f, 0.5f, 0.062f, 5, RED, rot = 90f),
    ))
    f("ec", "Ecuador", GOLD, listOf(hb(0.5f, 0.75f, BLUE), hb(0.75f, 0.99f, RED), cb(GREEN, ToyCircle(0.5f, 0.38f, 0.16f))))
    f("ee", "Estonia", NAVY, TRIH(NAVY, BLACK, WHITE))
    f("eg", "Egypt", RED, TRIH(RED, WHITE, BLACK) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("eh", "Western Sahara", BLACK, TRIH(BLACK, WHITE, GREEN) + listOf(cb(RED, ToyCircle(0.30f, 0.5f, 0.13f))))
    f("er", "Eritrea", GREEN, listOf(vb(0.01f, 0.55f, BLUE), cb(RED, ToyCircle(0.32f, 0.5f, 0.12f))))
    f("es", "Spain", RED, listOf(hb(0.28f, 0.72f, GOLD)))
    f("et", "Ethiopia", GREEN, TRIH(GREEN, GOLD, RED) + listOf(cb(BLUE, ToyCircle(0.5f, 0.5f, 0.13f)), star(0.5f, 0.5f, 0.10f, 5, GOLD)))
    f("fi", "Finland", WHITE, NV(WHITE, BLUE))
    f("fj", "Fiji", SKY, miniUK())
    f("fm", "Micronesia", SKY, listOf(starGrid(0.34f, 0.34f, 0.16f, 0.16f, 2, 2, 0.055f, WHITE)))
    f("fo", "Faroe Islands", WHITE, NV(WHITE, RED) + listOf(vb(0.32f, 0.38f, NAVY)))
    f("fr", "France", WHITE, V3(BLUE, WHITE, RED))
    f("ga", "Gabon", GREEN, TRIH(GREEN, GOLD, BLUE))
    // He asked for the flag he knows: a red St George cross on white, reaching every edge. The blue
    // field version made the dark navy the largest area of a 46dp badge, which is the "black line
    // around it" he reported - the saltire of the Union Jack is not drawn here either way
    f("gb", "United Kingdom", WHITE, listOf(
        rc(0.42f, 0.01f, 0.58f, 0.99f, RED), rc(0.01f, 0.42f, 0.99f, 0.58f, RED),
    ))
    f("gd", "Grenada", RED, listOf(hb(0.24f, 0.44f, GOLD), hb(0.44f, 0.62f, GREEN), hb(0.62f, 0.82f, GOLD), cb(RED, ToyCircle(0.5f, 0.53f, 0.08f))))
    f("ge", "Georgia", WHITE, CROSS(RED) + listOf(rc(0.30f, 0.16f, 0.40f, 0.26f, RED), rc(0.30f, 0.74f, 0.40f, 0.84f, RED), rc(0.60f, 0.16f, 0.70f, 0.26f, RED), rc(0.60f, 0.74f, 0.70f, 0.84f, RED)))
    f("gf", "French Guiana", BLUE, TRIH(BLUE, WHITE, RED) + listOf(cb(RED, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("gh", "Ghana", RED, TRIH(RED, GOLD, GREEN) + listOf(star(0.5f, 0.5f, 0.11f, 5, BLACK)))
    f("gi", "Gibraltar", WHITE, listOf(hb(0.62f, 0.90f, RED)))
    f("gl", "Greenland", WHITE, listOf(hb(0.5f, 0.99f, RED), cb(WHITE, ToyCircle(0.38f, 0.5f, 0.20f)), cb(RED, ToyCircle(0.52f, 0.5f, 0.20f))))
    f("gm", "Gambia", RED, TRIH(RED, BLUE, GREEN) + listOf(hb(0.42f, 0.46f, WHITE), hb(0.54f, 0.58f, WHITE)))
    f("gn", "Guinea", RED, TRIV(RED, GOLD, GREEN))
    f("gp", "Guadeloupe", NAVY, TRIH(NAVY, WHITE, RED))
    f("gq", "Equatorial Guinea", GREEN, TRIH(GREEN, WHITE, RED) + listOf(cb(SKY, ToyCircle(0.28f, 0.5f, 0.12f))))
    f("gr", "Greece", BLUE, listOf(
        stripes(9, WHITE),
        rc(0.02f, 0.04f, 0.36f, 0.458f, BLUE), rc(0.155f, 0.04f, 0.225f, 0.458f, WHITE), rc(0.02f, 0.225f, 0.36f, 0.273f, WHITE),
    ))
    f("gs", "South Georgia", NAVY, miniUK())
    f("gt", "Guatemala", SKY, TRIV(SKY, WHITE, SKY) + listOf(cb(GREEN, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("gu", "Guam", NAVY, listOf(cb(RED, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("gw", "Guinea-Bissau", RED, listOf(vb(0.01f, 0.38f, RED), hb(0.366f, 0.634f, GOLD), hb(0.634f, 0.99f, GREEN), cb(BLACK, ToyCircle(0.20f, 0.5f, 0.10f))))
    f("gy", "Guyana", GREEN, listOf(vb(0.01f, 0.40f, BLACK), vb(0.40f, 0.50f, WHITE), vb(0.50f, 0.60f, GOLD), vb(0.60f, 0.99f, GREEN), cb(RED, ToyCircle(0.35f, 0.5f, 0.11f))))
    f("hk", "Hong Kong", RED, listOf(star(0.5f, 0.5f, 0.16f, 5, WHITE, w = 0.62f)))
    f("hm", "Heard Island", NAVY, miniUK())
    f("hn", "Honduras", SKY, TRIH(SKY, WHITE, SKY) + listOf(cb(SKY, ToyCircle(0.5f, 0.5f, 0.08f))))
    // the shield is literally a checkerboard: white field with red squares, 5x5
    f("hr", "Croatia", RED, TRIH(RED, WHITE, BLUE) + listOf(
        rc(0.42f, 0.30f, 0.58f, 0.46f, WHITE),
        checker(0.42f, 0.30f, 0.032f, 5, 5, RED, 0),
    ))
    f("ht", "Haiti", NAVY, listOf(rc(0.02f, 0.04f, 0.98f, 0.50f, NAVY), rc(0.02f, 0.50f, 0.98f, 0.96f, RED), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("hu", "Hungary", RED, TRIH(RED, WHITE, GREEN))
    f("id", "Indonesia", RED, H2(RED) + listOf(hb(0.5f, 0.99f, WHITE)))
    f("ie", "Ireland", GREEN, TRIV(GREEN, WHITE, ORANGE))
    f("il", "Israel", WHITE, listOf(hb(0.14f, 0.26f, BLUE), hb(0.74f, 0.86f, BLUE), star(0.5f, 0.5f, 0.15f, 6, BLUE, w = 0.58f)))
    f("im", "Isle of Man", RED, miniUK())
    f("in", "India", Color(0xFFFF9933), listOf(
        hb(0.366f, 0.634f, WHITE), hb(0.634f, 0.99f, GREEN),
        // the chakra: a navy ring with a navy hub, which is a wheel at icon size
        cb(NAVY, ToyCircle(0.5f, 0.5f, 0.10f)), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.075f)), cb(NAVY, ToyCircle(0.5f, 0.5f, 0.028f)),
    ))
    f("io", "British Indian Ocean Ter.", NAVY, miniUK())
    f("iq", "Iraq", RED, TRIH(RED, WHITE, BLACK) + listOf(cb(GREEN, ToyCircle(0.5f, 0.56f, 0.05f))))
    /* the bands reach the edges (they stopped 0.06 short, which showed as white strips) and the
       middle carries the red emblem instead of a red dot */
    f("ir", "Iran", WHITE, listOf(
        hb(0.04f, 0.366f, GREEN), hb(0.634f, 0.96f, RED),
        FlagBand(RED, listOf(ToyPoly(listOf(
            Offset(0.500f, 0.400f), Offset(0.556f, 0.470f), Offset(0.540f, 0.585f),
            Offset(0.500f, 0.548f), Offset(0.460f, 0.585f), Offset(0.444f, 0.470f),
        )))),
    ))
    f("is", "Iceland", BLUE, NV(BLUE, WHITE) + listOf(vb(0.31f, 0.39f, RED), hb(0.43f, 0.57f, RED)))
    f("it", "Italy", WHITE, V3(GREEN, WHITE, RED))
    f("je", "Jersey", WHITE, miniUK())
    // the gold saltire with black and green quarters: four triangles leave the X in the base
    f("jm", "Jamaica", GOLD, listOf(
        FlagBand(BLACK, listOf(ToyPoly(listOf(Offset(0.02f, 0.04f), Offset(0.98f, 0.04f), Offset(0.5f, 0.5f))),
            ToyPoly(listOf(Offset(0.02f, 0.96f), Offset(0.98f, 0.96f), Offset(0.5f, 0.5f))))),
        FlagBand(GREEN, listOf(ToyPoly(listOf(Offset(0.02f, 0.04f), Offset(0.02f, 0.96f), Offset(0.5f, 0.5f))),
            ToyPoly(listOf(Offset(0.98f, 0.04f), Offset(0.98f, 0.96f), Offset(0.5f, 0.5f))))),
    ))
    f("jo", "Jordan", BLACK, TRIH(BLACK, WHITE, GREEN) + listOf(vb(0.01f, 0.38f, RED), star(0.18f, 0.5f, 0.06f, 7, WHITE)))
    f("jp", "Japan", WHITE, DISK(Color(0xFFD9344A), 0.26f))
    f("ke", "Kenya", BLACK, TRIH(BLACK, RED, GREEN) + listOf(hb(0.30f, 0.366f, WHITE), hb(0.634f, 0.70f, WHITE), cb(RED, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("kg", "Kyrgyzstan", RED, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("kh", "Cambodia", BLUE, listOf(hb(0.32f, 0.68f, RED), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("ki", "Kiribati", RED, listOf(hb(0.56f, 0.68f, WHITE), hb(0.68f, 0.78f, BLUE), cb(GOLD, ToyCircle(0.5f, 0.38f, 0.12f))))
    // four bands over the whole face, and the green hoist is a triangle with the crescent in it
    f("km", "Comoros", GOLD, listOf(
        hb(0.27f, 0.50f, WHITE), hb(0.50f, 0.73f, RED), hb(0.73f, 0.96f, BLUE),
        FlagBand(GREEN, listOf(ToyPoly(listOf(Offset(0.02f, 0.04f), Offset(0.32f, 0.50f), Offset(0.02f, 0.96f))))),
        cb(WHITE, ToyCircle(0.15f, 0.50f, 0.055f)),
    ))
    f("kn", "St. Kitts and Nevis", GREEN, listOf(hb(0.366f, 0.634f, BLACK), hb(0.634f, 0.99f, RED), star(0.32f, 0.36f, 0.05f, 5, WHITE), star(0.42f, 0.50f, 0.04f, 5, WHITE)))
    f("kp", "North Korea", BLUE, listOf(hb(0.16f, 0.24f, WHITE), hb(0.24f, 0.76f, RED), hb(0.76f, 0.84f, WHITE), cb(WHITE, ToyCircle(0.28f, 0.5f, 0.13f)), star(0.28f, 0.5f, 0.10f, 5, RED)))
    /* the taegeuk is two half discs plus two half-size ones, which is the S curve; the trigrams are
       three short bars each, upright because this renderer has no rotated rectangles */
    f("kr", "South Korea", WHITE, listOf(
        halfDisc(0.5f, 0.5f, 0.17f, true, RED),
        halfDisc(0.5f, 0.5f, 0.17f, false, BLUE),
        halfDisc(0.415f, 0.5f, 0.085f, false, RED),
        halfDisc(0.585f, 0.5f, 0.085f, true, BLUE),
        rc(0.13f, 0.13f, 0.25f, 0.163f, BLACK), rc(0.13f, 0.181f, 0.25f, 0.214f, BLACK), rc(0.13f, 0.232f, 0.25f, 0.265f, BLACK),
        rc(0.75f, 0.735f, 0.87f, 0.768f, BLACK), rc(0.75f, 0.786f, 0.87f, 0.819f, BLACK), rc(0.75f, 0.837f, 0.87f, 0.870f, BLACK),
        rc(0.75f, 0.13f, 0.87f, 0.163f, BLACK), rc(0.75f, 0.181f, 0.87f, 0.214f, BLACK), rc(0.75f, 0.232f, 0.87f, 0.265f, BLACK),
        rc(0.13f, 0.735f, 0.25f, 0.768f, BLACK), rc(0.13f, 0.786f, 0.25f, 0.819f, BLACK), rc(0.13f, 0.837f, 0.25f, 0.870f, BLACK),
    ))
    f("kw", "Kuwait", GREEN, TRIH(GREEN, WHITE, RED) + listOf(vb(0.01f, 0.30f, BLACK)))
    f("ky", "Cayman Islands", NAVY, miniUK())
    f("kz", "Kazakhstan", SKY, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.12f)), vb(0.01f, 0.14f, GOLD)))
    f("la", "Laos", NAVY, listOf(hb(0.28f, 0.72f, RED), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("lb", "Lebanon", RED, TRIH(RED, WHITE, RED) + listOf(cb(GREEN, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("lc", "St. Lucia", SKY, listOf(cb(WHITE, ToyCircle(0.5f, 0.38f, 0.10f)), cb(BLACK, ToyCircle(0.42f, 0.62f, 0.10f)), cb(GOLD, ToyCircle(0.58f, 0.62f, 0.10f))))
    f("li", "Liechtenstein", NAVY, listOf(hb(0.5f, 0.99f, RED), cb(GOLD, ToyCircle(0.28f, 0.30f, 0.09f))))
    f("lk", "Sri Lanka", GOLD, listOf(vb(0.05f, 0.22f, GREEN), vb(0.22f, 0.28f, ORANGE), rc(0.32f, 0.10f, 0.95f, 0.90f, Color(0xFF8D2029))))
    f("lr", "Liberia", RED, listOf(stripes(11, WHITE), rc(0.02f, 0.04f, 0.36f, 0.458f, NAVY), star(0.19f, 0.25f, 0.055f, 5, WHITE)))
    f("ls", "Lesotho", NAVY, TRIH(NAVY, WHITE, GREEN) + listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("lt", "Lithuania", GOLD, TRIH(GOLD, GREEN, RED))
    f("lu", "Luxembourg", RED, TRIH(RED, WHITE, SKY))
    f("lv", "Latvia", Color(0xFF9E3039), listOf(hb(0.40f, 0.60f, WHITE)))
    // Libya's flag carries no emblem: the white disc was invented
    f("ly", "Libya", RED, TRIH(RED, BLACK, GREEN))
    f("ma", "Morocco", RED, listOf(star(0.5f, 0.5f, 0.17f, 5, GREEN)))
    f("mc", "Monaco", RED, H2(RED) + listOf(hb(0.5f, 0.99f, WHITE)))
    f("md", "Moldova", NAVY, TRIV(BLUE, GOLD, RED) + listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("me", "Montenegro", RED, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("mf", "St. Martin", NAVY, miniUK())
    f("mg", "Madagascar", WHITE, listOf(vb(0.01f, 0.34f, RED), hb(0.5f, 0.99f, GREEN)))
    f("mh", "Marshall Islands", NAVY, listOf(hb(0.36f, 0.44f, WHITE), hb(0.44f, 0.52f, SKY), cb(WHITE, ToyCircle(0.24f, 0.30f, 0.10f))))
    f("mk", "North Macedonia", RED, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.13f))))
    f("ml", "Mali", GREEN, TRIV(GREEN, GOLD, RED))
    f("mm", "Myanmar", GOLD, TRIH(GOLD, GREEN, RED) + listOf(star(0.5f, 0.5f, 0.13f, 5, WHITE)))
    f("mn", "Mongolia", RED, TRIV(RED, BLUE, RED) + listOf(cb(GOLD, ToyCircle(0.17f, 0.5f, 0.09f))))
    f("mo", "Macau", GREEN, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.13f))))
    f("mp", "Northern Mariana Islands", NAVY, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.12f)), cb(SKY, ToyCircle(0.5f, 0.5f, 0.07f))))
    f("mq", "Martinique", NAVY, TRIH(NAVY, WHITE, RED))
    // red bands top and bottom of the green field, with the gold crescent and star
    f("mr", "Mauritania", GREEN, listOf(
        hb(0.04f, 0.16f, RED), hb(0.84f, 0.96f, RED),
        cb(GOLD, ToyCircle(0.46f, 0.50f, 0.12f)), cb(GREEN, ToyCircle(0.515f, 0.50f, 0.11f)),
        star(0.60f, 0.50f, 0.055f, 5, GOLD),
    ))
    f("ms", "Montserrat", NAVY, miniUK())
    f("mt", "Malta", WHITE, listOf(vb(0.01f, 0.28f, RED)))
    f("mu", "Mauritius", RED, listOf(hb(0.27f, 0.50f, BLUE), hb(0.50f, 0.73f, GOLD), hb(0.73f, 0.96f, GREEN)))
    f("mv", "Maldives", RED, listOf(
        rc(0.22f, 0.24f, 0.78f, 0.76f, GREEN),
        cb(WHITE, ToyCircle(0.46f, 0.50f, 0.11f)), cb(GREEN, ToyCircle(0.515f, 0.50f, 0.10f)),
    ))
    f("mw", "Malawi", BLACK, TRIH(BLACK, RED, GREEN) + listOf(cb(RED, ToyCircle(0.5f, 0.24f, 0.08f))))
    // the eagle over the cactus, not a brown dot
    f("mx", "Mexico", WHITE, TRIV(GREEN, WHITE, RED) + listOf(
        FlagBand(Color(0xFF7A5A2B), listOf(ToyPoly(listOf(
            Offset(0.50f, 0.40f), Offset(0.63f, 0.46f), Offset(0.56f, 0.52f),
            Offset(0.62f, 0.62f), Offset(0.50f, 0.58f), Offset(0.38f, 0.62f),
            Offset(0.44f, 0.52f), Offset(0.37f, 0.46f),
        )))),
        cb(GREEN, ToyCircle(0.5f, 0.67f, 0.03f)),
    ))
    // 14 stripes by count, and the crescent is a crescent (the cut circle is the canton colour)
    f("my", "Malaysia", RED, listOf(
        stripes(14, WHITE),
        rc(0.02f, 0.04f, 0.46f, 0.50f, NAVY),
        cb(GOLD, ToyCircle(0.19f, 0.27f, 0.09f)), cb(NAVY, ToyCircle(0.245f, 0.27f, 0.10f)),
        star(0.33f, 0.27f, 0.075f, 14, GOLD),
    ))
    f("mz", "Mozambique", GREEN, TRIH(GREEN, BLACK, GOLD) + listOf(cb(WHITE, ToyCircle(0.32f, 0.5f, 0.11f)), hb(0.30f, 0.366f, WHITE), hb(0.634f, 0.70f, WHITE)))
    f("na", "Namibia", NAVY, listOf(hb(0.04f, 0.366f, NAVY), hb(0.366f, 0.634f, RED), hb(0.634f, 0.96f, GREEN), cb(GOLD, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("nc", "New Caledonia", NAVY, miniUK())
    f("ne", "Niger", ORANGE, TRIH(ORANGE, WHITE, GREEN) + listOf(cb(ORANGE, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("nf", "Norfolk Island", GREEN, listOf(vb(0.40f, 0.60f, WHITE), cb(GREEN, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("ng", "Nigeria", GREEN, V3(GREEN, WHITE, GREEN))
    f("ni", "Nicaragua", SKY, TRIH(SKY, WHITE, SKY) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("nl", "Netherlands", RED, TRIH(RED, WHITE, NAVY))
    f("no", "Norway", RED, NV(RED, WHITE) + listOf(vb(0.31f, 0.39f, NAVY), hb(0.43f, 0.57f, NAVY)))
    /* the double pennant: two crimson triangles in a navy edge, on white so the pennant shape reads
       inside a square avatar instead of looking like a red circle */
    f("np", "Nepal", WHITE, listOf(
        FlagBand(NAVY, listOf(ToyPoly(listOf(Offset(0.12f, 0.04f), Offset(0.93f, 0.30f), Offset(0.12f, 0.33f))),
            ToyPoly(listOf(Offset(0.12f, 0.33f), Offset(0.97f, 0.66f), Offset(0.12f, 0.96f))))),
        FlagBand(RED, listOf(
            ToyPoly(listOf(Offset(0.15f, 0.08f), Offset(0.85f, 0.29f), Offset(0.15f, 0.30f))),
            ToyPoly(listOf(Offset(0.15f, 0.35f), Offset(0.89f, 0.65f), Offset(0.15f, 0.92f))),
        )),
        cb(WHITE, ToyCircle(0.33f, 0.19f, 0.05f)), cb(WHITE, ToyCircle(0.33f, 0.62f, 0.05f)),
    ))
    f("nr", "Nauru", NAVY, listOf(hb(0.58f, 0.66f, GOLD), star(0.30f, 0.32f, 0.09f, 12, WHITE)))
    f("nu", "Niue", GOLD, miniUK())
    f("nz", "New Zealand", NAVY, miniUK() + listOf(cb(RED, ToyCircle(0.64f, 0.26f, 0.055f), ToyCircle(0.78f, 0.42f, 0.055f), ToyCircle(0.66f, 0.60f, 0.055f))))
    f("om", "Oman", WHITE, TRIH(WHITE, RED, GREEN) + listOf(vb(0.01f, 0.30f, RED)))
    // white field, blue and red quarters to the corners, each with its own star
    f("pa", "Panama", WHITE, listOf(
        rc(0.50f, 0.04f, 0.98f, 0.50f, RED), rc(0.02f, 0.50f, 0.50f, 0.96f, BLUE),
        cb(BLUE, ToyCircle(0.26f, 0.27f, 0.075f)), cb(RED, ToyCircle(0.74f, 0.73f, 0.075f)),
    ))
    f("pe", "Peru", RED, V3(RED, WHITE, RED))
    f("pf", "French Polynesia", RED, TRIH(RED, WHITE, RED) + listOf(cb(GOLD, ToyCircle(0.5f, 0.44f, 0.09f))))
    // black over red to the edges, the bird of paradise as a gold star rather than a dot
    f("pg", "Papua New Guinea", BLACK, listOf(hb(0.50f, 0.96f, RED), star(0.30f, 0.30f, 0.10f, 5, GOLD, w = 0.5f)))
    // blue and red to the edges, white hoist triangle, sun and three stars in it
    f("ph", "Philippines", NAVY, listOf(
        hb(0.50f, 0.96f, RED),
        FlagBand(WHITE, listOf(ToyPoly(listOf(Offset(0.02f, 0.04f), Offset(0.42f, 0.50f), Offset(0.02f, 0.96f))))),
        cb(GOLD, ToyCircle(0.17f, 0.50f, 0.055f)),
        star(0.27f, 0.25f, 0.035f, 5, GOLD), star(0.31f, 0.50f, 0.035f, 5, GOLD), star(0.27f, 0.75f, 0.035f, 5, GOLD),
    ))
    f("pk", "Pakistan", GREEN, listOf(vb(0.01f, 0.24f, WHITE), cb(WHITE, ToyCircle(0.55f, 0.5f, 0.16f)), cb(GREEN, ToyCircle(0.60f, 0.5f, 0.17f)), star(0.72f, 0.43f, 0.055f, 5, WHITE, rot = 90f)))
    f("pl", "Poland", WHITE, H2(WHITE) + listOf(hb(0.5f, 0.99f, RED)))
    f("pm", "St. Pierre and Miquelon", NAVY, miniUK())
    f("pn", "Pitcairn Islands", NAVY, miniUK())
    f("pr", "Puerto Rico", RED, listOf(
        stripes(5, WHITE),
        FlagBand(NAVY, listOf(ToyPoly(listOf(Offset(0.02f, 0.04f), Offset(0.46f, 0.50f), Offset(0.02f, 0.96f))))),
        star(0.16f, 0.50f, 0.07f, 5, WHITE),
    ))
    f("ps", "Palestine", BLACK, TRIH(BLACK, WHITE, GREEN) + listOf(vb(0.01f, 0.36f, RED)))
    f("pt", "Portugal", RED, listOf(vb(0.01f, 0.38f, GREEN), cb(GOLD, ToyCircle(0.33f, 0.5f, 0.10f))))
    f("pw", "Palau", SKY, listOf(cb(GOLD, ToyCircle(0.42f, 0.5f, 0.20f))))
    f("py", "Paraguay", RED, TRIH(RED, WHITE, BLUE) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("qa", "Qatar", WHITE, listOf(vb(0.36f, 0.99f, Color(0xFF8D1B3D))))
    f("re", "Reunion", NAVY, TRIH(NAVY, WHITE, RED))
    f("ro", "Romania", NAVY, TRIV(BLUE, GOLD, RED))
    f("rs", "Serbia", RED, TRIH(RED, BLUE, WHITE) + listOf(cb(GOLD, ToyCircle(0.30f, 0.44f, 0.10f))))
    f("ru", "Russia", WHITE, TRIH(WHITE, BLUE, RED))
    f("rw", "Rwanda", SKY, TRIH(SKY, GOLD, GREEN) + listOf(cb(GOLD, ToyCircle(0.5f, 0.32f, 0.11f))))
    // the shahada as a bar and the sword under it: one white stripe was neither
    f("sa", "Saudi Arabia", GREEN, listOf(rc(0.20f, 0.36f, 0.80f, 0.475f, WHITE), rc(0.14f, 0.60f, 0.86f, 0.645f, WHITE)))
    // blue field to the top edge, with the five white stars of the southern cross
    f("sb", "Solomon Islands", GREEN, listOf(
        hb(0.04f, 0.50f, SKY),
        star(0.25f, 0.20f, 0.035f, 5, WHITE), star(0.40f, 0.28f, 0.032f, 5, WHITE),
        star(0.28f, 0.36f, 0.032f, 5, WHITE), star(0.45f, 0.42f, 0.030f, 5, WHITE), star(0.20f, 0.43f, 0.030f, 5, WHITE),
    ))
    // five rays radiating from the hoist top - blue, gold, red, white, green; the disc that stood in
    // for them dropped the gold and the white entirely
    f("sc", "Seychelles", NAVY, listOf(vb(0.01f, 0.34f, NAVY), vb(0.34f, 0.50f, GOLD), vb(0.50f, 0.64f, RED), hb(0.64f, 0.80f, WHITE), hb(0.80f, 0.99f, GREEN)))
    f("sd", "Sudan", RED, TRIH(RED, WHITE, BLACK) + listOf(vb(0.01f, 0.34f, GREEN)))
    f("se", "Sweden", BLUE, NV(BLUE, GOLD))
    // white below reaches the bottom edge now, with the crescent and five stars in the red half
    f("sg", "Singapore", RED, listOf(
        hb(0.5f, 0.96f, WHITE),
        cb(WHITE, ToyCircle(0.33f, 0.27f, 0.13f)),
        cb(RED, ToyCircle(0.385f, 0.27f, 0.14f)),
        starGrid(0.49f, 0.19f, 0.062f, 0.062f, 2, 2, 0.027f, WHITE),
        star(0.585f, 0.335f, 0.027f, 5, WHITE),
    ))
    f("sh", "St. Helena", NAVY, miniUK())
    f("si", "Slovenia", WHITE, TRIH(WHITE, BLUE, RED) + listOf(cb(WHITE, ToyCircle(0.26f, 0.34f, 0.09f))))
    f("sj", "Svalbard", NAVY, miniUK())
    f("sk", "Slovakia", WHITE, TRIH(WHITE, BLUE, RED) + listOf(cb(RED, ToyCircle(0.30f, 0.5f, 0.11f))))
    f("sl", "Sierra Leone", GREEN, TRIH(GREEN, WHITE, SKY))
    f("sm", "San Marino", WHITE, TRIH(WHITE, SKY, WHITE))
    f("sn", "Senegal", GREEN, TRIV(GREEN, GOLD, RED) + listOf(star(0.5f, 0.5f, 0.11f, 5, GREEN)))
    f("so", "Somalia", SKY, listOf(star(0.5f, 0.5f, 0.20f, 5, WHITE)))
    f("sr", "Suriname", GREEN, listOf(hb(0.20f, 0.28f, WHITE), hb(0.28f, 0.72f, RED), hb(0.72f, 0.80f, WHITE), star(0.5f, 0.5f, 0.13f, 5, GOLD)))
    f("ss", "South Sudan", BLACK, TRIH(BLACK, RED, GREEN) + listOf(vb(0.01f, 0.36f, SKY), star(0.19f, 0.50f, 0.07f, 5, GOLD)))
    f("st", "Sao Tome and Principe", GREEN, TRIH(GREEN, GOLD, RED) + listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("sv", "El Salvador", SKY, TRIH(SKY, WHITE, SKY) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    // red over blue to the edges, with the white hoist triangle
    f("sx", "Sint Maarten", NAVY, listOf(
        hb(0.04f, 0.50f, RED), hb(0.50f, 0.96f, BLUE),
        FlagBand(WHITE, listOf(ToyPoly(listOf(Offset(0.02f, 0.04f), Offset(0.42f, 0.50f), Offset(0.02f, 0.96f))))),
    ))
    f("sy", "Syria", RED, TRIH(RED, WHITE, BLACK) + listOf(star(0.42f, 0.5f, 0.05f, 5, GREEN), star(0.58f, 0.5f, 0.05f, 5, GREEN)))
    f("sz", "Eswatini", NAVY, listOf(hb(0.10f, 0.30f, GOLD), hb(0.70f, 0.90f, GOLD), rc(0.02f, 0.30f, 0.98f, 0.70f, RED), cb(BLACK, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("tc", "Turks and Caicos", NAVY, miniUK())
    f("td", "Chad", NAVY, TRIV(NAVY, GOLD, RED))
    f("tf", "French Southern Lands", NAVY, miniUK())
    f("tg", "Togo", GREEN, TRIH(GREEN, GOLD, GREEN) + listOf(rc(0.02f, 0.04f, 0.36f, 0.56f, RED), star(0.20f, 0.30f, 0.085f, 5, WHITE)))
    f("th", "Thailand", RED, listOf(hb(0.24f, 0.38f, WHITE), hb(0.38f, 0.62f, NAVY), hb(0.62f, 0.76f, WHITE)))
    f("tj", "Tajikistan", RED, TRIH(RED, WHITE, GREEN) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("tk", "Tokelau", GOLD, miniUK())
    f("tl", "Timor-Leste", RED, listOf(vb(0.01f, 0.40f, BLACK), vb(0.40f, 0.52f, GOLD), cb(WHITE, ToyCircle(0.22f, 0.5f, 0.09f))))
    f("tm", "Turkmenistan", GREEN, listOf(vb(0.01f, 0.22f, RED), cb(WHITE, ToyCircle(0.55f, 0.42f, 0.11f))))
    // white disc, red crescent, red star - the dot version read as a red coin
    f("tn", "Tunisia", RED, listOf(
        cb(WHITE, ToyCircle(0.47f, 0.5f, 0.25f)),
        cb(RED, ToyCircle(0.43f, 0.5f, 0.13f)),
        cb(WHITE, ToyCircle(0.475f, 0.5f, 0.105f)),
        star(0.565f, 0.5f, 0.058f, 5, RED, rot = 90f),
    ))
    // white canton with a red cross, not a red dot in a white square
    f("to", "Tonga", RED, listOf(
        rc(0.02f, 0.04f, 0.42f, 0.48f, WHITE),
        rc(0.195f, 0.10f, 0.245f, 0.40f, RED), rc(0.10f, 0.225f, 0.34f, 0.275f, RED),
    ))
    f("tr", "Turkey", Color(0xFFE30A17), listOf(
        cb(Color.White, ToyCircle(0.40f, 0.5f, 0.24f)),
        cb(Color(0xFFE30A17), ToyCircle(0.475f, 0.5f, 0.19f)),
        star(0.655f, 0.5f, 0.085f, 5, Color.White, rot = 90f),
    ))
    // red field, white-edged black diagonal, instead of a black disc
    f("tt", "Trinidad and Tobago", RED, listOf(
        FlagBand(WHITE, listOf(ToyPoly(listOf(Offset(0.06f, 0.04f), Offset(0.30f, 0.04f), Offset(0.98f, 0.60f), Offset(0.98f, 0.86f))))),
        FlagBand(BLACK, listOf(ToyPoly(listOf(Offset(0.20f, 0.04f), Offset(0.30f, 0.04f), Offset(0.98f, 0.70f), Offset(0.98f, 0.80f))))),
    ))
    f("tv", "Tuvalu", SKY, miniUK() + listOf(cb(GOLD, ToyCircle(0.7f, 0.6f, 0.06f))))
    f("tw", "Taiwan", RED, listOf(rc(0.02f, 0.04f, 0.46f, 0.50f, NAVY), cb(WHITE, ToyCircle(0.25f, 0.30f, 0.11f))))
    // green over blue across a black band, not a sky dot in the corner
    f("tz", "Tanzania", GREEN, listOf(
        hb(0.36f, 0.64f, BLACK), hb(0.30f, 0.36f, GOLD), hb(0.64f, 0.70f, GOLD),
        FlagBand(BLUE, listOf(ToyPoly(listOf(Offset(0.30f, 0.70f), Offset(0.99f, 0.70f), Offset(0.99f, 0.96f), Offset(0.30f, 0.96f))))),
    ))
    f("ua", "Ukraine", BLUE, H2(BLUE) + listOf(hb(0.5f, 0.99f, GOLD)))
    f("ug", "Uganda", BLACK, TRIH(BLACK, GOLD, RED) + listOf(hb(0.30f, 0.366f, WHITE), hb(0.634f, 0.70f, WHITE), cb(GREY, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("um", "US Minor Outlying Islands", NAVY, miniUK())
    /* thirteen stripes drawn by count, not four hand-placed ones, and a five-by-four star field
       instead of four dots */
    f("us", "United States", RED, listOf(
        stripes(13, WHITE),
        rc(0.02f, 0.04f, 0.46f, 0.5354f, BLUE),
        starGrid(0.085f, 0.105f, 0.083f, 0.108f, 5, 4, 0.028f, WHITE),
    ))
    f("uy", "Uruguay", WHITE, listOf(stripes(9, SKY), rc(0.02f, 0.04f, 0.36f, 0.409f, WHITE), cb(GOLD, ToyCircle(0.19f, 0.22f, 0.075f))))
    f("uz", "Uzbekistan", SKY, TRIH(SKY, WHITE, GREEN) + listOf(
        hb(0.366f, 0.40f, RED), hb(0.60f, 0.634f, RED),
        cb(WHITE, ToyCircle(0.19f, 0.27f, 0.09f)), cb(SKY, ToyCircle(0.23f, 0.27f, 0.08f)),
        starGrid(0.34f, 0.18f, 0.058f, 0.058f, 6, 2, 0.019f, WHITE),
    ))
    f("va", "Vatican City", GOLD, TRIH(GOLD, WHITE, GOLD) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("vc", "St. Vincent", SKY, TRIV(SKY, GOLD, GREEN) + listOf(cb(GREEN, ToyCircle(0.5f, 0.5f, 0.08f))))
    f("ve", "Venezuela", GOLD, TRIH(GOLD, BLUE, RED) + listOf(starGrid(0.36f, 0.36f, 0.05f, 0.05f, 4, 2, 0.022f, WHITE)))
    f("vg", "British Virgin Islands", NAVY, miniUK())
    f("vi", "U.S. Virgin Islands", WHITE, listOf(cb(GOLD, ToyCircle(0.42f, 0.5f, 0.12f)), cb(Color(0xFFB8763E), ToyCircle(0.6f, 0.42f, 0.07f))))
    f("vn", "Vietnam", RED, listOf(star(0.5f, 0.5f, 0.20f, 5, GOLD)))
    f("vu", "Vanuatu", BLACK, listOf(hb(0.30f, 0.70f, GREEN), hb(0.70f, 0.96f, RED), cb(GOLD, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("wf", "Wallis and Futuna", NAVY, miniUK())
    f("ws", "Samoa", RED, listOf(
        rc(0.02f, 0.04f, 0.44f, 0.52f, NAVY),
        starGrid(0.13f, 0.13f, 0.13f, 0.13f, 2, 2, 0.035f, WHITE), star(0.26f, 0.38f, 0.032f, 5, WHITE),
    ))
    f("xk", "Kosovo", NAVY, listOf(cb(GOLD, ToyCircle(0.5f, 0.44f, 0.13f))))
    f("ye", "Yemen", RED, TRIH(RED, WHITE, BLACK))
    f("yt", "Mayotte", NAVY, miniUK())
    f("za", "South Africa", GREEN, listOf(hb(0.42f, 0.58f, GREEN), hb(0.30f, 0.42f, WHITE), hb(0.58f, 0.70f, WHITE), hb(0.04f, 0.30f, RED), hb(0.70f, 0.96f, BLUE), vb(0.01f, 0.30f, BLACK)))
    // the orange/black/red block is vertical at the fly, not three horizontal bands, and the eagle
    // is above it
    f("zm", "Zambia", GREEN, listOf(
        rc(0.62f, 0.55f, 0.76f, 0.96f, RED), rc(0.76f, 0.55f, 0.90f, 0.96f, BLACK), rc(0.90f, 0.55f, 0.98f, 0.96f, ORANGE),
        star(0.50f, 0.22f, 0.07f, 5, GOLD, w = 0.45f),
    ))
    f("zw", "Zimbabwe", GREEN, TRIH(GREEN, GOLD, RED) + listOf(vb(0.01f, 0.26f, BLACK), cb(GOLD, ToyCircle(0.16f, 0.5f, 0.10f))))

    return out
}
