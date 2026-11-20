package app.localmind.assistant

import app.localmind.assistant.domain.atlas.AtlasFacts
import app.localmind.assistant.domain.atlas.AtlasFiles
import app.localmind.assistant.domain.atlas.AtlasQuiz
import app.localmind.assistant.domain.atlas.EqualEarth
import app.localmind.assistant.domain.atlas.PlaceFinder
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
}
