package app.localmind.assistant.domain.edition

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The edition (1.32): the morning paper the SlothLobster makes from a plan SlothLlama sends it.
 * The plan and the paper are the same JSON as the Lobster's jobs/Edition.kt (keep them in step;
 * docs/LOBSTER_MAILBOX.md has the format). The paper is data: shown and read aloud, never run.
 */

@Serializable
data class EditionTime(val name: String = "Morning", val h: Int = 7, val m: Int = 0)

/** A source is a feed or page link, "search: words" (a news search) or "top" (the region's top stories). */
@Serializable
data class EditionSection(val name: String, val sources: List<String> = emptyList(), val count: Int = 5)

@Serializable
data class EditionCorner(
    val lang: String,
    val name: String = "",
    val sources: List<String> = emptyList(),
    val count: Int = 1,
    val topic: String = "",
    /** Kept on the tablet only: a corner switched off isn't sent. */
    val on: Boolean = true
)

/** What the Lobster is asked for. */
@Serializable
data class EditionPlan(
    val format: String = Editions.PLAN_FORMAT,
    val v: Int = 1,
    val on: Boolean = true,
    val times: List<EditionTime> = listOf(EditionTime()),
    val weekends: Boolean = true,
    val top: Int = 3,
    val sections: List<EditionSection> = emptyList(),
    val corners: List<EditionCorner> = emptyList(),
    val lift: List<String> = emptyList(),
    val mute: List<String> = emptyList(),
    val now: Boolean = false,
    val sentAt: Long = 0
)

@Serializable
data class EdPara(
    val t: String,
    val en: String = "",
    /** A line the page put around the article (a link to another story, a date, a caption): kept, folded in the reader. */
    val aside: Boolean = false
)

@Serializable
data class EdStory(
    val title: String,
    val url: String = "",
    val outlet: String = "",
    val at: Long = 0,
    val summary: String = "",
    val meta: String = "",
    val outlets: Int = 1,
    val also: List<String> = emptyList(),
    /** A language corner's story, or (Lobster 1.11) any story read clean with the paper. */
    val paras: List<EdPara> = emptyList(),
    /** The story's lead picture as the Lobster sent it (base64 JPEG); moved to a file when kept. */
    val pic: String = "",
    /** Why there's no clean text ("Google News links open only in a browser"), when there isn't. */
    val pageOnly: String = ""
)

@Serializable
data class EdSection(val name: String, val stories: List<EdStory> = emptyList())

@Serializable
data class EdCorner(val lang: String, val name: String, val source: String = "", val stories: List<EdStory> = emptyList())

/** The paper as the Lobster sends it (sloth-express.json). */
@Serializable
data class EditionOut(
    val format: String = Editions.FORMAT,
    val v: Int = 1,
    val id: String,
    val name: String,
    val made: Long,
    val top: List<EdStory> = emptyList(),
    val sections: List<EdSection> = emptyList(),
    val corners: List<EdCorner> = emptyList(),
    val notes: List<String> = emptyList()
)

/** A desk persona's notes on an edition: written by the model in a chat, kept here when you choose. */
@Serializable
data class DeskNote(val personaId: String, val text: String, val at: Long)

/** An edition as kept on the tablet: the paper, what you've read, and the desk's notes. */
@Serializable
data class SavedEdition(
    val edition: EditionOut,
    /** Links (or titles) of stories opened or ticked as read. */
    val read: List<String> = emptyList(),
    val desk: List<DeskNote> = emptyList(),
    val received: Long = 0,
    /** Each story's lead picture, by story key: a file kept beside the paper (editions/pics). */
    val pics: Map<String, String> = emptyMap()
) {
    val stories: List<EdStory> get() = Editions.stories(edition)
    val readCount: Int get() = stories.count { Editions.storyKey(it) in read }
}

