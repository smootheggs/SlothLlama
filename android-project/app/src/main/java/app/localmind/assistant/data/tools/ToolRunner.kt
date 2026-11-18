package app.localmind.assistant.data.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import app.localmind.assistant.data.dictionary.ChineseDictionary
import app.localmind.assistant.data.library.LibraryStore
import app.localmind.assistant.data.lobster.LobsterMailbox
import app.localmind.assistant.data.lobster.OrderKind
import app.localmind.assistant.data.settings.AssistantSettingsStore
import app.localmind.assistant.domain.attachments.LibraryPassage
import app.localmind.assistant.domain.attachments.ToolResult
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.domain.tools.ToolNames
import app.localmind.assistant.domain.tools.ToolRequest
import app.localmind.assistant.domain.tools.ToolRouter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.ZonedDateTime
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the offline tools a message asks for (see [ToolRouter]) before the model answers, and
 * hands back what they found: results for the message's `<tools>` block, and library passages
 * when it asked to look something up. Timers and alarms go to the tablet's clock app.
 */
@Singleton
class ToolRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AssistantSettingsStore,
    private val notes: NotesStore,
    private val calendar: CalendarReader,
    private val dictionary: ChineseDictionary,
    private val library: LibraryStore,
    private val mailbox: LobsterMailbox
) {
    class Outcome(val results: List<ToolResult>, val passages: List<LibraryPassage>) {
        val isEmpty: Boolean get() = results.isEmpty() && passages.isEmpty()
    }

    private val none = Outcome(emptyList(), emptyList())

    /** Tools are on, and on for this persona (tutors only when allowed). */
    fun enabledFor(persona: Persona): Boolean =
        settings.toolsOn.value && (persona.speech == null || settings.toolsForTutors.value)

    suspend fun run(message: String, persona: Persona): Outcome {
        val routed = ToolRouter.route(message, ZonedDateTime.now())
        // "Ask the lobster to …" is a direct order, so it works even where the tools are off.
        val requests = if (enabledFor(persona)) routed else routed.filterIsInstance<ToolRequest.Lobster>()
        if (requests.isEmpty()) return none
        val results = mutableListOf<ToolResult>()
        val passages = mutableListOf<LibraryPassage>()
        for (request in requests) {
            try {
                when (request) {
                    is ToolRequest.Computed -> results += request.result
                    is ToolRequest.Timer -> results += timer(request)
                    is ToolRequest.Alarm -> results += alarm(request)
                    is ToolRequest.AddNote -> {
                        notes.add(request.text)
                        results += ToolResult(ToolNames.NOTES, "Note saved", "Saved to the user's notes (Notes page): \"${request.text}\".")
                    }
                    ToolRequest.ListNotes -> results += listNotes()
                    is ToolRequest.SearchNotes -> results += searchNotes(request.query)
                    is ToolRequest.Calendar -> results += agenda(request)
                    is ToolRequest.Define -> define(request.words)?.let { results += it }
                    is ToolRequest.Library -> passages += library.search(request.query)
                    is ToolRequest.Lobster -> results += lobster(request.text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Tool failed: ${request::class.simpleName}", e)
            }
        }
        return Outcome(results, passages)
    }

    /** Puts the request in the Lobster's mailbox; its catch comes back on the Lobster page. */
    private suspend fun lobster(text: String): ToolResult {
        if (!mailbox.isReady) {
            return ToolResult(
                ToolNames.LOBSTER, "The Lobster isn't set up",
                "The user asked the Sloth Lobster to do something, but it isn't set up yet (Lobster page). Say so briefly."
            )
        }
        val lower = text.lowercase()
        val kind = when {
            Regex("https?://").containsMatchIn(text) -> OrderKind.FETCH
            Regex("\\b(watch|keep an eye|let me know when|tell me when|alert me|track)\\b").containsMatchIn(lower) -> OrderKind.WATCH
            Regex("\\b(brief|briefing|round-?up|every morning|daily)\\b").containsMatchIn(lower) -> OrderKind.BRIEF
            else -> OrderKind.RESEARCH
        }
        val deep = Regex("\\b(deep|in depth|in-depth|thorough|overnight|detailed)\\b").containsMatchIn(lower)
        try {
            mailbox.send(kind, text, deep = deep && kind == OrderKind.RESEARCH)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return ToolResult(
                ToolNames.LOBSTER, "Couldn't reach the Lobster's mailbox",
                "The order couldn't be put in the Sloth Lobster's mailbox (${e.message ?: "folder not writable"}). Say so briefly."
            )
        }
        val what = kind.label.lowercase() + if (deep && kind == OrderKind.RESEARCH) " (deep, overnight)" else ""
        return ToolResult(
            ToolNames.LOBSTER, "Sent to the Lobster 🦞",
            "The request went to the Sloth Lobster's mailbox as a $what order; what it finds will appear on the " +
                "Lobster page. Tell the user it's on its way in one short sentence, and don't answer the request yourself."
        )
    }

    private fun timer(t: ToolRequest.Timer): ToolResult {
        val length = duration(t.seconds)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, t.seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        t.message?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        val what = "Timer for $length" + (t.message?.let { " ($it)" } ?: "")
        return if (start(intent)) ToolResult(ToolNames.TIMER, "$what set", "$what: started in the tablet's clock app.")
        else ToolResult(ToolNames.TIMER, "No clock app for timers", "The user asked for a timer ($length), but no clock app on the tablet accepts timers. Say so.")
    }

    private fun alarm(a: ToolRequest.Alarm): ToolResult {
        val time = "%02d:%02d".format(a.hour, a.minute)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, a.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, a.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        a.message?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        val what = "Alarm at $time" + (a.message?.let { " ($it)" } ?: "")
        return if (start(intent)) ToolResult(ToolNames.ALARM, "$what set", "$what: set in the tablet's clock app (next $time).")
        else ToolResult(ToolNames.ALARM, "No clock app for alarms", "The user asked for an alarm at $time, but no clock app on the tablet accepts alarms. Say so.")
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Clock app refused", e)
        false
    }

    private fun listNotes(): ToolResult {
        val all = notes.notes.value
        if (all.isEmpty()) return ToolResult(ToolNames.NOTES, "No notes yet", "The user has no notes yet. They can say \"take a note: …\".")
        val shown = all.take(MAX_NOTES)
        val more = if (all.size > shown.size) " (newest ${shown.size} of ${all.size})" else ""
        return ToolResult(
            ToolNames.NOTES,
            "${all.size} note${if (all.size == 1) "" else "s"}",
            "The user's notes$more, newest first:\n" + shown.joinToString("\n") { "• ${dated(it)}" }
        )
    }

    private fun searchNotes(query: String): ToolResult {
        val found = notes.search(query).take(MAX_NOTES)
        return if (found.isEmpty()) ToolResult(ToolNames.NOTES, "No notes about \"$query\"", "None of the user's notes mention \"$query\".")
        else ToolResult(
            ToolNames.NOTES,
            "${found.size} note${if (found.size == 1) "" else "s"} about \"$query\"",
            "The user's notes about \"$query\":\n" + found.joinToString("\n") { "• ${dated(it)}" }
        )
    }

    private fun dated(n: Note): String =
        "${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(n.createdAt))}: ${n.text.take(400)}"

    private suspend fun agenda(c: ToolRequest.Calendar): ToolResult {
        if (!settings.calendarOn.value || !calendar.hasPermission) {
            return ToolResult(
                ToolNames.CALENDAR, "Calendar not connected",
                "The app can't read the user's calendar yet. Tell them to switch on Settings → Assistant → Read my calendar."
            )
        }
        val events = calendar.events(c.from, c.days)
        if (events.isEmpty()) return ToolResult(ToolNames.CALENDAR, "Nothing on ${c.label}", "The user's calendar has nothing for ${c.label}.")
        return ToolResult(
            ToolNames.CALENDAR,
            "${events.size} event${if (events.size == 1) "" else "s"} ${if (c.days == 1) "on" else "in"} ${c.label}",
            "The user's calendar for ${c.label}:\n" + events.joinToString("\n") { "• ${calendar.describe(it)}" }
        )
    }

    private suspend fun define(words: List<String>): ToolResult? = withContext(Dispatchers.IO) {
        val lines = words.mapNotNull { w ->
            val found = dictionary.lookup(w) ?: return@mapNotNull null
            val entries = found.entries.take(3).joinToString(" | ") { e ->
                val head = if (e.traditional == e.simplified) e.simplified else "${e.simplified} (${e.traditional})"
                "$head ${e.pinyin}${if (e.jyutping.isNotBlank()) " / ${e.jyutping}" else ""}: ${e.definitions.take(4).joinToString("; ")}"
            }
            when {
                entries.isNotEmpty() -> entries
                found.cantoneseReading != null -> "$w (Cantonese ${found.cantoneseReading}): " +
                    found.characters.joinToString("; ") { "${it.simplified} ${it.definitions.firstOrNull().orEmpty()}" }
                else -> null
            }
        }
        if (lines.isEmpty()) null
        else ToolResult(ToolNames.DICTIONARY, "Dictionary: ${words.joinToString(", ")}", "From CC-CEDICT:\n" + lines.joinToString("\n") { "• $it" })
    }

    private fun duration(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return listOfNotNull(
            if (h > 0) "$h h" else null,
            if (m > 0) "$m min" else null,
            if (s > 0) "$s s" else null
        ).joinToString(" ")
    }

    private companion object {
        const val TAG = "ToolRunner"
        const val MAX_NOTES = 20
    }
}
