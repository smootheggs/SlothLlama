package app.localmind.assistant.ui.world

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.forecasts.Call
import app.localmind.assistant.domain.forecasts.Forecasts
import app.localmind.assistant.domain.world.Point
import app.localmind.assistant.domain.world.WorldData
import app.localmind.assistant.domain.world.WorldMarket
import app.localmind.assistant.domain.world.WorldYours
import app.localmind.assistant.ui.common.CallItSheet
import app.localmind.assistant.ui.common.ChoicePill
import app.localmind.assistant.ui.common.EditDialog
import app.localmind.assistant.ui.common.EmptyLine
import app.localmind.assistant.ui.common.FormField
import app.localmind.assistant.ui.common.Preset
import app.localmind.assistant.ui.common.Section
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.common.SmallLabel
import app.localmind.assistant.ui.common.showDay
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * World, under Weigh: the events you follow, the crowd's chance on each (Polymarket's or Kalshi's,
 * read by the Lobster), your calls against it, news on a call, and settlements to confirm. Upright,
 * a market opens over the list; sideways on a wide screen, beside it.
 */
@Composable
fun WorldScreen(viewModel: WorldViewModel, onBack: () -> Unit, onRecord: () -> Unit) {
    val y by viewModel.yours.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val opened by viewModel.opened.collectAsStateWithLifecycle()
    val news by viewModel.news.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(message) { message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearMessage() } }
    BackHandler(enabled = opened != null) { viewModel.open(null) }
    // Take what the Lobster brought, and ask again if the prices are over six hours old.
    LaunchedEffect(Unit) { viewModel.refresh() }
    var following by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val markets = remember(y) { WorldData.followed(y) }
    val callOf: (WorldMarket) -> Call? = { m -> calls.firstOrNull { it.source == WorldData.source(m) && (it.open || calls.none { o -> o.open && o.source == it.source }) } }
    val openMarket = opened?.let { y.markets[it] }

    val list: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            SlothTopBar(title = "World", subtitle = "Politics and events · the crowd's price, read only", onBack = onBack) {
                if (y.follow.isNotEmpty()) TextButton(onClick = { viewModel.ask() }) { Text("Ask the Lobster", color = P.Lobster, fontSize = 14.sp) }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WorldTab.entries.forEach { t -> ChoicePill(t.label, tab == t, { viewModel.showTab(t) }) }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 1000.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    RecordStrip(calls, onRecord)
                    when (tab) {
                        WorldTab.FOLLOWING -> {
                            news.forEach { n -> calls.firstOrNull { it.id == n.callId }?.let { c -> NewsCard(n, c, viewModel) } }
                            calls.filter { it.open }.forEach { c ->
                                val m = WorldData.keyOf(c)?.let { y.markets[it] }
                                WorldData.settlement(m)?.let { h -> SettleCard(c, m!!, h, viewModel) }
                            }
                            calls.filter { it.source == "world:own" && Forecasts.due(it, today) }.forEach { c -> DueCard(c, viewModel) }
                            Following(y, markets, callOf, today, viewModel)
                            val watching = calls.filter { it.open && it.source == "briefing" }
                            if (watching.isNotEmpty()) {
                                Section("From your briefings", note = "Watch items: asked about at the top of the Express when their day comes") {
                                    watching.forEach { c -> CallLine(c, null, today) {} }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { following = true }) { Text("Follow a market", color = P.Accent) }
                                OutlinedButton(onClick = { asking = true }) { Text("Your own question", color = P.Accent) }
                            }
                        }
                        WorldTab.CALLS -> {
                            val open = calls.filter { it.open }
                            Section("Open") {
                                if (open.isEmpty()) EmptyLine("No open calls. Call it on a market you follow.")
                                open.forEach { c ->
                                    val m = WorldData.keyOf(c)?.let { y.markets[it] }
                                    CallLine(c, m, today) { m?.let { viewModel.open(WorldData.key(it)) } }
                                }
                            }
                        }
                        WorldTab.SETTLED -> {
                            val done = calls.filter { !it.open }
                            Section("Settled") {
                                if (done.isEmpty()) EmptyLine("Nothing settled yet.")
                                done.forEach { c -> CallLine(c, WorldData.keyOf(c)?.let { y.markets[it] }, today) {} }
                            }
                        }
                    }
                    Text(
                        "The crowd's price is a reference, as the market is in Sports: your record shows whether you beat it. Prices are " +
                            "Polymarket's and Kalshi's public market data, read by the Lobster, with nothing about you; nothing is ever traded. " +
                            "When a market settles, the result is shown with its source and you confirm it.",
                        color = P.Faint, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
    val detail: @Composable () -> Unit = {
        val m = openMarket
        if (m != null) MarketPage(m, callOf(m), y.history[WorldData.key(m)].orEmpty(), news, today, viewModel)
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
                    if (openMarket != null) detail() else Text(
                        "Tap a market for how the crowd moved, your chance against it, and how it settles.",
                        color = P.Faint, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(32.dp)
                    )
                }
            }
            openMarket != null -> detail()
            else -> list(Modifier.fillMaxSize())
        }
    }

    if (following) FollowDialog(onDismiss = { following = false }) { viewModel.follow(it) }
    if (asking) {
        CallItSheet(
            where = "World · your own",
            question = "",
            editable = true,
            start = 0.5,
            presets = listOf(0.1, 0.3, 0.5, 0.7, 0.9).map { Preset("${Forecasts.percent(it)}", it) },
            checkDays = 30,
            note = "A question of your own, with no market: it's counted in World's record, and you mark it when its day comes.",
            onDismiss = { asking = false },
            onCall = { q, chance, check, why -> viewModel.own(q, chance ?: 0.5, check, why) }
        )
    }
}

