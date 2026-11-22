package app.localmind.assistant.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.domain.desk.Action
import app.localmind.assistant.domain.desk.Against
import app.localmind.assistant.domain.desk.Brake
import app.localmind.assistant.domain.desk.Branch
import app.localmind.assistant.domain.desk.Check
import app.localmind.assistant.domain.desk.Condition
import app.localmind.assistant.domain.desk.Costs
import app.localmind.assistant.domain.desk.DeskStrategies
import app.localmind.assistant.domain.desk.Exit
import app.localmind.assistant.domain.desk.Market
import app.localmind.assistant.domain.desk.Op
import app.localmind.assistant.domain.desk.Overlay
import app.localmind.assistant.domain.desk.Rule
import app.localmind.assistant.domain.desk.RuleCheck
import app.localmind.assistant.domain.desk.RuleEngine
import app.localmind.assistant.domain.desk.RuleWords
import app.localmind.assistant.domain.desk.Signal
import app.localmind.assistant.domain.desk.Sizing
import app.localmind.assistant.ui.common.ConfirmDialog
import app.localmind.assistant.ui.common.NameDialog
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.home.WIDE
import app.localmind.assistant.ui.theme.SlothPalette as P

/**
 * The rule editor (1.48): a rule as sentences made of blocks, each part a chip you tap to
 * change; the trading assumptions kept apart from the rule; and the count of tries. Edits are
 * saved as you go but count only when tested. On a wide screen your rules and the shelf sit on
 * the left and the assumptions on the right; upright, it's one column.
 */
@Composable
fun RuleEditor(my: MyRule, ui: DeskUi, vm: DeskViewModel, onBack: () -> Unit) {
    // The editor's own copy is what's edited (saved as you go); it takes the saved rule again
    // only after a change from outside, an undo or a rename.
    var draft by remember(my.id) { mutableStateOf(my.rule) }
    val reload by vm.reload.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(my.rule)
    LaunchedEffect(reload) { latest?.let { draft = it } }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val rule = draft

    Column(Modifier.fillMaxSize()) {
        SlothTopBar(
            title = rule?.name ?: my.name,
            subtitle = "SlothDesk · your rule · " + if (my.tries == 1) "1 try" else "${my.tries} tries",
            onBack = onBack,
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, contentDescription = "More", tint = P.Muted) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(P.Elevated)) {
                        if (rule != null) DropdownMenuItem(text = { Text("Rename", color = P.Text) }, onClick = { menu = false; renaming = true })
                        if (my.tested) DropdownMenuItem(text = { Text("The six questions", color = P.Text) }, onClick = { menu = false; vm.openReport(my.key) })
                        DropdownMenuItem(text = { Text("Delete this rule", color = P.Danger) }, onClick = { menu = false; deleting = true })
                    }
                }
            }
        )
        if (rule == null) {
            Column(Modifier.padding(24.dp)) {
                Text("This rule can't be read: ${my.problem ?: "it isn't a rule this version knows"}.", color = P.Warning, fontSize = 15.sp)
                Text("It may have come from a newer SlothDesk. Nothing in it is run.", color = P.Muted, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp))
                Action("Delete it") { deleting = true }
            }
        } else {
            val change: (Rule) -> Unit = { r -> draft = r; vm.edit(my.id, r) }
            val problems = remember(rule) { RuleCheck.problems(rule) }
            val missing = remember(rule, ui.tickers, ui.otherSeries) { vm.missingFor(rule) }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Three panes on a big tablet; the phone held sideways keeps the rule and the
                // assumptions side by side; upright, one column.
                val rail = maxWidth >= RAIL_FROM
                if (maxWidth >= WIDE) {
                    Row(Modifier.fillMaxSize()) {
                        if (rail) {
                            Rail(ui, my.id, vm, Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp))
                            VerticalDivider(color = P.Border)
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 16.dp)) {
                                RuleBody(rule, change, ui)
                                Spacer(Modifier.height(24.dp))
                            }
                            TestBar(my, rule, ui, problems, missing, vm)
                        }
                        VerticalDivider(color = P.Border)
                        Column(Modifier.width(if (rail) 320.dp else 300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp)) {
                            Assumptions(rule, change, ui)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Column(Modifier.widthIn(max = 760.dp)) {
                                RuleBody(rule, change, ui)
                                var unfolded by remember { mutableStateOf(false) }
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 20.dp).heightIn(min = 48.dp)
                                        .clickable(role = Role.Button, onClickLabel = if (unfolded) "Hide" else "Show") { unfolded = !unfolded },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Trading assumptions", color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    Text(if (unfolded) "Hide" else "Show", color = P.Accent, fontSize = 14.sp)
                                }
                                if (unfolded) Assumptions(rule, change, ui)
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                        TestBar(my, rule, ui, problems, missing, vm)
                    }
                }
            }
        }
    }

    if (renaming && rule != null) {
        NameDialog("Rename the rule", rule.name, onDismiss = { renaming = false }, onSave = { vm.rename(my.id, it) }, maxLength = 80)
    }
    if (deleting) {
        ConfirmDialog(
            "Delete ${my.name}?",
            "The rule goes, with its tries, its nearby check and its paper days. This can't be undone.",
            "Delete", onDismiss = { deleting = false }, onConfirm = { vm.delete(my.id) }
        )
    }
}

