package app.localmind.assistant.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import app.localmind.assistant.data.home.HomePrefs
import app.localmind.assistant.data.home.HomeSettings
import app.localmind.assistant.domain.home.GreetingLanguage
import app.localmind.assistant.domain.home.WordLanguage
import app.localmind.assistant.ui.theme.SlothPalette as P
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormatSymbols
import javax.inject.Inject

@HiltViewModel
class HomeSettingsViewModel @Inject constructor(private val prefs: HomePrefs) : ViewModel() {
    val settings = prefs.settings
    fun update(change: (HomeSettings) -> HomeSettings) = prefs.update(change)
}

/**
 * Settings → Home: each small touch on the Home page, on or off. Grouped as the page reads:
 * the hello, the date line, the day's Chinese, and the tiles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeSettingsSection() {
    val vm: HomeSettingsViewModel = hiltViewModel()
    val s by vm.settings.collectAsStateWithLifecycle()
    var birthdayOpen by remember { mutableStateOf(false) }

    Section("Home") {
        Text(
            "Small touches on the Home page. All worked out on this tablet; your name and birthday stay here.",
            color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Group("The hello")
        OutlinedTextField(
            value = s.name,
            onValueChange = { v -> vm.update { it.copy(name = v) } },
            label = { Text("What to call you", fontSize = 12.sp) },
            placeholder = { Text("Leave blank for no name", color = P.Faint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = P.Text, unfocusedTextColor = P.Text,
                focusedContainerColor = P.Card, unfocusedContainerColor = P.Card,
                focusedBorderColor = P.Accent, unfocusedBorderColor = P.Border,
                focusedLabelColor = P.Accent, unfocusedLabelColor = P.Muted, cursorColor = P.Accent
            )
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { birthdayOpen = true }
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Birthday", color = P.Text, fontSize = 15.sp)
                Text("For a hello on the day. Day and month only.", color = P.Muted, fontSize = 12.sp)
            }
            Text(birthdayLabel(s.birthday), color = if (s.birthday.isBlank()) P.Faint else P.Accent, fontSize = 14.sp)
        }
        ToggleRow(
            "A hello that notices the day",
            "A birthday, a festival, the season turning.",
            s.contextGreeting
        ) { v -> vm.update { it.copy(contextGreeting = v) } }
        ChoiceRow(
            "Hello in Chinese", "Above the hello; tap it to hear it.",
            GreetingLanguage.entries, s.chineseGreeting, { it.label }
        ) { v -> vm.update { it.copy(chineseGreeting = v) } }
        ToggleRow(
            "Sloth moods",
            "A coffee in the morning, sleepy at night, a lantern on festival days, coral when the Lobster's back.",
            s.slothMoods
        ) { v -> vm.update { it.copy(slothMoods = v) } }

        Divider()
        Group("The date line")
        ToggleRow(
            "Festivals and solar terms",
            "The 24 solar terms (节气) and the traditional festivals: today's, or the next one.",
            s.festivals
        ) { v -> vm.update { it.copy(festivals = v) } }
        ToggleRow(
            "Chinese zodiac",
            "The double hour (时辰) after the lunar date, and a countdown in the New Year's last two months.",
            s.chineseZodiac
        ) { v -> vm.update { it.copy(chineseZodiac = v) } }
        ToggleRow(
            "Western zodiac",
            "The sign the Sun is in (♐︎ Sagittarius), at the end of the line.",
            s.westernZodiac
        ) { v -> vm.update { it.copy(westernZodiac = v) } }
        Text(
            "Tap the date on Home for the almanac: the hour, the next term and festival, the New Year, the Sun.",
            color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
        )

        Divider()
        Group("A little Chinese")
        ToggleRow("Word of the day", "Hear it, keep it as a flashcard, or ask the teacher about it.", s.wordOfDay) { v ->
            vm.update { it.copy(wordOfDay = v) }
        }
        if (s.wordOfDay) {
            ChoiceRow(
                "Words in", "Alternate takes turns: Mandarin one day, Cantonese the next.",
                WordLanguage.entries, s.wordLanguage, { it.label }
            ) { v -> vm.update { it.copy(wordLanguage = v) } }
        }
        ToggleRow("Idiom of the day", "A 成语 with its story, one tap away.", s.idiomOfDay) { v ->
            vm.update { it.copy(idiomOfDay = v) }
        }

        Divider()
        Group("Tiles")
        ToggleRow("SlothDesk in Today", "The latest paper tickets, while they're fresh.", s.deskTile) { v ->
            vm.update { it.copy(deskTile = v) }
        }
        ToggleRow("The Lobster's headline", "The newest catch's top headline on its Today tile.", s.lobsterHeadline) { v ->
            vm.update { it.copy(lobsterHeadline = v) }
        }
        ToggleRow(
            "Practice this week",
            "Seven dots by Tutors, and when you last practised each language. A chat with a tutor or a flashcard review counts.",
            s.practiceWeek
        ) { v -> vm.update { it.copy(practiceWeek = v) } }
        ToggleRow("Five-minute drills", "Long-press a tutor, or open its menu, for quick questions at your level.", s.tutorDrills) { v ->
            vm.update { it.copy(tutorDrills = v) }
        }
        ToggleRow("Ourjong", "A tile that opens the Ourjong mahjong app, if it's installed.", s.ourjongTile) { v ->
            vm.update { it.copy(ourjongTile = v) }
        }
    }

    if (birthdayOpen) {
        BirthdayDialog(
            current = HomeSettings.parseBirthday(s.birthday),
            onDone = { md ->
                birthdayOpen = false
                vm.update { it.copy(birthday = md?.let { (m, d) -> "%02d-%02d".format(m, d) }.orEmpty()) }
            },
            onDismiss = { birthdayOpen = false }
        )
    }
}

@Composable
private fun Group(text: String) {
    Text(
        text, color = P.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun Divider() = HorizontalDivider(color = P.Border, modifier = Modifier.padding(vertical = 8.dp))

/** A setting with a few choices, as chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(title: String, detail: String, options: List<T>, current: T, label: (T) -> String, onChange: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(title, color = P.Text, fontSize = 15.sp)
        Text(detail, color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                val chosen = option == current
                Text(
                    label(option),
                    color = if (chosen) P.Accent else P.Muted, fontSize = 13.sp,
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, if (chosen) P.Accent else P.Border, RoundedCornerShape(10.dp))
                        .clickable(role = Role.RadioButton) { onChange(option) }
                        .semantics { selected = chosen }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** "21 Apr", or "Not set". */
