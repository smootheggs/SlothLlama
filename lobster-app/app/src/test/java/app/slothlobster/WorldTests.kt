package app.slothlobster

import app.slothlobster.jobs.World
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1.10: the crowd's prices for SlothLlama's World page, read from public market data. The markets here are made up. */
class WorldTests {

    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `an order names its markets once each`() {
        val refs = World.request("World: polymarket:Some-Event-Slug kalshi:kxrate-26dec, polymarket:some-event-slug")
        assertEquals(listOf("polymarket:some-event-slug", "kalshi:KXRATE-26DEC"), refs)
        assertEquals(emptyList<String>(), World.request("World: nothing here"))
    }

    @Test
    fun `polymarket's outcomes and prices are JSON inside strings`() {
        val m = obj(
            """{"id":"123","question":"Will the river treaty be ratified by March?","outcomes":"[\"Yes\", \"No\"]",
               "outcomePrices":"[\"0.55\", \"0.45\"]","active":true,"closed":false,"endDate":"2027-03-31T00:00:00Z",
               "description":"Resolves Yes if both parliaments ratify."}"""
        )
        val w = World.polymarketMarket(m, "polymarket:treaty", "River treaty", "treaty")!!
        assertEquals(0.55, w.chance!!, 1e-9)
        assertEquals("open", w.status)
        assertEquals("", w.result)
        assertEquals("Resolves Yes if both parliaments ratify.", w.rules)
        assertEquals("https://polymarket.com/event/treaty", w.url)
    }

    @Test
    fun `a closed polymarket market at one or zero is settled`() {
        val yes = obj("""{"id":"1","question":"Q?","outcomes":"[\"Yes\",\"No\"]","outcomePrices":"[\"1\",\"0\"]","closed":true}""")
        assertEquals("yes", World.polymarketMarket(yes, "polymarket:q", "", "q")!!.result)
        val no = obj("""{"id":"2","question":"Q?","outcomes":"[\"Yes\",\"No\"]","outcomePrices":"[\"0.004\",\"0.996\"]","closed":true}""")
        val n = World.polymarketMarket(no, "polymarket:q", "", "q")!!
        assertEquals("no", n.result)
        assertEquals("settled", n.status)
        val unsure = obj("""{"id":"3","question":"Q?","outcomes":"[\"Yes\",\"No\"]","outcomePrices":"[\"0.5\",\"0.5\"]","closed":true}""")
        assertEquals("closed", World.polymarketMarket(unsure, "polymarket:q", "", "q")!!.status)
    }

    @Test
    fun `kalshi's prices are dollars in strings, and the middle of bid and ask is the chance`() {
        val m = obj(
            """{"ticker":"KXRATE-26DEC-C25","title":"Will the central bank cut by 25bps in December?","yes_sub_title":"Cut 25bps",
               "status":"active","result":"","yes_bid_dollars":"0.6000","yes_ask_dollars":"0.6400","last_price_dollars":"0.6100",
               "close_time":"2026-12-17T18:59:00Z","rules_primary":"If the bank cuts by 25bps, Yes."}"""
        )
        val w = World.kalshiMarket(m, "kalshi:KXRATE-26DEC", "Rate decision in December?", "KXRATE-26DEC")!!
        assertEquals(0.62, w.chance!!, 1e-9)
        assertEquals("Will the central bank cut by 25bps in December?", w.question)
        assertEquals("open", w.status)
        // No bid: the last trade.
        val thin = obj("""{"ticker":"T","status":"active","yes_bid_dollars":"0.0000","yes_ask_dollars":"0.0100","last_price_dollars":"0.0100"}""")
        assertEquals(0.01, World.kalshiMarket(thin, "kalshi:T", "", "T")!!.chance!!, 1e-9)
        // Settled once it's determined with a result.
        val done = obj("""{"ticker":"T2","yes_sub_title":"Hold","status":"finalized","result":"no","last_price_dollars":"0.0000"}""")
        val d = World.kalshiMarket(done, "kalshi:T", "Rate decision?", "T")!!
        assertEquals("settled", d.status)
        assertEquals("no", d.result)
        assertNull(d.chance)
        assertEquals("Rate decision · Hold", d.question)
    }
}
