package app.localmind.assistant.ui.sports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.data.sports.GameNotes
import app.localmind.assistant.data.sports.SportsYours
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.Forecasts
import app.localmind.assistant.domain.odds.Odds
import app.localmind.assistant.domain.sports.Book
import app.localmind.assistant.domain.sports.Factor
import app.localmind.assistant.domain.sports.GameRow
import app.localmind.assistant.domain.sports.Goals
import app.localmind.assistant.domain.sports.Kind
import app.localmind.assistant.domain.sports.League
import app.localmind.assistant.domain.sports.LeagueData
import app.localmind.assistant.domain.sports.Market
import app.localmind.assistant.domain.sports.SportsLines
import app.localmind.assistant.domain.sports.SportsModels
import app.localmind.assistant.ui.common.ChoicePill
import app.localmind.assistant.ui.common.EditDialog
import app.localmind.assistant.ui.common.FormField
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.common.SmallLabel
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private fun f1(v: Double) = String.format(Locale.ENGLISH, "%.1f", v)
private fun f2(v: Double) = String.format(Locale.ENGLISH, "%.2f", v)
private fun signed(v: Double) = String.format(Locale.ENGLISH, "%+.1f", v)

/** One pick on offer: what it is, the model's chance, the market's (margin out) if there's a price. */
internal data class Offer(val market: Market, val arg: String, val label: String, val question: String, val model: Double, val marketChance: Double?)

@Composable
internal fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

@Composable
internal fun Stat(value: String, label: String, color: Color = P.Text) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = color, fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
        Text(label, color = P.Muted, fontSize = 12.sp)
    }
}