/** The edition's settings: the plan, plus what stays on the tablet (who sits on the desk). */
@Serializable
data class EditionSetup(
    val plan: EditionPlan = Editions.defaultPlan(),
    /** "quick", "standard" or "long": the section sizes last picked. */
    val length: String = "standard",
    /** Personas whose desk notes the edition offers (the Trader to start with). */
    val desk: List<String> = listOf(Editions.TRADER_ID),
    /** When the plan was last put in the mailbox, and when the Lobster said it had it. */
    val sentAt: Long = 0,
    val acceptedAt: Long = 0,
    val changedAt: Long = 0,
    /**
     * Outlets (as the paper names them, lower case) whose stories a tap opens on their page (1.45).
     * Every other story with clean text opens clean; one without opens its page.
     */
    val tapPage: List<String> = emptyList()
) {
    /** Changed since the Lobster last took it. */
    val unsent: Boolean get() = changedAt > sentAt
}

/** A language a corner can be in, its tutor, and the help the edition gives with it. */
enum class CornerLanguage(val code: String, val label: String, val badge: String, val partnerId: String, val locale: String) {
    MANDARIN("zh", "Mandarin", "普", "persona-mandarin-partner", "zh-CN"),
    CANTONESE("yue", "Cantonese", "粵", "persona-cantonese-partner", "zh-HK"),
    JAPANESE("ja", "Japanese", "日", "persona-japanese-partner", "ja-JP"),
    FRENCH("fr", "French", "Fr", "persona-french-partner", "fr-FR"),
    GERMAN("de", "German", "De", "persona-german-partner", "de-DE"),
    SPANISH("es", "Spanish", "Es", "persona-spanish-partner", "es-ES"),
    ITALIAN("it", "Italian", "It", "persona-italian-partner", "it-IT");

    val isChinese: Boolean get() = this == MANDARIN || this == CANTONESE

    /** What the corner adds to the story, said plainly in Settings. */
    val help: String
        get() = when (this) {
            MANDARIN -> "Pinyin, tap a word, key words by HSK level"
            CANTONESE -> "Jyutping, tap a word, key words"
            FRENCH -> "Key words with their gender"
            else -> "The story, and its tutor to talk it over with"
        }

    companion object {
        fun of(code: String): CornerLanguage? = entries.firstOrNull { it.code == code }
    }
}

/** One price on the markets strip, worked out from SlothDesk's prices. */
data class MarketTile(val name: String, val value: String, val change: String, val up: Boolean?, val asOf: String, val label: String)

/** A word worth learning in a corner story: computed from the dictionary and word lists, not written by a model. */
data class KeyWord(
    val word: String,
    val reading: String,
    val meaning: String,
    /** "HSK 4", "f.", "not in HSK"… */
    val note: String,
    val known: Boolean = false
)

object Editions {

    /**
     * An edition's name as people read it (1.50.3): "Morning edition"; a paper made on demand
     * (the Lobster names it "Edition") is the "Extra edition"; a name that already ends in
     * "edition" isn't given the word twice.
     */
    fun label(name: String): String {
        val n = name.trim()
        return when {
            n.isEmpty() || n.equals("edition", ignoreCase = true) -> "Extra edition"
            n.endsWith(" edition", ignoreCase = true) -> n
            else -> "$n edition"
        }
    }
    /** (1.54.2) Minutes after an edition's time before the Express says it's late. */
    const val LATE_AFTER_MIN = 30L

    /**
     * Today's edition that's late (1.54.2): the latest of today's times passed [LATE_AFTER_MIN]
     * minutes ago with no paper made since it ([newestMade], 0 for none). Null when the plan is off
     * or not sent yet, on a weekend for a weekdays plan, or when a paper (an extra one too) came.
     */
    fun late(setup: EditionSetup, now: ZonedDateTime, newestMade: Long): EditionTime? {
        val plan = setup.plan
        if (setup.sentAt == 0L || !plan.on) return null
        if (!plan.weekends && (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY)) return null
        val (time, at) = plan.times
            .map { it to now.toLocalDate().atTime(it.h.coerceIn(0, 23), it.m.coerceIn(0, 59)).atZone(now.zone) }
            .filter { (_, at) -> !at.plusMinutes(LATE_AFTER_MIN).isAfter(now) }
            .maxByOrNull { it.second } ?: return null
        return time.takeIf { newestMade < at.toInstant().toEpochMilli() }
    }

