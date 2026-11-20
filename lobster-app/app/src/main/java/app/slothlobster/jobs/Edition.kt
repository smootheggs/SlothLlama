package app.slothlobster.jobs

import app.slothlobster.web.FeedItem
import app.slothlobster.web.Web
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.text.Normalizer
import java.util.Calendar

/*
 * The edition (1.8): SlothLlama's morning paper. SlothLlama sends a plan (an "edition" order whose
 * text is the JSON below); the Lobster keeps it and, at each of its times, reads the sections'
 * feeds and the language corners' sources and sends the paper back as an "edition" catch, with a
 * plain Markdown copy for anything that can't show the paper. Everything here is plain data: the
 * plan names feeds, searches and pages to read, never anything to run.
 */

/** When an edition is made: "Morning" at 07:00. */
@Serializable
data class EditionTime(val name: String = "Morning", val h: Int = 7, val m: Int = 0)

/**
 * A section: its name, where its stories come from and how many it shows. A source is a feed or
 * page link, "search: words" (a news search), or "top" (the region's top stories).
 */
@Serializable
data class EditionSection(val name: String, val sources: List<String> = emptyList(), val count: Int = 5)

/** A language corner: the day's biggest stories in [lang] ("zh", "yue", "ja", "fr", "de", "es", "it"). */
@Serializable
data class EditionCorner(
    val lang: String,
    val name: String = "",
    val sources: List<String> = emptyList(),
    val count: Int = 1,
    /** Words to prefer ("economy"); stories with them come first. */
    val topic: String = ""
)

/** What SlothLlama asks for. */
@Serializable
data class EditionPlan(
    val format: String = Editions.PLAN_FORMAT,
    val v: Int = 1,
    val on: Boolean = true,
    val times: List<EditionTime> = listOf(EditionTime()),
    val weekends: Boolean = true,
    /** How many top stories: those carried by two or more outlets. */
    val top: Int = 3,
    val sections: List<EditionSection> = emptyList(),
    val corners: List<EditionCorner> = emptyList(),
    /** Stories with these words go first in their section. */
    val lift: List<String> = emptyList(),
    /** Stories with these words are left out. */
    val mute: List<String> = emptyList(),
    /** Make an edition as soon as the plan arrives. */
    val now: Boolean = false,
    val sentAt: Long = 0
)

/** A paragraph of a corner story; [en] is the source's own English line for it, when it gives one. */
@Serializable
data class EdPara(val t: String, val en: String = "")

@Serializable
data class EdStory(
    val title: String,
    val url: String = "",
    val outlet: String = "",
    val at: Long = 0,
    val summary: String = "",
    val meta: String = "",
    /** How many outlets carried it (top stories). */
    val outlets: Int = 1,
    /** The other outlets' names. */
    val also: List<String> = emptyList(),
    /** The story itself, for a language corner. */
    val paras: List<EdPara> = emptyList()
)

@Serializable
data class EdSection(val name: String, val stories: List<EdStory> = emptyList())

@Serializable
data class EdCorner(val lang: String, val name: String, val source: String = "", val stories: List<EdStory> = emptyList())

/** The paper itself: an "edition" catch's file, sloth-express.json. */
@Serializable
data class EditionOut(
    val format: String = Editions.FORMAT,
    val v: Int = 1,
    val id: String,
    /** "Morning", "Evening", or "Edition" for one made by hand. */
    val name: String,
    val made: Long,
    val top: List<EdStory> = emptyList(),
    val sections: List<EdSection> = emptyList(),
    val corners: List<EdCorner> = emptyList(),
    /** What went wrong on the way ("Couldn't read rthk.hk"). */
    val notes: List<String> = emptyList()
)

object Editions {
    const val PLAN_FORMAT = "slothexpress-plan"
    const val FORMAT = "slothexpress-edition"
    const val FILE_NAME = "sloth-express.json"

