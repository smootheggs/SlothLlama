package app.localmind.assistant.ui.atlas

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.ask.AskInbox
import app.localmind.assistant.data.ask.LaunchRequest
import app.localmind.assistant.data.atlas.AtlasPrefs
import app.localmind.assistant.data.atlas.AtlasRegions
import app.localmind.assistant.data.atlas.AtlasRepository
import app.localmind.assistant.data.atlas.AtlasWorld
import app.localmind.assistant.data.atlas.Outline
import app.localmind.assistant.data.attachments.AttachmentStore
import app.localmind.assistant.data.books.BookShelf
import app.localmind.assistant.data.dictionary.ChineseDictionary
import app.localmind.assistant.data.edition.EditionStore
import app.localmind.assistant.domain.atlas.AtlasCountry
import app.localmind.assistant.domain.atlas.AtlasFacts
import app.localmind.assistant.domain.atlas.AtlasQuestion
import app.localmind.assistant.domain.atlas.AtlasQuiz
import app.localmind.assistant.domain.atlas.AtlasSubdivision
import app.localmind.assistant.domain.atlas.EqualEarth
import app.localmind.assistant.domain.edition.Editions
import app.localmind.assistant.domain.persona.SpeechLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import kotlin.random.Random

/** A request to frame part of the map: a projected box, and a number so the same box can be asked twice. */
class Frame(val box: FloatArray, val nonce: Long = System.nanoTime())

/** A mark on the map: stories in today's edition and books set there. */
data class Pin(val stories: Int, val books: Int)

/** What the country card shows, worked out from the Atlas's data. */
data class CountryInfo(
    val country: AtlasCountry,
    val area: String,
    val neighbours: List<AtlasCountry>,
    /** Parts of the same sovereign it borders (Hong Kong and Macau beside China). */
    val alsoBorders: List<AtlasCountry>,
    val subdivisions: String,
    val subdivisionCount: Int,
    val time: String,
    /** Pinyin for the Chinese name, from the dictionary on the tablet. */
    val reading: String,
    /** Titles of today's stories that name it, and books set there. */
    val stories: List<String>,
    val books: List<String>
)

/** A search result: a country, or a place or subdivision in one. */
data class AtlasHit(val a3: String, val title: String, val detail: String, val subIndex: Int = -1)

data class QuizState(
    val kind: String,
    val region: String,
    val questions: List<AtlasQuestion>,
    val at: Int = 0,
    val chosen: Int? = null,
    val right: Int = 0,
    val missed: List<AtlasQuestion> = emptyList()
) {
    val question: AtlasQuestion? get() = questions.getOrNull(at)
    val done: Boolean get() = at >= questions.size
}

