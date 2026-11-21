package app.localmind.assistant.domain.atlas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The Atlas (1.33): a world map kept on the tablet. Borders, names, capitals and subdivisions are
 * Natural Earth's (public domain), built into assets/atlas by scripts/atlas/build_atlas.py; flags
 * are flag-icons' (MIT). Borders are de facto: who holds the ground. Everything counted here (areas,
 * neighbours, subdivisions, local time) is worked out on the tablet from that data.
 */

/** A name in the languages SlothLlama's tutors speak, as Natural Earth gives them. */
@Serializable
data class AtlasNames(
    val zh: String? = null,
    val zht: String? = null,
    val ja: String? = null,
    val ko: String? = null,
    val fr: String? = null,
    val de: String? = null,
    val es: String? = null,
    val it: String? = null
) {
    fun of(lang: String): String? = when (lang) {
        "zh" -> zh
        "zht" -> zht
        "ja" -> ja
        "ko" -> ko
        "fr" -> fr
        "de" -> de
        "es" -> es
        "it" -> it
        else -> null
    }

    fun all(): List<String> = listOfNotNull(zh, zht, ja, ko, fr, de, es, it)
}

@Serializable
data class AtlasCapital(
    val name: String,
    @SerialName("n") val names: AtlasNames = AtlasNames(),
    val lon: Double,
    val lat: Double,
    /** "capital", or "main city" for a territory without one (Hong Kong, Greenland). */
    val seat: String = "capital"
)

@Serializable
data class AtlasCountry(
    val a3: String,
    val a2: String = "",
    /** Natural Earth's short name ("Dem. Rep. Congo"); [english] is the one to show. */
    val name: String,
    val long: String = "",
    val formal: String = "",
    @SerialName("n") val names: AtlasNames = AtlasNames(),
    /** Sovereign country, Country, Dependency, Disputed, Indeterminate… */
    val type: String = "",
    val sov: String = "",
    val continent: String = "",
    val region: String = "",
    val subregion: String = "",
    /** Natural Earth's population estimate and the year it's for. */
    val pop: Long = 0,
    val popYear: Int = 0,
    val income: String = "",
    val label: List<Double> = emptyList(),
    val labelRank: Int = 5,
    /** Natural Earth's map colour (1–7): neighbours never share one. */
    val color: Int = 1,
    val flag: String = "",
    val capital: AtlasCapital? = null,
    val tz: String = "",
    val neighbours: List<String> = emptyList(),
    /** lon/lat box to frame it in: its main land, not every far island. */
    val box: List<Double> = emptyList(),
    val wikidata: String = ""
) {
    val english: String get() = long.ifBlank { name }
}

@Serializable
data class AtlasSubdivision(
    val a3: String,
    val name: String,
    val en: String = "",
    @SerialName("n") val names: AtlasNames = AtlasNames(),
    /** Province, State, Prefecture, Municipality… */
    val type: String = "",
    val iso: String = "",
    /** Natural Earth's own reading, where it has one (Pinyin for China's: Guǎngdōng). */
    val reading: String = "",
    val label: List<Double>? = null
) {
    val english: String get() = en.ifBlank { name }
}

@Serializable
data class AtlasPlace(
    val name: String,
    @SerialName("n") val names: AtlasNames = AtlasNames(),
    val a3: String,
    val lon: Double,
    val lat: Double,
    /** Natural Earth's scale rank: 0 the biggest places. */
    val rank: Int = 10,
    val tz: String = "",
    /** capital, capital-alt, region-capital or city. */
    val kind: String = "city"
)

@Serializable
data class CountriesFile(val format: String, val v: Int = 1, val source: String = "", val countries: List<AtlasCountry>)

@Serializable
data class SubdivisionsFile(val format: String, val v: Int = 1, val subdivisions: List<AtlasSubdivision>)

@Serializable
data class PlacesFile(val format: String, val v: Int = 1, val places: List<AtlasPlace>)

/** Outlines as rings of lon/lat pairs (degrees). */
typealias Rings = List<FloatArray>

