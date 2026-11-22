package app.localmind.assistant.domain.desk

/**
 * The Prices order SlothDesk gives the Lobster (1.50): the shelf's series, then whatever your
 * rules read, each named the way the Lobster fetches it: a plain ticker (Tiingo's end-of-day
 * prices), "crypto:BTCUSD" (Tiingo's crypto prices, every day), "fx:EURUSD" (Tiingo's FX
 * bars) or "fred:DFII10" (a FRED series). Only names that pass the rule checks go in; the
 * Lobster checks them again.
 */
object PricesOrder {
    /** What the shelf reads, first and always. */
    val BASE = listOf("GLD", "SLV", "USDJPY", "DTB3", "JP3M", "GVZ", "DEXJPUS")

    /** Series the Lobster knows by name, with their own sources. */
    val BUILTIN = setOf("USDJPY", "DTB3", "JP3M", "GVZ", "DEXJPUS")

    /** The most names one order carries (the Lobster's limit). */
    const val MAX = 24
    const val SINCE = "2008-01-01"

    /** The names to fetch, the shelf's first, at most [MAX]. */
    fun names(rules: List<Rule>): List<String> {
        val out = LinkedHashSet<String>(BASE)
        for (r in rules) {
            val assets = (RuleEngine.priceAssets(r) + RuleEngine.traded(r) + r.benchmark +
                listOfNotNull((r.overlay as? Overlay.CoveredCall)?.asset)).filter { it != "*" && RuleCheck.isTicker(it) }
            for (a in assets) {
                out += when {
                    a in BUILTIN -> a
                    r.markets[a] == Market.CRYPTO -> "crypto:$a"
                    r.markets[a] == Market.FX -> "fx:$a"
                    else -> a
                }
            }
            for (id in RuleEngine.seriesOf(r)) {
                if (id in BUILTIN || id.contains(':') || !FRED.matches(id)) continue
                out += "fred:$id"
            }
        }
        // A name asked for two ways (a ticker and "crypto:" the same) keeps its first.
        val seen = HashSet<String>()
        return out.filter { seen.add(it.substringAfter(':')) }.take(MAX)
    }

    fun text(rules: List<Rule>): String = "Prices for SlothDesk: ${names(rules).joinToString(" ")} from $SINCE"

    /** FRED's ids: capitals and digits (DFII10, T10YIE, BAMLH0A0HYM2). */
    private val FRED = Regex("^[A-Z0-9_]{2,24}$")
}