// --- the left rail ---

/** Wide enough for your rules on the left as well (a big tablet, not a phone held sideways). */
private val RAIL_FROM = 1100.dp

@Composable
private fun Rail(ui: DeskUi, current: String, vm: DeskViewModel, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RailLabel("YOUR RULES")
        ui.rules.forEach { m ->
            RailItem(m.name, (if (m.from.startsWith("shelf:")) "from the shelf" else "your rule") + " · " +
                (if (m.tries == 1) "1 try" else "${m.tries} tries"), selected = m.id == current) { vm.openRule(m.id) }
        }
        Action("New rule") { vm.newRule() }
        Spacer(Modifier.height(10.dp))
        RailLabel("START FROM THE SHELF")
        DeskStrategies.templates.forEach { t ->
            RailItem(t.label.substringBefore(" with ").substringBefore(" ("), "copy it to change it", selected = false) { vm.copyShelf(t.key) }
        }
    }
}

@Composable
private fun RailLabel(text: String) {
    Text(text, color = P.Faint, fontSize = 11.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(vertical = 6.dp).semantics { heading() })
}

@Composable
private fun RailItem(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape)
            .background(if (selected) P.AccentSoft else P.Canvas)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = if (selected) P.Text else P.Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, color = P.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// --- the rule ---

private fun firstAsset(r: Rule, ui: DeskUi): String = RuleEngine.traded(r).firstOrNull() ?: ui.tickers.firstOrNull() ?: "GLD"

private fun newCompare(r: Rule, ui: DeskUi): Condition {
    val t = firstAsset(r, ui)
    return Condition.Compare(Signal.Price(t), Op.ABOVE, Against.Of(Signal.Average(t, 50)))
}

