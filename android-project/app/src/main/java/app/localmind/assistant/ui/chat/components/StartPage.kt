package app.localmind.assistant.ui.chat.components

import android.icu.util.ChineseCalendar
import android.icu.util.Calendar as IcuCalendar
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.R
import app.localmind.assistant.domain.model.Conversation
import app.localmind.assistant.domain.persona.ChineseScript
import app.localmind.assistant.domain.persona.Persona
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import app.localmind.assistant.ui.theme.SlothPalette as P

/** A way in from the start page: a small label over one line of text. */
data class StartTile(val label: String, val text: String, val onClick: () -> Unit)

/**
 * The start page, shown when the app opens and after New chat. Calm on purpose, like Sloth
 * Lobster's: the time, today in Chinese, a hello, and a few ways to begin. Fully offline, so
 * there's no weather: the Chinese calendar fills that spot.
 */
@Composable
fun StartPage(
    persona: Persona,
    script: ChineseScript,
    tiles: List<StartTile>,
    suggestions: List<String>,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Keep the clock right while the page is showing.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
        }
    }
    val (hello, sub) = remember(now / 3_600_000) { slothHello(now) }

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = maxHeight * 0.10f, bottom = 24.dp)
        ) {
            Text(clockText(now), color = P.Muted, fontSize = 14.sp)
            Text(chineseToday(now, script), color = P.Faint, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.sloth_llama),
                    contentDescription = null,
                    modifier = Modifier.size(width = 46.dp, height = 40.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(hello, color = P.Text, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp)
            }
            Text(sub, color = P.Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
            if (persona.speech != null || persona.tagline.isNotBlank()) {
                Text(
                    "${persona.badge} ${persona.name}",
                    color = P.Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(22.dp))
            tiles.take(4).chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tile -> Tile(tile, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }

            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("TRY ASKING", color = P.Faint, fontSize = 11.sp, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(6.dp))
                suggestions.take(3).forEach { prompt ->
                    Text(
                        prompt,
                        color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSuggestion(prompt) }
                            .padding(vertical = 8.dp, horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Tile(tile: StartTile, modifier: Modifier) {
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, P.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = tile.onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Column {
            Text(tile.label, color = P.Faint, fontSize = 12.sp)
            Text(
                tile.text, color = P.Text, fontSize = 15.sp, lineHeight = 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Tiles for the start page: the last chat, the two tutors, and flashcards. */
fun defaultStartTiles(
    lastConversation: Conversation?,
    flashcardsDue: Int,
    onContinue: (String) -> Unit,
    onMandarin: () -> Unit,
    onCantonese: () -> Unit,
    onFlashcards: () -> Unit
): List<StartTile> = listOfNotNull(
    lastConversation?.let { StartTile("Continue", it.title.ifBlank { "Last chat" }) { onContinue(it.id) } },
    StartTile("Mandarin", "Practise a conversation", onMandarin),
    StartTile("Cantonese", "練下廣東話", onCantonese),
    StartTile(
        "Flashcards",
        if (flashcardsDue > 0) "$flashcardsDue due for review" else "Review your words",
        onFlashcards
    )
)

// --- Greeting -------------------------------------------------------------------------------

private val HELLOS = mapOf(
    "night" to listOf("Still up?" to "I'll keep things quiet. 🌙", "A late one." to "Short and gentle, then. 🌙", "Night owl hours." to "No rush at all. 🦉"),
    "morning" to listOf("Good morning." to "Slow and steady today? 🦥", "Morning." to "Coffee first, curiosity second. ☕", "Good morning." to "Take your time, I'm here. 🌱"),
    "afternoon" to listOf("Good afternoon." to "Nothing here is in a hurry. 🦥", "Afternoon." to "What's on your mind? 🍵", "Good afternoon." to "A good hour for a little practice. 🌿"),
    "evening" to listOf("Good evening." to "Settle in. 🕯️", "Evening." to "Feet up, mind open. 🌙", "Good evening." to "Glad you're here. 🍃")
)

internal fun slothHello(now: Long): Pair<String, String> {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val part = when {
        hour < 5 || hour >= 23 -> "night"
        hour < 12 -> "morning"
        hour < 18 -> "afternoon"
        else -> "evening"
    }
    val options = HELLOS.getValue(part)
    return options[cal.get(Calendar.DAY_OF_YEAR) % options.size]
}

internal fun clockText(now: Long): String {
    val date = Date(now)
    val weekday = android.text.format.DateFormat.format("EEEE", date)
    val day = DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    return "$weekday · $day · $time"
}

// --- Today in Chinese: weekday, lunar date and zodiac year (all computed on the device) ------

private val WEEKDAYS = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")
private val STEMS = "甲乙丙丁戊己庚辛壬癸"
private val BRANCHES = "子丑寅卯辰巳午未申酉戌亥"
private val DIGITS = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

internal fun chineseToday(now: Long, script: ChineseScript): String {
    val traditional = script == ChineseScript.TRADITIONAL
    val weekday = WEEKDAYS[Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK) - 1]
    val lunar = runCatching {
        val cal = ChineseCalendar(Date(now))
        val cycleYear = cal.get(IcuCalendar.YEAR)            // 1..60 in the sexagenary cycle
        val month = cal.get(IcuCalendar.MONTH) + 1
        val day = cal.get(IcuCalendar.DAY_OF_MONTH)
        val leap = cal.get(IcuCalendar.IS_LEAP_MONTH) == 1
        val animals = if (traditional) "鼠牛虎兔龍蛇馬羊猴雞狗豬" else "鼠牛虎兔龙蛇马羊猴鸡狗猪"
        val i = (cycleYear - 1).mod(60)
        val year = "${STEMS[i % 10]}${BRANCHES[i % 12]}${animals[i % 12]}年"
        val monthName = (if (leap) (if (traditional) "閏" else "闰") else "") + lunarMonth(month, traditional)
        "$year · 农历$monthName${lunarDay(day)}".let { if (traditional) it.replace("农历", "農曆") else it }
    }.getOrNull()
    return listOfNotNull(weekday, lunar).joinToString(" · ")
}

private fun lunarMonth(month: Int, traditional: Boolean): String = when (month) {
    1 -> "正月"
    11 -> "冬月"
    12 -> if (traditional) "臘月" else "腊月"
    else -> "${DIGITS[month]}月"
}

private fun lunarDay(day: Int): String = when {
    day <= 10 -> "初${DIGITS[day]}"
    day < 20 -> "十${DIGITS[day - 10]}"
    day == 20 -> "二十"
    day < 30 -> "廿${DIGITS[day - 20]}"
    else -> "三十"
}
