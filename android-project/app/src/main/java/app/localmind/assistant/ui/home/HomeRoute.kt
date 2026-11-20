package app.localmind.assistant.ui.home

import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Insights
import app.localmind.assistant.ui.trading.TradingViewModel
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.backup.AutoBackupState
import app.localmind.assistant.data.lobster.MailboxState
import app.localmind.assistant.domain.desk.DeskMath
import app.localmind.assistant.domain.home.DailyWord
import app.localmind.assistant.domain.home.Idiom
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.Persona
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.ui.chat.ChatViewModel
import app.localmind.assistant.ui.library.LibraryViewModel
import app.localmind.assistant.ui.notes.NotesViewModel
import app.localmind.assistant.ui.speech.rememberTutorSpeaker
import app.localmind.assistant.ui.study.FlashcardsViewModel
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date

/** The Home page, fed from the chat's state, flashcards, the library, notes, backups, the Desk and the Lobster. */
@Composable
fun HomeRoute(
    chatViewModel: ChatViewModel,
    script: ChineseScript,
    onOpenChat: () -> Unit,
    onOpenChats: () -> Unit,
    onFlashcards: () -> Unit,
    onDictionary: () -> Unit,
    /** The Core words lists. */
    onCoreWords: () -> Unit = {},
    /** The Trading book (ideas and desk sums). */
    onTrading: () -> Unit = {},
    onPersonas: () -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit,
    onLibrary: () -> Unit,
    onNotes: () -> Unit,
    onDesk: () -> Unit = {},
    /** The Later shelf (in the Reader). */
    onLater: () -> Unit = {},
    onLobster: (() -> Unit)? = null,
    /** The Lobster's mailbox, when the Lobster is switched on. */
    lobster: MailboxState? = null
) {
    val context = LocalContext.current
    val ui by chatViewModel.uiState.collectAsStateWithLifecycle()
    val flashcards: FlashcardsViewModel = hiltViewModel()
    val cards by flashcards.uiState.collectAsStateWithLifecycle()
    val library: LibraryViewModel = hiltViewModel()
    val shelf by library.items.collectAsStateWithLifecycle()
    val notesVm: NotesViewModel = hiltViewModel()
    val notes by notesVm.notes.collectAsStateWithLifecycle()
    val home: HomeViewModel = hiltViewModel()
    val trading: TradingViewModel = hiltViewModel()
    val tradingStats by trading.stats.collectAsStateWithLifecycle()
    val backup by home.backup.collectAsStateWithLifecycle()
    val meaningOn by home.meaningOn.collectAsStateWithLifecycle()
    val settings by home.settings.collectAsStateWithLifecycle()
    val desk by home.deskState.collectAsStateWithLifecycle()
    val written by home.written.collectAsStateWithLifecycle()
    val lastByPersona by home.lastByPersona.collectAsStateWithLifecycle()
    val reviews by home.reviews.collectAsStateWithLifecycle()
    val message by home.message.collectAsStateWithLifecycle()
    val laterItems by home.laterItems.collectAsStateWithLifecycle()
    val speaker = rememberTutorSpeaker()
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val todayDate = LocalDate.now(zone)

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            home.clearMessage()
        }
    }

    // Ourjong is a separate app: look for it each time Home comes back into view.
    var ourjong by remember { mutableStateOf<Intent?>(null) }
    LifecycleResumeEffect(Unit) {
        ourjong = OURJONG.firstNotNullOfOrNull { pkg -> runCatching { context.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull() }
        onPauseOrDispose { }
    }

    val lobsterLine = lobster?.let { lobsterStatus(it, now) }
    val newest = lobster?.catch?.firstNotNullOfOrNull { it.item }
    val glance = deskGlance(desk)
    val today = buildList {
        val due = cards.dueCount
        add(
            if (due > 0) TodayItem(Icons.Outlined.Style, "$due due", "Flashcards", "Review now", Tone.ACCENT, onFlashcards)
            else TodayItem(Icons.Outlined.Style, "None due", "Flashcards", "", Tone.QUIET, onFlashcards)
        )
        if (lobster != null && onLobster != null) {
            val caught = lobster.catch.size
            val headline = newest?.takeIf { settings.lobsterHeadline }?.let(::catchHeadline)
            add(
                if (caught > 0) TodayItem(
                    Icons.Outlined.Sailing, "$caught new", "From the Lobster",
                    headline ?: "Brought back · file it", Tone.LOBSTER, onLobster
                )
                else TodayItem(Icons.Outlined.Sailing, "Nothing new", "From the Lobster", lobsterLine.orEmpty(), Tone.QUIET, onLobster)
            )
        }
        if (settings.deskTile && glance != null && DeskMath.businessDaysBetween(glance.dataDate, todayDate) <= FRESH_DAYS) {
            add(deskItem(glance, todayDate, onDesk))
        }
        add(backupItem(backup, now, onSettings))
    }

    val things = buildList {
        val items = shelf?.size ?: 0
        add(
            HomeTile(
                Icons.Outlined.LocalLibrary, "Library",
                when (items) {
                    0 -> ""
                    1 -> "1 item"
                    else -> "$items items"
                },
                onLibrary
            )
        )
        add(
            HomeTile(
                Icons.Outlined.StickyNote2, "Notes",
                when (notes.size) {
                    0 -> ""
                    1 -> "1 note"
                    else -> "${notes.size} notes"
                },
                onNotes
            )
        )
        val toRead = laterItems.filter { !it.archived }
        add(
            HomeTile(
                Icons.Outlined.BookmarkBorder, "Later",
                if (toRead.isEmpty()) "" else "${toRead.size} to read · ${toRead.sumOf { it.minutesLeft }} min",
                onLater
            )
        )
        if (onLobster != null && lobsterLine != null) {
            add(HomeTile(Icons.Outlined.Sailing, "SlothLobster", lobsterLine, onLobster, Tone.LOBSTER))
        }
        add(
            HomeTile(
                Icons.Outlined.ShowChart, "SlothDesk",
                glance?.let { "Prices to ${shortDate(it.dataDate.toString())}" } ?: "",
                onDesk
            )
        )
        if (settings.ourjongTile) {
            val intent = ourjong
            add(
                HomeTile(
                    Icons.Outlined.Translate, "Ourjong",
                    if (intent != null) "" else "Not installed",
                    onClick = {
                        if (intent != null) runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        else Toast.makeText(context, "Ourjong isn't installed. Add it in Obtainium, then it opens from here.", Toast.LENGTH_LONG).show()
                    },
                    mark = "中"
                )
            )
        }
    }

    val more = listOf(
        HomeTile(Icons.Outlined.ChatBubbleOutline, "Chats", "", onOpenChats),
        HomeTile(Icons.Outlined.PersonOutline, "Personas", "", onPersonas),
        HomeTile(Icons.Outlined.Translate, "Dictionary", "", onDictionary),
        HomeTile(Icons.Outlined.FormatListNumbered, "Core words", "", onCoreWords),
        HomeTile(Icons.Outlined.Insights, "Trading book", tradingStats.due.takeIf { it > 0 }?.let { "$it to review" } ?: "", onTrading),
        HomeTile(Icons.Outlined.Memory, "Models", ui.activeModelName.ifBlank { "choose one" }, onModels)
    )

    // Practice: writing to a tutor, or reviewing a flashcard.
    val tutors = ui.personas.filter { it.speech != null }
    val practice = if (settings.practiceWeek) practiceWeek(written, tutors.map { it.id }.toSet(), reviews.map { it.second }, todayDate, zone) else null
    val lastAtByLanguage: Map<SpeechLanguage, Long> = tutors.groupBy { it.speech!! }.mapNotNull { (language, list) ->
        val chats = list.mapNotNull { lastByPersona[it.id] }.maxOrNull()
        val reviewed = reviews.filter { it.first.speech == language }.maxOfOrNull { it.second }
        listOfNotNull(chats, reviewed).maxOrNull()?.let { language to it }
    }.toMap()
    val lastPractised = if (!settings.practiceWeek) emptyMap() else
        lastAtByLanguage.mapNotNull { (language, at) -> practisedAgo(at, todayDate, zone)?.let { language to it } }.toMap()
    val recentLanguages = lastAtByLanguage.filterValues { now - it < RECENT_MS }.keys
    val todayLanguages = lastAtByLanguage.filterValues { practisedAgo(it, todayDate, zone) == "today" }.keys

    /** Starts a chat with [persona] (or the current one) and sends [prompt]. */
    fun startAndSend(persona: Persona?, prompt: String) {
        if (persona != null) chatViewModel.startConversationWith(persona.id) else chatViewModel.createNewConversation()
        chatViewModel.sendQuickAction(prompt)
        onOpenChat()
    }
    fun teacherFor(language: SpeechLanguage): Persona? =
        tutorGroups(ui.personas).firstOrNull { it.language == language }?.let(::drillTutor)

    HomeScreen(
        script = script,
        conversations = ui.conversations,
        personas = ui.personas,
        today = today,
        things = things,
        more = more,
        onSearch = onOpenChats,
        onSettings = onSettings,
        onOpenConversation = { id ->
            chatViewModel.selectConversation(id)
            onOpenChat()
        },
        onNewChat = {
            chatViewModel.createNewConversation()
            onOpenChat()
        },
        onStartWith = { tutor ->
            chatViewModel.startConversationWith(tutor.id)
            onOpenChat()
        },
        extras = HomeExtrasUi(
            settings = settings,
            caught = lobster?.catch?.size ?: 0,
            due = cards.dueCount,
            practice = practice,
            lastPractised = lastPractised,
            recentLanguages = recentLanguages,
            todayLanguages = todayLanguages
        ),
        actions = HomeActions(
            onDrill = { tutor -> startAndSend(tutor, DRILL) },
            onSpeak = { text, language -> speaker.speak(text, language) },
            onAddWord = { word -> home.addToFlashcards(word, script == ChineseScript.TRADITIONAL) },
            onAskWord = { word ->
                val language = if (word.cantonese) SpeechLanguage.CANTONESE else SpeechLanguage.MANDARIN
                startAndSend(teacherFor(language), askWord(word, script == ChineseScript.TRADITIONAL))
            },
            onAskIdiom = { idiom -> startAndSend(teacherFor(SpeechLanguage.MANDARIN), askIdiom(idiom, script == ChineseScript.TRADITIONAL)) }
        )
    )
}

