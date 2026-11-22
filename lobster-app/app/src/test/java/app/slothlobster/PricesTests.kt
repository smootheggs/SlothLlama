package app.slothlobster

import app.slothlobster.jobs.Prices
import app.slothlobster.jobs.Repeats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.4: SlothDesk's Prices orders. */
class PricesTests {

    @Test
    fun `an order names its series and start date`() {
        val (names, since) = Prices.request("Prices for SlothDesk: GLD SLV USDJPY DTB3 JP3M GVZ from 2008-01-01")
        assertEquals(listOf("GLD", "SLV", "USDJPY", "DTB3", "JP3M", "GVZ"), names)
        assertEquals(LocalDate.of(2008, 1, 1), since)
    }

    @Test
    fun `a daily order repeats and keeps its series`() {
        val text = "Prices for SlothDesk: GLD SLV DTB3 from 2010-01-01 every day at 18:30"
        val rule = Repeats.parse(text)!!
        assertEquals(1, rule.everyDays)
        assertEquals(18, rule.hour)
        assertEquals(30, rule.minute)
        val (names, since) = Prices.request(Repeats.strip(text))
        assertEquals(listOf("GLD", "SLV", "DTB3"), names)
        assertEquals(LocalDate.of(2010, 1, 1), since)
    }

    @Test
    fun `at most twenty-four series and no ordinary words`() {
        val many = (1..30).joinToString(" ") { "T$it" }
        val (names, since) = Prices.request("Prices for SlothDesk: $many")
        assertEquals(24, names.size)
        assertTrue(names.none { it == "PRICES" || it == "SLOTHDESK" })
        assertEquals(LocalDate.of(2008, 1, 1), since)
    }

    @Test
    fun `typed names say where to fetch them, in order`() {
        val (asks, since) = Prices.asks("Prices for SlothDesk: GLD SLV USDJPY DTB3 crypto:BTCUSD SPY fx:EURUSD fred:DFII10 fred:T10YIE from 2015-01-01")
        assertEquals(listOf("GLD", "SLV", "USDJPY", "DTB3", "BTCUSD", "SPY", "EURUSD", "DFII10", "T10YIE"), asks.map { it.name })
        assertEquals(Prices.Source.CRYPTO, asks[4].source)
        assertEquals(Prices.Source.NAMED, asks[5].source)
        assertEquals(Prices.Source.FX, asks[6].source)
        assertEquals(Prices.Source.FRED, asks[7].source)
        assertEquals(LocalDate.of(2015, 1, 1), since)
    }

    @Test
    fun `odd typed names are left out, and a name asked twice is fetched once`() {
        val (asks, _) = Prices.asks("Prices for SlothDesk: crypto:BTC fx:../etc fred:dfii10 GLD crypto:ETHUSD ETHUSD")
        assertEquals(listOf("DFII10", "GLD", "ETHUSD"), asks.map { it.name })
        assertEquals(Prices.Source.CRYPTO, asks[2].source)
    }

    @Test
    fun `fx history comes from FRED before 2020 and from Tiingo after`() {
        val old = LocalDate.of(2008, 1, 1)
        val recent = LocalDate.of(2023, 6, 1)
        // A major pair asked from 2008: the Fed's noon rate first, Tiingo from 2020 if FRED fails.
        assertEquals(Prices.FxPlan("DEXUSEU", Prices.TIINGO_FX_FROM), Prices.fxPlan("EURUSD", old, hasKey = true))
        assertEquals(Prices.FxPlan("DEXJPUS", Prices.TIINGO_FX_FROM), Prices.fxPlan("USDJPY", old, hasKey = true))
        // From a date Tiingo serves: Tiingo, with its highs and lows.
        assertEquals(Prices.FxPlan(null, recent), Prices.fxPlan("EURUSD", recent, hasKey = true))
        // A pair FRED doesn't carry: Tiingo, never asked for days before it starts.
        assertEquals(Prices.FxPlan(null, Prices.TIINGO_FX_FROM), Prices.fxPlan("NZDJPY", old, hasKey = true))
        // No key: FRED where it can, nothing otherwise.
        assertEquals(Prices.FxPlan("DEXUSUK", null), Prices.fxPlan("GBPUSD", recent, hasKey = false))
        assertEquals(Prices.FxPlan(null, null), Prices.fxPlan("NZDJPY", recent, hasKey = false))
    }
}

/** 1.5: briefs search on key words, not filler. */
class BriefQueryTests {

    @Test
    fun `filler words are dropped from a brief's news search`() {
        assertEquals("Precious metals market", app.slothlobster.web.Feeds.newsQuery("Precious metals market updates, top news, prices, volumes, etc."))
        assertEquals("Hong Kong economy", app.slothlobster.web.Feeds.newsQuery("Hong Kong economy news"))
    }

    @Test
    fun `quoted phrases and OR are kept, and an all-filler topic stays as it was`() {
        assertEquals("\"gold price\" OR silver", app.slothlobster.web.Feeds.newsQuery("\"gold price\" OR silver"))
        assertEquals("top news", app.slothlobster.web.Feeds.newsQuery("top news"))
    }

    @Test
    fun `recent stories only, when asked`() {
        assertTrue(app.slothlobster.web.Feeds.newsSearch("gold", days = 2).contains("when%3A2d"))
    }
}