/** Two thin bars, the model's over the market's, and their numbers. */
@Composable
internal fun Versus(label: String, model: Double, market: Double?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = P.Muted, fontSize = 14.sp, modifier = Modifier.width(170.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Bar(model, P.Accent)
            if (market != null) Bar(market, P.Muted)
        }
        Text("${Forecasts.percent(model)}%", color = P.Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(44.dp))
        Text(market?.let { "${Forecasts.percent(it)}%" } ?: "—", color = P.Muted, fontSize = 15.sp, modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun Bar(p: Double, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(P.Elevated)) {
        Box(Modifier.fillMaxWidth(p.toFloat().coerceIn(0.01f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

/** A game's page: football by goals, the rest by points. */
@Composable
internal fun GamePage(league: League, g: GameRow, data: LeagueData, yours: SportsYours, calls: List<Call>, vm: SportsViewModel) {
    val notes = yours.notes[vm.key(league, g.id)] ?: GameNotes()
    val book = notes.book ?: SportsLines.fromLine(g)
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var seed by remember { mutableIntStateOf(1) }
    val goals = league.kind == Kind.GOALS
    val records = remember(data.games) { SportsModels.records(data.games.filter { it.date < g.date }).associateBy { it.team } }

    Column(Modifier.fillMaxSize()) {
        SlothTopBar(
            title = if (goals) "${g.home} v ${g.away}" else "${g.away} at ${g.home}",
            subtitle = "${league.label} · ${whenOf(g)}",
            onBack = { vm.open(null) }
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 900.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamBlock(g.home, "home", records[g.home], Modifier.weight(1f))
                        Text(
                            if (g.played) "${g.homeScore} – ${g.awayScore}" else "v",
                            color = if (g.played) P.Text else P.Faint, fontSize = if (g.played) 28.sp else 16.sp, fontFamily = FontFamily.Serif
                        )
                        TeamBlock(g.away, "away", records[g.away], Modifier.weight(1f), end = true)
                    }
                }
                val offers = if (goals) goalsCards(g, data, notes, book, seed) else pointsCards(league, g, data, notes, book, seed)
                if (picking) PickDialog(offers, onDismiss = { picking = false }) { o, chance, why ->
                    vm.pick(league, g.id, SportsViewModel.dayOf(g), o.market, o.arg, o.question, chance, o.marketChance, book?.source ?: "", why)
                }
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallLabel("Your prices and what you know", Modifier.weight(1f))
                        TextButton(onClick = { editing = true }) { Text("Edit", color = P.Accent) }
                    }
                    BookLine(book, goals)
                    if (notes.factors.isEmpty()) Text("No factors of yours yet: absences, rest, travel, anything the results don't show.", color = P.Faint, fontSize = 13.sp)
                    notes.factors.forEach { f ->
                        Row {
                            Text("${f.label} (${if (f.toHome) g.home else g.away})", color = P.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(signed(f.value) + if (goals) " goals" else " pts", color = if (f.value < 0) P.Warning else P.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!g.played) Button(onClick = { picking = true }, colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent)) { Text("Make a pick") }
                    OutlinedButton(onClick = { seed++ }) { Text("Play it again", color = P.Text) }
                }
                val existing = calls.filter { it.source.startsWith("sports:${league.id}:${g.id}:") }
                if (existing.isNotEmpty()) {
                    Text("In Forecasts: " + existing.joinToString(" · ") { "${it.question} (${Forecasts.percent(it.latest?.chances?.get(0) ?: 0.0)}%)" }, color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
    if (editing) NotesDialog(g, goals, notes, book, onDismiss = { editing = false }) { vm.saveNotes(league, g.id, it) }
}

@Composable
private fun TeamBlock(name: String, side: String, r: app.localmind.assistant.domain.sports.TeamRecord?, modifier: Modifier, end: Boolean = false) {
    Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, color = P.Text, fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
        Text(side, color = P.Faint, fontSize = 12.sp)
        if (r != null && r.played > 0) {
            Text("${r.won}–${if (r.drawn > 0) "${r.drawn}–" else ""}${r.lost} · form ${r.form}", color = P.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BookLine(book: Book?, goals: Boolean) {
    if (book == null || book.empty) {
        Text("No prices: paste or type them in Edit, or ask the Lobster for the day's line.", color = P.Faint, fontSize = 13.sp)
        return
    }
    val parts = listOfNotNull(
        book.homeMoney?.let { "home $it" }, if (goals) book.draw?.let { "draw $it" } else null, book.awayMoney?.let { "away $it" },
        book.homeLine?.let { "home ${signed(it)}" + (book.linePrice?.let { p -> " ($p)" } ?: "") },
        book.total?.let { "total ${f1(it)}" + (book.overPrice?.let { p -> " (over $p)" } ?: "") }
    )
    Text(parts.joinToString(" · "), color = P.Text, fontSize = 15.sp)
    val margin = book.margin(goals)
    Text(
        "From ${book.source}" + (margin?.let { " · the book's margin ${f1(it * 100)}%, taken out" } ?: ""),
        color = P.Faint, fontSize = 12.sp
    )
}

/** NBA and NFL: the expected margin, the game played out, and what's on offer. */
@Composable
private fun pointsCards(league: League, g: GameRow, data: LeagueData, notes: GameNotes, book: Book?, seed: Int): List<Offer> {
    val e = remember(g, data.games.size, notes) { SportsModels.expect(g, data.games, league, notes.factors).let { it.copy(total = it.total + notes.totalShift) } }
    val played by produceState<app.localmind.assistant.domain.sports.Played?>(null, e, book, seed) {
        value = withContext(Dispatchers.Default) { SportsModels.play(e, book?.homeLine, book?.total, seed = seed) }
    }
    val fair = book?.fair(false)
    Card {
        SmallLabel("What goes in")
        Row { Text("Ratings from ${e.games} results (Elo, home edge in)", color = P.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text("${g.home} ${signed(e.ratingPoints)}", color = P.Text, fontSize = 14.sp) }
        if (notes.factors.isNotEmpty()) Row { Text("Your factors", color = P.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text(signed(e.margin - e.ratingPoints), color = P.Text, fontSize = 14.sp) }
        Row { Text("Expected margin", color = P.Text, fontSize = 15.sp, modifier = Modifier.weight(1f)); Text("${g.home} ${signed(e.margin)}", color = P.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        Row { Text("Expected total", color = P.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text(f1(e.total), color = P.Text, fontSize = 14.sp) }
    }
    val p = played ?: return emptyList()
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallLabel("${SportsModels.RUNS / 1000},000 games, played on the tablet", Modifier.weight(1f))
            Text("● model", color = P.Accent, fontSize = 12.sp)
            Text("   ● market", color = P.Muted, fontSize = 12.sp)
        }
        Margins(p, g, book?.homeLine)
        Versus("${g.home} win", p.homeWins, fair?.get(0))
        book?.homeLine?.let { Versus("${g.home} ${signed(it)}", p.homeCovers ?: 0.0, null) }
        book?.total?.let { Versus("Over ${f1(it)}", p.over ?: 0.0, null) }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat(f2(Odds.fairOdds(p.homeWins.coerceIn(0.01, 0.99))), "fair odds, ${g.home}")
            fair?.let { Stat(signed(Odds.edgePoints(p.homeWins, it[0])) + " pts", "model against the market", if (p.homeWins > it[0]) P.Accent else P.Muted) }
            Stat("±${f1(e.marginSd)}", "margin spread, this season")
        }
    }
    val day = whenOf(g).substringBefore(" ·")
    return buildList {
        add(Offer(Market.HOME, "", "${g.home} win", "${g.home} beat the ${g.away} ($day)", p.homeWins, fair?.get(0)))
        add(Offer(Market.AWAY, "", "${g.away} win", "${g.away} beat the ${g.home} ($day)", 1 - p.homeWins, fair?.get(1)))
        book?.homeLine?.let { l ->
            val c = p.homeCovers ?: 0.0
            add(Offer(Market.HOME_LINE, l.toString(), "${g.home} ${signed(l)}", "${g.home} cover ${signed(l)} against the ${g.away} ($day)", c, null))
            add(Offer(Market.AWAY_LINE, (-l).toString(), "${g.away} ${signed(-l)}", "${g.away} cover ${signed(-l)} against the ${g.home} ($day)", (1 - c - p.pushes).coerceAtLeast(0.0), null))
        }
        book?.total?.let { t ->
            val o = p.over ?: 0.0
            add(Offer(Market.OVER, t.toString(), "Over ${f1(t)}", "${g.away} at ${g.home}: over ${f1(t)} points ($day)", o, null))
            add(Offer(Market.UNDER, t.toString(), "Under ${f1(t)}", "${g.away} at ${g.home}: under ${f1(t)} points ($day)", 1 - o, null))
        }
    }
}

/** The margins the simulated games ended with: the home side's wins in moss, the line dashed. */
@Composable
private fun Margins(p: app.localmind.assistant.domain.sports.Played, g: GameRow, line: Double?) {
    val win = P.Accent
    val lose = P.BorderStrong
    val mark = P.Text
    val dash = P.Muted
    val top = p.bins.maxOf { it.second }.coerceAtLeast(1e-9)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val w = size.width / p.bins.size
        p.bins.forEachIndexed { i, (from, share) ->
            val h = (share / top).toFloat() * (size.height - 8f)
            drawRect(if (from >= 0) win else lose, Offset(i * w + 1f, size.height - h), Size(w - 2f, h))
        }
        val lo = p.bins.first().first
        val x0 = ((0 - lo) / p.step).toFloat() * w
        drawLine(mark, Offset(x0, 0f), Offset(x0, size.height), 2f)
        line?.let { l ->
            val x = ((-l - lo) / p.step).toFloat() * w
            drawLine(dash, Offset(x, 0f), Offset(x, size.height), 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text("${g.away} by more", color = P.Faint, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text("${g.home} by more", color = P.Faint, fontSize = 11.sp)
    }
}

/** Football: expected goals, every score, and what's on offer. */
@Composable
private fun goalsCards(g: GameRow, data: LeagueData, notes: GameNotes, book: Book?, seed: Int): List<Offer> {
    val x: Goals = remember(g, data.games.size, notes, seed) { SportsModels.goals(g, data.games, notes.factors) }
    val fair = book?.fair(true)
    Card {
        SmallLabel("Goals expected, from ${data.games.count { it.played && it.date < g.date }} results")
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Stat(f2(x.home), g.home)
            Stat(f2(x.away), g.away)
        }
        Text("Each side's scoring and conceding, home and away, against the league's averages (a Poisson model); your factors add or take away goals.", color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
    }
    Card {
        Row { SmallLabel("Every score, in percent", Modifier.weight(1f)); Text("${g.away} →", color = P.Faint, fontSize = 12.sp) }
        ScoreGrid(x)
        Text("${g.home} ↓ · darker is likelier", color = P.Faint, fontSize = 12.sp)
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallLabel("Against your prices", Modifier.weight(1f))
            Text("● model", color = P.Accent, fontSize = 12.sp)
            Text("   ● market", color = P.Muted, fontSize = 12.sp)
        }
        Versus(g.home, x.homeWin, fair?.get(0))
        Versus("Draw", x.draw, fair?.get(2))
        Versus(g.away, x.awayWin, fair?.get(1))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat("${Forecasts.percent(x.over(2.5))}%", "over 2.5 goals")
            Stat("${Forecasts.percent(x.bothScore)}%", "both teams score")
            Stat(f2(Odds.fairOdds(x.homeWin.coerceIn(0.01, 0.99))), "fair odds, ${g.home}")
        }
    }
    val day = whenOf(g).substringBefore(" ·")
    return listOf(
        Offer(Market.HOME, "", "${g.home} win", "${g.home} beat ${g.away} ($day)", x.homeWin, fair?.get(0)),
        Offer(Market.DRAW, "", "Draw", "${g.home} and ${g.away} draw ($day)", x.draw, fair?.get(2)),
        Offer(Market.AWAY, "", "${g.away} win", "${g.away} beat ${g.home} ($day)", x.awayWin, fair?.get(1)),
        Offer(Market.OVER, "2.5", "Over 2.5 goals", "${g.home} v ${g.away}: three goals or more ($day)", x.over(2.5), null),
        Offer(Market.UNDER, "2.5", "Under 2.5 goals", "${g.home} v ${g.away}: two goals or fewer ($day)", 1 - x.over(2.5), null),
        Offer(Market.BOTH_SCORE, "", "Both score", "${g.home} and ${g.away} both score ($day)", x.bothScore, null)
    )
}

@Composable
private fun ScoreGrid(x: Goals) {
    val top = x.grid.take(6).maxOf { row -> row.take(6).maxOf { it } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(24.dp))
            (0..5).forEach { a -> Text("$a", color = P.Faint, fontSize = 12.sp, modifier = Modifier.weight(1f)) }
        }
        (0..5).forEach { h ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$h", color = P.Faint, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                (0..5).forEach { a ->
                    val p = x.grid[h][a]
                    val alpha = (0.08 + 0.85 * p / top).toFloat()
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(6.dp)).background(P.Accent.copy(alpha = alpha)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(f1(p * 100), color = if (alpha > 0.5f) P.OnAccent else P.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// --- dialogs ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotesDialog(g: GameRow, goals: Boolean, notes: GameNotes, book: Book?, onDismiss: () -> Unit, onSave: (GameNotes) -> Unit) {
    var paste by remember { mutableStateOf("") }
    var home by remember { mutableStateOf(book?.homeMoney.orEmpty()) }
    var away by remember { mutableStateOf(book?.awayMoney.orEmpty()) }
    var draw by remember { mutableStateOf(book?.draw.orEmpty()) }
    var line by remember { mutableStateOf(book?.homeLine?.toString().orEmpty()) }
    var total by remember { mutableStateOf(book?.total?.toString().orEmpty()) }
    var factors by remember { mutableStateOf(notes.factors) }
    var label by remember { mutableStateOf("") }
    var toHome by remember { mutableStateOf(true) }
    var value by remember { mutableFloatStateOf(0f) }
    val step = if (goals) 0.05f else 0.5f

    fun ok(s: String) = s.isBlank() || Odds.parse(s) != null
    val valid = ok(home) && ok(away) && (!goals || ok(draw)) && (line.isBlank() || line.toDoubleOrNull() != null) && (total.isBlank() || total.toDoubleOrNull() != null)
    EditDialog("Prices and what you know", onDismiss, {
        val b = Book(
            homeMoney = home.trim().ifBlank { null }, awayMoney = away.trim().ifBlank { null }, draw = if (goals) draw.trim().ifBlank { null } else null,
            homeLine = line.toDoubleOrNull(), linePrice = book?.linePrice, total = total.toDoubleOrNull(), overPrice = book?.overPrice,
            source = if (book != null && home == book.homeMoney.orEmpty() && away == book.awayMoney.orEmpty() && book.source != "you") book.source else "you"
        )
        onSave(notes.copy(book = b.takeIf { !it.empty }, factors = factors))
        onDismiss()
    }, canSave = valid, saveLabel = "Save") {
        FormField("Paste a line", paste, { v ->
            paste = v.take(400)
            val b = SportsLines.paste(paste, g, goals)
            b.homeMoney?.let { home = it }
            b.awayMoney?.let { away = it }
            b.draw?.let { draw = it }
            b.homeLine?.let { line = it.toString() }
            b.total?.let { total = it.toString() }
        }, lines = 2, hint = if (goals) "Arsenal 2.05 · Draw 3.50 · Chelsea 3.70" else "Warriors −115 · Lakers −105 · Spread GSW −1.5 · O/U 228.5")
        Text("Read on the tablet: American (−115), decimal (1.87) or fractional (20/23).", color = P.Faint, fontSize = 12.sp)
        FormField(g.home, home, { home = it.take(10) })
        if (goals) FormField("Draw", draw, { draw = it.take(10) })
        FormField(g.away, away, { away = it.take(10) })
        if (!goals) {
            FormField("${g.home}'s spread", line, { line = it.take(8) }, hint = "−1.5 means they give 1.5")
            FormField("Total", total, { total = it.take(8) }, hint = "228.5")
        }
        val prices = listOfNotNull(Odds.parse(home), Odds.parse(away)) + if (goals) listOfNotNull(Odds.parse(draw)) else emptyList()
        if (prices.size == (if (goals) 3 else 2)) {
            val fair = Odds.fair(prices)
            Text(
                "The book's margin: ${f1(Odds.margin(prices) * 100)}%. Without it: " + fair.joinToString(" · ") { "${Forecasts.percent(it)}%" },
                color = P.Muted, fontSize = 13.sp
            )
        }
        SmallLabel("What results don't show")
        factors.forEachIndexed { i, f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${f.label}: ${signed(f.value)} for ${if (f.toHome) g.home else g.away}", color = P.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { factors = factors.filterIndexed { j, _ -> j != i } }) { Text("Remove", color = P.Muted) }
            }
        }
        FormField("A factor", label, { label = it.take(60) }, hint = "Starting guard out; second night of a back-to-back")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill(g.home, toHome, { toHome = true })
            ChoicePill(g.away, !toHome, { toHome = false })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (goals) "Goals" else "Points of margin", color = P.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(signed(value.toDouble()), color = if (value < 0) P.Warning else P.Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value, onValueChange = { value = (it / step).roundToInt() * step },
            valueRange = if (goals) -1f..1f else -10f..10f,
            colors = SliderDefaults.colors(thumbColor = P.Accent, activeTrackColor = P.Accent, inactiveTrackColor = P.Elevated)
        )
        TextButton(onClick = {
            factors = factors + Factor(label.trim(), toHome, value.toDouble())
            label = ""; value = 0f
        }, enabled = label.isNotBlank() && value != 0f) { Text("Add the factor", color = P.Accent) }
    }
}

@Composable
internal fun PickDialog(offers: List<Offer>, onDismiss: () -> Unit, onPick: (Offer, Double, String) -> Unit) {
    var chosen by remember { mutableStateOf(offers.firstOrNull()) }
    var chance by remember { mutableFloatStateOf(((offers.firstOrNull()?.model ?: 0.5) * 100).roundToInt().toFloat().coerceIn(1f, 99f)) }
    var why by remember { mutableStateOf("") }
    EditDialog("Make a pick", onDismiss, {
        chosen?.let { o -> onPick(o, chance / 100.0, "model ${Forecasts.percent(o.model)}%" + (o.marketChance?.let { m -> ", market ${Forecasts.percent(m)}%" } ?: "") + if (why.isBlank()) "" else ". $why") }
        onDismiss()
    }, canSave = chosen != null, saveLabel = "Pick it") {
        offers.forEach { o ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChoicePill(o.label, chosen == o, { chosen = o; chance = (o.model * 100).roundToInt().toFloat().coerceIn(1f, 99f) })
                Box(Modifier.weight(1f))
                Text("${Forecasts.percent(o.model)}%" + (o.marketChance?.let { " · market ${Forecasts.percent(it)}%" } ?: ""), color = P.Muted, fontSize = 13.sp)
            }
        }
        chosen?.let { o ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your chance", color = P.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${chance.roundToInt()}%", color = P.Accent, fontSize = 24.sp, fontFamily = FontFamily.Serif)
            }
            Slider(
                value = chance, onValueChange = { chance = it.roundToInt().toFloat() }, valueRange = 1f..99f, steps = 97,
                colors = SliderDefaults.colors(thumbColor = P.Accent, activeTrackColor = P.Accent, inactiveTrackColor = P.Elevated)
            )
            Text(
                "Starts at the model's ${Forecasts.percent(o.model)}%; move it if you know better." +
                    (o.marketChance?.let { m -> " Against the market: ${signed(Odds.edgePoints(chance / 100.0, m))} points." } ?: ""),
                color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp
            )
        }
        FormField("Why (optional)", why, { why = it.take(300) }, lines = 2)
        Text("It goes to Forecasts, and marks itself when the result comes in. Paper only.", color = P.Faint, fontSize = 12.sp)
    }
}