object AtlasFiles {
    private val json = Json { ignoreUnknownKeys = true }

    fun countries(text: String): List<AtlasCountry> = json.decodeFromString(CountriesFile.serializer(), text)
        .also { require(it.format == "slothatlas-countries") { "Not the Atlas's countries" } }.countries

    fun subdivisions(text: String): List<AtlasSubdivision> = json.decodeFromString(SubdivisionsFile.serializer(), text)
        .also { require(it.format == "slothatlas-subdivisions") { "Not the Atlas's subdivisions" } }.subdivisions

    fun places(text: String): List<AtlasPlace> = json.decodeFromString(PlacesFile.serializer(), text)
        .also { require(it.format == "slothatlas-places") { "Not the Atlas's places" } }.places

    /**
     * The outlines file: "ATL1", then a count of shapes; each shape a count of rings; each ring a
     * count of points, then each point as zigzag varint steps of 0.01° from the one before.
     */
    fun shapes(bytes: ByteArray): List<Rings> {
        require(bytes.size >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "ATL1") { "Not an Atlas outlines file" }
        var at = 4
        fun next(): Int {
            var result = 0L
            var shift = 0
            while (true) {
                require(at < bytes.size && shift < 64) { "The outlines file is cut short" }
                val b = bytes[at++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b < 0x80) break
                shift += 7
            }
            return ((result ushr 1) xor -(result and 1)).toInt()
        }
        val count = next()
        require(count in 0..100_000) { "Too many shapes" }
        return List(count) {
            val rings = next()
            require(rings in 0..10_000) { "Too many rings" }
            List(rings) {
                val n = next()
                require(n in 0..1_000_000) { "Too many points" }
                val out = FloatArray(n * 2)
                var x = 0
                var y = 0
                for (i in 0 until n) {
                    x += next()
                    y += next()
                    out[2 * i] = x / 100f
                    out[2 * i + 1] = y / 100f
                }
                out
            }
        }
    }
}

/**
 * The Equal Earth projection (Šavrič, Patterson, Jenny 2018): equal-area, so areas measured on it
 * are true. x runs about ±2.71, y about ±1.32 for a unit sphere; y here grows downward, as on screen.
 */
object EqualEarth {
    private const val A1 = 1.340264
    private const val A2 = -0.081106
    private const val A3 = 0.000893
    private const val A4 = 0.003796
    private val M = sqrt(3.0) / 2
    const val EARTH_KM = 6371.0088
    const val HALF_WIDTH = 2.7066297

    fun x(lon: Double, lat: Double): Double {
        val th = asin(M * sin(lat * PI / 180))
        val t2 = th * th
        val t6 = t2 * t2 * t2
        return (lon * PI / 180) * cos(th) / (M * (A1 + 3 * A2 * t2 + t6 * (7 * A3 + 9 * A4 * t2)))
    }

    /** Downward y. */
    fun y(lat: Double): Double {
        val th = asin(M * sin(lat * PI / 180))
        val t2 = th * th
        val t6 = t2 * t2 * t2
        return -th * (A1 + A2 * t2 + t6 * (A3 + A4 * t2))
    }

    /** How far down the map runs: 90°S is at +Y_MAX, 90°N at -Y_MAX (y grows downward). */
    val Y_MAX: Double = -y(90.0)

    /** The latitude at a (downward) [yDown]: the inverse of [y], by Newton's method. */
    fun latOf(yDown: Double): Double {
        val target = -yDown.coerceIn(-Y_MAX, Y_MAX)
        var th = target / A1
        repeat(12) {
            val t2 = th * th
            val t6 = t2 * t2 * t2
            th -= (th * (A1 + A2 * t2 + t6 * (A3 + A4 * t2)) - target) / (A1 + 3 * A2 * t2 + t6 * (7 * A3 + 9 * A4 * t2))
        }
        return asin((sin(th) / M).coerceIn(-1.0, 1.0)) * 180 / PI
    }