private fun birthdayLabel(text: String): String {
    val (m, d) = HomeSettings.parseBirthday(text) ?: return "Not set"
    return "$d ${DateFormatSymbols.getInstance().shortMonths[m - 1]}"
}

/** Day and month: no year is asked for or kept. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BirthdayDialog(current: Pair<Int, Int>?, onDone: (Pair<Int, Int>?) -> Unit, onDismiss: () -> Unit) {
    var month by remember { mutableIntStateOf(current?.first ?: 1) }
    var dayText by remember { mutableStateOf(current?.second?.toString().orEmpty()) }
    val months = DateFormatSymbols.getInstance().shortMonths.take(12)
    val day = dayText.toIntOrNull()
    val valid = day != null && HomeSettings.parseBirthday("%02d-%02d".format(month, day)) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Elevated,
        title = { Text("Birthday", color = P.Text) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    months.forEachIndexed { i, name ->
                        val chosen = month == i + 1
                        Text(
                            name, color = if (chosen) P.Accent else P.Muted, fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, if (chosen) P.Accent else P.Border, RoundedCornerShape(10.dp))
                                .clickable(role = Role.RadioButton) { month = i + 1 }
                                .semantics { selected = chosen }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = dayText,
                    onValueChange = { v -> dayText = v.filter { it.isDigit() }.take(2) },
                    label = { Text("Day", fontSize = 12.sp) },
                    singleLine = true,
                    isError = dayText.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.width(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = P.Text, unfocusedTextColor = P.Text,
                        focusedBorderColor = P.Accent, unfocusedBorderColor = P.Border,
                        focusedLabelColor = P.Accent, unfocusedLabelColor = P.Muted, cursorColor = P.Accent
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onDone(month to day!!) }, enabled = valid) { Text("Save", color = if (valid) P.Accent else P.Faint) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onDone(null) }) { Text("Clear", color = P.Muted) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = P.Muted) }
            }
        }
    )
}
