package app.localmind.assistant.data.home

import android.content.Context
import android.content.SharedPreferences
import app.localmind.assistant.domain.home.GreetingLanguage
import app.localmind.assistant.domain.home.WordLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the Home page shows. Everything is on by default except what needs you to fill it in. */
data class HomeSettings(
    /** What the hello calls you; blank = no name. */
    val name: String = "",
    /** A birthday as "MM-dd" (no year); blank = not set. */
    val birthday: String = "",
    val contextGreeting: Boolean = true,
    val chineseGreeting: GreetingLanguage = GreetingLanguage.ALTERNATE,
    val slothMoods: Boolean = true,
    /** Festivals and the 24 solar terms on the date line. */
    val festivals: Boolean = true,
    /** The double hour on the date line and the countdown to the New Year. */
    val chineseZodiac: Boolean = true,
    /** The sign the Sun is in, at the end of the date line. */
    val westernZodiac: Boolean = true,
    val wordOfDay: Boolean = true,
    val wordLanguage: WordLanguage = WordLanguage.ALTERNATE,
    val idiomOfDay: Boolean = true,
    val deskTile: Boolean = true,
    val lobsterHeadline: Boolean = true,
    val practiceWeek: Boolean = true,
    val tutorDrills: Boolean = true,
    val ourjongTile: Boolean = true,
    /** Persona ids on Home's favourites row, in order. */
    val favourites: List<String> = DEFAULT_FAVOURITES,
    /** The order of the Know and Do tiles, by key ("library", "desk"…); missing keys go last. */
    val knowOrder: List<String> = emptyList(),
    val doOrder: List<String> = emptyList()
) {
    /** True when [month]/[day] is your birthday (29 February is kept on the 28th in other years). */
    fun isBirthday(month: Int, day: Int, leapYear: Boolean): Boolean {
        val (m, d) = parseBirthday(birthday) ?: return false
        if (m == 2 && d == 29 && !leapYear) return month == 2 && day == 28
        return m == month && d == day
    }

    companion object {
        val DEFAULT_FAVOURITES = listOf(
            "persona-trader", "persona-librarian", "persona-mandarin-partner", "persona-cantonese-partner", "persona-general-assistant"
        )

        /** [items] in [order] (by [key]); anything the order doesn't name keeps its place after. */
        fun <T> arranged(items: List<T>, order: List<String>, key: (T) -> String): List<T> {
            val at = order.withIndex().associate { (i, k) -> k to i }
            return items.withIndex().sortedBy { (i, t) -> at[key(t)] ?: (order.size + i) }.map { it.value }
        }

        /** [keys] with the items at [a] and [b] swapped. */
        fun swapped(keys: List<String>, a: Int, b: Int): List<String> {
            if (a !in keys.indices || b !in keys.indices || a == b) return keys
            return keys.toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }
        }

        /** "04-21" → (4, 21); anything else → null. */
        fun parseBirthday(text: String): Pair<Int, Int>? {
            val parts = text.trim().split('-')
            if (parts.size != 2) return null
            val m = parts[0].toIntOrNull() ?: return null
            val d = parts[1].toIntOrNull() ?: return null
            val max = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            if (m !in 1..12 || d !in 1..max[m - 1]) return null
            return m to d
        }
    }
}

/**
 * The Home page's settings, in the "home" settings file (so they're in backups). Kept on the
 * tablet only; the name and birthday are used for the hello and nothing else.
 */
@Singleton
class HomePrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<HomeSettings> = _settings.asStateFlow()

    fun update(change: (HomeSettings) -> HomeSettings) {
        val next = change(_settings.value).let { s ->
            s.copy(name = s.name.take(MAX_NAME), birthday = if (HomeSettings.parseBirthday(s.birthday) != null) s.birthday.trim() else "")
        }
        prefs.edit()
            .putString("name", next.name)
            .putString("birthday", next.birthday)
            .putBoolean("context_greeting", next.contextGreeting)
            .putString("chinese_greeting", next.chineseGreeting.name)
            .putBoolean("sloth_moods", next.slothMoods)
            .putBoolean("festivals", next.festivals)
            .putBoolean("chinese_zodiac", next.chineseZodiac)
            .putBoolean("western_zodiac", next.westernZodiac)
            .putBoolean("word_of_day", next.wordOfDay)
            .putString("word_language", next.wordLanguage.name)
            .putBoolean("idiom_of_day", next.idiomOfDay)
            .putBoolean("desk_tile", next.deskTile)
            .putBoolean("lobster_headline", next.lobsterHeadline)
            .putBoolean("practice_week", next.practiceWeek)
            .putBoolean("tutor_drills", next.tutorDrills)
            .putBoolean("ourjong_tile", next.ourjongTile)
            .putString("favourites", next.favourites.joinToString(","))
            .putString("know_order", next.knowOrder.joinToString(","))
            .putString("do_order", next.doOrder.joinToString(","))
            .apply()
        _settings.value = next
    }

    /** Read again, after a backup has been restored. */
    fun reload() {
        _settings.value = read()
    }

    private fun read(): HomeSettings {
        val d = HomeSettings()
        return HomeSettings(
            name = prefs.getString("name", d.name).orEmpty(),
            birthday = prefs.getString("birthday", d.birthday).orEmpty(),
            contextGreeting = prefs.getBoolean("context_greeting", d.contextGreeting),
            chineseGreeting = GreetingLanguage.entries.firstOrNull { it.name == prefs.getString("chinese_greeting", null) } ?: d.chineseGreeting,
            slothMoods = prefs.getBoolean("sloth_moods", d.slothMoods),
            festivals = prefs.getBoolean("festivals", d.festivals),
            chineseZodiac = prefs.getBoolean("chinese_zodiac", d.chineseZodiac),
            westernZodiac = prefs.getBoolean("western_zodiac", d.westernZodiac),
            wordOfDay = prefs.getBoolean("word_of_day", d.wordOfDay),
            wordLanguage = WordLanguage.entries.firstOrNull { it.name == prefs.getString("word_language", null) } ?: d.wordLanguage,
            idiomOfDay = prefs.getBoolean("idiom_of_day", d.idiomOfDay),
            deskTile = prefs.getBoolean("desk_tile", d.deskTile),
            lobsterHeadline = prefs.getBoolean("lobster_headline", d.lobsterHeadline),
            practiceWeek = prefs.getBoolean("practice_week", d.practiceWeek),
            tutorDrills = prefs.getBoolean("tutor_drills", d.tutorDrills),
            ourjongTile = prefs.getBoolean("ourjong_tile", d.ourjongTile),
            favourites = prefs.getString("favourites", null)?.let { split(it) } ?: d.favourites,
            knowOrder = prefs.getString("know_order", null)?.let { split(it) }.orEmpty(),
            doOrder = prefs.getString("do_order", null)?.let { split(it) }.orEmpty()
        )
    }

    private fun split(text: String): List<String> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val FILE = "home"
        private const val MAX_NAME = 40
    }
}