    /** The Lobster's word on a late edition: one plain line of a sane length, or null. */
    fun lobsterNote(raw: String?): String? =
        raw?.replace(Regex("[\\p{Cntrl}\\s]+"), " ")?.trim()?.take(200)?.takeIf { it.isNotEmpty() }

    const val PLAN_FORMAT = "slothexpress-plan"
    const val FORMAT = "slothexpress-edition"
    const val FILE_NAME = "sloth-express.json"
    const val TRADER_ID = "persona-trader"
    /** The Lobster takes orders of 4,000 characters at most. */
    const val MAX_PLAN_CHARS = 3_900

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parse(text: String): EditionOut {
        val e = json.decodeFromString(EditionOut.serializer(), text)
        require(e.format == FORMAT) { "Not an edition" }
        require(e.v == 1) { "A newer edition than this SlothLlama reads (v${e.v})" }
        return tidy(e)
    }

    /** Within bounds, so a damaged or odd file can't swamp the page. */
    private fun tidy(e: EditionOut): EditionOut {
        fun s(x: EdStory) = x.copy(
            title = x.title.take(240), summary = x.summary.take(400), outlet = x.outlet.take(60), meta = x.meta.take(80),
            also = x.also.take(8).map { it.take(60) },
            paras = x.paras.take(20).map { EdPara(it.t.take(1_200), it.en.take(1_200), it.aside) },
            url = x.url.takeIf { it.startsWith("https://") }.orEmpty().take(2_000),
            pic = "", pageOnly = ""
        )
        // A news story read clean: a whole article's worth, its picture, or why it's page only.
        fun newsStory(x: EdStory) = s(x).copy(
            paras = cleanParas(x.paras),
            pic = x.pic.take(MAX_PIC_CHARS).takeIf { it.length < MAX_PIC_CHARS }.orEmpty(),
            pageOnly = x.pageOnly.replace(Regex("[\\p{Cntrl}]"), " ").take(200)
        )
        return e.copy(
            name = e.name.take(40),
            top = e.top.take(8).map(::newsStory),
            sections = e.sections.take(12).map { sec -> EdSection(sec.name.take(40), sec.stories.take(15).map(::newsStory)) },
            corners = e.corners.take(10).map { c -> EdCorner(c.lang.take(4), c.name.take(40), c.source.take(60), c.stories.take(5).map(::s)) },
            notes = e.notes.take(20).map { it.take(200) }
        )
    }

    fun encodeSaved(s: SavedEdition): String = json.encodeToString(SavedEdition.serializer(), s)
    fun parseSaved(text: String): SavedEdition = json.decodeFromString(SavedEdition.serializer(), text)
    fun encodeSetup(s: EditionSetup): String = json.encodeToString(EditionSetup.serializer(), s)
    fun parseSetup(text: String): EditionSetup = json.decodeFromString(EditionSetup.serializer(), text)

    /**
     * The order text: the plan as JSON, without the corners switched off. Null when it's too
     * long for one order.
     */
    fun planOrder(setup: EditionSetup, now: Boolean, at: Long): String? {
        val plan = setup.plan.copy(
            corners = setup.plan.corners.filter { it.on }.map { it.copy(on = true) },
            now = now,
            sentAt = at
        )
        val text = json.encodeToString(EditionPlan.serializer(), plan)
        return text.takeIf { it.length <= MAX_PLAN_CHARS }
    }

    fun stories(e: EditionOut): List<EdStory> = e.top + e.sections.flatMap { it.stories } + e.corners.flatMap { it.stories }

    /** The paper's motto, under its name on the masthead and on Home. */
    const val MOTTO = "Slowly digesting a rapidly changing world"

    fun storyKey(s: EdStory): String = s.url.ifBlank { s.title }.trim().lowercase().take(300)

    // --- reading a story (1.43) ---

    /** A clean story's paragraphs within bounds: 120 at most, 24,000 characters in all. */
    const val MAX_CLEAN_PARAS = 120
    const val MAX_CLEAN_CHARS = 24_000
    /** A lead picture as sent: a 720-pixel JPEG is well under this in base64. */
    const val MAX_PIC_CHARS = 600_000

