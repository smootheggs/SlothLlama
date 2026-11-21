package app.localmind.assistant.domain.sports

import app.localmind.assistant.domain.odds.Odds
import app.localmind.assistant.domain.odds.Price
import kotlinx.serialization.Serializable

/**
 * A game's prices, as written (so "−115" stays "−115"): money lines for each side (and the draw,
 * in football), the home side's spread and its price, the total and the over's price.
 */
@Serializable
data class Book(
    val homeMoney: String? = null,
    val awayMoney: String? = null,
    val draw: String? = null,
    /** The home side's spread: −1.5 means they give a point and a half. */
    val homeLine: Double? = null,
    val linePrice: String? = null,
    val total: Double? = null,
    val overPrice: String? = null,
    /** Where it's from: "you", or the line's provider via the Lobster. */
    val source: String = "you"
) {
    fun price(s: String?): Price? = s?.let { Odds.parse(it) }

    /** The market's own chances for home, away (and draw), margin taken out; null without every side. */
    fun fair(withDraw: Boolean): List<Double>? {
        val sides = listOf(price(homeMoney), price(awayMoney)) + if (withDraw) listOf(price(draw)) else emptyList()
        if (sides.any { it == null }) return null
        return Odds.fair(sides.filterNotNull())
    }

    fun margin(withDraw: Boolean): Double? {
        val sides = listOf(price(homeMoney), price(awayMoney)) + if (withDraw) listOf(price(draw)) else emptyList()
        if (sides.any { it == null }) return null
        return Odds.margin(sides.filterNotNull())
    }

    val empty: Boolean get() = homeMoney == null && awayMoney == null && draw == null && homeLine == null && total == null
}

/** What you'd pick on a game; its chance is worked out from the model and the price, never by a model. */
enum class Market { HOME, AWAY, DRAW, HOME_LINE, AWAY_LINE, OVER, UNDER, BOTH_SCORE, WIN, PODIUM }

object SportsLines {
    private val PRICE = Regex("""(?<![\d.])([+\-−–]\d{3,4}|\d{1,2}[.,]\d{2}|\d{1,3}/\d{1,3}|evens?)(?![\d.])""", RegexOption.IGNORE_CASE)
    private val SPREAD = Regex("""(?<![\d.])([+\-−–]\d{1,2}(?:\.\d)?)(?![\d/])""")
    private val TOTAL = Regex("""(?:o/u|over/under|total|\bo)\s*:?\s*(\d{1,3}(?:\.\d)?)(?![\d/])""", RegexOption.IGNORE_CASE)
    private val DETAILS = Regex("""^([A-Z]{2,5})\s*([+\-−]?\d+(?:\.\d)?)$""")

    /** The line the Lobster brought (ESPN's, as shown), as a book. */
    fun fromLine(g: GameRow): Book? {
        val l = g.line ?: return null
        val d = l.details.trim()
        val homeLine = when {
            d.equals("EVEN", true) || d.equals("PK", true) || d.equals("PICK", true) -> 0.0
            else -> DETAILS.matchEntire(d)?.let { m ->
                val v = m.groupValues[2].replace('−', '-').toDoubleOrNull() ?: return@let null
                when (m.groupValues[1]) {
                    g.homeAbbr -> v
                    g.awayAbbr -> -v
                    else -> null
                }
            }
        }
        val b = Book(
            homeMoney = l.homeMoney?.takeIf { Odds.parse(it) != null },
            awayMoney = l.awayMoney?.takeIf { Odds.parse(it) != null },
            homeLine = homeLine,
            total = l.overUnder,
            source = l.provider.ifBlank { "the line" }
        )
        return b.takeIf { !it.empty }
    }

    /** Words in a team's name worth looking for: the whole name, its nickname, its letters. */
    fun keys(name: String, abbr: String): List<String> {
        val words = name.lowercase().split(' ').filter { it.length >= 4 }
        return (listOf(name.lowercase(), abbr.lowercase().takeIf { it.length >= 2 }) + words).filterNotNull().distinct()
    }

    private fun mentions(text: String, keys: List<String>): List<Int> =
        keys.flatMap { k -> Regex("""\b${Regex.escape(k)}\b""").findAll(text).map { it.range.first } }.distinct().sorted()