/** Languages practised within two weeks keep their chip on Home. */
private const val RECENT_MS = 14 * 24 * 60 * 60 * 1000L

/** Ourjong's release build, then its dev build ("Ourjong Dev"). */
private val OURJONG = listOf("app.ourjong", "app.ourjong.debug")

/** Desk tickets older than this many business days aren't "today" any more. */
private const val FRESH_DAYS = 2

private const val DRILL =
    "Let's do a five-minute drill. Ask me five short questions, one at a time, at my level, mixing " +
        "vocabulary, a little grammar and one I answer by speaking. Wait for each answer, correct me " +
        "gently, and finish with one line on what to review."

internal fun askWord(word: DailyWord, traditional: Boolean): String {
    val written = word.written(traditional)
    val reading = if (word.cantonese) "Jyutping" else "pinyin"
    return "Teach me today's word, $written (${word.reading}, \"${word.meaning}\"): when people use it, " +
        "two short example sentences with $reading and English, and one mistake learners make with it."
}

internal fun askIdiom(idiom: Idiom, traditional: Boolean): String =
    "Tell me about the idiom ${idiom.written(traditional)} (${idiom.pinyin}): its story in a few lines, " +
        "how it's used today, and one natural example sentence with pinyin and English."

private fun deskItem(g: DeskGlance, today: LocalDate, onDesk: () -> Unit): TodayItem {
    val tickets = if (g.trades == 1) "1 ticket" else "${g.trades} tickets"
    return when {
        g.trades == 0 -> TodayItem(Icons.Outlined.ShowChart, "No trades", "SlothDesk", "Prices to ${shortDate(g.dataDate.toString())}", Tone.QUIET, onDesk)
        g.unmarked > 0 -> TodayItem(Icons.Outlined.ShowChart, tickets, "SlothDesk", "Mark how it went", Tone.ACCENT, onDesk)
        else -> TodayItem(Icons.Outlined.ShowChart, "All marked", "SlothDesk", "", Tone.QUIET, onDesk, calm = "tickets marked")
    }
}