    /** The page's own lines around the article (1.51.1): an allowance of their own, so they never crowd it out. */
    const val MAX_ASIDES = 60
    const val MAX_ASIDE_CHARS = 6_000

    fun cleanParas(paras: List<EdPara>): List<EdPara> {
        var left = MAX_CLEAN_CHARS
        var count = 0
        var asideLeft = MAX_ASIDE_CHARS
        var asides = 0
        return paras.mapNotNull { p ->
            if (p.aside) {
                if (asides >= MAX_ASIDES || asideLeft <= 0) return@mapNotNull null
                val t = p.t.take(minOf(1_200, asideLeft))
                asideLeft -= t.length
                asides++
                return@mapNotNull EdPara(t, aside = true)
            }
            if (left <= 0 || count >= MAX_CLEAN_PARAS) return@mapNotNull null
            val t = p.t.take(minOf(4_000, left))
            left -= t.length
            count++
            EdPara(t)
        }.filter { it.t.isNotBlank() }
    }

    /** Whether a news story came with its own text to read here. */
    fun hasClean(s: EdStory): Boolean = s.paras.isNotEmpty() && s.pageOnly.isBlank()

    /**
     * The clean story as Markdown for the reader: its paragraphs, headings and lists as the Lobster kept them.
     * The page's own lines around the article (links, dates, captions) come marked with [ASIDE_MARK], so the
     * reader folds them away without losing them; [asides] false leaves them out, for chats and reading time.
     */
    fun cleanText(s: EdStory, asides: Boolean = true): String = s.paras
        .filter { asides || !it.aside }
        .map { p ->
            // The mark is ours alone: a page can't fold its own article text by writing it.
            val t = p.t.replace(ASIDE_TOKEN, "").trim()
            if (p.aside) ASIDE_MARK + t.replace(Regex("\\s*\\n\\s*"), " ") else t
        }
        .filter { it.isNotBlank() && it != ASIDE_MARK }
        .joinToString("\n\n")

    /** Starts a line the page put around the article, in a clean text. */
    const val ASIDE_TOKEN = "%%aside%%"
    const val ASIDE_MARK = "$ASIDE_TOKEN "

    /** A text without the page's lines around the article: what goes to a chat, the library or a reading time. */
    fun withoutAsides(text: String): String =
        if (ASIDE_TOKEN !in text) text
        else text.lineSequence().filterNot { it.trimStart().startsWith(ASIDE_TOKEN) }.joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n").trim()

    /** Whether a tap on this story reads it clean: whenever the text is here, unless its outlet is set to the page. */
    fun tapsClean(setup: EditionSetup, s: EdStory): Boolean =
        hasClean(s) && s.outlet.trim().lowercase() !in setup.tapPage

    /** The setup with this outlet's taps reading clean (the default), or opening the page. */
    fun withTap(setup: EditionSetup, outlet: String, clean: Boolean): EditionSetup {
        val o = outlet.trim().lowercase().take(60)
        if (o.isEmpty()) return setup
        val next = if (clean) setup.tapPage - o else (setup.tapPage + o).distinct().takeLast(MAX_TAP_OUTLETS)
        return setup.copy(tapPage = next)
    }

    private const val MAX_TAP_OUTLETS = 100

    /** The news stories (top and sections; not the language corners, which open their own page). */
    fun news(e: EditionOut): List<EdStory> = e.top + e.sections.flatMap { it.stories }

    /** A news story in the paper by its link (as the Lobster sent it, or as it was handed back). */
    fun findNews(e: EditionOut, url: String): EdStory? {
        val k = url.trim().lowercase()
        if (k.isEmpty()) return null
        return news(e).firstOrNull { it.url.trim().lowercase() == k }
    }

    // --- the default plan ---

    private const val HN = "https://hnrss.org/frontpage"

    /** Section sizes for Quick, Standard and Long. */
    fun sizes(length: String): Int = when (length) {
        "quick" -> 3
        "long" -> 8
        else -> 5
    }