private fun newBranch(r: Rule, ui: DeskUi): Branch {
    val t = firstAsset(r, ui)
    val tags = r.branches.map { it.tag }.toSet()
    val tag = (2..9).map { "branch$it" }.first { it !in tags }
    return Branch(tag, listOf(Condition.Compare(Signal.Price(t), Op.BELOW, Against.Of(Signal.Average(t, 200)))), Action.Hold(mapOf(t to 0.5)))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleBody(rule: Rule, change: (Rule) -> Unit, ui: DeskUi) {
    val locked = rule.overlay != null
    SectionLabel("TRADES")
    Text(RuleEngine.traded(rule).joinToString(", ").ifBlank { "nothing yet" } + " · cash earns T-bills",
        color = P.Text, fontSize = 15.sp)

    rule.branches.forEachIndexed { bi, b ->
        BranchCard(rule, bi, b, change, ui, locked)
    }
    if (!locked && rule.branches.size < RuleCheck.MAX_BRANCHES) {
        Row(Modifier.padding(top = 8.dp)) {
            Action("Add a branch: else, when…") { change(rule.copy(branches = rule.branches + newBranch(rule, ui))) }
        }
    }

    SectionLabel("OTHERWISE")
    Weights(rule.otherwise, allowEmpty = true, ui = ui) { change(rule.copy(otherwise = it)) }

    SectionLabel("LEAVE WHEN")
    if (rule.exits.isEmpty()) Words("the conditions stop holding", faint = true)
    rule.exits.forEachIndexed { ei, e ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExitParts(e, ui) { ne -> change(rule.copy(exits = rule.exits.mapIndexed { i, x -> if (i == ei) ne else x })) }
            Chip("×", muted = true, description = "Remove this exit") { change(rule.copy(exits = rule.exits.filterIndexed { i, _ -> i != ei })) }
        }
    }
    if (!locked) {
        val t = firstAsset(rule, ui)
        val stretch = rule.branches.flatMap { it.conditions }.filterIsInstance<Condition.Compare>().map { it.left }
            .firstOrNull { it is Signal.Stretch } ?: Signal.Stretch(t, 60)
        MenuChip("Add an exit", listOf(
            "Back within a band of zero" to { change(rule.copy(exits = rule.exits + Exit.Within(stretch, 0.5))) },
            "After a number of days" to { change(rule.copy(exits = rule.exits + Exit.After(20))) },
            "A stop" to { change(rule.copy(exits = rule.exits + Exit.Stop(0.05))) },
            "A trailing stop" to { change(rule.copy(exits = rule.exits + Exit.Stop(0.10, trailing = true))) },
            "A profit target" to { change(rule.copy(exits = rule.exits + Exit.Target(0.20))) }
        ), muted = true)
    }

    SectionLabel("DRAWDOWN BRAKE")
    val brake = rule.brake
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (brake == null) {
            Words("Off", faint = true)
            Chip("Turn on", muted = true) { change(rule.copy(brake = Brake())) }
        } else {
            Words("After a")
            NumberChip("The fall", brake.drawdown, NumSpec(0.02, 0.9, percent = true)) { change(rule.copy(brake = brake.copy(drawdown = it))) }
            Words("fall from its peak, hold")
            NumberChip("The size kept", brake.cut, NumSpec(0.0, 0.99, percent = true)) { change(rule.copy(brake = brake.copy(cut = it))) }
            Words("of the size for")
            NumberChip("How long", brake.days.toDouble(), NumSpec(1.0, 250.0, whole = true, suffix = " days")) { change(rule.copy(brake = brake.copy(days = it.toInt()))) }
            Chip("Turn off", muted = true) { change(rule.copy(brake = null)) }
        }
    }

    (rule.overlay as? Overlay.CoveredCall)?.let { o ->
        SectionLabel("OPTIONS, MODELLED")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Words("Sell a")
            NumberChip("The call's delta", o.delta, NumSpec(0.05, 0.6, percent = true), label = "${RuleWords.num(o.delta * 100)}-delta") {
                change(rule.copy(overlay = o.copy(delta = it)))
            }
            Words("call about")
            NumberChip("Days to expiry", o.dte.toDouble(), NumSpec(7.0, 120.0, whole = true, suffix = " days")) { change(rule.copy(overlay = o.copy(dte = it.toInt()))) }
            Words("out on ${o.asset}, priced from ${RuleWords.seriesName(o.vol)}: a sketch, not quotes.")
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = P.Faint, fontSize = 11.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp).semantics { heading() })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BranchCard(rule: Rule, bi: Int, b: Branch, change: (Rule) -> Unit, ui: DeskUi, locked: Boolean) {
    val any = b.conditions.size == 1 && b.conditions[0] is Condition.AnyOf
    val conds = if (any) (b.conditions[0] as Condition.AnyOf).of else b.conditions
    fun setBranch(nb: Branch) = change(rule.copy(branches = rule.branches.mapIndexed { i, x -> if (i == bi) nb else x }))
    fun setConds(list: List<Condition>, anyMode: Boolean = any) =
        setBranch(b.copy(conditions = if (anyMode && list.isNotEmpty()) listOf(Condition.AnyOf(list)) else list))

    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(shape).background(P.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Words(if (bi == 0) "When" else "Else, when")
            when {
                conds.isEmpty() -> Words("always")
                locked -> Words("always")
                else -> MenuChip(if (any) "any of these hold" else "all of these hold", listOf(
                    "all of these hold" to { setConds(conds, false) },
                    "any of these hold" to { setConds(conds.map { c -> unEnterOnly(c) }, true) }
                ))
            }
        }
        conds.forEachIndexed { ci, c ->
            ConditionLine(c, ui, canEnterOnly = !any,
                onChange = { nc -> setConds(conds.mapIndexed { j, x -> if (j == ci) nc else x }) },
                onRemove = { setConds(conds.filterIndexed { j, _ -> j != ci }) })
        }
        if (!locked && conds.size < RuleCheck.MAX_CONDITIONS) {
            MenuChip("Add a condition", listOf(
                "A comparison" to { setConds(conds + newCompare(rule, ui)) },
                "The calendar" to { setConds(conds + Condition.Calendar(months = listOf(1))) }
            ), muted = true)
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Words("Then")
            if (locked) Words(RuleWords.action(b.action)) else ActionParts(b.action, rule, ui) { setBranch(b.copy(action = it)) }
        }
        if (!locked && conds.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Words("Held")
                MenuChip(if (b.until) "until an exit" else "while these hold", listOf(
                    "while these hold" to { setBranch(b.copy(until = false)) },
                    "until an exit" to { setBranch(b.copy(until = true)) }
                ))
            }
        }
        if (!locked && rule.branches.size > 1) {
            Row { Action("Remove this branch", muted = true) { change(rule.copy(branches = rule.branches.filterIndexed { i, _ -> i != bi })) } }
        }
    }
}

