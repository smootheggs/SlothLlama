package app.localmind.assistant.ui.sports

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.sports.SportsYours
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.Forecasts
import app.localmind.assistant.domain.sports.GameRow
import app.localmind.assistant.domain.sports.Kind
import app.localmind.assistant.domain.sports.League
import app.localmind.assistant.domain.sports.LeagueData
import app.localmind.assistant.domain.sports.SportsBundle
import app.localmind.assistant.domain.sports.SportsLines
import app.localmind.assistant.domain.sports.SportsModels
import app.localmind.assistant.ui.common.ChoicePill
import app.localmind.assistant.ui.common.EmptyLine
import app.localmind.assistant.ui.common.Section
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.common.SmallLabel
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WHEN = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.ENGLISH)

/** "Sat 21 Nov · 19:00", in the tablet's time. */
internal fun whenOf(g: GameRow): String =
    runCatching { java.time.Instant.parse(g.date.let { if (it.length == 17) it.replace("Z", ":00Z") else it }).atZone(ZoneId.systemDefault()).format(WHEN) }
        .getOrDefault(g.date.take(10))

/**
 * Sports, under Weigh: games and results the Lobster brings, tables worked out from them, and your
 * picks. A game opens to its numbers: ratings, your factors and prices, and the game played out.
 */