    val LANGS = setOf("zh", "yue", "ja", "fr", "de", "es", "it", "en")
    private const val MAX_TIMES = 4
    private const val MAX_SECTIONS = 10
    private const val MAX_CORNERS = 8
    private const val MAX_SOURCES = 6
    private const val MAX_WORDS = 30
    const val MAX_PARAS = 14
    const val MAX_STORY_CHARS = 3_500

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(e: EditionOut): String = json.encodeToString(EditionOut.serializer(), e)
    fun encodePlan(p: EditionPlan): String = json.encodeToString(EditionPlan.serializer(), p)

    /** The plan, checked and kept within bounds. Throws if it isn't a plan. */
    fun parsePlan(text: String): EditionPlan {
        val p = json.decodeFromString(EditionPlan.serializer(), text)
        require(p.format == PLAN_FORMAT) { "Not an edition plan" }
        require(p.v == 1) { "A newer edition plan than this SlothLobster reads (v${p.v}); update it" }
        return p.copy(
            times = p.times.take(MAX_TIMES).map {
                EditionTime(name(it.name, "Edition"), it.h.coerceIn(0, 23), it.m.coerceIn(0, 59))
            }.distinctBy { it.name }.ifEmpty { listOf(EditionTime()) },
            top = p.top.coerceIn(0, 6),
            sections = p.sections.take(MAX_SECTIONS).map {
                EditionSection(name(it.name, "News"), sources(it.sources), it.count.coerceIn(1, 12))
            }.filter { it.sources.isNotEmpty() },
            corners = p.corners.take(MAX_CORNERS).filter { it.lang in LANGS }.map {
                EditionCorner(it.lang, name(it.name, it.lang), sources(it.sources), it.count.coerceIn(1, 4), it.topic.trim().take(80))
            }.filter { it.sources.isNotEmpty() },
            lift = words(p.lift),
            mute = words(p.mute)
        )
    }

    private fun name(s: String, fallback: String) = s.replace(Regex("[\\p{Cntrl}]"), "").trim().take(40).ifBlank { fallback }

    private fun words(list: List<String>) = list.map { it.trim().take(40) }.filter { it.isNotBlank() }.distinct().take(MAX_WORDS)

    /** Only sources the Lobster can read: https links, "search: …" and "top". */
    fun sources(list: List<String>): List<String> = list.mapNotNull { raw ->
        val s = raw.trim()
        when {
            s.equals("top", ignoreCase = true) -> "top"
            s.startsWith("search:", ignoreCase = true) -> s.substringAfter(':').trim().take(120).takeIf { it.isNotBlank() }?.let { "search: $it" }
            else -> Web.safeUrl(s)
        }
    }.distinct().take(MAX_SOURCES)

    // --- when ---

    private fun key(day: String, t: EditionTime) = "$day|${t.name}"

    /**
     * The edition due now, if any: the latest time already passed today that hasn't been made.
     * Earlier ones missed today are skipped (one paper, not three in a row after a long sleep).
     */
    fun due(plan: EditionPlan, now: Calendar, made: Collection<String>): EditionTime? {
        if (!plan.on) return null
        val weekday = now.get(Calendar.DAY_OF_WEEK)
        if (!plan.weekends && (weekday == Calendar.SATURDAY || weekday == Calendar.SUNDAY)) return null
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val latest = plan.times.filter { it.h * 60 + it.m <= minutes }.maxByOrNull { it.h * 60 + it.m } ?: return null
        return latest.takeIf { key(Repeats.dayKey(now), it) !in made }
    }

    /** The keys to remember once [time] is made: it and every earlier time today. */
    fun madeKeys(plan: EditionPlan, now: Calendar, time: EditionTime): List<String> {
        val day = Repeats.dayKey(now)
        return plan.times.filter { it.h * 60 + it.m <= time.h * 60 + time.m }.map { key(day, it) }
    }

    // --- choosing stories ---