private fun unEnterOnly(c: Condition): Condition = when (c) {
    is Condition.Compare -> c.copy(enterOnly = false)
    is Condition.Calendar -> c.copy(enterOnly = false)
    is Condition.AnyOf -> c.copy(enterOnly = false)
    is Condition.Not -> c.copy(enterOnly = false)
}

private fun withEnterOnly(c: Condition, on: Boolean): Condition = when (c) {
    is Condition.Compare -> c.copy(enterOnly = on)
    is Condition.Calendar -> c.copy(enterOnly = on)
    is Condition.AnyOf -> c.copy(enterOnly = on)
    is Condition.Not -> c.copy(enterOnly = on)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionLine(c: Condition, ui: DeskUi, canEnterOnly: Boolean, onChange: (Condition) -> Unit, onRemove: () -> Unit) {
    var rowDays by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf(false) }
    val more = buildList<Pair<String, () -> Unit>> {
        if (c is Condition.Compare && !c.op.crossing) add("True … days in a row" to { rowDays = true })
        if (canEnterOnly && c !is Condition.AnyOf) {
            add((if (c.enterOnly) "Keep checking it while held" else "Check it only before entering") to { onChange(withEnterOnly(c, !c.enterOnly)) })
        }
        add("Remove" to onRemove)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        when (c) {
            is Condition.Compare -> {
                SignalChip("What it reads", c.left, RuleWords.signal(c.left), ui.tickers, ui.otherSeries) { onChange(c.copy(left = it)) }
                MenuChip(RuleWords.op(c.op, c.left), Op.entries.map { op ->
                    RuleWords.op(op, c.left) to { onChange(c.copy(op = op, days = if (op.crossing) 1 else c.days)) }
                })
                RightParts(c, ui, onChange)
                if (c.days > 1) Words("${c.days} days in a row")
            }
            is Condition.Calendar -> Chip(RuleWords.calendar(c).replaceFirstChar { it.uppercase() }) { calendar = true }
            else -> Words(RuleWords.condition(c).replaceFirstChar { it.uppercase() })
        }
        MenuChip("⋯", more, muted = true, description = "More for this condition")
    }
    if (c.enterOnly) Words("…checked only before opening a new position", faint = true)
    if (rowDays && c is Condition.Compare) {
        NumberDialog("True this many days in a row", c.days.toDouble(), NumSpec.ROW, onDismiss = { rowDays = false }) { onChange(c.copy(days = it.toInt())) }
    }
    if (calendar && c is Condition.Calendar) {
        CalendarDialog(c.months, c.weekdays, c.dates, c.around, onDismiss = { calendar = false }) { m, w, d, a ->
            onChange(c.copy(months = m, weekdays = w, dates = d, around = a))
        }
    }
}

private fun specFor(s: Signal): NumSpec = if (RuleWords.unit(s) == RuleWords.Unit.PERCENT) NumSpec.ANY_PCT else NumSpec.ANY

