package app.localmind.assistant.domain.briefing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One story in a pasted briefing, as the briefing gave it. [confidence] is its own rating: 3 high, 2 medium, 1 low, 0 none. */
@Serializable
data class BriefStory(
    val rank: Int,
    val headline: String,
    val region: String = "",
    val facts: String = "",
    val implications: String = "",
    val gaps: String = "",
    val confidence: Int = 0,
    val confidenceNote: String = ""
)

/** A briefing pasted from elsewhere (an AI's or anyone's), read on the tablet in parts. */
@Serializable
data class Briefing(
    val id: String,
    val takenAt: Long,
    val source: String = "Grok",
    /** The lines before the stories: its date, cutoff and method, as written. */
    val preface: String = "",
    val stories: List<BriefStory> = emptyList(),
    /** The watchlist's observable facts, one per line. */
    val seen: List<String> = emptyList(),
    /** The watchlist's speculation, kept apart and labelled as such. */
    val guess: String = "",
    /** Anything that didn't fit the template, as written. */
    val rest: String = ""
)

@Serializable
data class BriefingFile(val format: Int = 1, val briefings: List<Briefing> = emptyList())

/**
 * Reads a briefing in the shape of the "global geopolitics and security" template (numbered
 * stories with Verified facts, Implications, Information gaps and Source confidence; then an
 * escalation watchlist of observable facts and labelled speculation). Bold marks and headings are
 * optional. Nothing is guessed: what doesn't fit is kept, as written, at the end.
 */