    private fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun has(item: FeedItem, words: List<String>): Boolean {
        if (words.isEmpty()) return false
        val text = fold(item.title + " " + item.summary)
        return words.any { w ->
            val f = fold(w)
            if (f.any { isCjk(it) }) f in text else Regex("(^|[^\\p{L}\\p{N}])" + Regex.escape(f) + "($|[^\\p{L}\\p{N}])").containsMatchIn(text)
        }
    }

    /** Muted stories out, lifted ones first (each group keeps its order). */
    fun <T> arrange(items: List<T>, lift: List<String>, mute: List<String>, of: (T) -> FeedItem): List<T> {
        val kept = items.filterNot { has(of(it), mute) }
        val (up, rest) = kept.partition { has(of(it), lift) }
        return up + rest
    }

    /** Stories about [topic] first ("economy, trade" or "economy OR trade"). */
    fun <T> preferTopic(items: List<T>, topic: String, of: (T) -> FeedItem): List<T> {
        val words = topic.split(Regex("[,;]|\\s+OR\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        if (words.isEmpty()) return items
        val (on, off) = items.partition { has(of(it), words) }
        return on + off
    }

    /** Several feeds' items taken in turn, so one busy feed doesn't fill a section. */
    fun <T> interleave(lists: List<List<T>>): List<T> {
        val out = ArrayList<T>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) for (l in lists) if (i < l.size) out += l[i]
        return out
    }

    private val STOP = setOf(
        "the", "and", "for", "with", "from", "that", "this", "into", "over", "after", "about", "amid", "says", "said",
        "will", "has", "have", "are", "was", "were", "its", "his", "her", "their", "new", "more", "than", "how",
        "why", "what", "who", "when", "out", "off", "not", "but", "can", "may", "could", "would", "as", "at", "by",
        "of", "on", "in", "to", "a", "an", "is", "be", "it", "up", "us", "vs"
    )

    private fun isCjk(c: Char) = c in '぀'..'ヿ' || c in '㐀'..'䶿' || c in '一'..'鿿' || c in '豈'..'﫿'

    /** A title's telling words; Chinese and Japanese titles by pairs of characters. */
    fun words(title: String): Set<String> {
        val t = fold(title)
        val cjk = t.filter(::isCjk)
        if (cjk.length >= 4) return cjk.windowed(2).toSet()
        return t.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in STOP }
            .map { if (it.length > 4 && it.endsWith("s")) it.dropLast(1) else it }
            .toSet()
    }

    /** Two titles tell the same story when most of the shorter one's words are in the other. */
    fun same(a: Set<String>, b: Set<String>): Boolean {
        val small = minOf(a.size, b.size)
        if (small < 3) return false
        return a.intersect(b).size.toDouble() / small >= 0.6
    }

    /**
     * Stories several outlets carry: groups of [items] (outlet, item) telling the same story,
     * biggest first, then newest. Only groups with two or more different outlets count.
     */
    fun topStories(items: List<Pair<String, FeedItem>>, max: Int): List<List<Pair<String, FeedItem>>> {
        if (max <= 0) return emptyList()
        val keyed = items.map { it to words(it.second.title) }
        val groups = ArrayList<MutableList<Pair<Pair<String, FeedItem>, Set<String>>>>()
        for (k in keyed) {
            val home = groups.firstOrNull { g -> g.any { same(it.second, k.second) } }
            if (home != null) home += k else groups += mutableListOf(k)
        }
        return groups
            .map { g -> g.map { it.first } }
            .filter { g -> g.map { it.first.lowercase() }.distinct().size >= 2 }
            .sortedWith(compareByDescending<List<Pair<String, FeedItem>>> { g -> g.map { it.first.lowercase() }.distinct().size }
                .thenByDescending { g -> g.maxOf { it.second.at } })
            .take(max)
    }

    /** A top story: the best-told version (a summary, the newest), and who else carried it. */
    fun topStory(group: List<Pair<String, FeedItem>>): EdStory {
        val lead = group.maxWith(compareBy<Pair<String, FeedItem>> { if (it.second.summary.isNotBlank()) 1 else 0 }.thenBy { it.second.at })
        val outlets = group.map { it.first }.distinctBy { it.lowercase() }
        return story(lead.first, lead.second).copy(outlets = outlets.size, also = outlets.filterNot { it.equals(lead.first, ignoreCase = true) })
    }