/** "Awake", "Working: …", "Paused", "Last seen 2 hr. ago". */
private fun lobsterStatus(state: MailboxState, now: Long): String {
    val status = state.status
    return when {
        state.paused -> "Paused"
        status == null -> "Not heard from yet"
        now - status.at < AWAKE_MS -> status.working?.let { "Working: $it" } ?: "Awake"
        else -> "Last seen " + DateUtils.getRelativeTimeSpanString(status.at, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
    }
}

private fun backupItem(state: AutoBackupState, now: Long, onSettings: () -> Unit): TodayItem = when {
    !state.enabled -> TodayItem(Icons.Outlined.Shield, "Off", "Backup", "Turn it on in Settings", Tone.QUIET, onSettings, calm = "weekly backup off")
    state.lastAt == 0L -> TodayItem(Icons.Outlined.Shield, "Soon", "Backup", "First one within a day", Tone.QUIET, onSettings, calm = "first backup within a day")
    !state.lastOk -> TodayItem(Icons.Outlined.Shield, "Not saved", "Backup", state.lastResult ?: "Open Settings to see why", Tone.PROBLEM, onSettings)
    else -> dayLabel(state.lastAt, now).let { day ->
        TodayItem(Icons.Outlined.Shield, "Saved", "Backup", day.replaceFirstChar { it.uppercase() }, Tone.OK, onSettings, calm = "backup saved $day")
    }
}

/** "today", "yesterday", "Sun" within the week, else a short date. */
private fun dayLabel(time: Long, now: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = time }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val daysAgo = ((startOfDay(today) - startOfDay(day)) / DateUtils.DAY_IN_MILLIS).toInt()
    return when {
        daysAgo <= 0 -> "today"
        daysAgo == 1 -> "yesterday"
        daysAgo < 7 -> android.text.format.DateFormat.format("EEE", Date(time)).toString()
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(time))
    }
}

private fun startOfDay(c: Calendar): Long = (c.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** A heartbeat younger than this means the Lobster is up (as on the Lobster page). */
private const val AWAKE_MS = 3 * 60 * 1000L