@Composable
fun SportsScreen(viewModel: SportsViewModel, onBack: () -> Unit, onForecasts: () -> Unit) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val yours by viewModel.yours.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val chosen by viewModel.league.collectAsStateWithLifecycle()
    val opened by viewModel.opened.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearMessage() }
    }
    BackHandler(enabled = opened != null) { viewModel.open(null) }

    val followed = yours.follow.mapNotNull { League.of(it) }
    val league = chosen ?: followed.firstOrNull { results?.leagues?.containsKey(it.id) == true } ?: followed.firstOrNull()
    val data = league?.let { results?.leagues?.get(it.id) }

    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        val o = opened
        if (o != null && data != null) {
            if (o.league.kind == Kind.RACE) RacePage(o, data, yours, viewModel)
            else data.games.firstOrNull { it.id == o.id }?.let { g -> GamePage(o.league, g, data, yours, calls, viewModel) }
                ?: Text("That game isn't here any more.", color = P.Muted, modifier = Modifier.padding(24.dp))
        } else {
            Column(Modifier.fillMaxSize()) {
                SlothTopBar(title = "Sports", subtitle = followed.joinToString(" · ") { it.label }.ifBlank { "Pick what you follow" }, onBack = onBack) {
                    TextButton(onClick = { viewModel.askLobster(daily = !yours.dailySent) }) { Text("Ask the Lobster", color = P.Lobster, fontSize = 14.sp) }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SportsTab.entries.forEach { t -> ChoicePill(t.label, tab == t, { viewModel.showTab(t) }) }
                    Box(Modifier.width(12.dp))
                    followed.forEach { l -> ChoicePill(l.label, league == l, { viewModel.showLeague(l) }, color = P.Muted) }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier.widthIn(max = 1000.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when {
                            results == null -> Setup(yours, viewModel)
                            tab == SportsTab.PICKS -> Picks(calls, results!!, onForecasts)
                            league == null || data == null -> {
                                EmptyLine("Nothing for ${league?.label ?: "this league"} yet. Ask the Lobster, or pick another.")
                                Setup(yours, viewModel)
                            }
                            tab == SportsTab.TABLES -> Tables(league, data)
                            else -> Games(league, data, yours, viewModel)
                        }
                        Text(
                            "Results from ESPN's scoreboard and Jolpica-F1, through the Lobster. Ratings, tables and chances are worked out on the tablet. Paper only: nothing is ever bet.",
                            color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Setup(yours: SportsYours, vm: SportsViewModel) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("What you follow", color = P.Text, fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
        Text(
            "The Lobster brings this season's results and the next two weeks' games (with the day's line, where ESPN shows one), " +
                "then fresh ones every morning. Everything else is worked out here.",
            color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            League.entries.forEach { l ->
                val on = l.id in yours.follow
                ChoicePill(l.label, on, { vm.follow(if (on) yours.follow - l.id else yours.follow + l.id) })
            }
        }
        Button(onClick = { vm.askLobster(daily = true) }, colors = ButtonDefaults.buttonColors(containerColor = P.Lobster, contentColor = P.OnAccent)) {
            Text(if (yours.backfillSent) "Ask the Lobster again" else "Ask the Lobster for results")
        }
        Text("Needs SlothLobster 1.9. It reads public scoreboards only, and never bets or posts.", color = P.Faint, fontSize = 12.sp)
    }
}

@Composable
private fun Games(league: League, data: LeagueData, yours: SportsYours, vm: SportsViewModel) {
    if (league.kind == Kind.RACE) return Races(league, data, vm)
    val today = LocalDate.now()
    val coming = data.games.filter { !it.played && SportsViewModel.dayOf(it) >= today.minusDays(1) }.take(40)
    val done = data.games.filter { it.played }.sortedByDescending { it.date }.take(30)
    Section("Coming up", note = if (coming.isEmpty()) null else "Tap a game for its numbers") {
        if (coming.isEmpty()) EmptyLine("No games in the next two weeks.")
        coming.forEach { g -> GameRowView(league, g, data, yours, vm) }
    }
    Section("Results") {
        if (done.isEmpty()) EmptyLine("No results yet.")
        done.forEach { g -> GameRowView(league, g, data, yours, vm) }
    }
}

@Composable
private fun GameRowView(league: League, g: GameRow, data: LeagueData, yours: SportsYours, vm: SportsViewModel) {
    val notes = yours.notes[vm.key(league, g.id)]
    // The model's favourite, for games still to come (a quick look; the page plays it out).
    val lean = remember(g.id, data.games.size, notes) {
        if (g.played) null else when (league.kind) {
            Kind.POINTS -> SportsModels.expect(g, data.games, league, notes?.factors.orEmpty()).let { e ->
                val p = SportsModels.play(e, null, null, runs = 4_000).homeWins
                if (p >= 0.5) "${g.homeAbbr.ifBlank { g.home }} ${Forecasts.percent(p)}%" else "${g.awayAbbr.ifBlank { g.away }} ${Forecasts.percent(1 - p)}%"
            }
            Kind.GOALS -> SportsModels.goals(g, data.games, notes?.factors.orEmpty()).let { x ->
                val best = listOf(g.home to x.homeWin, "Draw" to x.draw, g.away to x.awayWin).maxBy { it.second }
                "${best.first} ${Forecasts.percent(best.second)}%"
            }
            Kind.RACE -> null
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp)).clickable { vm.open(Opened(league, g.id)) }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(whenOf(g), color = P.Faint, fontSize = 12.sp, modifier = Modifier.width(118.dp))
        // American leagues list the away side first ("Lakers at Warriors"); football the home side.
        val title = if (league.kind == Kind.GOALS) "${g.home} v ${g.away}" else "${g.away} at ${g.home}"
        Text(title, color = P.Text, fontSize = 15.sp, fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        when {
            g.played -> Text(
                if (league.kind == Kind.GOALS) "${g.homeScore}–${g.awayScore}" else "${g.awayScore}–${g.homeScore}",
                color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium
            )
            g.state == "in" -> Text("on now", color = P.Accent, fontSize = 13.sp)
            lean != null -> Text(lean, color = P.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Races(league: League, data: LeagueData, vm: SportsViewModel) {
    val next = data.races.firstOrNull { it.results.isEmpty() }
    Section("The calendar", note = next?.let { "Next: ${it.name}, ${it.date}" }) {
        data.races.forEach { r ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).clickable { vm.open(Opened(league, r.round.toString())) }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("R${r.round}", color = P.Faint, fontSize = 12.sp, modifier = Modifier.width(36.dp))
                Text(r.name, color = P.Text, fontSize = 15.sp, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.results.firstOrNull { it.pos == 1 }?.driver ?: r.date, color = if (r.results.isEmpty()) P.Faint else P.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Tables(league: League, data: LeagueData) {
    if (league.kind == Kind.RACE) {
        val table = remember(data.races) { SportsModels.standings(data.races) }
        Section("Drivers", note = "Points from finishing places (25, 18, 15… 1); sprints and bonus points aren't counted") {
            table.forEachIndexed { i, (d, pts) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
                    Text("${i + 1}", color = P.Faint, fontSize = 13.sp, modifier = Modifier.width(32.dp))
                    Text(d, color = P.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("$pts", color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        return
    }
    val table = remember(data.games) { SportsModels.table(data.games, league.kind) }
    val goals = league.kind == Kind.GOALS
    Section(if (goals) "The table" else "All teams", note = if (goals) "Points, then goal difference; counted from the results" else "By wins, counted from the results; not split by conference") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            SmallLabel("Team", Modifier.weight(1f))
            listOf("P", "W", if (goals) "D" else "", "L", if (goals) "GD" else "+/−", if (goals) "Pts" else "Form").forEach { h ->
                if (h.isNotEmpty()) SmallLabel(h, Modifier.width(if (h == "Form") 64.dp else 40.dp))
            }
        }
        table.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}  ${r.team}", color = P.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                listOfNotNull(
                    "${r.played}", "${r.won}", if (goals) "${r.drawn}" else null, "${r.lost}",
                    (if (r.diff > 0) "+" else "") + r.diff, if (goals) "${r.points}" else r.form
                ).forEachIndexed { j, v ->
                    Text(v, color = if (j == 5 || (!goals && j == 4)) P.Text else P.Muted, fontSize = 13.sp, modifier = Modifier.width(if (!goals && j == 4) 64.dp else 40.dp))
                }
            }
        }
    }
}

@Composable
private fun Picks(calls: List<Call>, results: SportsBundle, onForecasts: () -> Unit) {
    val mine = calls.filter { it.source.startsWith("sports:") }.sortedByDescending { it.createdAt }
    // Beating the closing line: the market's chance at the start against the one you picked at.
    val clv = mine.mapNotNull { c ->
        val pick = SportsLines.pickOf(c.source) ?: return@mapNotNull null
        val atPick = c.market.firstOrNull()?.chances?.get(0) ?: return@mapNotNull null
        val g = results.leagues[pick.league.id]?.games?.firstOrNull { it.id == pick.id } ?: return@mapNotNull null
        val fair = SportsLines.fromLine(g)?.fair(false) ?: return@mapNotNull null
        val closing = when (pick.market) {
            app.localmind.assistant.domain.sports.Market.HOME -> fair[0]
            app.localmind.assistant.domain.sports.Market.AWAY -> fair[1]
            else -> return@mapNotNull null
        }
        (closing - atPick) * 100
    }
    Section("Your picks", note = if (mine.isEmpty()) null else "Kept in Forecasts; each marks itself when its result comes in") {
        if (mine.isEmpty()) EmptyLine("Open a game and make a pick: it goes to Forecasts with your chance and the market's.")
        mine.take(40).forEach { c ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(c.question, color = P.Text, fontSize = 15.sp, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${Forecasts.percent(c.latest?.chances?.get(0) ?: 0.0)}%", color = P.Text, fontSize = 14.sp)
                Text(
                    when { c.void -> "void"; c.happened == 0 -> "won"; c.happened == 1 -> "lost"; else -> "open" },
                    color = when (c.happened) { 0 -> P.Success; 1 -> P.Warning; else -> P.Faint }, fontSize = 13.sp, modifier = Modifier.width(44.dp)
                )
            }
        }
    }
    if (clv.isNotEmpty()) {
        val avg = clv.average()
        Text(
            String.format(Locale.ENGLISH, "Against the closing line: %+.1f points on average over %d money-line picks. Above 0 means the price moved your way after you picked.", avg, clv.size),
            color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp
        )
    }
    OutlinedButton(onClick = onForecasts) { Text("Open Forecasts", color = P.Text) }
}
