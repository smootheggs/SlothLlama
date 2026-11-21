package app.localmind.assistant.ui.sports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.data.sports.SportsYours
import app.localmind.assistant.domain.forecasts.Forecasts
import app.localmind.assistant.domain.sports.DriverOdds
import app.localmind.assistant.domain.sports.Factor
import app.localmind.assistant.domain.sports.LeagueData
import app.localmind.assistant.domain.sports.Market
import app.localmind.assistant.domain.sports.SportsModels
import app.localmind.assistant.ui.common.ChoicePill
import app.localmind.assistant.ui.common.EditDialog
import app.localmind.assistant.ui.common.FormField
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.common.SmallLabel
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** A race: its result once run; before that, every driver's chances, from their recent races and your factors. */
@Composable
internal fun RacePage(o: Opened, data: LeagueData, yours: SportsYours, vm: SportsViewModel) {
    val round = o.id.toIntOrNull() ?: return
    val race = data.races.firstOrNull { it.round == round } ?: return
    val factors = yours.raceFactors["f1:$round"].orEmpty()
    var seed by remember { mutableIntStateOf(1) }
    var adding by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val before = remember(data.races, round) { data.races.filter { it.round < round } }
    val odds by produceState(emptyList<DriverOdds>(), before, factors, seed) {
        value = withContext(Dispatchers.Default) { SportsModels.race(before, factors, seed = seed) }
    }
    Column(Modifier.fillMaxSize()) {
        SlothTopBar(title = race.name, subtitle = "Formula 1 · round $round · ${race.date}", onBack = { vm.open(null) })
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 900.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (race.results.isNotEmpty()) {
                    Card {
                        SmallLabel("The result")
                        race.results.sortedBy { it.pos ?: 99 }.forEach { f ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(if (f.classified) "${f.pos}" else "—", color = P.Faint, fontSize = 13.sp, modifier = Modifier.width(32.dp))
                                Text(f.driver, color = P.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Text(if (f.classified) f.team else f.status, color = P.Muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallLabel(if (race.results.isEmpty()) "${SportsModels.RUNS / 1000},000 races, run on the tablet" else "What the model gave it beforehand", Modifier.weight(1f))
                    }
                    if (odds.isEmpty()) Text("No races run yet this season to go on.", color = P.Faint, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth()) {
                        SmallLabel("Driver", Modifier.weight(1f))
                        listOf("Form", "Out", "Win", "Podium", "Points").forEach { SmallLabel(it, Modifier.width(56.dp)) }
                    }
                    odds.take(20).forEach { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.driver, color = P.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(d.team, color = P.Faint, fontSize = 11.sp, maxLines = 1)
                            }
                            Text(d.avg?.let { String.format(Locale.ENGLISH, "%.1f", it) } ?: "—", color = P.Muted, fontSize = 13.sp, modifier = Modifier.width(56.dp))
                            Text("${Forecasts.percent(d.dnfRate)}%", color = P.Muted, fontSize = 13.sp, modifier = Modifier.width(56.dp))
                            Text("${Forecasts.percent(d.win)}%", color = P.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(56.dp))
                            Text("${Forecasts.percent(d.podium)}%", color = P.Text, fontSize = 13.sp, modifier = Modifier.width(56.dp))
                            Text("${Forecasts.percent(d.points)}%", color = P.Muted, fontSize = 13.sp, modifier = Modifier.width(56.dp))
                        }
                    }
                    Text(
                        "Form is the average finishing place over the last six races; Out is the chance of not finishing, from how often they didn't. A spread of about two and a half places either way.",
                        color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp
                    )
                }
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallLabel("What you know", Modifier.weight(1f))
                        TextButton(onClick = { adding = true }) { Text("Add", color = P.Accent) }
                    }
                    if (factors.isEmpty()) Text("A penalty, a new floor, a wet race: move a driver up or down a few places.", color = P.Faint, fontSize = 13.sp)
                    factors.forEachIndexed { i, f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${f.driver}: ${f.label}", color = P.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(String.format(Locale.ENGLISH, "%+.1f places", f.value), color = if (f.value > 0) P.Warning else P.Accent, fontSize = 14.sp)
                            TextButton(onClick = { vm.saveRaceFactors(round, factors.filterIndexed { j, _ -> j != i }) }) { Text("Remove", color = P.Muted) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (race.results.isEmpty() && odds.isNotEmpty()) {
                        Button(onClick = { picking = true }, colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent)) { Text("Make a pick") }
                    }
                    OutlinedButton(onClick = { seed++ }) { Text("Run it again", color = P.Text) }
                }
            }
        }
    }
    if (adding) RaceFactorDialog(odds.map { it.driver }, onDismiss = { adding = false }) { vm.saveRaceFactors(round, factors + it) }
    if (picking) {
        val offers = odds.take(8).flatMap { d ->
            listOf(
                Offer(Market.WIN, d.driver, "${d.driver} wins", "${d.driver} wins the ${race.name}", d.win, null),
                Offer(Market.PODIUM, d.driver, "${d.driver} on the podium", "${d.driver} finishes on the podium at the ${race.name}", d.podium, null)
            )
        }
        PickDialog(offers, onDismiss = { picking = false }) { pick, chance, why ->
            vm.pick(o.league, round.toString(), runCatching { LocalDate.parse(race.date) }.getOrDefault(LocalDate.now()), pick.market, pick.arg, pick.question, chance, null, "", why)
        }
    }
}

@Composable
private fun RaceFactorDialog(drivers: List<String>, onDismiss: () -> Unit, onSave: (Factor) -> Unit) {
    var driver by remember { mutableStateOf(drivers.firstOrNull().orEmpty()) }
    var label by remember { mutableStateOf("") }
    var places by remember { mutableFloatStateOf(0f) }
    EditDialog("What you know", onDismiss, { onSave(Factor(label.trim(), value = places.toDouble(), driver = driver)); onDismiss() },
        canSave = driver.isNotBlank() && label.isNotBlank() && places != 0f) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            drivers.forEach { d -> ChoicePill(d.substringAfterLast(' '), driver == d, { driver = d }) }
        }
        FormField("Why", label, { label = it.take(60) }, hint = "Five-place grid penalty")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Places (− is better)", color = P.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(String.format(Locale.ENGLISH, "%+.1f", places), color = if (places > 0) P.Warning else P.Accent, fontSize = 18.sp)
        }
        Slider(
            value = places, onValueChange = { places = (it * 2).roundToInt() / 2f }, valueRange = -5f..5f,
            colors = SliderDefaults.colors(thumbColor = P.Accent, activeTrackColor = P.Accent, inactiveTrackColor = P.Elevated)
        )
    }
}
