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

    f("ad", "آندورا", BLUE, V3(BLUE, GOLD, RED))
    f("ae", "امارات", GREEN, listOf(hb(0.366f, 0.634f, WHITE), hb(0.634f, 0.90f, BLACK), vb(0.05f, 0.28f, RED)))
    f("af", "افغانستان", BLACK, TRIH(BLACK, RED, GREEN))
    f("ag", "آنتیگوا و باربودا", NAVY, listOf(hb(0.10f, 0.35f, NAVY), hb(0.35f, 0.55f, BLUE), hb(0.55f, 0.75f, WHITE), hb(0.75f, 0.90f, NAVY)))
    f("ai", "آنگویلا", BLUE, miniUK())
    f("al", "آلبانی", RED, listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.13f))))
    f("am", "ارمنستان", RED, TRIH(RED, BLUE, GOLD))
    f("ao", "آنگولا", RED, listOf(hb(0.5f, 0.90f, BLACK), cb(GOLD, ToyCircle(0.42f, 0.42f, 0.12f))))
    f("ar", "آرژانتین", SKY, listOf(hb(0.366f, 0.634f, WHITE), cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("at", "اتریش", WHITE, listOf(hb(0.366f, 0.634f, RED)))
    f("au", "استرالیا", NAVY, miniUK() + listOf(cb(WHITE, ToyCircle(0.64f, 0.26f, 0.05f), ToyCircle(0.78f, 0.40f, 0.05f), ToyCircle(0.64f, 0.60f, 0.05f), ToyCircle(0.82f, 0.78f, 0.05f))))
    f("aw", "آروبا", SKY, listOf(hb(0.62f, 0.68f, WHITE), hb(0.68f, 0.74f, RED), cb(WHITE, ToyCircle(0.16f, 0.30f, 0.06f), ToyCircle(0.24f, 0.38f, 0.06f))))
    f("ax", "جزایر آلند", BLUE, NV(BLUE, GOLD) + listOf(vb(0.31f, 0.39f, RED), hb(0.43f, 0.57f, RED)))
    f("az", "آذربایجان", SKY, listOf(hb(0.366f, 0.634f, RED), hb(0.634f, 0.90f, GREEN), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("ba", "بوسنی و هرزگوین", NAVY, listOf(vb(0.05f, 0.55f, GOLD), hb(0.66f, 0.90f, NAVY)))
    f("bb", "باربادوس", BLUE, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("bd", "بنگلادش", GREEN, DISK(RED, 0.24f))
    f("be", "بلژیک", BLACK, TRIV(BLACK, GOLD, RED))
    f("bf", "بورکینافاسو", RED, listOf(hb(0.5f, 0.90f, GREEN), cb(GOLD, ToyCircle(0.5f, 0.42f, 0.11f))))
    f("bg", "بلغارستان", WHITE, TRIH(WHITE, GREEN, RED))
    f("bh", "بحرین", WHITE, listOf(vb(0.34f, 0.95f, RED)))
    f("bi", "بوروندی", GREEN, listOf(hb(0.366f, 0.634f, WHITE), cb(RED, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("bj", "بنین", GREEN, listOf(vb(0.05f, 0.38f, GREEN), hb(0.366f, 0.634f, GOLD), hb(0.634f, 0.90f, RED)))
    f("bl", "سنت بارتلمی", NAVY, miniUK())
    f("bm", "برمودا", RED, miniUK())
    f("bn", "برونئی", GOLD, listOf(hb(0.28f, 0.52f, WHITE), hb(0.52f, 0.76f, BLACK)))
    f("bo", "بولیوی", RED, TRIH(RED, GOLD, GREEN))
    f("br", "برزیل", GREEN, listOf(hb(0.30f, 0.70f, GOLD), cb(BLUE, ToyCircle(0.5f, 0.5f, 0.14f)), cb(WHITE, ToyCircle(0.46f, 0.45f, 0.04f))))
    f("bs", "باهاما", SKY, listOf(hb(0.366f, 0.634f, GOLD), vb(0.05f, 0.30f, BLACK)))
    f("bt", "بوتان", GOLD, listOf(cb(Color(0xFFE8663C), ToyCircle(0.5f, 0.5f, 0.16f))))
    f("bw", "بوتسوانا", SKY, listOf(hb(0.42f, 0.50f, BLACK), hb(0.50f, 0.58f, WHITE)))
    f("by", "بلاروس", RED, listOf(hb(0.66f, 0.90f, GREEN), vb(0.05f, 0.22f, WHITE)))
    f("bz", "بلیز", NAVY, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.20f)), cb(BLUE, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("ca", "کانادا", WHITE, listOf(vb(0.05f, 0.30f, RED), vb(0.70f, 0.95f, RED), cb(RED, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("cd", "کنگو دموکراتیک", SKY, listOf(vb(0.62f, 0.72f, RED), cb(GOLD, ToyCircle(0.24f, 0.28f, 0.09f))))
    f("cf", "آفریقای مرکزی", BLUE, listOf(hb(0.10f, 0.30f, BLUE), hb(0.30f, 0.50f, WHITE), hb(0.50f, 0.70f, GREEN), hb(0.70f, 0.90f, GOLD), vb(0.05f, 0.25f, RED)))
    f("cg", "کنگو", GREEN, listOf(vb(0.62f, 0.95f, RED), vb(0.50f, 0.62f, GOLD)))
    f("ch", "سوئیس", RED, CROSS(WHITE))
    f("ci", "ساحل عاج", WHITE, TRIV(GOLD, WHITE, ORANGE))
    f("ck", "جزایر کوک", BLUE, miniUK() + listOf(cb(WHITE, ToyCircle(0.7f, 0.5f, 0.07f))))
    f("cl", "شیلی", WHITE, listOf(hb(0.5f, 0.90f, RED), rc(0.05f, 0.10f, 0.36f, 0.50f, BLUE), cb(WHITE, ToyCircle(0.20f, 0.30f, 0.07f))))
    f("cm", "کامرون", GREEN, TRIV(GREEN, RED, GOLD) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("cn", "چین", RED, listOf(
        cb(GOLD, ToyCircle(0.33f, 0.38f, 0.13f)),
        cb(GOLD, ToyCircle(0.52f, 0.24f, 0.05f)),
        cb(GOLD, ToyCircle(0.56f, 0.36f, 0.05f)),
        cb(GOLD, ToyCircle(0.56f, 0.50f, 0.05f)),
    ))
    f("co", "کلمبیا", GOLD, listOf(hb(0.10f, 0.50f, GOLD), hb(0.50f, 0.75f, BLUE), hb(0.75f, 0.90f, RED)))
    f("cr", "کاستاریکا", BLUE, listOf(hb(0.24f, 0.36f, WHITE), hb(0.36f, 0.64f, RED), hb(0.64f, 0.76f, WHITE)))
    f("cu", "کوبا", BLUE, listOf(hb(0.10f, 0.26f, WHITE), hb(0.42f, 0.58f, WHITE), hb(0.74f, 0.90f, WHITE), rc(0.05f, 0.10f, 0.42f, 0.56f, RED), cb(WHITE, ToyCircle(0.24f, 0.33f, 0.09f))))
    f("cv", "کیپ‌ورد", NAVY, listOf(hb(0.44f, 0.56f, WHITE), hb(0.56f, 0.68f, RED), cb(GOLD, ToyCircle(0.5f, 0.62f, 0.05f))))
    f("cw", "کوراسائو", BLUE, listOf(hb(0.58f, 0.70f, GOLD), vb(0.05f, 0.22f, WHITE)))
    f("cx", "جزیره کریسمس", BLUE, listOf(cb(GREEN, ToyCircle(0.72f, 0.62f, 0.13f)), cb(GOLD, ToyCircle(0.28f, 0.32f, 0.10f))))
    f("cy", "قبرس", WHITE, listOf(cb(Color(0xFFB8763E), ToyCircle(0.5f, 0.52f, 0.10f))))
    f("cz", "چک", WHITE, listOf(hb(0.5f, 0.90f, RED), FlagBand(BLUE, listOf(ToyPoly(listOf(Offset(0.05f, 0.10f), Offset(0.34f, 0.50f), Offset(0.05f, 0.90f)))))))
    f("de", "آلمان", BLACK, TRIH(BLACK, RED, GOLD))
    f("dj", "جیبوتی", SKY, listOf(hb(0.5f, 0.90f, GREEN), vb(0.05f, 0.40f, WHITE), cb(RED, ToyCircle(0.20f, 0.32f, 0.08f))))
    f("dk", "دانمارک", RED, NV(RED, WHITE))
    f("dm", "دومینیکا", GREEN, listOf(hb(0.366f, 0.634f, GOLD), hb(0.634f, 0.90f, BLACK), cb(RED, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("do", "جمهوری دومینیکن", NAVY, listOf(rc(0.05f, 0.10f, 0.48f, 0.48f, BLUE), rc(0.52f, 0.10f, 0.95f, 0.48f, RED), rc(0.05f, 0.52f, 0.48f, 0.90f, RED), rc(0.52f, 0.52f, 0.95f, 0.90f, BLUE), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("dz", "الجزایر", GREEN, listOf(vb(0.50f, 0.95f, WHITE), cb(WHITE, ToyCircle(0.30f, 0.5f, 0.16f)), cb(RED, ToyCircle(0.34f, 0.5f, 0.13f))))
    f("ec", "اکوادور", GOLD, listOf(hb(0.5f, 0.75f, BLUE), hb(0.75f, 0.90f, RED), cb(GREEN, ToyCircle(0.5f, 0.38f, 0.16f))))
    f("ee", "استونی", NAVY, TRIH(NAVY, BLACK, WHITE))
    f("eg", "مصر", RED, TRIH(RED, WHITE, BLACK) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("eh", "صحرای غربی", BLACK, TRIH(BLACK, WHITE, GREEN) + listOf(cb(RED, ToyCircle(0.30f, 0.5f, 0.13f))))
    f("er", "اریتره", GREEN, listOf(vb(0.05f, 0.55f, BLUE), cb(RED, ToyCircle(0.32f, 0.5f, 0.12f))))
    f("es", "اسپانیا", RED, listOf(hb(0.28f, 0.72f, GOLD)))
    f("et", "اتیوپی", GREEN, TRIH(GREEN, GOLD, RED) + listOf(cb(BLUE, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("fi", "فنلاند", WHITE, NV(WHITE, BLUE))
    f("fj", "فیجی", SKY, miniUK())
    f("fm", "میکرونزی", SKY, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("fo", "جزایر فارو", WHITE, NV(WHITE, RED) + listOf(vb(0.32f, 0.38f, NAVY)))
    f("fr", "فرانسه", WHITE, V3(BLUE, WHITE, RED))
    f("ga", "گابن", GREEN, TRIH(GREEN, GOLD, BLUE))
    f("gb", "بریتانیا", NAVY, listOf(
        rc(0.05f, 0.10f, 0.95f, 0.90f, WHITE),
        rc(0.42f, 0.10f, 0.58f, 0.90f, WHITE), rc(0.05f, 0.42f, 0.95f, 0.58f, WHITE),
        rc(0.45f, 0.10f, 0.55f, 0.90f, RED), rc(0.05f, 0.45f, 0.95f, 0.55f, RED),
    ))
    f("gd", "گرنادا", RED, listOf(hb(0.24f, 0.44f, GOLD), hb(0.44f, 0.62f, GREEN), hb(0.62f, 0.82f, GOLD), cb(RED, ToyCircle(0.5f, 0.53f, 0.08f))))
    f("ge", "گرجستان", WHITE, CROSS(RED) + listOf(rc(0.30f, 0.16f, 0.40f, 0.26f, RED), rc(0.30f, 0.74f, 0.40f, 0.84f, RED), rc(0.60f, 0.16f, 0.70f, 0.26f, RED), rc(0.60f, 0.74f, 0.70f, 0.84f, RED)))
    f("gf", "گویان فرانسه", BLUE, TRIH(BLUE, WHITE, RED) + listOf(cb(RED, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("gh", "غنا", RED, TRIH(RED, GOLD, GREEN) + listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("gi", "جبل‌الطارق", WHITE, listOf(hb(0.62f, 0.90f, RED)))
    f("gl", "گرینلند", WHITE, listOf(hb(0.5f, 0.90f, RED), cb(WHITE, ToyCircle(0.38f, 0.5f, 0.20f)), cb(RED, ToyCircle(0.52f, 0.5f, 0.20f))))
    f("gm", "گامبیا", RED, TRIH(RED, BLUE, GREEN) + listOf(hb(0.42f, 0.46f, WHITE), hb(0.54f, 0.58f, WHITE)))
    f("gn", "گینه", RED, TRIV(RED, GOLD, GREEN))
    f("gp", "گوادلوپ", NAVY, TRIH(NAVY, WHITE, RED))
    f("gq", "گینه استوایی", GREEN, TRIH(GREEN, WHITE, RED) + listOf(cb(SKY, ToyCircle(0.28f, 0.5f, 0.12f))))
    f("gr", "یونان", BLUE, listOf(
        hb(0.10f, 0.26f, WHITE), hb(0.42f, 0.58f, WHITE), hb(0.74f, 0.90f, WHITE),
        rc(0.05f, 0.10f, 0.36f, 0.58f, BLUE), rc(0.16f, 0.10f, 0.24f, 0.58f, WHITE), rc(0.05f, 0.28f, 0.36f, 0.38f, WHITE),
    ))
    f("gs", "جورجیای جنوبی", NAVY, miniUK())
    f("gt", "گواتمالا", SKY, TRIV(SKY, WHITE, SKY) + listOf(cb(GREEN, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("gu", "گوام", NAVY, listOf(cb(RED, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("gw", "گینه بیسائو", RED, listOf(vb(0.05f, 0.38f, RED), hb(0.366f, 0.634f, GOLD), hb(0.634f, 0.90f, GREEN), cb(BLACK, ToyCircle(0.20f, 0.5f, 0.10f))))
    f("gy", "گویان", GREEN, listOf(vb(0.05f, 0.40f, BLACK), vb(0.40f, 0.50f, WHITE), vb(0.50f, 0.60f, GOLD), vb(0.60f, 0.95f, GREEN), cb(RED, ToyCircle(0.35f, 0.5f, 0.11f))))
    f("hk", "هنگ‌کنگ", RED, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.13f))))
    f("hm", "جزیره هرد", NAVY, miniUK())
    f("hn", "هندوراس", SKY, TRIH(SKY, WHITE, SKY) + listOf(cb(SKY, ToyCircle(0.5f, 0.5f, 0.08f))))
    f("hr", "کرواسی", RED, TRIH(RED, WHITE, BLUE) + listOf(cb(RED, ToyCircle(0.5f, 0.44f, 0.10f))))
    f("ht", "هائیتی", NAVY, listOf(rc(0.05f, 0.10f, 0.95f, 0.50f, NAVY), rc(0.05f, 0.50f, 0.95f, 0.90f, RED), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("hu", "مجارستان", RED, TRIH(RED, WHITE, GREEN))
    f("id", "اندونزی", RED, H2(RED) + listOf(hb(0.5f, 0.90f, WHITE)))
    f("ie", "ایرلند", GREEN, TRIV(GREEN, WHITE, ORANGE))
    f("il", "اسرائیل", WHITE, listOf(hb(0.14f, 0.26f, BLUE), hb(0.74f, 0.86f, BLUE), cb(BLUE, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("im", "جزیره من", RED, miniUK())
    f("in", "هند", Color(0xFFFF9933), listOf(hb(0.366f, 0.634f, WHITE), hb(0.634f, 0.90f, GREEN), cb(NAVY, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("io", "قلمرو اقیانوس هند", NAVY, miniUK())
    f("iq", "عراق", RED, TRIH(RED, WHITE, BLACK) + listOf(cb(GREEN, ToyCircle(0.5f, 0.56f, 0.05f))))
    f("ir", "ایران", WHITE, listOf(hb(0.10f, 0.366f, GREEN), hb(0.634f, 0.90f, RED), cb(RED, ToyCircle(0.5f, 0.5f, 0.08f))))
    f("is", "ایسلند", BLUE, NV(BLUE, WHITE) + listOf(vb(0.31f, 0.39f, RED), hb(0.43f, 0.57f, RED)))
    f("it", "ایتالیا", WHITE, V3(GREEN, WHITE, RED))
    f("je", "جرسی", WHITE, miniUK())
    f("jm", "جامائیکا", GREEN, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.16f)), cb(BLACK, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("jo", "اردن", BLACK, TRIH(BLACK, WHITE, GREEN) + listOf(vb(0.05f, 0.38f, RED), cb(WHITE, ToyCircle(0.18f, 0.5f, 0.06f))))
    f("jp", "ژاپن", WHITE, DISK(Color(0xFFD9344A), 0.26f))
    f("ke", "کنیا", BLACK, TRIH(BLACK, RED, GREEN) + listOf(hb(0.30f, 0.366f, WHITE), hb(0.634f, 0.70f, WHITE), cb(RED, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("kg", "قرقیزستان", RED, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("kh", "کامبوج", BLUE, listOf(hb(0.32f, 0.68f, RED), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("ki", "کیریباتی", RED, listOf(hb(0.56f, 0.68f, WHITE), hb(0.68f, 0.78f, BLUE), cb(GOLD, ToyCircle(0.5f, 0.38f, 0.12f))))
    f("km", "کومور", GOLD, listOf(hb(0.10f, 0.325f, GOLD), hb(0.325f, 0.55f, WHITE), hb(0.55f, 0.775f, RED), hb(0.775f, 0.90f, NAVY), vb(0.05f, 0.30f, GREEN), cb(WHITE, ToyCircle(0.18f, 0.5f, 0.09f))))
    f("kn", "سنت کیتس", GREEN, listOf(hb(0.366f, 0.634f, BLACK), hb(0.634f, 0.90f, RED), cb(WHITE, ToyCircle(0.32f, 0.36f, 0.05f))))
    f("kp", "کره شمالی", BLUE, listOf(hb(0.16f, 0.24f, WHITE), hb(0.24f, 0.76f, RED), hb(0.76f, 0.84f, WHITE), cb(WHITE, ToyCircle(0.28f, 0.5f, 0.13f))))
    f("kr", "کره جنوبی", WHITE, listOf(cb(NAVY, ToyCircle(0.5f, 0.5f, 0.15f)), cb(RED, ToyCircle(0.5f, 0.44f, 0.11f))))
    f("kw", "کویت", GREEN, TRIH(GREEN, WHITE, RED) + listOf(vb(0.05f, 0.30f, BLACK)))
    f("ky", "کیمن", NAVY, miniUK())
    f("kz", "قزاقستان", SKY, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.12f)), vb(0.05f, 0.14f, GOLD)))
    f("la", "لائوس", NAVY, listOf(hb(0.28f, 0.72f, RED), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("lb", "لبنان", RED, TRIH(RED, WHITE, RED) + listOf(cb(GREEN, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("lc", "سنت لوسیا", SKY, listOf(cb(WHITE, ToyCircle(0.5f, 0.38f, 0.10f)), cb(BLACK, ToyCircle(0.42f, 0.62f, 0.10f)), cb(GOLD, ToyCircle(0.58f, 0.62f, 0.10f))))
    f("li", "لیختن‌اشتاین", NAVY, listOf(hb(0.5f, 0.90f, RED), cb(GOLD, ToyCircle(0.28f, 0.30f, 0.09f))))
    f("lk", "سری‌لانکا", GOLD, listOf(vb(0.05f, 0.22f, GREEN), vb(0.22f, 0.28f, ORANGE), rc(0.32f, 0.10f, 0.95f, 0.90f, Color(0xFF8D2029))))
    f("lr", "لیبریا", RED, listOf(hb(0.10f, 0.24f, WHITE), hb(0.38f, 0.52f, WHITE), hb(0.66f, 0.80f, WHITE), rc(0.05f, 0.10f, 0.36f, 0.44f, NAVY), cb(WHITE, ToyCircle(0.20f, 0.27f, 0.07f))))
    f("ls", "لسوتو", NAVY, TRIH(NAVY, WHITE, GREEN) + listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("lt", "لیتوانی", GOLD, TRIH(GOLD, GREEN, RED))
    f("lu", "لوکزامبورگ", RED, TRIH(RED, WHITE, SKY))
    f("lv", "لتونی", Color(0xFF9E3039), listOf(hb(0.40f, 0.60f, WHITE)))
    f("ly", "لیبی", RED, TRIH(RED, BLACK, GREEN) + listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("ma", "مراکش", RED, listOf(cb(GREEN, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("mc", "موناکو", RED, H2(RED) + listOf(hb(0.5f, 0.90f, WHITE)))
    f("md", "مولداوی", NAVY, TRIV(BLUE, GOLD, RED) + listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("me", "مونته‌نگرو", RED, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("mf", "سنت مارتین", NAVY, miniUK())
    f("mg", "ماداگاسکار", WHITE, listOf(vb(0.05f, 0.34f, RED), hb(0.5f, 0.90f, GREEN)))
    f("mh", "جزایر مارشال", NAVY, listOf(hb(0.36f, 0.44f, WHITE), hb(0.44f, 0.52f, SKY), cb(WHITE, ToyCircle(0.24f, 0.30f, 0.10f))))
    f("mk", "مقدونیه شمالی", RED, listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.13f))))
    f("ml", "مالی", GREEN, TRIV(GREEN, GOLD, RED))
    f("mm", "میانمار", GOLD, TRIH(GOLD, GREEN, RED) + listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("mn", "مغولستان", RED, TRIV(RED, BLUE, RED) + listOf(cb(GOLD, ToyCircle(0.17f, 0.5f, 0.09f))))
    f("mo", "ماکائو", GREEN, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.13f))))
    f("mp", "ماریانای شمالی", NAVY, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.12f)), cb(SKY, ToyCircle(0.5f, 0.5f, 0.07f))))
    f("mq", "مارتینیک", NAVY, TRIH(NAVY, WHITE, RED))
    f("mr", "موریتانی", GREEN, listOf(cb(GOLD, ToyCircle(0.5f, 0.40f, 0.11f)), hb(0.68f, 0.76f, GOLD)))
    f("ms", "مونتسرات", NAVY, miniUK())
    f("mt", "مالت", WHITE, listOf(vb(0.05f, 0.28f, RED)))
    f("mu", "موریس", RED, listOf(hb(0.10f, 0.30f, RED), hb(0.30f, 0.50f, BLUE), hb(0.50f, 0.70f, GOLD), hb(0.70f, 0.90f, GREEN)))
    f("mv", "مالدیو", RED, listOf(rc(0.22f, 0.24f, 0.78f, 0.76f, GREEN), cb(WHITE, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("mw", "مالاوی", BLACK, TRIH(BLACK, RED, GREEN) + listOf(cb(RED, ToyCircle(0.5f, 0.24f, 0.08f))))
    f("mx", "مکزیک", WHITE, TRIV(GREEN, WHITE, RED) + listOf(cb(Color(0xFF7A5A2B), ToyCircle(0.5f, 0.5f, 0.10f))))
    f("my", "مالزی", RED, listOf(hb(0.10f, 0.24f, WHITE), hb(0.38f, 0.52f, WHITE), hb(0.66f, 0.80f, WHITE), rc(0.05f, 0.10f, 0.44f, 0.56f, NAVY), cb(GOLD, ToyCircle(0.20f, 0.30f, 0.09f))))
    f("mz", "موزامبیک", GREEN, TRIH(GREEN, BLACK, GOLD) + listOf(cb(WHITE, ToyCircle(0.32f, 0.5f, 0.11f)), hb(0.30f, 0.366f, WHITE), hb(0.634f, 0.70f, WHITE)))
    f("na", "نامیبیا", NAVY, listOf(hb(0.10f, 0.366f, NAVY), hb(0.366f, 0.634f, RED), hb(0.634f, 0.90f, GREEN), cb(GOLD, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("nc", "کالدونیای جدید", NAVY, miniUK())
    f("ne", "نیجر", ORANGE, TRIH(ORANGE, WHITE, GREEN) + listOf(cb(ORANGE, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("nf", "جزیره نورفولک", GREEN, listOf(vb(0.40f, 0.60f, WHITE), cb(GREEN, ToyCircle(0.5f, 0.5f, 0.14f))))
    f("ng", "نیجریه", GREEN, V3(GREEN, WHITE, GREEN))
    f("ni", "نیکاراگوئه", SKY, TRIH(SKY, WHITE, SKY) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("nl", "هلند", RED, TRIH(RED, WHITE, NAVY))
    f("no", "نروژ", RED, NV(RED, WHITE) + listOf(vb(0.31f, 0.39f, NAVY), hb(0.43f, 0.57f, NAVY)))
    f("np", "نپال", NAVY, listOf(cb(RED, ToyCircle(0.4f, 0.5f, 0.18f)), cb(WHITE, ToyCircle(0.4f, 0.5f, 0.11f))))
    f("nr", "نائورو", NAVY, listOf(hb(0.58f, 0.66f, GOLD), cb(WHITE, ToyCircle(0.30f, 0.32f, 0.09f))))
    f("nu", "نیووی", GOLD, miniUK())
    f("nz", "نیوزیلند", NAVY, miniUK() + listOf(cb(RED, ToyCircle(0.64f, 0.26f, 0.055f), ToyCircle(0.78f, 0.42f, 0.055f), ToyCircle(0.66f, 0.60f, 0.055f))))
    f("om", "عمان", WHITE, TRIH(WHITE, RED, GREEN) + listOf(vb(0.05f, 0.30f, RED)))
    f("pa", "پاناما", WHITE, listOf(rc(0.05f, 0.10f, 0.50f, 0.50f, BLUE), cb(BLUE, ToyCircle(0.28f, 0.30f, 0.10f)), rc(0.50f, 0.50f, 0.95f, 0.90f, RED), cb(RED, ToyCircle(0.72f, 0.70f, 0.10f))))
    f("pe", "پرو", RED, V3(RED, WHITE, RED))
    f("pf", "پلی‌نزی فرانسه", RED, TRIH(RED, WHITE, RED) + listOf(cb(GOLD, ToyCircle(0.5f, 0.44f, 0.09f))))
    f("pg", "پاپوآ گینه نو", BLACK, listOf(hb(0.10f, 0.50f, RED), cb(GOLD, ToyCircle(0.30f, 0.32f, 0.10f))))
    f("ph", "فیلیپین", NAVY, listOf(hb(0.5f, 0.90f, RED), vb(0.05f, 0.42f, WHITE), cb(GOLD, ToyCircle(0.20f, 0.5f, 0.10f))))
    f("pk", "پاکستان", GREEN, listOf(vb(0.05f, 0.24f, WHITE), cb(WHITE, ToyCircle(0.55f, 0.5f, 0.16f)), cb(GREEN, ToyCircle(0.60f, 0.5f, 0.17f)), cb(WHITE, ToyCircle(0.70f, 0.36f, 0.05f))))
    f("pl", "لهستان", WHITE, H2(WHITE) + listOf(hb(0.5f, 0.90f, RED)))
    f("pm", "سنت پیر و میکلون", NAVY, miniUK())
    f("pn", "جزایر پیت‌کرن", NAVY, miniUK())
    f("pr", "پورتوریکو", RED, listOf(hb(0.10f, 0.26f, WHITE), hb(0.42f, 0.58f, WHITE), hb(0.74f, 0.90f, WHITE), rc(0.05f, 0.10f, 0.42f, 0.56f, NAVY), cb(WHITE, ToyCircle(0.24f, 0.33f, 0.09f))))
    f("ps", "فلسطین", BLACK, TRIH(BLACK, WHITE, GREEN) + listOf(vb(0.05f, 0.36f, RED)))
    f("pt", "پرتغال", RED, listOf(vb(0.05f, 0.38f, GREEN), cb(GOLD, ToyCircle(0.33f, 0.5f, 0.10f))))
    f("pw", "پالائو", SKY, listOf(cb(GOLD, ToyCircle(0.42f, 0.5f, 0.20f))))
    f("py", "پاراگوئه", RED, TRIH(RED, WHITE, BLUE) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("qa", "قطر", WHITE, listOf(vb(0.36f, 0.95f, Color(0xFF8D1B3D))))
    f("re", "رونیون", NAVY, TRIH(NAVY, WHITE, RED))
    f("ro", "رومانی", NAVY, TRIV(BLUE, GOLD, RED))
    f("rs", "صربستان", RED, TRIH(RED, BLUE, WHITE) + listOf(cb(GOLD, ToyCircle(0.30f, 0.44f, 0.10f))))
    f("ru", "روسیه", WHITE, TRIH(WHITE, BLUE, RED))
    f("rw", "رواندا", SKY, TRIH(SKY, GOLD, GREEN) + listOf(cb(GOLD, ToyCircle(0.5f, 0.32f, 0.11f))))
    f("sa", "عربستان", GREEN, listOf(hb(0.40f, 0.52f, WHITE)))
    f("sb", "جزایر سلیمان", GREEN, listOf(hb(0.10f, 0.50f, SKY), hb(0.50f, 0.90f, GREEN), cb(GOLD, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("sc", "سیشل", NAVY, listOf(vb(0.05f, 0.40f, NAVY), vb(0.40f, 0.62f, RED), hb(0.62f, 0.90f, GREEN), cb(WHITE, ToyCircle(0.32f, 0.42f, 0.08f))))
    f("sd", "سودان", RED, TRIH(RED, WHITE, BLACK) + listOf(vb(0.05f, 0.34f, GREEN)))
    f("se", "سوئد", BLUE, NV(BLUE, GOLD))
    f("sg", "سنگاپور", RED, listOf(hb(0.5f, 0.90f, WHITE), cb(WHITE, ToyCircle(0.34f, 0.30f, 0.12f)), cb(RED, ToyCircle(0.40f, 0.30f, 0.13f))))
    f("sh", "سنت هلنا", NAVY, miniUK())
    f("si", "اسلوونی", WHITE, TRIH(WHITE, BLUE, RED) + listOf(cb(WHITE, ToyCircle(0.26f, 0.34f, 0.09f))))
    f("sj", "سوالبارد", NAVY, miniUK())
    f("sk", "اسلواکی", WHITE, TRIH(WHITE, BLUE, RED) + listOf(cb(RED, ToyCircle(0.30f, 0.5f, 0.11f))))
    f("sl", "سیرالئون", GREEN, TRIH(GREEN, WHITE, SKY))
    f("sm", "سان‌مارینو", WHITE, TRIH(WHITE, SKY, WHITE))
    f("sn", "سنگال", GREEN, TRIV(GREEN, GOLD, RED) + listOf(cb(GREEN, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("so", "سومالی", SKY, DISK(WHITE, 0.20f))
    f("sr", "سورینام", GREEN, listOf(hb(0.10f, 0.24f, WHITE), hb(0.24f, 0.76f, RED), hb(0.76f, 0.90f, WHITE), cb(GOLD, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("ss", "سودان جنوبی", BLACK, TRIH(BLACK, RED, GREEN) + listOf(vb(0.05f, 0.36f, SKY), cb(GOLD, ToyCircle(0.20f, 0.5f, 0.07f))))
    f("st", "سائوتومه", GREEN, TRIH(GREEN, GOLD, RED) + listOf(cb(BLACK, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("sv", "السالوادور", SKY, TRIH(SKY, WHITE, SKY) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("sx", "سینت مارتن", NAVY, listOf(hb(0.10f, 0.50f, NAVY), hb(0.50f, 0.90f, RED), vb(0.05f, 0.40f, WHITE)))
    f("sy", "سوریه", RED, TRIH(RED, WHITE, BLACK) + listOf(cb(GREEN, ToyCircle(0.42f, 0.5f, 0.05f), ToyCircle(0.58f, 0.5f, 0.05f))))
    f("sz", "اسواتینی", NAVY, listOf(hb(0.10f, 0.30f, GOLD), hb(0.70f, 0.90f, GOLD), rc(0.05f, 0.30f, 0.95f, 0.70f, RED), cb(BLACK, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("tc", "ترک و کایکوس", NAVY, miniUK())
    f("td", "چاد", NAVY, TRIV(NAVY, GOLD, RED))
    f("tf", "سرزمین‌های جنوبی فرانسه", NAVY, miniUK())
    f("tg", "توگو", GREEN, TRIH(GREEN, GOLD, GREEN) + listOf(rc(0.05f, 0.10f, 0.36f, 0.56f, RED), cb(WHITE, ToyCircle(0.20f, 0.33f, 0.09f))))
    f("th", "تایلند", RED, listOf(hb(0.24f, 0.38f, WHITE), hb(0.38f, 0.62f, NAVY), hb(0.62f, 0.76f, WHITE)))
    f("tj", "تاجیکستان", RED, TRIH(RED, WHITE, GREEN) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("tk", "توکلائو", GOLD, miniUK())
    f("tl", "تیمور شرقی", RED, listOf(vb(0.05f, 0.40f, BLACK), vb(0.40f, 0.52f, GOLD), cb(WHITE, ToyCircle(0.22f, 0.5f, 0.09f))))
    f("tm", "ترکمنستان", GREEN, listOf(vb(0.05f, 0.22f, RED), cb(WHITE, ToyCircle(0.55f, 0.42f, 0.11f))))
    f("tn", "تونس", RED, listOf(cb(WHITE, ToyCircle(0.5f, 0.5f, 0.26f)), cb(RED, ToyCircle(0.5f, 0.5f, 0.12f))))
    f("to", "تونگا", RED, listOf(rc(0.05f, 0.10f, 0.42f, 0.48f, WHITE), cb(RED, ToyCircle(0.24f, 0.29f, 0.06f))))
    f("tr", "ترکیه", Color(0xFFE30A17), listOf(
        cb(Color.White, ToyCircle(0.40f, 0.5f, 0.24f)),
        cb(Color(0xFFE30A17), ToyCircle(0.475f, 0.5f, 0.19f)),
        cb(Color.White, ToyCircle(0.66f, 0.5f, 0.075f)),
    ))
    f("tt", "ترینیداد", RED, listOf(hb(0.366f, 0.634f, WHITE), cb(BLACK, ToyCircle(0.5f, 0.5f, 0.16f))))
    f("tv", "تووالو", SKY, miniUK() + listOf(cb(GOLD, ToyCircle(0.7f, 0.6f, 0.06f))))
    f("tw", "تایوان", RED, listOf(rc(0.05f, 0.10f, 0.46f, 0.50f, NAVY), cb(WHITE, ToyCircle(0.25f, 0.30f, 0.11f))))
    f("tz", "تانزانیا", GREEN, listOf(hb(0.36f, 0.64f, BLACK), hb(0.30f, 0.36f, GOLD), hb(0.64f, 0.70f, GOLD), cb(SKY, ToyCircle(0.2f, 0.2f, 0.07f))))
    f("ua", "اوکراین", BLUE, H2(BLUE) + listOf(hb(0.5f, 0.90f, GOLD)))
    f("ug", "اوگاندا", BLACK, TRIH(BLACK, GOLD, RED) + listOf(hb(0.30f, 0.366f, WHITE), hb(0.634f, 0.70f, WHITE), cb(GREY, ToyCircle(0.5f, 0.5f, 0.10f))))
    f("um", "جزایر کوچک دورافتاده آمریکا", NAVY, miniUK())
    f("us", "آمریکا", RED, listOf(
        hb(0.243f, 0.377f, WHITE), hb(0.511f, 0.645f, WHITE), hb(0.779f, 0.90f, WHITE),
        rc(0.06f, 0.105f, 0.46f, 0.50f, BLUE),
        cb(WHITE, ToyCircle(0.14f, 0.22f, 0.035f), ToyCircle(0.28f, 0.22f, 0.035f), ToyCircle(0.21f, 0.36f, 0.035f), ToyCircle(0.36f, 0.36f, 0.035f)),
    ))
    f("uy", "اروگوئه", WHITE, listOf(hb(0.24f, 0.38f, SKY), hb(0.52f, 0.66f, SKY), hb(0.80f, 0.90f, SKY), cb(GOLD, ToyCircle(0.22f, 0.30f, 0.10f))))
    f("uz", "ازبکستان", SKY, TRIH(SKY, WHITE, GREEN) + listOf(hb(0.366f, 0.40f, RED), hb(0.60f, 0.634f, RED), cb(SKY, ToyCircle(0.22f, 0.28f, 0.09f))))
    f("va", "واتیکان", GOLD, TRIH(GOLD, WHITE, GOLD) + listOf(cb(GOLD, ToyCircle(0.5f, 0.5f, 0.09f))))
    f("vc", "سنت وینسنت", SKY, TRIV(SKY, GOLD, GREEN) + listOf(cb(GREEN, ToyCircle(0.5f, 0.5f, 0.08f))))
    f("ve", "ونزوئلا", GOLD, TRIH(GOLD, BLUE, RED) + listOf(cb(WHITE, ToyCircle(0.5f, 0.40f, 0.09f))))
    f("vg", "جزایر ویرجین بریتیش", NAVY, miniUK())
    f("vi", "جزایر ویرجین آمریکا", WHITE, listOf(cb(GOLD, ToyCircle(0.42f, 0.5f, 0.12f)), cb(Color(0xFFB8763E), ToyCircle(0.6f, 0.42f, 0.07f))))
    f("vn", "ویتنام", RED, DISK(GOLD, 0.20f))
    f("vu", "وانواتو", BLACK, listOf(hb(0.10f, 0.30f, BLACK), hb(0.30f, 0.70f, GREEN), hb(0.70f, 0.90f, RED), cb(GOLD, ToyCircle(0.5f, 0.5f, 0.11f))))
    f("wf", "والیس و فوتونا", NAVY, miniUK())
    f("ws", "ساموآ", RED, listOf(rc(0.05f, 0.10f, 0.44f, 0.52f, NAVY), cb(WHITE, ToyCircle(0.22f, 0.26f, 0.06f), ToyCircle(0.34f, 0.26f, 0.06f), ToyCircle(0.28f, 0.38f, 0.06f))))
    f("xk", "کوزوو", NAVY, listOf(cb(GOLD, ToyCircle(0.5f, 0.44f, 0.13f))))
    f("ye", "یمن", RED, TRIH(RED, WHITE, BLACK))
    f("yt", "مایوت", NAVY, miniUK())
    f("za", "آفریقای جنوبی", GREEN, listOf(hb(0.42f, 0.58f, GREEN), hb(0.30f, 0.42f, WHITE), hb(0.58f, 0.70f, WHITE), hb(0.10f, 0.30f, RED), hb(0.70f, 0.90f, BLUE), vb(0.05f, 0.30f, BLACK)))
    f("zm", "زامبیا", GREEN, listOf(hb(0.10f, 0.30f, ORANGE), hb(0.30f, 0.50f, BLACK), hb(0.50f, 0.70f, RED), cb(GOLD, ToyCircle(0.78f, 0.30f, 0.08f))))
    f("zw", "زیمبابوه", GREEN, TRIH(GREEN, GOLD, RED) + listOf(vb(0.05f, 0.26f, BLACK), cb(GOLD, ToyCircle(0.16f, 0.5f, 0.10f))))

    return out
}

private val GREY = Color(0xFF9BA8B4)

private val GREY = Color(0xFF9BA8B4)

private val EN = mapOf(
    "ad" to "Andorra", "ae" to "United Arab Emirates", "af" to "Afghanistan", "ag" to "Antigua & Barbuda",
    "ai" to "Anguilla", "al" to "Albania", "am" to "Armenia", "ao" to "Angola", "ar" to "Argentina",
    "at" to "Austria", "au" to "Australia", "aw" to "Aruba", "ax" to "Åland Islands", "az" to "Azerbaijan",
    "ba" to "Bosnia & Herzegovina", "bb" to "Barbados", "bd" to "Bangladesh", "be" to "Belgium",
    "bf" to "Burkina Faso", "bg" to "Bulgaria", "bh" to "Bahrain", "bi" to "Burundi", "bj" to "Benin",
    "bl" to "St. Barthélemy", "bm" to "Bermuda", "bn" to "Brunei", "bo" to "Bolivia", "br" to "Brazil",
    "bs" to "Bahamas", "bt" to "Bhutan", "bw" to "Botswana", "by" to "Belarus", "bz" to "Belize",
    "ca" to "Canada", "cd" to "DR Congo", "cf" to "Central African Rep.", "cg" to "Congo",
    "ch" to "Switzerland", "ci" to "Côte d'Ivoire", "ck" to "Cook Islands", "cl" to "Chile",
    "cm" to "Cameroon", "cn" to "China", "co" to "Colombia", "cr" to "Costa Rica", "cu" to "Cuba",
    "cv" to "Cape Verde", "cw" to "Curaçao", "cx" to "Christmas Island", "cy" to "Cyprus", "cz" to "Czechia",
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
    "re" to "Réunion", "ro" to "Romania", "rs" to "Serbia", "ru" to "Russia", "rw" to "Rwanda",
    "sa" to "Saudi Arabia", "sb" to "Solomon Islands", "sc" to "Seychelles", "sd" to "Sudan",
    "se" to "Sweden", "sg" to "Singapore", "sh" to "St. Helena", "si" to "Slovenia", "sj" to "Svalbard",
    "sk" to "Slovakia", "sl" to "Sierra Leone", "sm" to "San Marino", "sn" to "Senegal", "so" to "Somalia",
    "sr" to "Suriname", "ss" to "South Sudan", "st" to "São Tomé & Príncipe", "sv" to "El Salvador",
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