/** Your calls in World: how many, the Brier score, and how often you beat the crowd. */
@Composable
private fun RecordStrip(calls: List<Call>, onRecord: () -> Unit) {
    val world = calls.filter { it.source.startsWith("world:") || it.source == "briefing" || it.area == app.localmind.assistant.domain.forecasts.Area.POLITICS }
    val s = remember(world) { Forecasts.summary(world, LocalDate.now()) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onRecord)
            .background(P.Card).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Your calls", color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(
            listOfNotNull("${s.checked} marked", s.brier?.let { String.format(java.util.Locale.ENGLISH, "%.2f", it) }).joinToString(" · "),
            color = P.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f)
        )
        if (s.withReference > 0) Text("beat the crowd ${s.beat} of ${s.withReference}", color = P.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Following(y: WorldYours, markets: List<WorldMarket>, callOf: (WorldMarket) -> Call?, today: LocalDate, vm: WorldViewModel) {
    var calling by remember { mutableStateOf<WorldMarket?>(null) }
    Section("Following", note = if (markets.isEmpty()) null else "The crowd's chance of yes, and how it moved this week") {
        if (y.follow.isEmpty()) EmptyLine("Nothing followed yet. Paste a Polymarket or Kalshi link: elections, central banks, anything with a market.")
        markets.forEach { m ->
            val c = callOf(m)
            val history = y.history[WorldData.key(m)].orEmpty()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(10.dp)).clickable { vm.open(WorldData.key(m)) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(m.question, color = P.Text, fontSize = 15.sp, lineHeight = 21.sp, fontFamily = FontFamily.Serif, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        WorldViewModel.platformName(m.platform) + when (m.status) { "settled" -> " · settled ${m.result}"; "closed" -> " · trading over"; else -> "" },
                        color = P.Faint, fontSize = 12.sp
                    )
                }
                Spark(history, Modifier.width(64.dp).height(24.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(m.chance?.let { "${Forecasts.percent(it)}%" } ?: "—", color = P.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    WorldData.weekChange(history, today)?.let { d ->
                        val pts = Forecasts.percent(kotlin.math.abs(d))
                        Text(if (pts == 0) "· this week" else "${if (d > 0) "▲" else "▼"} $pts this week", color = P.Faint, fontSize = 11.sp)
                    }
                }
                Box(Modifier.width(84.dp), contentAlignment = Alignment.CenterEnd) {
                    if (c != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(c.latest?.chances?.firstOrNull()?.let { "you ${Forecasts.percent(it)}%" } ?: "you —", color = P.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(if (c.open) "check ${showDay(c.checkOn).substringBeforeLast(' ')}" else "settled", color = P.Faint, fontSize = 11.sp)
                        }
                    } else if (m.status == "open") {
                        Text(
                            "Call it", color = P.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).clickable { calling = m }.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
        val waiting = y.follow.filter { ref -> markets.none { it.ref == ref } }
        waiting.forEach { ref ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$ref · waiting for the Lobster", color = P.Faint, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.unfollow(ref) }) { Text("Unfollow", color = P.Muted, fontSize = 13.sp) }
            }
        }
    }
    calling?.let { m -> CallSheet(m, onDismiss = { calling = null }, vm = vm) }
}

/** Call it on a market: the crowd's chance as a preset, checked when trading ends. */
@Composable
private fun CallSheet(m: WorldMarket, onDismiss: () -> Unit, vm: WorldViewModel) {
    val today = LocalDate.now()
    val check = WorldData.checkOnFor(m, today)
    CallItSheet(
        where = "World · ${WorldViewModel.platformName(m.platform)}",
        question = m.question,
        context = (m.chance?.let { "Crowd ${Forecasts.percent(it)}% (${WorldViewModel.platformName(m.platform)}). " } ?: "No price yet. ") +
            "Settles on the market's own rules.",
        start = m.chance ?: 0.5,
        presets = listOfNotNull(m.chance?.let { Preset("Crowd ${Forecasts.percent(it)}%", it) }) + listOf(0.1, 0.3, 0.5, 0.7, 0.9).map { Preset("${Forecasts.percent(it)}", it) },
        settles = "Checked when it settles (trading ends ${showDay(check)}); you confirm the result.",
        fixedCheckOn = check,
        onDismiss = onDismiss,
        onCall = { _, chance, _, why -> vm.call(m, chance ?: 0.5, check, why) }
    )
}

/** A call's line: the question, your chance, the crowd's, and when it's checked (or how it went). */
@Composable
private fun CallLine(c: Call, m: WorldMarket?, today: LocalDate, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.question, color = P.Text, fontSize = 15.sp, lineHeight = 21.sp, fontFamily = FontFamily.Serif, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                (if (c.source == "briefing") "watch item" else m?.let { WorldViewModel.platformName(it.platform) } ?: "your own") +
                    (if (c.evidence.isNotBlank()) " · ${c.evidence}" else ""),
                color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        m?.chance?.takeIf { c.open }?.let { Text("crowd ${Forecasts.percent(it)}%", color = P.Muted, fontSize = 12.sp) }
        Text(c.latest?.chances?.firstOrNull()?.let { "${Forecasts.percent(it)}%" } ?: "—", color = P.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(
            when {
                c.void -> "didn't count"
                c.happened != null -> if (Forecasts.wentYourWay(c) == true) "✓" else "✕"
                else -> Forecasts.date(c.checkOn)?.let { Forecasts.relative(it, today) } ?: ""
            },
            color = when {
                c.happened != null && Forecasts.wentYourWay(c) == true -> P.Success
                c.happened != null -> P.Warning
                else -> P.Faint
            },
            fontSize = 12.sp, modifier = Modifier.width(72.dp), maxLines = 1
        )
    }
}

/** News on a call, from its Lobster watch: keep your chance, or move it a step. */
@Composable
private fun NewsCard(n: News, c: Call, vm: WorldViewModel) {
    val now = c.latest?.chances?.firstOrNull() ?: 0.5
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.AccentSoft).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallLabel("News on a call", Modifier.weight(1f))
            Text("from a Lobster watch", color = P.Faint, fontSize = 11.sp)
        }
        Text(c.question, color = P.Text, fontSize = 16.sp, lineHeight = 22.sp, fontFamily = FontFamily.Serif)
        Text(
            n.title + (n.text.lineSequence().map { it.trim().trimStart('-', '*', '·', ' ') }.firstOrNull { it.length > 20 }?.let { ": ${it.take(160)}" } ?: ""),
            color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("You said ${Forecasts.percent(now)}%. Still?", color = P.Text, fontSize = 13.sp)
            listOf(-0.10, -0.05).forEach { d -> ChoicePill("${Forecasts.percent(d)}", false, { vm.changeChance(c, (now + d).coerceIn(0.01, 0.99), "news: ${n.title.take(80)}"); vm.seen(n) }, color = P.Muted) }
            ChoicePill("Keep ${Forecasts.percent(now)}", true, { vm.seen(n) })
            listOf(0.05, 0.10).forEach { d -> ChoicePill("+${Forecasts.percent(d)}", false, { vm.changeChance(c, (now + d).coerceIn(0.01, 0.99), "news: ${n.title.take(80)}"); vm.seen(n) }, color = P.Muted) }
        }
    }
}

/** A market settled: its result, with where it's from, proposed for you to confirm. */
@Composable
private fun SettleCard(c: Call, m: WorldMarket, happened: Int, vm: WorldViewModel) {
    var reasoning by remember(c.id) { mutableStateOf("") }
    var byHand by remember(c.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.AccentSoft).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallLabel("Settled: confirm it", Modifier.weight(1f))
            Text("nothing is marked until you do", color = P.Faint, fontSize = 11.sp)
        }
        Text(c.question, color = P.Text, fontSize = 16.sp, lineHeight = 22.sp, fontFamily = FontFamily.Serif)
        Text(
            "${WorldViewModel.platformName(m.platform)} settled it ${if (happened == 0) "Yes" else "No"}. You said " +
                (c.latest?.chances?.firstOrNull()?.let { "${Forecasts.percent(it)}%" } ?: "—") +
                (c.market.firstOrNull()?.let { "; the crowd was at ${Forecasts.percent(it.chances[0])}% when you called." } ?: "."),
            color = P.Muted, fontSize = 13.sp, lineHeight = 19.sp
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("The reasoning, if you like:", color = P.Faint, fontSize = 12.sp)
            listOf("sound" to "Sound", "partly" to "Partly", "no" to "Shaky").forEach { (key, label) ->
                ChoicePill(label, reasoning == key, { reasoning = if (reasoning == key) "" else key }, color = P.Muted)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!byHand) {
                ChoicePill(if (happened == 0) "Confirm: it happened" else "Confirm: it didn't", true, { vm.confirm(c, m, happened, reasoning) })
                ChoicePill("Something's wrong", false, { byHand = true }, color = P.Muted)
            } else {
                ChoicePill("It happened", false, { vm.mark(c, 0, false, reasoning) })
                ChoicePill("It didn't", false, { vm.mark(c, 1, false, reasoning) })
                ChoicePill("Doesn't count", false, { vm.mark(c, null, true, "") }, color = P.Muted)
            }
        }
    }
}