    /** Kilometres along the parallel at [lat] for one unit of x: what a scale bar there measures. */
    fun kmPerUnit(lat: Double): Double {
        val th = asin(M * sin(lat * PI / 180))
        val t2 = th * th
        val t6 = t2 * t2 * t2
        return EARTH_KM * cos(lat * PI / 180) * M * (A1 + 3 * A2 * t2 + t6 * (7 * A3 + 9 * A4 * t2)) / cos(th)
    }

    /** Rings projected: the same layout, x and y in projection units. */
    fun project(rings: Rings): Rings = rings.map { r ->
        FloatArray(r.size).also { out ->
            for (i in 0 until r.size / 2) {
                val lon = r[2 * i].toDouble()
                val lat = r[2 * i + 1].toDouble()
                out[2 * i] = x(lon, lat).toFloat()
                out[2 * i + 1] = y(lat).toFloat()
            }
        }
    }

    /** Area in km² of projected rings (holes wound the other way subtract). */
    fun areaKm2(projected: Rings): Double {
        var sum = 0.0
        for (r in projected) {
            val n = r.size / 2
            var s = 0.0
            for (i in 0 until n) {
                val j = (i + 1) % n
                s += r[2 * i].toDouble() * r[2 * j + 1] - r[2 * j].toDouble() * r[2 * i + 1]
            }
            sum += s / 2
        }
        return abs(sum) * EARTH_KM * EARTH_KM
    }

