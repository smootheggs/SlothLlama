package app.localmind.assistant.ui.home

import app.localmind.assistant.data.home.HomeSettings
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.FormatListNumbered
import app.localmind.assistant.ui.trading.TradingViewModel
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WorkOutline
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
    onPersonas: () -> Unit,
    onModels: () -> Unit,
    onSettings: () -> Unit,
    onLibrary: () -> Unit,
    /** SlothBooks: the shelf, papers and the Librarian. */
    onBooks: () -> Unit = {},
    /** The Sloth Express: the edition the Lobster makes. */
    onEdition: () -> Unit = {},
    /** The Atlas: the world map on the tablet. */
    onAtlas: () -> Unit = {},
    /** Projects: the record of each project, and write-ups from it. */
    onProjects: () -> Unit = {},
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
    val booksVm: app.localmind.assistant.ui.books.BooksViewModel = hiltViewModel()
    val bookList by booksVm.books.collectAsStateWithLifecycle()
    val notesVm: NotesViewModel = hiltViewModel()
    val notes by notesVm.notes.collectAsStateWithLifecycle()
    val home: HomeViewModel = hiltViewModel()
    val trading: TradingViewModel = hiltViewModel()
    val tradingStats by trading.stats.collectAsStateWithLifecycle()
    val projectsVm: app.localmind.assistant.ui.projects.ProjectsViewModel = hiltViewModel()
    val projectList by projectsVm.projects.collectAsStateWithLifecycle()
    val backup by home.backup.collectAsStateWithLifecycle()
    val meaningOn by home.meaningOn.collectAsStateWithLifecycle()
    val settings by home.settings.collectAsStateWithLifecycle()
    val desk by home.deskState.collectAsStateWithLifecycle()
    val written by home.written.collectAsStateWithLifecycle()
    val lastByPersona by home.lastByPersona.collectAsStateWithLifecycle()
    val reviews by home.reviews.collectAsStateWithLifecycle()
    val message by home.message.collectAsStateWithLifecycle()
    val laterItems by home.laterItems.collectAsStateWithLifecycle()
    val editionVm: app.localmind.assistant.ui.edition.EditionViewModel = hiltViewModel()
    val paper by editionVm.latest.collectAsStateWithLifecycle()
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
    val caught = lobster?.catch?.size ?: 0
    // Today's edition, until it's a day old: what's unread lights up The Sloth Express.
    val freshPaper = paper?.takeIf { now - it.edition.made < 20L * 60 * 60 * 1000 }
    val unreadNews = freshPaper?.let { it.stories.size - it.readCount }?.takeIf { it > 0 }
    // Paper tickets still to mark, while they're fresh: they light up SlothDesk.
    val ticketsToMark = glance?.takeIf {
        settings.deskTile && it.unmarked > 0 && DeskMath.businessDaysBetween(it.dataDate, todayDate) <= FRESH_DAYS
    }?.unmarked

    // The quiet line under the hello: what's calm, and a backup problem in red. What needs you
    // lights up its own tile below instead, so nothing shows twice.
    val status = buildList {
        if (lobster != null && onLobster != null && caught == 0) add(StatusNote("nothing new from the Lobster", onLobster))
        add(backupNote(backup, now, onSettings))
    }

    // Learn: the word tools beside the tutors (the tutors themselves are their own row).
    val learn = listOf(
        HomeTile(Icons.Outlined.FormatListNumbered, "Core words", "", onCoreWords),
        HomeTile(
            Icons.Outlined.Style, "Flashcards", cards.dueCount.takeIf { it > 0 }?.let { "$it due" } ?: "", onFlashcards,
            if (cards.dueCount > 0) Tone.ACCENT else Tone.QUIET
        ),
        HomeTile(Icons.Outlined.Translate, "Dictionary", "", onDictionary)
    )

    // Know: what you read (SlothBooks), what you note, and what's saved for later.
    val know = buildList {
        val reading = bookList.filter { !it.isPaper && it.status == app.localmind.assistant.domain.books.Status.READING }
            .maxByOrNull { it.updatedAt }
        val freshPapers = bookList.count { it.fresh }
        val shelved = bookList.count { !it.isPaper }
        add(
            HomeTile(
                Icons.AutoMirrored.Outlined.MenuBook, "SlothBooks",
                when {
                    freshPapers == 1 -> "1 new paper"
                    freshPapers > 1 -> "$freshPapers new papers"
                    reading != null -> "${reading.title} · ${reading.progress}%"
                    shelved > 0 -> "%,d books".format(shelved)
                    else -> ""
                },
                onBooks,
                if (freshPapers > 0 || reading != null) Tone.ACCENT else Tone.QUIET,
                key = "books"
            )
        )
        if (onLobster != null || paper != null) {
            add(
                HomeTile(
                    Icons.Outlined.Newspaper, "The Sloth Express", "", onEdition,
                    if (unreadNews != null) Tone.ACCENT else Tone.QUIET,
                    key = "edition",
                    badge = unreadNews?.let(::badgeCount)
                )
            )
        }
        add(HomeTile(Icons.Outlined.Public, "Atlas", "", onAtlas, key = "atlas"))
        val toRead = laterItems.filter { !it.archived }
        add(
            HomeTile(
                Icons.Outlined.BookmarkBorder, "Later",
                if (toRead.isEmpty()) "" else "${toRead.size} to read · ${toRead.sumOf { it.minutesLeft }} min",
                onLater,
                key = "later"
            )
        )
    }

    // Do: SlothDesk (strategies, ideas, sums), Projects, the Lobster, and a game of mahjong.
    val act = buildList {
        val ideasDue = tradingStats.due
        add(
            HomeTile(
                Icons.Outlined.ShowChart, "SlothDesk",
                when {
                    ideasDue == 1 -> "1 idea to review"
                    ideasDue > 1 -> "$ideasDue ideas to review"
                    ticketsToMark != null -> if (ticketsToMark == 1) "1 ticket to mark" else "$ticketsToMark tickets to mark"
                    else -> glance?.let { "Prices to ${shortDate(it.dataDate.toString())}" } ?: ""
                },
                onDesk,
                if (ideasDue > 0 || ticketsToMark != null) Tone.ACCENT else Tone.QUIET,
                key = "desk"
            )
        )
        // Projects: lit when a milestone is late; otherwise the next one due, across your projects.
        val current = projectList.filter { !it.archived }
        val glances = current.map { app.localmind.assistant.domain.projects.Projects.glance(it, todayDate) }
        val late = glances.sumOf { it.overdue.size }
        val soonest = glances.mapNotNull { g -> g.next?.let { n -> app.localmind.assistant.domain.projects.Projects.date(n.due)?.let { n to it } } }
            .minByOrNull { it.second }
        add(
            HomeTile(
                Icons.Outlined.WorkOutline, "Projects",
                when {
                    late == 1 -> "1 milestone late"
                    late > 1 -> "$late milestones late"
                    soonest != null -> "${soonest.first.title} · ${app.localmind.assistant.domain.projects.Projects.relative(soonest.second, todayDate)}"
                    current.size == 1 -> "1 project"
                    current.size > 1 -> "${current.size} projects"
                    else -> ""
                },
                onProjects,
                if (late > 0) Tone.ACCENT else Tone.QUIET,
                key = "projects"
            )
        )
        if (onLobster != null && lobsterLine != null) {
            // A catch lights it up: the count on the icon and, if you like, the newest headline.
            val headline = newest?.takeIf { caught > 0 && settings.lobsterHeadline }?.let(::catchHeadline)
            add(
                HomeTile(
                    Icons.Outlined.Sailing, "SlothLobster",
                    headline ?: if (caught > 0) "Brought back · file it" else lobsterLine,
                    onLobster, Tone.LOBSTER, key = "lobster",
                    badge = caught.takeIf { it > 0 }?.let(::badgeCount)
                )
            )
        }
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
                    mark = "中",
                    key = "ourjong"
                )
            )
        }
    }

    // The Library is what SlothLlama reads (files and Wikipedia it searches): set up once, like Models.
    val more = listOf(
        HomeTile(Icons.Outlined.ChatBubbleOutline, "Chats", "", onOpenChats),
        HomeTile(Icons.Outlined.PersonOutline, "Personas", "", onPersonas),
        HomeTile(Icons.Outlined.Memory, "Models", ui.activeModelName.ifBlank { "choose one" }, onModels, key = "models"),
        HomeTile(Icons.Outlined.LocalLibrary, "Library", "", onLibrary),
        // Notes is a tool you go to, like the Library: it moved here so Know stays one row.
        HomeTile(Icons.Outlined.StickyNote2, "Notes", "", onNotes)
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

    // Arranged as you left them (Home → Arrange), and your favourite personas in their order.
    // SlothBooks took the Library's place in Know (1.29), so an order saved before keeps its slot.
    val knowArranged = HomeSettings.arranged(know, settings.knowOrder.map { if (it == "library") "books" else it }) { it.key }
    val actArranged = HomeSettings.arranged(act, settings.doOrder) { it.key }
    val favourites = settings.favourites.mapNotNull { id -> ui.personas.firstOrNull { it.id == id } }
    val favouriteNotes = favourites.mapNotNull { p -> practisedAgo(lastByPersona[p.id], todayDate, zone)?.let { p.id to it } }.toMap()

    HomeScreen(
        script = script,
        conversations = ui.conversations,
        personas = ui.personas,
        status = status,
        learn = learn,
        know = knowArranged,
        act = actArranged,
        more = more,
        favourites = favourites,
        favouriteNotes = favouriteNotes,
        onChooseFavourites = onPersonas,
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
            onAskIdiom = { idiom -> startAndSend(teacherFor(SpeechLanguage.MANDARIN), askIdiom(idiom, script == ChineseScript.TRADITIONAL)) },
            onSwap = { section, a, b ->
                when (section) {
                    "know" -> home.update { it.copy(knowOrder = HomeSettings.swapped(knowArranged.map { t -> t.key }, a, b)) }
                    "do" -> home.update { it.copy(doOrder = HomeSettings.swapped(actArranged.map { t -> t.key }, a, b)) }
                    "fav" -> home.update { it.copy(favourites = HomeSettings.swapped(favourites.map { p -> p.id }, a, b) + it.favourites.filter { id -> favourites.none { p -> p.id == id } }) }
                }
            }
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

/** A count for a tile's badge: "24", or "99+". */
private fun badgeCount(n: Int): String = if (n > 99) "99+" else n.toString()

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

private fun backupNote(state: AutoBackupState, now: Long, onSettings: () -> Unit): StatusNote = when {
    !state.enabled -> StatusNote("weekly backup off", onSettings)
    state.lastAt == 0L -> StatusNote("first backup within a day", onSettings)
    !state.lastOk -> StatusNote("backup not saved · open Settings", onSettings, problem = true)
    else -> StatusNote("backup saved ${dayLabel(state.lastAt, now)}", onSettings)
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