    /**
     * Reads a pasted line ("Warriors −115 · Lakers −105 · Spread GSW −1.5 (−110) · O/U 228.5") on
     * the tablet: each team's price is the first one after its name, before the other's; a spread
     * is a small signed number after a name; the total follows O/U, total or over/under.
     */
    fun paste(text: String, g: GameRow, withDraw: Boolean): Book {
        val t = text.lowercase().replace('−', '-').replace('–', '-')
        val home = mentions(t, keys(g.home, g.homeAbbr))
        val away = mentions(t, keys(g.away, g.awayAbbr))
        val draw = Regex("""\bdraw\b|\btie\b|\bx\b""").findAll(t).map { it.range.first }.toList()
        val all = (home + away + draw).sorted()
        fun priceAfter(starts: List<Int>): String? {
            for (s in starts) {
                val stop = all.firstOrNull { it > s } ?: t.length
                PRICE.findAll(t, s).firstOrNull { it.range.first < stop }?.let { return it.groupValues[1] }
            }
            return null
        }
        var homeLine: Double? = null
        var linePrice: String? = null
        for ((starts, sign) in listOf(home to 1.0, away to -1.0)) {
            if (homeLine != null) break
            for (s in starts) {
                val window = t.substring(s, minOf(t.length, s + 30))
                val m = SPREAD.find(window) ?: continue
                // A spread right after the name, not a price (those have three digits or a point and two).
                if (PRICE.find(window)?.range?.first == m.range.first) continue
                homeLine = m.groupValues[1].toDouble() * sign
                linePrice = PRICE.find(t, s + m.range.last + 1)?.takeIf { it.range.first - (s + m.range.last) < 12 }?.groupValues?.get(1)
                break
            }
        }
        val totalMatch = TOTAL.find(t)
        val total = totalMatch?.groupValues?.get(1)?.toDoubleOrNull()
        val overPrice = totalMatch?.let { m -> PRICE.find(t, m.range.last + 1)?.takeIf { it.range.first - m.range.last < 12 }?.groupValues?.get(1) }
        return Book(
            homeMoney = priceAfter(home),
            awayMoney = priceAfter(away),
            draw = if (withDraw) priceAfter(draw) else null,
            homeLine = homeLine,
            linePrice = linePrice,
            total = total,
            overPrice = overPrice
        )
    }

    // --- picks, in Forecasts ---

    /** "sports:nba:401909305:HOME_LINE:-1.5" and back. */
    fun source(league: League, id: String, market: Market, arg: String = ""): String = "sports:${league.id}:$id:${market.name}:$arg"

    data class Pick(val league: League, val id: String, val market: Market, val arg: String)

    fun pickOf(source: String): Pick? {
        val p = source.split(':')
        if (p.size < 5 || p[0] != "sports") return null
        val league = League.of(p[1]) ?: return null
        val market = runCatching { Market.valueOf(p[3]) }.getOrNull() ?: return null
        return Pick(league, p[2], market, p.drop(4).joinToString(":"))
    }

    /**
     * How a pick came out from the result: true (it happened), false (it didn't), null (not
     * played yet, or a push: a line landed exactly).
     */
    fun outcome(pick: Pick, game: GameRow?, race: RaceRow?): Boolean? {
        if (pick.market == Market.WIN || pick.market == Market.PODIUM) {
            val r = race ?: return null
            if (r.results.isEmpty()) return null
            val f = r.results.firstOrNull { it.driver == pick.arg } ?: return false
            val pos = f.pos?.takeIf { f.classified } ?: return false
            return if (pick.market == Market.WIN) pos == 1 else pos <= 3
        }
        val g = game?.takeIf { it.played } ?: return null
        val h = g.homeScore!!.toDouble()
        val a = g.awayScore!!.toDouble()
        val n = pick.arg.toDoubleOrNull()
        return when (pick.market) {
            Market.HOME -> h > a
            Market.AWAY -> a > h
            Market.DRAW -> h == a
            Market.HOME_LINE -> n?.let { val v = h + it - a; if (v == 0.0) null else v > 0 }
            Market.AWAY_LINE -> n?.let { val v = a + it - h; if (v == 0.0) null else v > 0 }
            Market.OVER -> n?.let { if (h + a == it) null else h + a > it }
            Market.UNDER -> n?.let { if (h + a == it) null else h + a < it }
            Market.BOTH_SCORE -> h > 0 && a > 0
            else -> null
        }
    }
}