/** What a signal is compared with: a number, another signal, or its own percentile. */
@Composable
private fun RightParts(c: Condition.Compare, ui: DeskUi, onChange: (Condition) -> Unit) {
    var number by remember { mutableStateOf(false) }
    var signal by remember { mutableStateOf(false) }
    var pct by remember { mutableStateOf(false) }
    val right = c.right
    MenuChip(RuleWords.against(right, c.left), listOf(
        "A number…" to { number = true },
        "Another signal…" to { signal = true },
        "Its own percentile…" to { pct = true }
    ), description = "Compared with: ${RuleWords.against(right, c.left)}")
    if (number) {
        NumberDialog("Compared with a number", (right as? Against.Value)?.x ?: 0.0, specFor(c.left), onDismiss = { number = false }) {
            onChange(c.copy(right = Against.Value(it)))
        }
    }
    if (signal) {
        val start = (right as? Against.Of)?.signal ?: Signal.Average(c.left.assets.firstOrNull() ?: ui.tickers.firstOrNull() ?: "GLD", 200)
        SignalDialog("Compared with", start, ui.tickers, ui.otherSeries, onDismiss = { signal = false }) { onChange(c.copy(right = Against.Of(it))) }
    }
    if (pct) {
        val p = right as? Against.Percentile ?: Against.Percentile(0.8, 252)
        var q by remember { mutableStateOf(RuleWords.num(p.q * 100)) }
        var d by remember { mutableStateOf(p.days.toString()) }
        val qs = NumSpec(0.01, 0.99, percent = true, whole = true)
        val qv = qs.parse(q)
        val dv = NumSpec.DAYS.parse(d)
        SmallDialog("Its own percentile", onDismiss = { pct = false }, canSave = qv != null && dv != null, onSave = {
            if (qv != null && dv != null) onChange(c.copy(right = Against.Percentile(qv, dv.toInt(), dv.toInt() / 2)))
        }) {
            NumberField("Percentile", q, { q = it }, qs)
            NumberField("Of the last", d, { d = it }, NumSpec.DAYS)
            Text("With less than half that history, the comparison doesn't block.", color = P.Faint, fontSize = 12.sp)
        }
    }
}

// --- actions and weights ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionParts(a: Action, rule: Rule, ui: DeskUi, onChange: (Action) -> Unit) {
    val t = firstAsset(rule, ui)
    val list = (RuleEngine.traded(rule) + ui.tickers + listOf("SPY", "TLT", "GLD")).distinct().take(3)
    MenuChip(when (a) { is Action.Hold -> "hold"; is Action.Rotate -> "hold the best"; is Action.Scale -> "size" }, listOf(
        "hold" to { if (a !is Action.Hold) onChange(Action.Hold(mapOf(t to 1.0))) },
        "hold the best of a list" to { if (a !is Action.Rotate) onChange(Action.Rotate(list, Signal.Momentum("*", 120), 1)) },
        "size with a signal" to { if (a !is Action.Scale) onChange(Action.Scale(t, Signal.Momentum(t, 120), 0.0, 0.2)) }
    ))
    when (a) {
        is Action.Hold -> Weights(a.weights, allowEmpty = false, ui = ui) { onChange(Action.Hold(it)) }
        is Action.Rotate -> {
            var picking by remember { mutableStateOf(false) }
            NumberChip("How many", a.top.toDouble(), NumSpec(1.0, (a.assets.size - 1).coerceAtLeast(1).toDouble(), whole = true)) { onChange(a.copy(top = it.toInt())) }
            Words("of")
            Chip(a.assets.joinToString(", "), description = "The list: ${a.assets.joinToString(", ")}") { picking = true }
            Words("by")
            SignalChip("How they're ranked", a.score, RuleWords.signal(a.score), ui.tickers, ui.otherSeries, rotation = true) { onChange(a.copy(score = it)) }
            val min = a.minScore
            if (min == null) {
                Chip("any score", muted = true, description = "Add a minimum score") { onChange(a.copy(minScore = 0.0)) }
            } else {
                Words("only above")
                NumberChip("Minimum score", min, specFor(a.score)) { onChange(a.copy(minScore = it)) }
                Chip("×", muted = true, description = "No minimum score") { onChange(a.copy(minScore = null)) }
            }
            if (picking) {
                TickersDialog("The list", a.assets, ui.tickers, onDismiss = { picking = false }) { l ->
                    onChange(a.copy(assets = l, top = a.top.coerceAtMost(l.size - 1)))
                }
            }
        }
        is Action.Scale -> {
            TickerChip("Asset", a.asset, ui.tickers) { onChange(a.copy(asset = it)) }
            Words("from none at")
            NumberChip("From", a.from, specFor(a.signal)) { onChange(a.copy(from = it)) }
            Words("to")
            NumberChip("The most", a.max, NumSpec(0.01, RuleCheck.MAX_GROSS, percent = true), label = RuleWords.weight(a.max)) { onChange(a.copy(max = it)) }
            Words("at")
            NumberChip("To", a.to, specFor(a.signal)) { onChange(a.copy(to = it)) }
            Words("of")
            SignalChip("The signal", a.signal, RuleWords.signal(a.signal), ui.tickers, ui.otherSeries) { onChange(a.copy(signal = it)) }
        }
    }
}