@HiltViewModel
class AtlasViewModel @Inject constructor(
    private val repo: AtlasRepository,
    private val editions: EditionStore,
    private val shelf: BookShelf,
    private val dictionary: ChineseDictionary,
    private val askInbox: AskInbox,
    private val attachments: AttachmentStore
) : ViewModel() {

    val prefs: StateFlow<AtlasPrefs> = repo.settings

    private val _world = MutableStateFlow<AtlasWorld?>(null)
    val world: StateFlow<AtlasWorld?> = _world.asStateFlow()

    private val _paths = MutableStateFlow<List<Path>>(emptyList())
    val paths: StateFlow<List<Path>> = _paths.asStateFlow()

    private val _picked = MutableStateFlow<String?>(null)
    val picked: StateFlow<String?> = _picked.asStateFlow()

    private val _regions = MutableStateFlow<AtlasRegions?>(null)
    val regions: StateFlow<AtlasRegions?> = _regions.asStateFlow()

    private val _regionPaths = MutableStateFlow<List<Path>>(emptyList())
    val regionPaths: StateFlow<List<Path>> = _regionPaths.asStateFlow()

    private val _pickedSub = MutableStateFlow<Int?>(null)
    val pickedSub: StateFlow<Int?> = _pickedSub.asStateFlow()

    private val _frame = MutableStateFlow<Frame?>(null)
    val frame: StateFlow<Frame?> = _frame.asStateFlow()

    private val _info = MutableStateFlow<CountryInfo?>(null)
    val info: StateFlow<CountryInfo?> = _info.asStateFlow()

    private val _places = MutableStateFlow(false)
    /** The "Your places" layer: today's stories and your books, pinned. */
    val places: StateFlow<Boolean> = _places.asStateFlow()

    private val _pins = MutableStateFlow<Map<String, Pin>>(emptyMap())
    val pins: StateFlow<Map<String, Pin>> = _pins.asStateFlow()

    private val _results = MutableStateFlow<List<AtlasHit>>(emptyList())
    val results: StateFlow<List<AtlasHit>> = _results.asStateFlow()

    private val _quiz = MutableStateFlow<QuizState?>(null)
    val quiz: StateFlow<QuizState?> = _quiz.asStateFlow()

    private val _region = MutableStateFlow("World")
    val region: StateFlow<String> = _region.asStateFlow()

    private var storiesBy: Map<String, List<String>> = emptyMap()
    private var booksBy: Map<String, List<String>> = emptyMap()

    init {
        viewModelScope.launch {
            val w = runCatching { repo.world() }.getOrNull() ?: return@launch
            _paths.value = withContext(Dispatchers.Default) { w.outlines.map(::pathOf) }
            _world.value = w
            _picked.value?.let { describe(it) }
        }
    }

    // --- the map ---

    fun flag(code: String): suspend () -> ImageBitmap? = { repo.flag(code) }

    fun setLight(on: Boolean) = repo.setLight(on)

    fun setLabels(lang: String) = repo.setLabels(lang)

    /** Picks a country (null: none) and loads its subdivisions; [frame] moves the map to it. */
    fun pick(a3: String?, frame: Boolean = false) {
        _picked.value = a3
        _pickedSub.value = null
        if (a3 == null) {
            _regions.value = null
            _regionPaths.value = emptyList()
            _info.value = null
            return
        }
        viewModelScope.launch {
            val w = repo.world()
            val c = w.byA3[a3] ?: return@launch
            if (frame) frameCountry(c)
            describe(a3)
            val r = runCatching { repo.regions(a3) }.getOrNull() ?: return@launch
            if (_picked.value != a3) return@launch
            _regionPaths.value = withContext(Dispatchers.Default) { r.outlines.map(::pathOf) }
            _regions.value = r
            describe(a3)
        }
    }

    /** Frames the picked country (the country page's map). */
    fun framePicked() {
        val c = _picked.value?.let { _world.value?.byA3?.get(it) } ?: return
        frameCountry(c)
    }

    /** From a story or the Reader: the country on the map, framed. */
    fun focus(a3: String) = pick(a3, frame = true)

    fun pickSub(index: Int?) {
        _pickedSub.value = index
    }

    private fun frameCountry(c: AtlasCountry) {
        if (c.box.size == 4) _frame.value = Frame(EqualEarth.boxOf(c.box[0], c.box[1], c.box[2], c.box[3]))
    }

    /** Frames a region: the world, a continent. */
    fun showRegion(name: String) {
        _region.value = name
        val w = _world.value ?: return
        if (name == "World") {
            _frame.value = Frame(EqualEarth.boxOf(-180.0, -58.0, 180.0, 84.0))
            return
        }
        val boxes = w.countries.filter { AtlasFacts.inRegion(it, name) && it.box.size == 4 && it.type != "Indeterminate" }
            // Far-off islands and territories would stretch a continent's frame: the big pieces only.
            .filter { it.pop > 500_000 || it.labelRank <= 4 }
            .map { EqualEarth.boxOf(it.box[0], it.box[1], it.box[2], it.box[3]) }
        if (boxes.isEmpty()) return
        _frame.value = Frame(floatArrayOf(boxes.minOf { it[0] }, boxes.minOf { it[1] }, boxes.maxOf { it[2] }, boxes.maxOf { it[3] }))
    }

    fun setPlaces(on: Boolean) {
        _places.value = on
        if (on) viewModelScope.launch { refreshPins() }
    }

    /** Which countries today's edition and your shelf name: worked out with the place finder. */
    private suspend fun refreshPins() {
        val finder = runCatching { repo.finder() }.getOrNull() ?: return
        editions.load()
        shelf.load()
        val paper = editions.latest.value
        val books = shelf.books.value
        withContext(Dispatchers.Default) {
            val stories = HashMap<String, MutableList<String>>()
            paper?.stories?.forEach { s ->
                val text = s.title + "\n" + s.summary + "\n" + s.paras.joinToString("\n") { it.t }
                finder.find(text, 2).forEach { hit -> stories.getOrPut(hit.a3) { mutableListOf() } += s.title }
            }
            val shelved = HashMap<String, MutableList<String>>()
            books.forEach { b ->
                val a3 = b.country.takeIf { it.isNotBlank() }?.let(finder::country) ?: return@forEach
                shelved.getOrPut(a3) { mutableListOf() } += b.title
            }
            storiesBy = stories
            booksBy = shelved
            _pins.value = (stories.keys + shelved.keys).associateWith { Pin(stories[it]?.size ?: 0, shelved[it]?.size ?: 0) }
        }
        _picked.value?.let { describe(it) }
    }

    private suspend fun describe(a3: String) {
        val w = _world.value ?: return
        val c = w.byA3[a3] ?: return
        val i = w.indexOf[a3] ?: return
        if (storiesBy.isEmpty() && booksBy.isEmpty() && !_places.value) runCatching { refreshPinsQuietly() }
        val (others, same) = AtlasFacts.neighbours(c, w.byA3)
        val subs = _regions.value?.takeIf { it.a3 == a3 }?.subdivisions.orEmpty()
        val reading = c.names.zh?.let { zh ->
            runCatching { dictionary.romanize(SHORT_ZH[a3] ?: zh, SpeechLanguage.MANDARIN) }.getOrNull()
        }.orEmpty()
        _info.value = CountryInfo(
            country = c,
            area = AtlasFacts.area(w.outlines[i].areaKm2),
            neighbours = others,
            alsoBorders = same,
            subdivisions = AtlasFacts.subdivisionKinds(subs),
            subdivisionCount = subs.size,
            time = AtlasFacts.time(c.tz, Instant.now()),
            reading = reading,
            stories = storiesBy[a3].orEmpty().distinct(),
            books = booksBy[a3].orEmpty().distinct()
        )
    }

    private var pinsTried = false

    private suspend fun refreshPinsQuietly() {
        if (pinsTried) return
        pinsTried = true
        refreshPins()
    }

    // --- search ---

    fun search(query: String) = viewModelScope.launch {
        val q = query.trim()
        if (q.length < 2) {
            _results.value = emptyList()
            return@launch
        }
        val w = repo.world()
        val subs = repo.subdivisionNames()
        _results.value = withContext(Dispatchers.Default) {
            val f = PlaceFinderKeys.fold(q)
            fun hits(names: List<String>) = names.any { PlaceFinderKeys.fold(it).contains(f) }
            val countries = w.countries.filter { hits(listOf(it.name, it.english) + it.names.all()) }
                .sortedBy { if (PlaceFinderKeys.fold(it.english).startsWith(f)) 0 else 1 }
                .map { AtlasHit(it.a3, it.english, listOf(it.capital?.name, it.subregion).filter { s -> !s.isNullOrBlank() }.joinToString(" · ")) }
            val cities = w.places.filter { hits(listOf(it.name) + it.names.all()) }.sortedBy { it.rank }.take(12)
                .map { AtlasHit(it.a3, it.name, (w.byA3[it.a3]?.english ?: it.a3) + if (it.kind == "capital") " · capital" else "") }
            val regions = subs.withIndex().filter { (_, s) -> hits(listOf(s.name, s.english) + s.names.all()) }.take(20)
                .map { (_, s) -> AtlasHit(s.a3, s.english, listOfNotNull(s.type.ifBlank { null }, w.byA3[s.a3]?.english).joinToString(" · ")) }
            (countries + cities + regions).distinctBy { it.a3 + it.title }.take(40)
        }
    }

    // --- quiz ---

    fun startQuiz(kind: String, region: String = _region.value) = viewModelScope.launch {
        val w = repo.world()
        val lang = prefs.value.labels
        val random = Random(System.currentTimeMillis())
        val questions = when (kind) {
            "capital" -> AtlasQuiz.capitals(w.countries, region, 10, random, lang)
            "subdivision" -> {
                val a3 = _picked.value ?: "CHN"
                if (_picked.value == null) pick("CHN")
                w.byA3[a3]?.let(::frameCountry)
                val r = repo.regions(a3)
                AtlasQuiz.subdivisions(a3, r.subdivisions, 10, random, lang)
            }
            else -> AtlasQuiz.flags(w.countries, region, 10, random, lang)
        }
        _quiz.value = QuizState(kind, region, questions)
        _pickedSub.value = if (kind == "subdivision") questions.firstOrNull()?.subIndex else null
    }

    fun answer(index: Int) {
        val q = _quiz.value ?: return
        val question = q.question ?: return
        if (q.chosen != null) return
        val right = index == question.answer
        _quiz.value = q.copy(chosen = index, right = q.right + if (right) 1 else 0, missed = if (right) q.missed else q.missed + question)
        if (question.kind == "subdivision") _pickedSub.value = question.subIndex
    }

    fun next() {
        val q = _quiz.value ?: return
        _quiz.value = q.copy(at = q.at + 1, chosen = null)
        if (q.kind == "subdivision") _pickedSub.value = q.questions.getOrNull(q.at + 1)?.subIndex
    }

    /** Again, with only the ones missed. */
    fun retryMissed() {
        val q = _quiz.value ?: return
        if (q.missed.isEmpty()) return
        _quiz.value = QuizState(q.kind, q.region, q.missed.shuffled())
        if (q.kind == "subdivision") _pickedSub.value = q.missed.firstOrNull()?.subIndex
    }

    fun endQuiz() {
        _quiz.value = null
        _pickedSub.value = null
    }

    // --- talk ---

    /** A chat about the country with the tutor of its language (or the assistant), its card attached. */
    fun talkAbout(info: CountryInfo) {
        val c = info.country
        val card = buildString {
            append(c.english).append(" (").append(c.type.lowercase()).append(")\n")
            c.capital?.let { append(if (it.seat == "capital") "Capital: " else "Main city: ").append(it.name).append('\n') }
            append("Region: ").append(c.subregion).append(", ").append(c.continent).append('\n')
            AtlasFacts.population(c).takeIf { it.isNotBlank() }?.let { append("Population (Natural Earth estimate): ").append(it).append('\n') }
            append("Area: ").append(info.area).append('\n')
            if (info.neighbours.isNotEmpty()) append("Neighbours by land: ").append(info.neighbours.joinToString(", ") { it.english }).append('\n')
            if (info.subdivisions.isNotBlank()) append("Subdivisions: ").append(info.subdivisions).append('\n')
            append("Names: ").append(listOfNotNull(c.names.zh, c.names.ja, c.names.fr, c.names.de, c.names.es, c.names.it).joinToString(" · ")).append('\n')
            append("\nFrom the Atlas on the tablet (Natural Earth; borders as held on the ground).")
        }
        askInbox.post(
            LaunchRequest.Shared(
                prompt = "Let's talk about ${c.english}. Tell me one thing worth knowing about it, then ask me a question about it.",
                document = attachments.textDocument("Atlas · ${c.english}", card),
                personaId = AtlasFacts.tutor(c.a3)
            )
        )
    }

    companion object {
        /** Everyday Chinese names, for the reading (Natural Earth gives the formal ones). */
        private val SHORT_ZH = mapOf("CHN" to "中国", "TWN" to "台湾", "USA" to "美国", "GBR" to "英国", "KOR" to "韩国", "PRK" to "朝鲜", "RUS" to "俄罗斯")

        fun pathOf(o: Outline): Path = Path().apply {
            fillType = PathFillType.EvenOdd
            for (r in o.rings) {
                if (r.size < 6) continue
                moveTo(r[0], r[1])
                for (i in 1 until r.size / 2) lineTo(r[2 * i], r[2 * i + 1])
                close()
            }
        }
    }
}

/** Folding for search: case and accents ignored. */
object PlaceFinderKeys {
    fun fold(s: String): String = app.localmind.assistant.domain.atlas.PlaceFinder.fold(s)
}