    fun defaultPlan(): EditionPlan = EditionPlan(
        times = listOf(EditionTime("Morning", 7, 0)),
        top = 3,
        sections = listOf(
            EditionSection("World", listOf("https://feeds.bbci.co.uk/news/world/rss.xml", "https://feeds.npr.org/1001/rss.xml"), 5),
            EditionSection("Markets", listOf("search: gold OR silver OR LBMA"), 4),
            EditionSection("Asia", listOf("search: Hong Kong OR China economy"), 4),
            EditionSection("Tech", listOf(HN), 5)
        ),
        corners = defaultCorners()
    )

    /** One corner per tutor language. Each falls back to Google News's top stories for its region. */
    fun defaultCorners(): List<EditionCorner> = listOf(
        EditionCorner("zh", "Mandarin", listOf("https://easymandarin.news/en", "top"), 2),
        EditionCorner("yue", "Cantonese", listOf("top"), 1),
        EditionCorner("ja", "Japanese", listOf("https://www3.nhk.or.jp/rss/news/cat0.xml", "top"), 1),
        EditionCorner("fr", "French", listOf("https://www.rfi.fr/fr/rss", "top"), 2),
        EditionCorner("de", "German", listOf("https://www.tagesschau.de/index~rss2.xml", "top"), 1),
        EditionCorner("es", "Spanish", listOf("https://feeds.bbci.co.uk/mundo/rss.xml", "top"), 1),
        EditionCorner("it", "Italian", listOf("https://www.ansa.it/sito/ansait_rss.xml", "top"), 1, on = false)
    )

    /** Every section set to the length's size. */
    fun withLength(plan: EditionPlan, length: String): EditionPlan =
        plan.copy(sections = plan.sections.map { it.copy(count = sizes(length)) }, top = if (length == "quick") 2 else 3)

    /**
     * A source as typed: a link, "search: words", "top", or plain words (taken as a search).
     * Null for nothing usable.
     */
    fun source(text: String): String? {
        val t = text.trim()
        return when {
            t.isEmpty() -> null
            t.equals("top", ignoreCase = true) -> "top"
            t.startsWith("search:", ignoreCase = true) -> t.substringAfter(':').trim().takeIf { it.isNotEmpty() }?.let { "search: ${it.take(120)}" }
            t.startsWith("https://", ignoreCase = true) -> t.take(500)
            t.startsWith("http://", ignoreCase = true) -> "https://" + t.substring(7).take(500)
            ' ' !in t && '.' in t && t.length <= 200 -> "https://$t"
            else -> "search: ${t.take(120)}"
        }
    }

    /** A source shown in a line: the site's name, or the search. */
    fun sourceLabel(source: String): String = when {
        source == "top" -> "Top stories (Google News)"
        source.startsWith("search:") -> "News search: \"" + source.substringAfter(':').trim() + "\""
        else -> source.substringAfter("://").substringBefore('/').removePrefix("www.").removePrefix("feeds.")
    }

    /** Swaps two items (tap one, then another). */
    fun <T> swap(list: List<T>, a: Int, b: Int): List<T> {
        if (a !in list.indices || b !in list.indices || a == b) return list
        val out = list.toMutableList()
        val x = out[a]
        out[a] = out[b]
        out[b] = x
        return out
    }

    // --- words on the page ---

    /** "35 min", "2 h", "yesterday", "3 days"; "" when the time isn't known. */
    fun ago(at: Long, now: Long): String {
        if (at <= 0) return ""
        val minutes = ((now - at) / 60_000).coerceAtLeast(0)
        return when {
            minutes < 1 -> "now"
            minutes < 60 -> "$minutes min"
            minutes < 24 * 60 -> "${minutes / 60} h"
            minutes < 48 * 60 -> "yesterday"
            else -> "${minutes / (24 * 60)} days"
        }
    }

    /** The small print under a story: "The Guardian · 3 outlets · 2 h", or "60 points · 12 comments". */
    fun smallPrint(s: EdStory, now: Long): String = listOf(
        s.outlet,
        s.meta,
        if (s.outlets >= 2) "${s.outlets} outlets" else "",
        ago(s.at, now)
    ).filter { it.isNotBlank() }.joinToString(" · ")

