package app.localmind.assistant.ui.lobster

import app.localmind.assistant.data.lobster.OrderKind

/**
 * How to ask the Lobster, shown only when "How to ask" is opened. The Lobster has no model: it
 * runs web searches, reads pages and brings back their text, and SlothLlama does the thinking
 * offline. So an order works like a good search, not like a chat message.
 */
object LobsterTips {

    const val HOW_IT_WORKS =
        "The Lobster has no AI of its own. It runs web searches, reads the pages it finds and brings " +
            "their text back; SlothLlama reads that and answers, offline. So write orders like searches: " +
            "names, places, years and key words, not \"please\" or \"tell me about\"."

    data class Tip(val rule: String, val examples: List<String>)

    fun forKind(kind: OrderKind): Tip = when (kind) {
        OrderKind.RESEARCH -> Tip(
            "First line: the question, plainly. Then up to four searches on new lines, each a few key words " +
                "seen from a different side (the source, a year, the opposite view). Deep goes wider, overnight.",
            listOf(
                "How did NBA rule changes since 2000 change scoring?\nNBA rule changes 2001 2004 hand-checking\n" +
                    "NBA pace and scoring trends since 2000\nNBA three-point rate history",
                "Is GLD or physical gold cheaper to hold over 10 years?\nGLD expense ratio tracking difference\n" +
                    "physical gold storage insurance costs per year"
            )
        )
        OrderKind.FETCH -> Tip(
            "One link per order. Pages come back as clean text; PDFs and EPUBs as files for the Library.",
            listOf("https://en.wikipedia.org/wiki/Gold_as_an_investment")
        )
        OrderKind.WATCH -> Tip(
            "A link to check for changes, with words to look for after it; or a few words for a news topic. " +
                "Narrow words mean fewer, better alerts.",
            listOf(
                "https://www.example.com/careers senior product manager, Hong Kong",
                "Hong Kong Monetary Authority interest rate decision"
            )
        )
        OrderKind.BRIEF -> Tip(
            "Two to four key words for one subject. \"Updates\", \"top news\" and \"etc.\" only narrow the search. " +
                "For several subjects, send one brief each, or add feeds in the Lobster (Settings → Feeds, # groups). " +
                "Add \"every morning at 7\" to make it daily.",
            listOf("gold silver prices every morning at 7", "Hong Kong economy", "Formula 1")
        )
        else -> Tip("", emptyList())
    }

    /** A chat prompt that asks SlothLlama to turn a rough idea into a good order. */
    fun draftPrompt(kind: OrderKind, idea: String): String =
        "Help me write an order for the SlothLobster, a helper app with no AI of its own: it runs web searches " +
            "(DuckDuckGo, Bing, Wikipedia), reads the pages it finds and brings their text back for you to read.\n\n" +
            "Write a ${kind.label} order for what I want, in this shape and nothing else:\n" +
            when (kind) {
                OrderKind.RESEARCH -> "- first line: the question, plainly\n- then 2 to 4 lines, each a short web search " +
                    "(3 to 7 key words) from a different angle: sources, years, names, the opposing view\n"
                OrderKind.BRIEF -> "- one line: 2 to 4 key words for the news search (no \"updates\" or \"news\")\n"
                OrderKind.WATCH -> "- one line: the page's link, then the words to look for; or 2 to 5 key words for a news topic\n"
                else -> "- one line\n"
            } +
            "\nWhat I want: " + idea.trim().ifBlank { "(describe it here)" }
}

/**
 * A catch's text as a plain preview: no Markdown marks, no heading that repeats the title,
 * links shown by their words.
 */
fun plainPreview(markdown: String, title: String): String {
    val lines = markdown.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return lines
        .filterIndexed { i, l -> !(i == 0 && l.trimStart('#', ' ').equals(title.trim(), ignoreCase = true)) }
        .map { l ->
            l.replace(Regex("^#{1,6}\\s+"), "")
                .replace(Regex("^[-*•]\\s+"), "· ")
                .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
                .replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")
                .replace(Regex("(?<![\\w*])[_*](\\S(?:.*?\\S)?)[_*](?![\\w*])"), "$1")
                .replace("`", "")
        }
        .joinToString("\n")
}
