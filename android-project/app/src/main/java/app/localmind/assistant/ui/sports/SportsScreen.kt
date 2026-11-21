package app.localmind.assistant.ui.sports

import android.widget.Toast
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.common.Preset
import app.localmind.assistant.ui.common.CallItSheet
import app.localmind.assistant.domain.sports.Market
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
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

    // Upright, a game opens over the list. Sideways on a wide screen, the list stays on the left and
    // the game or race opens beside it.
    val list: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
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
                        else -> Games(league, data, yours, calls, viewModel)
                    }
                    Text(
                        "Results from ESPN's scoreboard and Jolpica-F1, through the Lobster. Ratings, tables and chances are worked out on the tablet. Paper only: nothing is ever bet.",
                        color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
    val showDetail = opened != null && data != null
    val detail: @Composable () -> Unit = {
        val o = opened
        val d = data
        if (o != null && d != null) {
            if (o.league.kind == Kind.RACE) RacePage(o, d, yours, viewModel)
            else d.games.firstOrNull { it.id == o.id }?.let { g -> GamePage(o.league, g, d, yours, calls, viewModel) }
                ?: Text("That game isn't here any more.", color = P.Muted, modifier = Modifier.padding(24.dp))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(P.Canvas)) {
        val twoPanes = maxWidth >= WIDE && maxWidth > maxHeight
        // Worked out here: maxWidth can't be read inside the Row, whose scope hides this one's.
        val listWidth = minOf(600.dp, maxWidth * 0.48f)
        when {
            twoPanes -> Row(Modifier.fillMaxSize()) {
                list(Modifier.width(listWidth).fillMaxHeight())
                Box(Modifier.width(1.dp).fillMaxHeight().background(P.Border))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (showDetail) detail() else Text(
                        "Tap a game for its numbers: ratings, your factors and prices, and the game played out.",
                        color = P.Faint, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(32.dp)
                    )
                }
            }
            showDetail -> detail()
            else -> list(Modifier.fillMaxSize())
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
private fun Games(league: League, data: LeagueData, yours: SportsYours, calls: List<Call>, vm: SportsViewModel) {
    if (league.kind == Kind.RACE) return Races(league, data, vm)
    val today = LocalDate.now()
    val coming = data.games.filter { !it.played && SportsViewModel.dayOf(it) >= today.minusDays(1) }.take(40)
    val done = data.games.filter { it.played }.sortedByDescending { it.date }.take(30)
    Section("Coming up", note = if (coming.isEmpty()) null else "Tap a game for its numbers") {
        if (coming.isEmpty()) EmptyLine("No games in the next two weeks.")
        coming.forEach { g -> GameRowView(league, g, data, yours, calls, vm) }
    }
    Section("Results") {
        if (done.isEmpty()) EmptyLine("No results yet.")
        done.forEach { g -> GameRowView(league, g, data, yours, calls, vm) }
    }
}

@Composable
private fun GameRowView(league: League, g: GameRow, data: LeagueData, yours: SportsYours, calls: List<Call>, vm: SportsViewModel) {
    val notes = yours.notes[vm.key(league, g.id)]
    val book = notes?.book ?: SportsLines.fromLine(g)
    // The model's favourite, for games still to come (a quick look; the page plays it out), as a
    // pick you can call from the row: its chance, and the market's with the margin out if there's a price.
    val lean = remember(g.id, data.games.size, notes) { if (g.played || g.state == "in") null else quickOffer(league, g, data, notes?.factors.orEmpty(), book) }
    val picked = calls.firstOrNull { it.open && it.source.startsWith("sports:${league.id}:${g.id}:") }
    var calling by remember { mutableStateOf(false) }
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
            lean != null -> {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${lean.label} ${Forecasts.percent(lean.model)}%", color = P.Muted, fontSize = 13.sp, maxLines = 1)
                    lean.marketChance?.let { m -> Text("market ${Forecasts.percent(m)}%", color = P.Faint, fontSize = 11.sp, maxLines = 1) }
                }
                if (picked != null) {
                    Text(
                        "picked · ${picked.latest?.chances?.firstOrNull()?.let { Forecasts.percent(it) } ?: "—"}%",
                        color = P.Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(P.AccentSoft).padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                } else {
                    Text(
                        "Call it", color = P.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).clickable { calling = true }.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
    if (calling && lean != null) {
        CallItSheet(
            where = "Sports · ${league.label}",
            question = lean.question,
            context = "Model ${Forecasts.percent(lean.model)}%" +
                (lean.marketChance?.let { " · market ${Forecasts.percent(it)}% (${book?.source ?: "the line"}, margin out)" } ?: " · no price yet") +
                (if (notes?.factors.isNullOrEmpty()) "" else " · with your factors"),
            start = lean.model,
            presets = listOfNotNull(
                Preset("Model ${Forecasts.percent(lean.model)}%", lean.model),
                lean.marketChance?.let { Preset("Market ${Forecasts.percent(it)}%", it) }
            ) + listOf(0.55, 0.7, 0.8).map { Preset("${Forecasts.percent(it)}", it) },
            settles = "Marks itself from the final score. Nothing to remember.",
            fixedCheckOn = SportsViewModel.dayOf(g).toString(),
            onDismiss = { calling = false },
            onCall = { _, chance, _, why ->
                val c = chance ?: lean.model
                val made = "model ${Forecasts.percent(lean.model)}%" + (lean.marketChance?.let { m -> ", market ${Forecasts.percent(m)}%" } ?: "")
                vm.pick(league, g.id, SportsViewModel.dayOf(g), lean.market, lean.arg, lean.question, c, lean.marketChance, book?.source ?: "", if (why.isBlank()) made else "$made. $why")
            }
        )
    }
}

/** The favourite on a game still to come, as an offer: who, the model's chance, the market's. */
private fun quickOffer(league: League, g: GameRow, data: LeagueData, factors: List<app.localmind.assistant.domain.sports.Factor>, book: app.localmind.assistant.domain.sports.Book?): Offer? {
    val day = whenOf(g).substringBefore(" ·")
    return when (league.kind) {
        Kind.POINTS -> {
            val p = SportsModels.play(SportsModels.expect(g, data.games, league, factors), null, null, runs = 4_000).homeWins
            val fair = book?.fair(false)
            if (p >= 0.5) Offer(Market.HOME, "", g.homeAbbr.ifBlank { g.home }, "${g.home} beat the ${g.away} ($day)", p, fair?.get(0))
            else Offer(Market.AWAY, "", g.awayAbbr.ifBlank { g.away }, "${g.away} beat the ${g.home} ($day)", 1 - p, fair?.get(1))
        }
        Kind.GOALS -> {
            val x = SportsModels.goals(g, data.games, factors)
            val fair = book?.fair(true)
            listOf(
                Offer(Market.HOME, "", g.home, "${g.home} beat ${g.away} ($day)", x.homeWin, fair?.get(0)),
                Offer(Market.DRAW, "", "Draw", "${g.home} and ${g.away} draw ($day)", x.draw, fair?.get(2)),
                Offer(Market.AWAY, "", g.away, "${g.away} beat ${g.home} ($day)", x.awayWin, fair?.get(1))
            ).maxBy { it.model }
        }
        Kind.RACE -> null
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
    OutlinedButton(onClick = onForecasts) { Text("Your record", color = P.Text) }
}
