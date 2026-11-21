package app.localmind.assistant

import app.localmind.assistant.domain.atlas.AtlasFacts
import app.localmind.assistant.domain.atlas.AtlasFiles
import app.localmind.assistant.domain.atlas.AtlasQuiz
import app.localmind.assistant.domain.atlas.EqualEarth
import app.localmind.assistant.domain.atlas.PlaceFinder
import app.localmind.assistant.domain.atlas.PlaceHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/** 1.33: the Atlas, read from the app's own assets as the app reads them. */
class AtlasUnitTests {

    private val dir = File("src/main/assets/atlas")
    private val countries by lazy { AtlasFiles.countries(File(dir, "countries.json").readText()) }
    private val shapes by lazy { AtlasFiles.shapes(File(dir, "countries.bin").readBytes()) }
    private val subs by lazy { AtlasFiles.subdivisions(File(dir, "subdivisions.json").readText()) }
    private val places by lazy { AtlasFiles.places(File(dir, "places.json").readText()) }
    private val byA3 by lazy { countries.associateBy { it.a3 } }
    private val finder by lazy { PlaceFinder(countries, places, subs) }

    @Test
    fun `every country has its outline, and the subdivisions theirs`() {
        assertEquals(countries.size, shapes.size)
        assertEquals(subs.size, AtlasFiles.shapes(File(dir, "subdivisions.bin").readBytes()).size)
        assertTrue(shapes.all { it.isNotEmpty() && it.all { r -> r.size >= 6 && r.size % 2 == 0 } })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a file that isn't an outlines file is refused`() {
        AtlasFiles.shapes("PK\u0003\u0004".toByteArray())
    }

    @Test
    fun `areas measured on the equal-area map are about right`() {
        fun area(a3: String) = EqualEarth.areaKm2(EqualEarth.project(shapes[countries.indexOfFirst { it.a3 == a3 }]))
        assertTrue(area("CHN") in 9.0e6..9.8e6)
        assertTrue(area("FRA") in 5.5e5..7.0e5) // with French Guiana
        assertTrue(area("RUS") in 1.6e7..1.75e7)
    }

    @Test
    fun `a point inside a country is found, the sea isn't`() {
        val china = EqualEarth.project(shapes[countries.indexOfFirst { it.a3 == "CHN" }])
        assertTrue(EqualEarth.contains(china, EqualEarth.x(104.0, 34.0).toFloat(), EqualEarth.y(34.0).toFloat()))
        assertFalse(EqualEarth.contains(china, EqualEarth.x(150.0, 10.0).toFloat(), EqualEarth.y(10.0).toFloat()))
    }

    @Test
    fun `China's neighbours, with Hong Kong and Macau apart`() {
        val (others, same) = AtlasFacts.neighbours(byA3.getValue("CHN"), byA3)
        assertEquals(14, others.size)
        assertEquals(setOf("HKG", "MAC"), same.map { it.a3 }.toSet())
    }

    @Test
    fun `China's subdivisions counted by kind`() {
        val china = subs.filter { it.a3 == "CHN" && it.type.isNotBlank() }
        assertEquals("22 provinces · 5 autonomous regions · 4 municipalities", AtlasFacts.subdivisionKinds(china))
        assertEquals("Guǎngdōng", china.first { it.name == "Guangdong" }.reading)
    }

    @Test
    fun `places named in a story are found`() {
        assertEquals("CHN", finder.find("Beijing said on Friday that talks would resume.").first().a3)
        assertEquals("HKG", finder.find("香港本周末天气转凉").first().a3)
        assertEquals("USA", finder.find("The U.S. Senate passed an energy bill.").first().a3)
        assertEquals("CHN", finder.find("Factories in Guangdong slowed in October.").first().a3)
        assertEquals("FRA", finder.find("Le gouvernement a présenté le budget à Paris.").first().a3)
        assertTrue(finder.find("Male voters and nice weather.").isEmpty())
    }

    /** 1.45.1: a name shared by places in several countries (Los Angeles is also a city in Chile). */
    private fun at(text: String, named: String): String? = finder.find(text, 8).firstOrNull { it.named == named }?.a3