/** A question of your own whose day has come: did it happen? */
@Composable
private fun DueCard(c: Call, vm: WorldViewModel) {
    var reasoning by remember(c.id) { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.AccentSoft).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallLabel("Did this happen?")
        Text(c.question, color = P.Text, fontSize = 16.sp, lineHeight = 22.sp, fontFamily = FontFamily.Serif)
        Text(c.latest?.chances?.firstOrNull()?.let { "You said ${Forecasts.percent(it)}%" } ?: "No chance given", color = P.Muted, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("The reasoning, if you like:", color = P.Faint, fontSize = 12.sp)
            listOf("sound" to "Sound", "partly" to "Partly", "no" to "Shaky").forEach { (key, label) ->
                ChoicePill(label, reasoning == key, { reasoning = if (reasoning == key) "" else key }, color = P.Muted)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill("It happened", false, { vm.mark(c, 0, false, reasoning) })
            ChoicePill("It didn't", false, { vm.mark(c, 1, false, reasoning) })
            ChoicePill("Doesn't count", false, { vm.mark(c, null, true, "") }, color = P.Muted)
        }
    }
}

/** A market's page: the crowd and you over time, news, how it settles, and your reasons. */
@Composable
private fun MarketPage(m: WorldMarket, c: Call?, history: List<Point>, news: List<News>, today: LocalDate, vm: WorldViewModel) {
    var calling by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }
    var watching by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        SlothTopBar(title = WorldViewModel.platformName(m.platform), subtitle = m.eventTitle.ifBlank { "A market you follow" }, onBack = { vm.open(null) })
        Column(
            Modifier.widthIn(max = 900.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(m.question, color = P.Text, fontSize = 22.sp, lineHeight = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
            val mine = c?.latest?.chances?.firstOrNull()
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Stat(m.chance?.let { "${Forecasts.percent(it)}%" } ?: "—", "the crowd", P.Muted)
                if (mine != null) Stat("${Forecasts.percent(mine)}%", "you", P.Accent)
                if (mine != null && m.chance != null) {
                    val gap = Forecasts.percent(mine) - Forecasts.percent(m.chance)
                    Stat(if (gap > 0) "+$gap" else "$gap", "you against the crowd")
                }
                if (c != null) Stat(showDay(c.checkOn).substringBeforeLast(' '), if (c.open) "check date" else "settled")
            }
            if (history.size >= 2 || (c?.estimates?.size ?: 0) > 1) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.Card).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row {
                        SmallLabel("How it moved", Modifier.weight(1f))
                        Text("— you", color = P.Accent, fontSize = 12.sp)
                        Text("   - - the crowd", color = P.Muted, fontSize = 12.sp)
                    }
                    Trail(history, c, today, Modifier.fillMaxWidth().height(150.dp))
                }
            }
            news.filter { it.callId == c?.id }.forEach { n -> if (c != null) NewsCard(n, c, vm) }
            if (m.rules.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallLabel("How it settles")
                    Text(m.rules, color = P.Muted, fontSize = 14.sp, lineHeight = 20.sp)
                    Text("As the market's own rules say. When it settles, the result is shown here with its source, and you confirm it.", color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            if (c != null && c.estimates.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallLabel("Your reasons")
                    c.estimates.reversed().forEach { e ->
                        Text(
                            "${showDay(e.on)} · ${Forecasts.percent(e.chances[0])}% · ${e.reason.ifBlank { "no reason written" }}",
                            color = if (e.reason.isBlank()) P.Faint else P.Muted, fontSize = 13.sp, lineHeight = 19.sp
                        )
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    c == null && m.status == "open" -> OutlinedButton(onClick = { calling = true }) { Text("Call it", color = P.Accent) }
                    c != null && c.open -> {
                        OutlinedButton(onClick = { changing = true }) { Text("Change my chance", color = P.Accent) }
                        OutlinedButton(onClick = { watching = true }) { Text("Lobster: watch the news", color = P.Lobster) }
                    }
                }
                TextButton(onClick = { vm.unfollow(m.ref) }) { Text("Unfollow", color = P.Muted) }
            }
        }
    }
    if (calling) CallSheet(m, onDismiss = { calling = false }, vm = vm)
    if (changing && c != null) {
        CallItSheet(
            where = "World · change your chance",
            question = c.question,
            context = m.chance?.let { "The crowd is at ${Forecasts.percent(it)}% now." } ?: "",
            start = c.latest?.chances?.firstOrNull() ?: 0.5,
            presets = listOfNotNull(m.chance?.let { Preset("Crowd ${Forecasts.percent(it)}%", it) }),
            settles = "The check date stays ${showDay(c.checkOn)}.",
            fixedCheckOn = c.checkOn,
            onDismiss = { changing = false },
            onCall = { _, chance, _, why -> vm.changeChance(c, chance ?: 0.5, why) }
        )
    }
    if (watching && c != null) {
        var topic by remember { mutableStateOf(c.question.split(' ').filter { it.length > 2 }.take(8).joinToString(" ").trimEnd('?', '.')) }
        EditDialog("Lobster: watch the news", { watching = false }, { vm.watchNews(c, topic); watching = false }, canSave = topic.isNotBlank(), saveLabel = "Send") {
            Text("A few key words work best. The Lobster checks the news every few hours; its alerts show here, on the call. It never posts or acts.", color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
            FormField("Topic", topic, { topic = it.take(120) })
        }
    }
}