object Briefings {
    const val KEEP_DAYS = 30
    const val MAX_TEXT = 40_000
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val STORY = Regex("""^\**\s*(\d{1,2})[.)]\s+(.+?)\s*\**$""")
    private val FIELD = Regex("""^[-*•]?\s*\**\s*(?:the\s+)?(verified facts|facts|global implications|implications|why it matters|information gaps|gaps|source confidence|confidence)\s*:?\s*\**\s*:?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val SEEN = Regex("""^\**\s*observable facts?\s*:?\s*\**\s*:?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val GUESS = Regex("""^\**\s*speculation[^:]*:?\s*\**\s*:?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val HEADING = Regex("""^#{1,6}\s+.*$""")
    private val WATCH_HEAD = Regex("""escalation watchlist|watchlist""", RegexOption.IGNORE_CASE)
    private val BULLET = Regex("""^\s*[-*•]\s+(.*)$""")
    private val LEVEL = Regex("""^(high|medium|low)\b\s*(.*)$""", RegexOption.IGNORE_CASE)

    private fun clean(s: String) = s.replace("**", "").replace(Regex("""\s+"""), " ").trim()

    fun parse(text: String, id: String, takenAt: Long, source: String = "Grok"): Briefing {
        val lines = text.replace("\r\n", "\n").take(MAX_TEXT).lines()
        val preface = StringBuilder()
        val rest = StringBuilder()
        val stories = mutableListOf<BriefStory>()
        val seen = mutableListOf<String>()
        val guess = StringBuilder()
        var story: BriefStory? = null
        var field = ""
        var inWatch = false
        var watchPart = ""

        fun closeStory() {
            story?.let { stories += it }
            story = null
            field = ""
        }
        fun addTo(f: String, value: String) {
            val s = story ?: return
            val v = clean(value)
            if (v.isEmpty()) return
            story = when (f) {
                "facts" -> s.copy(facts = (s.facts + " " + v).trim())
                "implications" -> s.copy(implications = (s.implications + " " + v).trim())
                "gaps" -> s.copy(gaps = (s.gaps + " " + v).trim())
                "confidence" -> {
                    val m = LEVEL.find(v)
                    if (m != null && s.confidence == 0) s.copy(
                        confidence = when (m.groupValues[1].lowercase()) { "high" -> 3; "medium" -> 2; else -> 1 },
                        confidenceNote = m.groupValues[2].trim().trim('(', ')', '.', ' ')
                    ) else s.copy(confidenceNote = (s.confidenceNote + " " + v).trim())
                }
                else -> s
            }
        }

        for (raw in lines) {
            val t = raw.trim()
            if (t.isEmpty()) {
                // A paragraph break ends the speculation; what follows is the briefing's own closing.
                if (inWatch && watchPart == "guess" && guess.isNotEmpty()) watchPart = "done"
                continue
            }
            if (HEADING.matches(t)) {
                if (WATCH_HEAD.containsMatchIn(t)) { closeStory(); inWatch = true }
                continue
            }
            if (!inWatch && WATCH_HEAD.containsMatchIn(t) && t.length < 60) { closeStory(); inWatch = true; continue }
            if (inWatch) {
                val seenLine = SEEN.matchEntire(t)
                val guessLine = if (seenLine == null) GUESS.matchEntire(t) else null
                val bullet = BULLET.matchEntire(raw)?.groupValues?.get(1)
                when {
                    seenLine != null -> {
                        watchPart = "seen"
                        clean(seenLine.groupValues[1]).takeIf { it.isNotEmpty() }?.let { seen += it }
                    }
                    guessLine != null -> {
                        watchPart = "guess"
                        guess.append(clean(guessLine.groupValues[1])).append(' ')
                    }
                    watchPart == "seen" && bullet != null -> seen += clean(bullet)
                    watchPart == "guess" -> guess.append(clean(t)).append(' ')
                    else -> {
                        watchPart = "done"
                        rest.appendLine(clean(t))
                    }
                }
                continue
            }
            val s = STORY.matchEntire(t)
            if (s != null && FIELD.matchEntire(t) == null) {
                closeStory()
                val whole = clean(s.groupValues[2])
                val region = Regex("""\(([^()]*)\)\s*$""").find(whole)?.groupValues?.get(1).orEmpty()
                story = BriefStory(s.groupValues[1].toInt(), whole.removeSuffix("($region)").trim(), region.trim())
                continue
            }
            val f = FIELD.matchEntire(t)
            if (f != null && story != null) {
                field = when (f.groupValues[1].lowercase()) {
                    "verified facts", "facts" -> "facts"
                    "global implications", "implications", "why it matters" -> "implications"
                    "information gaps", "gaps" -> "gaps"
                    else -> "confidence"
                }
                addTo(field, f.groupValues[2])
                continue
            }
            when {
                story != null && field.isNotEmpty() -> addTo(field, t)
                stories.isEmpty() && story == null -> preface.appendLine(clean(t))
                else -> rest.appendLine(clean(t))
            }
        }
        closeStory()
        return Briefing(
            id = id, takenAt = takenAt, source = source, preface = preface.toString().trim(),
            stories = stories, seen = seen, guess = guess.toString().trim(), rest = rest.toString().trim()
        )
    }

    /** A story as plain text, for a chat or the Lobster. */
    fun asText(s: BriefStory, source: String): String = buildString {
        appendLine("${s.headline}${if (s.region.isNotBlank()) " (${s.region})" else ""}")
        appendLine("From a pasted $source briefing; not checked on the tablet.")
        if (s.facts.isNotBlank()) appendLine("What it says happened: ${s.facts}")
        if (s.implications.isNotBlank()) appendLine("Why it matters, in its view: ${s.implications}")
        if (s.gaps.isNotBlank()) appendLine("What's unclear: ${s.gaps}")
    }.trim()

    /** Briefings newer than [KEEP_DAYS], newest first; a new one goes on top. */
    fun kept(list: List<Briefing>, now: Long): List<Briefing> =
        list.filter { now - it.takenAt < KEEP_DAYS * 86_400_000L }.sortedByDescending { it.takenAt }

    fun encode(list: List<Briefing>): String = json.encodeToString(BriefingFile.serializer(), BriefingFile(briefings = list))
    fun decode(text: String): List<Briefing> = json.decodeFromString(BriefingFile.serializer(), text).briefings
    fun merge(mine: List<Briefing>, theirs: List<Briefing>): List<Briefing> {
        val have = mine.map { it.id }.toSet()
        return mine + theirs.filter { it.id !in have }
    }
}