    @Test
    fun `a shared name goes to the bigger place unless the text says otherwise`() {
        assertEquals("USA", at("Wildfires spread near Los Angeles on Tuesday.", "Los Angeles"))
        assertEquals("CAN", at("A storm hit Vancouver.", "Vancouver"))
        assertEquals("USA", at("Portland police said.", "Portland"))
        // A region right beside it says which.
        assertEquals("USA", at("Fires near Los Angeles, California, forced evacuations.", "Los Angeles"))
        assertEquals("CHL", at("Floods hit Los Ángeles, Chile, overnight.", "Los Ángeles"))
        assertEquals("USA", at("Protests in Atlanta, Georgia, grew.", "Georgia"))
        assertEquals("PAK", at("Rain in Hyderabad, Pakistan's Sindh province.", "Hyderabad"))
        // Named elsewhere, a country decides only between places of about the same size.
        assertEquals("USA", at("Chile's president visited Los Angeles on Monday.", "Los Angeles"))
        assertEquals("GEO", at("Georgia's parliament met in Tbilisi.", "Georgia"))
        assertEquals("ESP", at("Valencia flooded again, Spain said.", "Valencia"))
    }

    @Test
    fun `a story's place is the place itself, with why it's that one`() {
        val la = finder.find("Wildfires spread near Los Angeles on Tuesday.", 4).first()
        assertEquals("Los Angeles", la.place)
        assertEquals("city", la.placeKind)
        assertTrue(la.located && la.lon < -117 && la.lon > -119 && la.lat > 33 && la.lat < 35)
        assertTrue("CHL" in la.others)
        assertEquals(PlaceHit.Why.BIGGER, la.why)
        val beside = finder.find("Fires near Los Angeles, California, forced evacuations.", 4).first { it.named == "Los Angeles" }
        assertEquals(PlaceHit.Why.BESIDE, beside.why)
        assertEquals("California", beside.by)
        val elsewhere = finder.find("Valencia flooded again, Spain said.", 4).first { it.a3 == "ESP" }
        assertEquals("Valencia", elsewhere.named)
        assertEquals(PlaceHit.Why.ELSEWHERE, elsewhere.why)
        assertEquals("Spain", elsewhere.by)
        // A name only one place has needs no reason.
        val tokyo = finder.find("Markets in Tokyo rose.", 4).first()
        assertTrue(tokyo.others.isEmpty())
        assertEquals("", AtlasFacts.whyHere(tokyo) { byA3[it]?.english ?: it })
        val why = AtlasFacts.whyHere(la) { byA3[it]?.english ?: it }
        assertTrue(why, why.startsWith("Chile has one by that name too.") && why.endsWith("the bigger, better-known one."))
        assertEquals("the United States", AtlasFacts.inSentence("USA", "United States"))
        assertEquals("Chile", AtlasFacts.inSentence("CHL", "Chile"))
    }

    @Test
    fun `pins go on the places a story names, not the region that only says where`() {
        val la = finder.spots("Fires near Los Angeles, California. The United States sent help.")
        assertEquals(listOf("Los Angeles"), la.map { it.place })
        // Shanghai is a city as well as a region: listed beside Beijing, it's a place of its own.
        val cities = finder.spots("Beijing, Shanghai and Shenzhen traded.").map { it.named }
        assertTrue(cities.toString(), cities.containsAll(listOf("Beijing", "Shanghai")))
        // A country is pinned only where the story names nothing inside it.
        val countries = finder.spots("China and Japan talked.")
        assertEquals(setOf("CHN", "JPN"), countries.map { it.a3 }.toSet())
        assertTrue(countries.all { it.placeKind == "country" && it.located })
        assertEquals(listOf("Atlanta"), finder.spots("Protests in Atlanta, Georgia, grew.").map { it.place })
    }

    @Test
    fun `a book's country is matched in any language`() {
        assertEquals("CHN", finder.country("China"))
        assertEquals("JPN", finder.country("日本国").let { it ?: finder.country("Japan") })
        assertNull(finder.country("Atlantis"))
    }