/** Weights across assets, long or short; [allowEmpty]: none is cash. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Weights(w: Map<String, Double>, allowEmpty: Boolean, ui: DeskUi, onChange: (Map<String, Double>) -> Unit) {
    if (w.isEmpty()) Words("cash (T-bills)")
    w.entries.forEachIndexed { i, (asset, x) ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TickerChip("Asset", asset, ui.tickers) { n ->
                onChange(w.entries.associate { (k, v) -> (if (k == asset) n else k) to v })
            }
            NumberChip("Weight of $asset", x, NumSpec.WEIGHT, label = RuleWords.weight(x)) { v -> onChange(w + (asset to v)) }
            if (allowEmpty || w.size > 1) Chip("×", muted = true, description = "Remove $asset") { onChange(w - asset) }
            if (i < w.size - 1) Words(",")
        }
    }
    if (w.size < RuleCheck.MAX_LIST) {
        val next = ui.tickers.firstOrNull { it !in w } ?: "SPY"
        Row { Action(if (w.isEmpty()) "Hold something instead" else "Add an asset", muted = true) { onChange(w + (next to if (w.isEmpty()) 1.0 else 0.5)) } }
    }
}

// --- exits ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExitParts(e: Exit, ui: DeskUi, onChange: (Exit) -> Unit) {
    when (e) {
        is Exit.Within -> {
            SignalChip("What it reads", e.signal, RuleWords.signal(e.signal), ui.tickers, ui.otherSeries) { onChange(e.copy(signal = it)) }
            Words("is back within")
            val spec = if (RuleWords.unit(e.signal) == RuleWords.Unit.PERCENT) NumSpec(0.0001, 10.0, percent = true) else NumSpec(0.01, 10.0)
            NumberChip("The band", e.band, spec) { onChange(e.copy(band = it)) }
            Words("of zero")
        }
        is Exit.After -> {
            Words("After")
            NumberChip("Days held", e.days.toDouble(), NumSpec.DAYS) { onChange(e.copy(days = it.toInt())) }
        }
        is Exit.Stop -> {
            MenuChip(if (e.trailing) "a trailing stop" else "a stop", listOf(
                "a stop" to { onChange(e.copy(trailing = false)) },
                "a trailing stop" to { onChange(e.copy(trailing = true)) }
            ))
            if (e.byAtr) {
                NumberChip("How many ATRs", e.atr, NumSpec(0.5, 20.0), label = "${RuleWords.num(e.atr)} ATRs") { onChange(e.copy(atr = it)) }
                NumberChip("ATR days", e.atrDays.toDouble(), NumSpec(2.0, 250.0, whole = true, suffix = " days"), label = "over ${e.atrDays} days") {
                    onChange(e.copy(atrDays = it.toInt()))
                }
            } else {
                NumberChip("How far", e.pct, NumSpec(0.005, 0.9, percent = true)) { onChange(e.copy(pct = it)) }
            }
            MenuChip(if (e.byAtr) "in ATRs" else "in %", listOf(
                "in %" to { onChange(Exit.Stop(pct = 0.05, trailing = e.trailing)) },
                "in ATRs (needs highs and lows)" to { onChange(Exit.Stop(trailing = e.trailing, atr = 3.0, atrDays = 14)) }
            ), muted = true, description = "Measure the stop in % or in ATRs")
            Words(if (e.trailing) "below the best close" else "below the entry")
        }
        is Exit.Target -> {
            Words("A profit of")
            NumberChip("The target", e.pct, NumSpec(0.005, 10.0, percent = true)) { onChange(e.copy(pct = it)) }
        }
    }
}

// --- the trading assumptions ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Assumptions(rule: Rule, change: (Rule) -> Unit, ui: DeskUi) {
    val costs = Costs()
    val crypto = RuleEngine.MarketCosts()
    Text("TRADING ASSUMPTIONS", color = P.Faint, fontSize = 11.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() })
    Text("How the test trades, kept apart from the rule itself.", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))

    Fact("Costs", "${RuleWords.num(costs.slippageBp / 100)}% a trade on funds and stocks, ${RuleWords.num(costs.fxSlippageBp / 100)}% on " +
        "currency pairs, ${RuleWords.num(crypto.cryptoSlippageBp / 100)}% on crypto; and a check with every cost doubled")
    Fact("Trades", "at the next open, decided at the close")
    Setting("Checks the rule") {
        MenuChip(RuleWords.check(rule.check), Check.entries.map { c -> RuleWords.check(c) to { change(rule.copy(check = c)) } })
    }
    Setting("Size") {
        MenuChip(when (rule.sizing) {
            is Sizing.AsWritten -> "as written"; is Sizing.Equal -> "the same money in each"; is Sizing.EqualRisk -> "equal risk"
            is Sizing.SteadyRisk -> "a steady risk"; is Sizing.RiskPerTrade -> "risk per trade"
        }, listOf(
            "as written" to { change(rule.copy(sizing = Sizing.AsWritten)) },
            "the same money in each" to { change(rule.copy(sizing = Sizing.Equal(rule.maxGross.coerceAtMost(1.0)))) },
            "equal risk" to { change(rule.copy(sizing = Sizing.EqualRisk(60, rule.maxGross.coerceAtMost(1.0)))) },
            "a steady risk" to { change(rule.copy(sizing = Sizing.SteadyRisk(0.10, 60))) },
            "risk per trade" to { change(rule.copy(sizing = Sizing.RiskPerTrade(0.01))) }
        ))
        when (val s = rule.sizing) {
            is Sizing.AsWritten -> Unit
            is Sizing.Equal -> NumberChip("In all", s.gross, NumSpec(0.01, RuleCheck.MAX_GROSS, percent = true), label = "${RuleWords.num(s.gross * 100)}% in all") {
                change(rule.copy(sizing = s.copy(gross = it)))
            }
            is Sizing.EqualRisk -> {
                NumberChip("Measured over", s.days.toDouble(), NumSpec.LONG_DAYS) { change(rule.copy(sizing = s.copy(days = it.toInt()))) }
                NumberChip("In all", s.gross, NumSpec(0.01, RuleCheck.MAX_GROSS, percent = true), label = "${RuleWords.num(s.gross * 100)}% in all") {
                    change(rule.copy(sizing = s.copy(gross = it)))
                }
            }
            is Sizing.SteadyRisk -> {
                NumberChip("Volatility a year", s.target, NumSpec(0.01, 1.0, percent = true), label = "${RuleWords.num(s.target * 100)}% a year") {
                    change(rule.copy(sizing = s.copy(target = it)))
                }
                NumberChip("Measured over", s.days.toDouble(), NumSpec.LONG_DAYS) { change(rule.copy(sizing = s.copy(days = it.toInt()))) }
            }
            is Sizing.RiskPerTrade -> NumberChip("Risk at the stop", s.risk, NumSpec(0.001, 0.1, percent = true), label = "${RuleWords.num(s.risk * 100)}% at the stop") {
                change(rule.copy(sizing = s.copy(risk = it)))
            }
        }
    }
    Setting("Total up to") {
        NumberChip("Total weight", rule.maxGross, NumSpec(0.1, RuleCheck.MAX_GROSS, suffix = "x"), label = "${RuleWords.num(rule.maxGross)}x") {
            change(rule.copy(maxGross = it))
        }
    }
    Setting("Skips resizes under") {
        NumberChip("No-trade band", rule.band, NumSpec(0.0, 0.5, percent = true)) { change(rule.copy(band = it)) }
    }
    Fact("Held back", "the last 30% of the years; the test never tunes on them")
    Setting("Compared with holding") {
        TickerChip("Compared with", rule.benchmark, ui.tickers) { change(rule.copy(benchmark = it)) }
    }
    val traded = RuleEngine.traded(rule)
    if (traded.isNotEmpty()) {
        Text("Each market's own rules", color = P.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
        traded.forEach { a ->
            Setting("$a is") {
                val m = rule.markets[a] ?: Market.FUND
                MenuChip(RuleWords.market(m), Market.entries.map { mk ->
                    RuleWords.market(mk) to {
                        change(rule.copy(markets = if (mk == Market.FUND) rule.markets - a else rule.markets + (a to mk)))
                    }
                })
            }
        }
        traded.forEach { a -> CarrySetting(rule, a, change, ui) }
    }
    Fact("Shorts and leverage", "borrow and financing are charged")
}

@Composable
private fun CarrySetting(rule: Rule, a: String, change: (Rule) -> Unit, ui: DeskUi) {
    var one by remember { mutableStateOf(false) }
    var two by remember { mutableStateOf(false) }
    val now = rule.carry[a]
    val label = when {
        now == null -> "nothing extra"
        now.startsWith("gap:") -> now.removePrefix("gap:").replace(":", " − ")
        else -> now
    }
    Setting("$a earns") {
        MenuChip(label, listOf(
            "nothing extra" to { change(rule.copy(carry = rule.carry - a)) },
            "a rate…" to { one = true },
            "one rate minus another…" to { two = true }
        ), description = "Carry on $a: $label")
    }
    if (one) {
        SeriesDialog("$a earns", now?.takeIf { !it.startsWith("gap:") } ?: "DTB3", ui.otherSeries, onDismiss = { one = false }) {
            change(rule.copy(carry = rule.carry + (a to it)))
        }
    }
    if (two) {
        val parts = now?.takeIf { it.startsWith("gap:") }?.split(":")
        var x by remember { mutableStateOf(parts?.getOrNull(1) ?: "DTB3") }
        var y by remember { mutableStateOf(parts?.getOrNull(2) ?: "JP3M") }
        val ok = RuleCheck.isSeries(x) && RuleCheck.isSeries(y) && RuleCheck.isSeries("gap:$x:$y")
        SmallDialog("$a earns", onDismiss = { two = false }, canSave = ok, onSave = { change(rule.copy(carry = rule.carry + (a to "gap:$x:$y"))) }) {
            Text("Counted every day held, paid when short: FX swap points, crypto funding.", color = P.Muted, fontSize = 13.sp)
            TextInput("This rate", x, { x = it.trim() }, error = !RuleCheck.isSeries(x))
            Suggestions(ui.otherSeries, setOf(x)) { x = it }
            TextInput("Minus this one", y, { y = it.trim() }, error = !RuleCheck.isSeries(y))
            Suggestions(ui.otherSeries, setOf(y)) { y = it }
        }
    }
}

@Composable
private fun Fact(label: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = P.Text, fontSize = 14.sp)
        Text(text, color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
    }
    HorizontalDivider(color = P.Border)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Setting(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = P.Text, fontSize = 14.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
    HorizontalDivider(color = P.Border)
}

// --- testing ---

@Composable
private fun TestBar(my: MyRule, rule: Rule, ui: DeskUi, problems: List<String>, missing: List<String>, vm: DeskViewModel) {
    Column(Modifier.fillMaxWidth().background(P.Card).padding(horizontal = 20.dp, vertical = 12.dp)) {
        problems.firstOrNull()?.let {
            Text("⚠ $it" + (if (problems.size > 1) " (and ${problems.size - 1} more)" else "") +
                ". Changes are saved once the rule is within its limits again.", color = P.Warning, fontSize = 13.sp)
        }
        if (problems.isEmpty() && missing.isNotEmpty()) {
            Text("Needs prices for ${missing.joinToString(", ")}: import a prices file that has them.", color = P.Warning, fontSize = 13.sp)
        }
        if (!ui.hasData) Text("No prices yet: get prices on the Desk first.", color = P.Warning, fontSize = 13.sp)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((if (my.tries == 1) "1 try" else "${my.tries} tries") + " so far on this rule", color = P.Text, fontSize = 14.sp)
                Text("Each change you test is one more try; the luck question gets harder with every one.",
                    color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
            }
            // Edited since the test: the saved copy says so, or an edit is still being saved.
            val changed = my.changed || rule != my.rule
            if (my.tested && changed) Action("Undo changes", muted = true) { vm.undo(my.id) }
            else if (my.canUndo) Action("Back a version", muted = true) { vm.undo(my.id) }
            Spacer(Modifier.width(8.dp))
            val ready = problems.isEmpty() && missing.isEmpty() && ui.hasData && ui.working == null
            if (my.tested && !changed) {
                PrimaryButton("See the test", enabled = true) { vm.openReport(my.key) }
            } else {
                PrimaryButton("Test it", enabled = ready) { vm.test(my.id, rule) }
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (enabled) P.OnAccent else P.Faint,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) P.Accent else P.Elevated)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
}