    /** The masthead's line: "MORNING EDITION · FRIDAY 20 NOVEMBER 2026 · 24 STORIES · 9 MIN". */
    fun masthead(e: EditionOut, zone: ZoneId, listenMinutes: Int): String {
        val day = Instant.ofEpochMilli(e.made).atZone(zone).format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH))
        val parts = listOf(label(e.name), day, "${stories(e).size} stories") + if (listenMinutes > 0) listOf("$listenMinutes min") else emptyList()
        return parts.joinToString(" · ").uppercase(Locale.ENGLISH)
    }

    /** What reading it aloud would say, block by block, each with its language ("" = the tablet's). */
    fun listen(e: EditionOut): List<Pair<String, String>> = buildList {
        add("The ${e.name.lowercase(Locale.ENGLISH)} edition." to "")
        if (e.top.isNotEmpty()) {
            add("Top stories." to "")
            e.top.forEach { add(spoken(it) to "") }
        }
        e.sections.filter { it.stories.isNotEmpty() }.forEach { s ->
            add("${s.name}." to "")
            s.stories.forEach { add(spoken(it) to "") }
        }
        e.corners.filter { it.stories.isNotEmpty() }.forEach { c ->
            add("${c.name} corner." to "")
            c.stories.forEach { add(it.title to c.lang) }
        }
    }

    private fun spoken(s: EdStory): String = s.title.trimEnd('.') + "." + if (s.summary.isNotBlank()) " " + s.summary else ""

    /** About how long the listen version runs, at a news reader's 150 words a minute. */
    fun listenMinutes(e: EditionOut): Int {
        val blocks = listen(e)
        val words = blocks.sumOf { (t, lang) -> if (lang == "zh" || lang == "yue" || lang == "ja") t.length / 3 else t.split(' ').size }
        return maxOf(1, (words + 149) / 150)
    }

    // --- the desk ---

    /** Stories a desk reads: those of sections named like markets, money or Asia, and the top stories. */
    fun deskStories(e: EditionOut): List<EdStory> {
        val wanted = Regex("(?i)market|money|business|econom|metal|gold|asia|finance|trade|crypto|stock")
        val picked = e.sections.filter { wanted.containsMatchIn(it.name) }.flatMap { it.stories }
        return (e.top + picked).distinctBy(::storyKey).take(14)
    }

    /** The card a desk persona is given: today's prices (worked out on the tablet) and the headlines. */
    fun deskCard(e: EditionOut, tiles: List<MarketTile>): String = buildString {
        append("The ").append(label(e.name).lowercase(Locale.ENGLISH)).append("'s markets and headlines.\n\n")
        if (tiles.isNotEmpty()) {
            append("Prices (SlothDesk's latest data, worked out on the tablet):\n")
            tiles.forEach { append("- ").append(it.name).append(": ").append(it.value).append(' ').append(it.change).append(" (").append(it.asOf).append(")\n") }
            append('\n')
        }
        append("Headlines (titles and the feeds' one-line summaries only, not the articles):\n")
        deskStories(e).forEachIndexed { i, s ->
            append(i + 1).append(". ").append(s.title)
            if (s.outlet.isNotBlank()) append(" (").append(s.outlet).append(')')
            if (s.summary.isNotBlank()) append(" — ").append(s.summary)
            append('\n')
        }
    }.trim()

    fun deskPrompt(e: EditionOut): String =
        "From this card only: in three short numbered lines, what would your desk watch today, and why? " +
            "Name the headline or price each line comes from. These are headlines, not articles: say so where it matters. Not advice."

    // --- the markets strip ---

    /** Name, how to show the value, and its label, for the series the strip shows (in order). */
    private val STRIP = listOf("GLD" to "GLD", "SLV" to "SLV", "USDJPY" to "USDJPY", "DTB3" to "US 3M", "GVZ" to "Gold vol")

    /**
     * Up to four tiles from SlothDesk's price series: the last close and the move since the
     * close before (percent for prices, basis points for rates, points for volatility). [series] maps a
     * name to (kind, dates, closes or values).
     */
    fun marketTiles(series: Map<String, Triple<String, List<String>, List<Double>>>): List<MarketTile> =
        STRIP.mapNotNull { (name, label) ->
            val (kind, dates, values) = series[name] ?: return@mapNotNull null
            if (values.size < 2 || dates.size != values.size) return@mapNotNull null
            val last = values.last()
            val before = values[values.size - 2]
            if (!last.isFinite() || !before.isFinite()) return@mapNotNull null
            when (kind) {
                "bars" -> {
                    val pct = (last / before - 1) * 100
                    MarketTile(
                        label, "%.2f".format(Locale.ENGLISH, last),
                        "%+.2f%%".format(Locale.ENGLISH, pct), if (pct == 0.0) null else pct > 0, dates.last(), "close"
                    )
                }
                "vol" -> {
                    // A volatility index moves in points, not basis points.
                    val pts = (last - before) * 100
                    MarketTile(
                        label, "%.1f".format(Locale.ENGLISH, last * 100), "%+.1f pts".format(Locale.ENGLISH, pts),
                        if (pts == 0.0) null else pts > 0, dates.last(), "index"
                    )
                }
                else -> {
                    val bp = (last - before) * 10_000
                    MarketTile(
                        label, "%.2f%%".format(Locale.ENGLISH, last * 100), "%+.0f bp".format(Locale.ENGLISH, bp),
                        if (bp == 0.0) null else bp > 0, dates.last(), "rate"
                    )
                }
            }
        }.take(4)

    // --- key words ---

    /** The HSK level a tutor level reads without help: words above it are key words. */
    fun hskCutoff(level: String): Int = when (level) {
        "BEGINNER" -> 1
        "ELEMENTARY" -> 2
        "INTERMEDIATE" -> 4
        "ADVANCED" -> 5
        else -> 6
    }

    /** The frequency rank a tutor level knows up to, for French. */
    fun rankCutoff(level: String): Int = when (level) {
        "BEGINNER" -> 300
        "ELEMENTARY" -> 800
        "INTERMEDIATE" -> 2_000
        "ADVANCED" -> 4_000
        else -> 8_000
    }

    /**
     * A Chinese story's key words: words of two characters or more with an HSK level above
     * [cutoff] (or no level, when the dictionary knows them), most used in the story first, then
     * the harder ones. [words] are (word, reading, meaning) as the dictionary split the text.
     */
    fun chineseKeyWords(
        words: List<Triple<String, String, String>>,
        hsk: Map<String, Int>,
        cutoff: Int,
        known: Set<String>,
        max: Int = 6
    ): List<KeyWord> {
        val counts = words.groupingBy { it.first }.eachCount()
        return words.asSequence()
            // Names (a capital in the reading: 香港 Xiānggǎng) aren't vocabulary.
            .filter { it.first.length >= 2 && it.third.isNotBlank() && it.second.firstOrNull()?.isUpperCase() != true }
            .filter { (hsk[it.first] ?: 99) > cutoff }
            .distinctBy { it.first }
            .sortedWith(compareByDescending<Triple<String, String, String>> { counts[it.first] ?: 0 }.thenBy { hsk[it.first] ?: 99 })
            .take(max)
            .map { (w, r, m) -> KeyWord(w, r, m, hsk[w]?.let { "HSK $it" } ?: "beyond HSK", w in known) }
            .toList()
    }

    /** A four-character idiom (成语) in the story, if the dictionary marks one. */
    fun idiom(words: List<Triple<String, String, String>>): Triple<String, String, String>? =
        words.firstOrNull { it.first.length == 4 && it.third.contains("(idiom", ignoreCase = true) }

    /** A French story's words, folded for looking up: "l'économie" → "economie". */
    fun frenchTokens(text: String): List<String> =
        text.replace('’', '\'').split(Regex("[^\\p{L}'-]+"))
            .map { it.substringAfter('\'').trim('-') }
            .filter { it.length >= 3 }
}
