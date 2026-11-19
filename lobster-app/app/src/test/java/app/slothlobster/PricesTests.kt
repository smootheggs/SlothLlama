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
    fun `at most twelve series and no ordinary words`() {
        val many = (1..20).joinToString(" ") { "T$it" }
        val (names, since) = Prices.request("Prices for SlothDesk: $many")
        assertEquals(12, names.size)
        assertTrue(names.none { it == "PRICES" || it == "SLOTHDESK" })
        assertEquals(LocalDate.of(2008, 1, 1), since)
    }
}