    fun story(outlet: String, item: FeedItem, paras: List<EdPara> = emptyList()) = EdStory(
        title = item.title.take(240),
        url = item.link,
        outlet = outlet.take(60),
        at = item.at,
        summary = item.summary.take(300),
        meta = item.meta.take(80),
        paras = paras
    )

    // --- pages without a feed ---

    /**
     * The stories a listing page links to, for sites without a feed (Easy Mandarin News): links
     * to the same site whose path looks like an article, grouped by their folder; the biggest
     * group is taken as the site's articles, in page order.
     */
    fun listing(html: String, pageUrl: String, max: Int): List<FeedItem> {
        val doc = Jsoup.parse(html, pageUrl)
        val host = Web.site(pageUrl)
        val page = pageUrl.substringBefore('#').substringBefore('?').trimEnd('/')
        val seen = HashSet<String>()
        val found = ArrayList<Pair<String, FeedItem>>()
        for (a in doc.select("a[href]")) {
            val href = Web.safeUrl(a.absUrl("href")) ?: continue
            val clean = href.substringBefore('#')
            if (Web.site(clean) != host || '?' in clean) continue
            val path = clean.substringAfter("://").substringAfter('/', "").trimEnd('/')
            val parts = path.split('/').filter { it.isNotBlank() }
            if (parts.size < 2 || clean.trimEnd('/') == page) continue
            val last = parts.last()
            // An article's address ends in a long name or id, not a short section word.
            if (last.length < 8) continue
            val text = a.text().trim().replace(Regex("\\s+"), " ")
            if (text.length < 6 || !seen.add(clean)) continue
            found += parts.dropLast(1).joinToString("/") to FeedItem(text.take(240), clean)
        }
        val folder = found.groupingBy { it.first }.eachCount().maxByOrNull { it.value }?.key ?: return emptyList()
        return found.filter { it.first == folder }.map { it.second }.take(max)
    }

    // --- the story itself ---

    private val TONE = Regex("[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]")
    private const val ZH_PUNCT = "。！？，、；：“”「」『』（）《》…—"

    private fun isZh(c: Char) = c in '㐀'..'䶿' || c in '一'..'鿿' || c in '豈'..'﫿' || c in ZH_PUNCT

