package app.localmind.assistant.data.atlas

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.localmind.assistant.domain.atlas.AtlasCountry
import app.localmind.assistant.domain.atlas.AtlasFiles
import app.localmind.assistant.domain.atlas.AtlasPlace
import app.localmind.assistant.domain.atlas.AtlasSubdivision
import app.localmind.assistant.domain.atlas.EqualEarth
import app.localmind.assistant.domain.atlas.PlaceFinder
import app.localmind.assistant.domain.atlas.Rings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A country or subdivision's outline, projected (Equal Earth), with its box and area. */
class Outline(val rings: Rings, val bounds: FloatArray, val areaKm2: Double)

/** The world as the Atlas draws it: countries in order, their outlines, and the places. */
class AtlasWorld(
    val countries: List<AtlasCountry>,
    val outlines: List<Outline>,
    val places: List<AtlasPlace>
) {
    val byA3: Map<String, AtlasCountry> = countries.associateBy { it.a3 }
    val indexOf: Map<String, Int> = countries.withIndex().associate { (i, c) -> c.a3 to i }
    val capitals: List<AtlasPlace> = places.filter { it.kind == "capital" }
}

/** A country's subdivisions and their outlines, in the same order. */
class AtlasRegions(val a3: String, val subdivisions: List<AtlasSubdivision>, val outlines: List<Outline>)

/** The Atlas's settings: the map in white, and the language of its labels. */
data class AtlasPrefs(val light: Boolean = false, val labels: String = "en")

/**
 * The Atlas's data, from the app's own assets (nothing is downloaded): Natural Earth's countries,
 * subdivisions and places, and flag-icons' flags. Read once, off the main thread, when first asked.
 */
@Singleton
class AtlasRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val lock = Mutex()
    private var world: AtlasWorld? = null
    private var subdivisions: List<AtlasSubdivision>? = null
    private var subOutlines: List<Rings>? = null
    private var finder: PlaceFinder? = null
    private val regions = HashMap<String, AtlasRegions>()
    private val flags = object : LruCache<String, ImageBitmap>(80) {}
    private val prefs = context.getSharedPreferences("atlas", Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(AtlasPrefs(prefs.getBoolean(KEY_LIGHT, false), prefs.getString(KEY_LABELS, "en") ?: "en"))
    val settings: StateFlow<AtlasPrefs> = _prefs.asStateFlow()

    fun setLight(on: Boolean) {
        prefs.edit().putBoolean(KEY_LIGHT, on).apply()
        _prefs.value = _prefs.value.copy(light = on)
    }

    fun setLabels(lang: String) {
        prefs.edit().putString(KEY_LABELS, lang).apply()
        _prefs.value = _prefs.value.copy(labels = lang)
    }

    private fun asset(name: String): ByteArray = context.assets.open("atlas/$name").use { it.readBytes() }

    private fun outline(rings: Rings): Outline {
        val projected = EqualEarth.project(rings)
        return Outline(projected, EqualEarth.bounds(projected), EqualEarth.areaKm2(projected))
    }

    suspend fun world(): AtlasWorld = lock.withLock {
        world ?: withContext(Dispatchers.Default) {
            val countries = AtlasFiles.countries(String(asset("countries.json"), Charsets.UTF_8))
            val shapes = AtlasFiles.shapes(asset("countries.bin"))
            require(shapes.size == countries.size) { "The Atlas's outlines don't match its countries" }
            val places = AtlasFiles.places(String(asset("places.json"), Charsets.UTF_8))
            AtlasWorld(countries, shapes.map(::outline), places)
        }.also { world = it }
    }

    private suspend fun allSubdivisions(): Pair<List<AtlasSubdivision>, List<Rings>> {
        val subs = subdivisions
        val shapes = subOutlines
        if (subs != null && shapes != null) return subs to shapes
        return withContext(Dispatchers.Default) {
            val s = AtlasFiles.subdivisions(String(asset("subdivisions.json"), Charsets.UTF_8))
            val o = AtlasFiles.shapes(asset("subdivisions.bin"))
            require(o.size == s.size) { "The Atlas's subdivision outlines don't match" }
            s to o
        }.also { (s, o) ->
            subdivisions = s
            subOutlines = o
        }
    }

    /** A country's subdivisions (provinces, states…) with their outlines. */
    suspend fun regions(a3: String): AtlasRegions = lock.withLock {
        regions[a3] ?: run {
            val (subs, shapes) = allSubdivisions()
            withContext(Dispatchers.Default) {
                val picked = subs.indices.filter { subs[it].a3 == a3 }
                AtlasRegions(a3, picked.map { subs[it] }, picked.map { outline(shapes[it]) })
            }.also { regions[a3] = it }
        }
    }

    /** Every subdivision's names (no outlines): for search. */
    suspend fun subdivisionNames(): List<AtlasSubdivision> = lock.withLock { allSubdivisions().first }

    /** Finds the places a text names (for the edition, the Reader and SlothBooks). */
    suspend fun finder(): PlaceFinder {
        finder?.let { return it }
        val w = world()
        val subs = subdivisionNames()
        return withContext(Dispatchers.Default) { PlaceFinder(w.countries, w.places, subs) }.also { finder = it }
    }

    /** A flag, drawn from the app's own PNGs (96×72). */
    suspend fun flag(code: String): ImageBitmap? {
        if (code.isBlank() || !code.all { it.isLetter() || it == '-' }) return null
        flags.get(code)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { context.assets.open("atlas/flags/$code.png").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }
                .onFailure { Log.w(TAG, "No flag for $code") }
                .getOrNull()
        }?.also { flags.put(code, it) }
    }

    companion object {
        private const val TAG = "AtlasRepository"
        private const val KEY_LIGHT = "light"
        private const val KEY_LABELS = "labels"
    }
}