@Composable
private fun Stat(value: String, label: String, color: androidx.compose.ui.graphics.Color = P.Text) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = color, fontSize = 24.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium)
        Text(label, color = P.Faint, fontSize = 12.sp)
    }
}

/** A small line of the crowd's chance over the days kept. */
@Composable
private fun Spark(history: List<Point>, modifier: Modifier) {
    if (history.size < 2) {
        Box(modifier)
        return
    }
    val color = P.Muted
    val points = history.sortedBy { it.on }.takeLast(30)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val lo = points.minOf { it.chance }
        val hi = points.maxOf { it.chance }
        val span = (hi - lo).coerceAtLeast(0.05)
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = i * w / (points.size - 1)
            val y = (h - ((p.chance - lo) / span) * h).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.5f))
    }
}

/** The crowd (dashed) and you (solid, in steps) from the first day kept to today, on 0–100%. */
@Composable
private fun Trail(history: List<Point>, c: Call?, today: LocalDate, modifier: Modifier) {
    val you = P.Accent
    val crowd = P.Muted
    val grid = P.Border
    val days = (history.map { it.on } + c?.estimates?.map { it.on }.orEmpty()).mapNotNull { Forecasts.date(it) }
    val start = days.minOrNull() ?: today
    val span = ChronoUnit.DAYS.between(start, today).toFloat().coerceAtLeast(1f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun x(day: String) = ((Forecasts.date(day)?.let { ChronoUnit.DAYS.between(start, it).toFloat() } ?: 0f) / span).coerceIn(0f, 1f) * w
        fun y(p: Double) = h - p.toFloat() * h
        drawLine(grid, Offset(0f, h / 2), Offset(w, h / 2), 1f)
        val sorted = history.sortedBy { it.on }
        if (sorted.size >= 2) {
            val path = Path()
            sorted.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.on), y(p.chance)) else path.lineTo(x(p.on), y(p.chance)) }
            drawPath(path, crowd, style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))))
        }
        val est = c?.estimates.orEmpty()
        if (est.isNotEmpty()) {
            val path = Path()
            est.forEachIndexed { i, e ->
                val px = x(e.on)
                val py = y(e.chances[0])
                if (i == 0) path.moveTo(px, py) else {
                    path.lineTo(px, y(est[i - 1].chances[0]))
                    path.lineTo(px, py)
                }
            }
            path.lineTo(w, y(est.last().chances[0]))
            drawPath(path, you, style = Stroke(4f))
        }
    }
}

/** Follow a market: paste its link (Polymarket or Kalshi), or a ref. */
@Composable
private fun FollowDialog(onDismiss: () -> Unit, onFollow: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    EditDialog("Follow a market", onDismiss, {
        if (onFollow(text)) onDismiss() else bad = true
    }, canSave = text.isNotBlank(), saveLabel = "Follow") {
        Text(
            "Paste a Polymarket event link (polymarket.com/event/…) or a Kalshi market link (kalshi.com/markets/…). The Lobster reads its " +
                "public prices; nothing is traded.",
            color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp
        )
        FormField("Link", text, { text = it.take(300); bad = false }, lines = 2)
        if (bad) Text("That isn't a Polymarket or Kalshi link.", color = P.Warning, fontSize = 13.sp)
    }
}