    /**
     * A Chinese line without the Pinyin some learner sites print beside each word ("měi guó美国"):
     * words with tone marks go, and so do short lower-case words written right before a character
     * (the neutral tones: "de的"). Names in Latin letters ("AI", "Nvidia") stay.
     */
    fun withoutPinyin(line: String): String {
        val tokens = Regex("[A-Za-zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜüÜ']+").findAll(line).toList()
        if (tokens.isEmpty()) return line
        val out = StringBuilder()
        var at = 0
        var dropped = false
        for (m in tokens) {
            val w = m.value
            val next = line.getOrNull(m.range.last + 1)
            val glued = next != null && isZh(next) && next !in ZH_PUNCT
            val drop = TONE.containsMatchIn(w) || (glued && w.length <= 6 && w == w.lowercase())
            out.append(line, at, m.range.first)
            if (drop) dropped = true else out.append(w)
            at = m.range.last + 1
        }
        if (!dropped) return line
        out.append(line, at, line.length)
        // The spaces only parted one Pinyin-and-word from the next: Chinese is written without them.
        return out.toString()
            .replace(Regex("\\s+(?=[\\u3400-\\u9FFF。！？，、；：])"), "")
            .replace(Regex("(?<=[\\u3400-\\u9FFF。！？，、；：])\\s+"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    /**
     * A Chinese story as paragraphs, each with the English line the source gives after it (learner
     * sites print a translation under each sentence). Pinyin is taken out; SlothLlama adds its own.
     */
    fun chineseParas(lines: List<String>): List<EdPara> {
        val out = ArrayList<EdPara>()
        var chars = 0
        for (raw in lines) {
            val line = raw.trimStart('#', '-', '>', ' ').trim()
            if (line.isEmpty()) continue
            val zhChars = line.count { it in '㐀'..'鿿' }
            if (zhChars >= 2) {
                val clean = withoutPinyin(line)
                // English sentences between Chinese ones, in a single block: pulled out as the translation.
                val english = ArrayList<String>()
                val chinese = clean.replace(Regex("(?<=[。！？])\\s*([A-Z][^\\u3400-\\u9FFF]{12,}?[.!?])(?=\\s*([\\u3400-\\u9FFF“「]|$))")) { m ->
                    english += m.groupValues[1].trim()
                    ""
                }
                val last = chinese.indexOfLast { isZh(it) }
                val zh = if (last >= 0) chinese.substring(0, last + 1).trim() else chinese.trim()
                val tail = if (last >= 0) chinese.substring(last + 1).trim() else ""
                if (tail.split(' ').size >= 3) english += tail
                if (zh.isBlank()) continue
                out += EdPara(zh.take(600), english.joinToString(" ").take(600))
                chars += zh.length
            } else if (out.isNotEmpty() && out.last().en.isBlank() && line.length >= 12 && zhChars == 0) {
                out[out.lastIndex] = out.last().copy(en = line.take(600))
            }
            if (out.size >= MAX_PARAS || chars >= MAX_STORY_CHARS) break
        }
        return out
    }

    /** A Chinese story's own Chinese title (its first heading), when the listing only gave an English one. */
    fun chineseTitle(lines: List<String>): String? =
        lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.let(::withoutPinyin)?.trim()
            ?.takeIf { t -> t.count { it in '\u3400'..'\u9FFF' } >= 2 }?.take(160)

    /** Any other language's story: its paragraphs, headings and lists as plain lines. */
    fun paras(lines: List<String>): List<EdPara> {
        val out = ArrayList<EdPara>()
        var chars = 0
        for (raw in lines) {
            val line = raw.trimStart('#', '-', '>', ' ').trim()
            if (line.length < 25) continue
            out += EdPara(line.take(900))
            chars += line.length
            if (out.size >= MAX_PARAS || chars >= MAX_STORY_CHARS) break
        }
        return out
    }

    // --- the plain copy ---

    /** Square brackets in a title would break a Markdown link ("[pdf]"): round ones read the same. */
    fun linkText(s: String) = s.replace('[', '(').replace(']', ')')

    /** The edition as Markdown, for the Lobster's own shelf and any SlothLlama that can't show the paper. */
    fun markdown(e: EditionOut, date: String): String = buildString {
        append("# ").append(e.name).append(" edition\n\n_").append(date).append("_\n\n")
        fun line(s: EdStory) {
            append("- ")
            if (s.url.isNotBlank()) append('[').append(linkText(s.title)).append("](").append(s.url).append(')') else append(linkText(s.title))
            val small = listOf(s.outlet, s.meta).filter { it.isNotBlank() }.joinToString(" · ")
            if (small.isNotBlank()) append(" — ").append(small)
            append('\n')
        }
        if (e.top.isNotEmpty()) {
            append("## Top stories\n\n")
            e.top.forEach(::line)
            append('\n')
        }
        e.sections.filter { it.stories.isNotEmpty() }.forEach { s ->
            append("## ").append(s.name).append("\n\n")
            s.stories.forEach(::line)
            append('\n')
        }
        e.corners.filter { it.stories.isNotEmpty() }.forEach { c ->
            append("## ").append(c.name).append("\n\n")
            c.stories.forEach(::line)
            append('\n')
        }
    }.trim()

    /** Every story in the edition. */
    fun stories(e: EditionOut): List<EdStory> = e.top + e.sections.flatMap { it.stories } + e.corners.flatMap { it.stories }
}