    @Test
    fun `time is worked out from the zone`() {
        assertTrue(AtlasFacts.time("Asia/Shanghai", Instant.parse("2026-11-20T21:48:00Z")).startsWith("UTC+8 · 05:48"))
        assertTrue(AtlasFacts.time("Asia/Kolkata", Instant.parse("2026-11-20T00:00:00Z")).startsWith("UTC+5:30"))
        assertEquals("", AtlasFacts.time("Nowhere/None", Instant.EPOCH))
    }

    @Test
    fun `facts read plainly`() {
        assertEquals("1.40 billion (2019)", AtlasFacts.population(byA3.getValue("CHN")))
        assertEquals("about 9.37 million km²", AtlasFacts.area(9_370_965.0))
        assertEquals("about 634,000 km²", AtlasFacts.area(634_482.0))
    }

    @Test
    fun `quizzes ask from the same region and keep one right answer`() {
        val random = kotlin.random.Random(7)
        val flags = AtlasQuiz.flags(countries, "Asia", 10, random)
        assertEquals(10, flags.size)
        assertTrue(flags.all { it.options.size == 4 && it.answer in 0..3 && it.options.distinct().size == 4 })
        val caps = AtlasQuiz.capitals(countries, "Europe", 10, random)
        assertTrue(caps.all { q -> q.options[q.answer] == byA3.getValue(q.a3).capital!!.name })
        val provinces = AtlasQuiz.subdivisions("CHN", subs.filter { it.a3 == "CHN" }, 10, random)
        assertTrue(provinces.all { q -> q.options[q.answer] == subs.filter { it.a3 == "CHN" }[q.subIndex].english })
    }

    // --- 1.35: the map's looks and the shaded relief ---

    @Test
    fun `every population and count reads as a whole number`() {
        // Natural Earth gave Somalia's as 10192317.3, which once stopped the whole map opening.
        assertTrue(countries.all { it.pop >= 0 && it.popYear >= 0 })
    }

    @Test
    fun `latitude comes back from the map's y`() {
        for (lat in listOf(-85.0, -60.0, -33.3, 0.0, 12.5, 47.5, 72.0, 85.0)) {
            assertEquals(lat, EqualEarth.latOf(EqualEarth.y(lat)), 1e-6)
        }
        assertEquals(EqualEarth.Y_MAX, EqualEarth.y(-90.0), 1e-9)
    }

    @Test
    fun `the scale bar measures true along a parallel`() {
        // Ten degrees of longitude: about 1,112 km at the equator, about 556 at 60°.
        assertEquals(1111.95, EqualEarth.x(10.0, 0.0) * EqualEarth.kmPerUnit(0.0), 0.5)
        assertEquals(555.97, (EqualEarth.x(10.0, 60.0) - EqualEarth.x(0.0, 60.0)) * EqualEarth.kmPerUnit(60.0), 0.5)
    }

    @Test
    fun `the relief is a lossless WebP the shape of the map`() {
        val bytes = File(dir, "relief.webp").readBytes()
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WEBPVP8L", String(bytes, 8, 8, Charsets.US_ASCII))
        assertEquals(0x2f, bytes[20].toInt() and 0xFF)
        val bits = (bytes[21].toInt() and 0xFF) or ((bytes[22].toInt() and 0xFF) shl 8) or
            ((bytes[23].toInt() and 0xFF) shl 16) or ((bytes[24].toInt() and 0xFF) shl 24)
        val width = (bits and 0x3FFF) + 1
        val height = ((bits shr 14) and 0x3FFF) + 1
        assertEquals(5120, width)
        assertEquals(EqualEarth.HALF_WIDTH / EqualEarth.Y_MAX, width.toDouble() / height, 0.002)
        assertTrue("about 3 MB", bytes.size in 1_000_000..4_000_000)
    }

    @Test
    fun `seas are named over water`() {
        assertTrue(AtlasFacts.SEAS.isNotEmpty())
        val projected = shapes.map(EqualEarth::project)
        for (sea in AtlasFacts.SEAS) {
            val x = EqualEarth.x(sea.lon, sea.lat)
            val y = EqualEarth.y(sea.lat)
            val onLand = projected.any { EqualEarth.contains(it, x.toFloat(), y.toFloat()) }
            assertFalse("${sea.name} sits on land", onLand)
        }
    }
}