    /** Box of projected rings: minX, minY, maxX, maxY. */
    fun bounds(projected: Rings): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (r in projected) for (i in 0 until r.size / 2) {
            val x = r[2 * i]
            val y = r[2 * i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    /** A lon/lat box as a projected box (the edges bow, so a grid of points is projected). */
    fun boxOf(lonMin: Double, latMin: Double, lonMax: Double, latMax: Double): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0..4) for (j in 0..4) {
            val lon = lonMin + (lonMax - lonMin) * i / 4
            val lat = latMin + (latMax - latMin) * j / 4
            val x = x(lon, lat).toFloat()
            val y = y(lat).toFloat()
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    /** Even-odd test: inside an outline (holes count as outside). */
    fun contains(projected: Rings, x: Float, y: Float): Boolean {
        var inside = false
        for (r in projected) {
            val n = r.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = r[2 * i]
                val yi = r[2 * i + 1]
                val xj = r[2 * j]
                val yj = r[2 * j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
        }
        return inside
    }
}

/** A place found in a text: the country it's in, and what was named. */
data class PlaceHit(
    val a3: String,
    /** The words as found ("Guangdong", "香港"). */
    val named: String,
    /** country, capital, city or subdivision. */
    val kind: String,
    val count: Int
)

/**
 * Finds the places a text names: countries (in English and the tutors' languages, plus common
 * short forms such as "U.S."), capitals and big cities, and the subdivisions of large countries.
 * English names must match with their capitals, whole words only; Chinese and Japanese names are
 * found inside the text. Ambiguous English words ("Male", "Nice") are left out.
 *
 * Some names belong to places in more than one country (Los Angeles, Vancouver, Santiago,
 * Georgia). Which one is meant (1.46): a country or region named right beside it ("Los Angeles,
 * California", "Chile's Los Ángeles"); else a country the text names elsewhere, but only between
 * places of about the same size; else the bigger place (Natural Earth's rank) and the higher kind.
 */
class PlaceFinder(countries: List<AtlasCountry>, places: List<AtlasPlace>, subdivisions: List<AtlasSubdivision>) {

    /** [rank]: Natural Earth's scale rank, 0 the biggest (countries and regions count as 0). */
    private data class Entry(val a3: String, val kind: String, val priority: Int, val rank: Int = 0)

    /** A name found in the text, where, and the places it could be. */
    private class Match(val start: Int, val end: Int, val named: String, val options: List<Entry>, val possessive: Boolean) {
        val sure: Boolean get() = options.distinctBy { it.a3 }.size == 1
    }

    private val order = compareBy<Entry>({ it.priority }, { it.rank })

    private val latin = HashMap<String, MutableList<Entry>>()
    private val cjk = HashMap<String, MutableList<Entry>>()
    private val countryNames = HashMap<String, String>()
    private var longest = 1

    init {
        // Every place a name could be, one per country (the higher kind, then the bigger place).
        fun add(map: HashMap<String, MutableList<Entry>>, key: String, e: Entry) {
            val list = map.getOrPut(key) { mutableListOf() }
            val i = list.indexOfFirst { it.a3 == e.a3 }
            if (i < 0) list += e else if (order.compare(e, list[i]) < 0) list[i] = e
        }
        fun put(name: String?, e: Entry) {
            val n = name?.trim()?.takeIf { it.length >= 2 } ?: return
            if (n.any(::isCjk)) {
                if (n.length < 2) return
                add(cjk, n, e)
            } else {
                val key = key(n)
                if (key in STOP || key.length < 3) return
                add(latin, key, e)
                longest = maxOf(longest, key.count { it == ' ' } + 1)
            }
        }
        for (c in countries) {
            val e = Entry(c.a3, "country", 0)
            (listOf(c.name, c.long) + c.names.all() + ALIASES[c.a3].orEmpty()).forEach { put(it, e) }
            // Chinese and Japanese names without the formal ending ("中华人民共和国" is also "中国").
            SHORT_CJK[c.a3]?.forEach { put(it, e) }
            (listOf(c.name, c.long, c.formal) + c.names.all() + ALIASES[c.a3].orEmpty()).forEach { n ->
                countryNames[fold(n)] = c.a3
            }
        }
        for (p in places) {
            if (p.kind == "city" && p.rank > 6) continue
            val e = Entry(p.a3, if (p.kind == "capital") "capital" else "city", if (p.kind == "capital") 1 else 3, p.rank)
            (listOf(p.name) + p.names.all()).forEach { put(it, e) }
        }
        for (s in subdivisions) {
            if (s.a3 !in BIG) continue
            val e = Entry(s.a3, "subdivision", 2)
            if (s.english.length >= 5) put(s.english, e)
            listOfNotNull(s.names.zh, s.names.zht, s.names.ja).forEach { n ->
                put(n, e)
                SUFFIXES.firstOrNull { n.endsWith(it) && n.length - it.length >= 2 }?.let { put(n.removeSuffix(it), e) }
            }
        }
    }

    /** The country a name stands for ("China", "中国", "U.S."), or null. Exact, any language. */
    fun country(name: String): String? = countryNames[fold(name)]

    /** The places [text] names, the most named first; one hit per country. */
    fun find(text: String, max: Int = 4): List<PlaceHit> {
        val matches = ArrayList<Match>()
        // English and other Latin-script names: runs of words, the first one capitalised.
        val words = Regex("[\\p{L}\\p{N}.'’-]+").findAll(text).toList()
        var i = 0
        while (i < words.size) {
            val w = clean(words[i].value)
            if (w.isEmpty() || !w[0].isUpperCase()) {
                i++
                continue
            }
            var matched = 0
            for (n in minOf(longest, words.size - i) downTo 1) {
                val phrase = (i until i + n).joinToString(" ") { clean(words[it].value) }
                val options = latin[key(phrase)] ?: continue
                val last = words[i + n - 1].value
                matches += Match(words[i].range.first, words[i + n - 1].range.last + 1, phrase, options, last.endsWith("'s") || last.endsWith("’s"))
                matched = n
                break
            }
            i += maxOf(matched, 1)
        }
        // Chinese and Japanese: the longest name at each place in each run of characters.
        Regex("[\\u3040-\\u30FF\\u3400-\\u9FFF\\uF900-\\uFAFF]+").findAll(text).forEach { m ->
            val run = m.value
            var at = 0
            while (at < run.length) {
                var len = minOf(10, run.length - at)
                var took = 0
                while (len >= 2) {
                    val s = run.substring(at, at + len)
                    val options = cjk[s]
                    if (options != null) {
                        matches += Match(m.range.first + at, m.range.first + at + len, s, options, false)
                        took = len
                        break
                    }
                    len--
                }
                at += maxOf(took, 1)
            }
        }

        val elsewhere = matches.filter { it.sure }.map { it.options.first().a3 }.toSet()
        val found = LinkedHashMap<String, Pair<Entry, MutableList<String>>>()
        for (m in matches) {
            val e = if (m.sure) m.options.minWith(order) else choose(m, matches, text, elsewhere)
            val slot = found.getOrPut(e.a3) { e to mutableListOf() }
            if (e.priority < slot.first.priority) found[e.a3] = e to slot.second
            found[e.a3]!!.second += m.named
        }
        return found.values
            .map { (e, names) -> PlaceHit(e.a3, names.groupingBy { it }.eachCount().maxBy { it.value }.key, e.kind, names.size) }
            .sortedWith(compareByDescending<PlaceHit> { it.count }.thenBy { KIND_ORDER.indexOf(it.kind) })
            .take(max)
    }

    /** Which of a shared name's places the text means (see the class's note). */
    private fun choose(m: Match, all: List<Match>, text: String, elsewhere: Set<String>): Entry {
        val top = m.options.minWith(order)
        val beside = all.filter { o -> o !== m && o.sure && beside(m, o, text) }.map { it.options.first().a3 }.toSet()
        m.options.filter { it.a3 in beside }.minWithOrNull(order)?.let { return it }
        m.options.filter { it.a3 in elsewhere && it.rank - top.rank <= 1 }.minWithOrNull(order)?.let { return it }
        return top
    }

    /** "Los Angeles, California", "Hyderabad in Pakistan", or "Chile's Los Ángeles". */
    private fun beside(m: Match, o: Match, text: String): Boolean = when {
        o.start >= m.end -> BESIDE_AFTER.matches(text.substring(m.end, o.start))
        o.end <= m.start -> o.possessive && text.substring(o.end, m.start).isBlank()
        else -> false
    }

    companion object {
        private val KIND_ORDER = listOf("country", "capital", "subdivision", "city")

        /** What may stand between a place and the region that says which one: ", " or " in ". */
        private val BESIDE_AFTER = Regex("\\s*,\\s*|\\s+in\\s+", RegexOption.IGNORE_CASE)

        /** Countries whose provinces or states are named often enough to look for. */
        val BIG = setOf("CHN", "USA", "CAN", "AUS", "IND", "BRA", "DEU", "JPN", "MEX", "RUS", "ESP", "ITA", "FRA", "GBR", "KOR", "TWN")

        private val SUFFIXES = listOf("维吾尔自治区", "維吾爾自治區", "壮族自治区", "壯族自治區", "回族自治区", "回族自治區", "自治区", "自治區", "特别行政区", "特別行政區", "省", "市", "県", "府", "都", "道")

        private val SHORT_CJK = mapOf(
            "CHN" to listOf("中国", "中國"), "TWN" to listOf("台湾", "台灣", "臺灣"), "HKG" to listOf("香港"), "MAC" to listOf("澳门", "澳門"),
            "JPN" to listOf("日本"), "KOR" to listOf("韩国", "韓國", "韓国"), "PRK" to listOf("朝鲜", "北韩", "北韓"), "USA" to listOf("美国", "美國"),
            "GBR" to listOf("英国", "英國"), "FRA" to listOf("法国", "法國"), "DEU" to listOf("德国", "德國"), "RUS" to listOf("俄罗斯", "俄羅斯"),
            "ITA" to listOf("意大利"), "ESP" to listOf("西班牙"), "IND" to listOf("印度"), "VNM" to listOf("越南"), "SGP" to listOf("新加坡")
        )

        private val ALIASES = mapOf(
            "USA" to listOf("United States", "U.S.", "U.S", "US", "USA", "America"),
            "GBR" to listOf("United Kingdom", "UK", "U.K.", "Britain", "Great Britain"),
            "ARE" to listOf("UAE", "United Arab Emirates"),
            "COD" to listOf("DR Congo", "DRC", "Democratic Republic of the Congo"),
            "CIV" to listOf("Ivory Coast", "Côte d'Ivoire"),
            "CZE" to listOf("Czech Republic", "Czechia"),
            "MMR" to listOf("Burma", "Myanmar"),
            "TUR" to listOf("Turkey", "Türkiye"),
            "KOR" to listOf("South Korea"),
            "PRK" to listOf("North Korea"),
            "NLD" to listOf("Netherlands", "Holland"),
            "VAT" to listOf("Vatican", "Vatican City"),
            "PSX" to listOf("Palestine", "Gaza", "West Bank"),
            "BIH" to listOf("Bosnia"),
            "HKG" to listOf("Hong Kong"),
            "MAC" to listOf("Macau", "Macao")
        )

        /** English words that are also places: too often just words. */
        private val STOP = setOf("male", "nice", "mobile", "split", "reading", "bath", "island", "man", "victoria")

        private fun isCjk(c: Char) = c in '぀'..'ヿ' || c in '㐀'..'鿿' || c in '豈'..'﫿'

        private fun clean(w: String): String = w.trim('.', '\'', '’', '-').removeSuffix("'s").removeSuffix("’s").let { s ->
            // "U.S." keeps its dots; "China." doesn't.
            if (w.count { it == '.' } >= 2) w.trim('\'', '’', '-') else s
        }

        fun key(s: String): String = s.trim().replace(",", "").replace(Regex("\\s+"), " ").let { if (it.uppercase() == it && it.length <= 5) it else it.lowercase() }

        fun fold(s: String): String =
            Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    }
}

/** What the country card says, worked out from the Atlas's data. */
object AtlasFacts {
    /** "1.40 billion (2019)", "7.5 million (2019)". */
    fun population(c: AtlasCountry): String {
        if (c.pop <= 0) return ""
        val n = c.pop.toDouble()
        val s = when {
            n >= 1e9 -> "%.2f billion".format(Locale.ENGLISH, n / 1e9)
            n >= 1e6 -> "%.1f million".format(Locale.ENGLISH, n / 1e6)
            n >= 1e3 -> "%,d".format(Locale.ENGLISH, c.pop)
            else -> c.pop.toString()
        }
        return if (c.popYear > 0) "$s (${c.popYear})" else s
    }

    /** "about 9.37 million km²", "about 634,000 km²". */
    fun area(km2: Double): String = when {
        km2 >= 1e6 -> "about %.2f million km²".format(Locale.ENGLISH, km2 / 1e6)
        km2 >= 10_000 -> "about %,d km²".format(Locale.ENGLISH, (km2 / 1_000).toLong() * 1_000)
        km2 >= 100 -> "about %,d km²".format(Locale.ENGLISH, (km2 / 10).toLong() * 10)
        else -> "under 100 km² (too small for this map to measure well)"
    }

    /** "UTC+8 · 03:48 now", worked out from the capital's time zone. */
    fun time(tz: String, now: Instant): String {
        val zone = runCatching { ZoneId.of(tz) }.getOrNull() ?: return ""
        val at = now.atZone(zone)
        val seconds = at.offset.totalSeconds
        val h = seconds / 3600
        val m = abs(seconds % 3600) / 60
        val offset = "UTC" + (if (seconds >= 0) "+" else "-") + abs(h) + if (m > 0) ":%02d".format(m) else ""
        return "$offset · %02d:%02d now".format(at.hour, at.minute)
    }

    /** Subdivisions counted by type: "22 provinces · 5 autonomous regions · 4 municipalities". */
    fun subdivisionKinds(subs: List<AtlasSubdivision>): String =
        subs.groupingBy { it.type.ifBlank { "Subdivision" } }.eachCount().entries
            .sortedByDescending { it.value }
            .joinToString(" · ") { (type, n) -> "$n ${plural(type.lowercase(Locale.ENGLISH), n)}" }

    private fun plural(word: String, n: Int): String = when {
        n == 1 -> word
        word.endsWith("y") && !word.endsWith("ey") -> word.dropLast(1) + "ies"
        word.endsWith("s") || word.endsWith("x") || word.endsWith("ch") || word.endsWith("sh") -> word + "es"
        else -> word + "s"
    }

    /**
     * Neighbours by land, split: other countries, and parts of the same sovereign (Hong Kong and
     * Macau beside China). Areas Natural Earth marks indeterminate (Siachen) are left out.
     */
    fun neighbours(c: AtlasCountry, byA3: Map<String, AtlasCountry>): Pair<List<AtlasCountry>, List<AtlasCountry>> {
        val all = c.neighbours.mapNotNull { byA3[it] }.filter { it.type != "Indeterminate" }
        val (same, other) = all.partition { it.sov == c.sov && c.sov.isNotBlank() }
        return other.sortedBy { it.english } to same.sortedBy { it.english }
    }

    /** The tutor a country's main language has, if any. */
    fun tutor(a3: String): String? = when (a3) {
        "CHN", "TWN", "SGP" -> "persona-mandarin-partner"
        "HKG", "MAC" -> "persona-cantonese-partner"
        "JPN" -> "persona-japanese-partner"
        "FRA", "BEL", "MCO", "LUX" -> "persona-french-partner"
        "DEU", "AUT", "LIE" -> "persona-german-partner"
        "ESP", "MEX", "ARG", "COL", "PER", "VEN", "CHL", "ECU", "GTM", "CUB", "BOL", "DOM", "HND", "PRY", "SLV", "NIC", "CRI", "PAN", "URY", "PRI" -> "persona-spanish-partner"
        "ITA", "SMR", "VAT" -> "persona-italian-partner"
        else -> null
    }

    /** Oceans (from the whole world) and seas (from a little closer), named where their water is widest. */
    val SEAS = listOf(
        SeaLabel("Pacific Ocean", -150.0, 5.0, true), SeaLabel("Pacific Ocean", 165.0, 25.0, true),
        SeaLabel("Atlantic Ocean", -35.0, 22.0, true), SeaLabel("Atlantic Ocean", -18.0, -25.0, true),
        SeaLabel("Indian Ocean", 78.0, -22.0, true), SeaLabel("Arctic Ocean", 0.0, 82.0, true), SeaLabel("Southern Ocean", 60.0, -58.0, true),
        SeaLabel("South China Sea", 114.0, 14.0), SeaLabel("East China Sea", 126.0, 29.0), SeaLabel("Sea of Japan", 135.0, 40.5),
        SeaLabel("Philippine Sea", 133.0, 18.0), SeaLabel("Bay of Bengal", 89.0, 15.0), SeaLabel("Arabian Sea", 64.0, 15.0),
        SeaLabel("Caspian Sea", 50.5, 41.5), SeaLabel("Mediterranean Sea", 18.0, 34.5), SeaLabel("Black Sea", 34.0, 43.3),
        SeaLabel("Red Sea", 38.5, 20.0), SeaLabel("North Sea", 3.0, 56.0), SeaLabel("Baltic Sea", 18.5, 56.8),
        SeaLabel("Bay of Biscay", -5.5, 45.3), SeaLabel("Gulf of Mexico", -90.0, 25.0), SeaLabel("Caribbean Sea", -75.0, 15.0),
        SeaLabel("Hudson Bay", -85.0, 60.0), SeaLabel("Coral Sea", 155.0, -17.0), SeaLabel("Tasman Sea", 160.0, -38.0),
        SeaLabel("Bering Sea", -178.0, 58.0), SeaLabel("Persian Gulf", 51.5, 27.0), SeaLabel("Gulf of Guinea", 3.0, 1.0)
    )

    /** The regions the map can frame, by Natural Earth's continent. */
    val REGIONS = listOf("World", "Africa", "Americas", "Asia", "Europe", "Oceania")

    fun inRegion(c: AtlasCountry, region: String): Boolean = when (region) {
        "World" -> true
        "Americas" -> c.continent == "North America" || c.continent == "South America"
        else -> c.continent == region
    }

    /** Label languages for the map and quiz: English always, and the tutors' languages. */
    val LABELS = listOf("en" to "English", "zh" to "中文", "zht" to "繁體", "ja" to "日本語", "fr" to "Français", "de" to "Deutsch", "es" to "Español", "it" to "Italiano")

    fun label(english: String, names: AtlasNames, lang: String): String = if (lang == "en") english else names.of(lang) ?: english
}

/** A body of water's name on the map: oceans from far out, seas from closer in. */
data class SeaLabel(val name: String, val lon: Double, val lat: Double, val ocean: Boolean = false)

/** A quiz question: what's shown, the options, and which is right. */
data class AtlasQuestion(
    val kind: String,
    /** The country (for a flag or capital) or subdivision index asked about. */
    val a3: String,
    val subIndex: Int = -1,
    val prompt: String,
    val options: List<String>,
    val answer: Int
)

object AtlasQuiz {
    /** Flags: which country's flag is shown; the others come from the same region. */
    fun flags(countries: List<AtlasCountry>, region: String, count: Int, random: kotlin.random.Random, lang: String = "en"): List<AtlasQuestion> {
        val pool = countries.filter { it.flag.isNotBlank() && AtlasFacts.inRegion(it, region) && it.type != "Indeterminate" }
        return pool.shuffled(random).take(count).map { c ->
            val others = pool.filter { it.a3 != c.a3 && it.subregion == c.subregion }.shuffled(random).take(3)
                .let { if (it.size < 3) it + pool.filter { o -> o.a3 != c.a3 && o !in it }.shuffled(random).take(3 - it.size) else it }
            options(c, others, random) { AtlasFacts.label(it.english, it.names, lang) }.let { (opts, right) ->
                AtlasQuestion("flag", c.a3, prompt = "Whose flag is it?", options = opts, answer = right)
            }
        }
    }

    /** Capitals: the capital of a country, from capitals in the same region. */
    fun capitals(countries: List<AtlasCountry>, region: String, count: Int, random: kotlin.random.Random, lang: String = "en"): List<AtlasQuestion> {
        val pool = countries.filter { it.capital?.seat == "capital" && AtlasFacts.inRegion(it, region) }
        return pool.shuffled(random).take(count).map { c ->
            val others = pool.filter { it.a3 != c.a3 }.shuffled(random).take(3)
            val name = { x: AtlasCountry -> x.capital!!.let { AtlasFacts.label(it.name, it.names, lang) } }
            options(c, others, random, name).let { (opts, right) ->
                AtlasQuestion("capital", c.a3, prompt = "The capital of ${AtlasFacts.label(c.english, c.names, lang)}?", options = opts, answer = right)
            }
        }
    }

    /** Subdivisions: which one is lit on the country's map. */
    fun subdivisions(a3: String, subs: List<AtlasSubdivision>, count: Int, random: kotlin.random.Random, lang: String = "en"): List<AtlasQuestion> {
        if (subs.size < 4) return emptyList()
        return subs.indices.shuffled(random).take(count).map { i ->
            val others = subs.indices.filter { it != i }.shuffled(random).take(3)
            val all = (others + i).shuffled(random)
            AtlasQuestion(
                "subdivision", a3, i, prompt = "Which one is lit?",
                options = all.map { subName(subs[it], lang) }, answer = all.indexOf(i)
            )
        }
    }

    fun subName(s: AtlasSubdivision, lang: String): String =
        if (lang == "en") s.english else listOfNotNull(s.names.of(lang), s.english).first()

    private fun <T> options(right: T, others: List<T>, random: kotlin.random.Random, name: (T) -> String): Pair<List<String>, Int> {
        val all = (others + right).shuffled(random)
        return all.map(name) to all.indexOf(right)
    }
}
