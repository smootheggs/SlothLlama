package app.localmind.assistant.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.LocalDate
import kotlin.math.roundToInt

/** A one-tap chance on the Call it sheet: the model's, the market's, the crowd's, or a round number. */
data class Preset(val label: String, val chance: Double)

/**
 * Call it: the one sheet for making a call, wherever you are (a game row, a watchlist line, a
 * price level). Upright it rises from the bottom; on a wide screen lying on its side it opens at
 * the side, so the list or chart you were looking at stays in view. The question, the presets and
 * the check date come from where you are; the chance and the reason are yours.
 *
 * [settles]: a line saying how it settles by itself ("Marks itself from the final score"), with the
 * check date fixed at [fixedCheckOn]; without it you pick the date. [optionalChance]: a watch item
 * may go in without a chance, counted but not scored.
 */
@Composable
internal fun CallItSheet(
    where: String,
    question: String,
    onDismiss: () -> Unit,
    onCall: (question: String, chance: Double?, checkOn: String, reason: String) -> Unit,
    context: String = "",
    editable: Boolean = false,
    start: Double = 0.5,
    presets: List<Preset> = emptyList(),
    settles: String? = null,
    fixedCheckOn: String = "",
    checkDays: Long = 7,
    optionalChance: Boolean = false,
    note: String = ""
) {
    val config = LocalConfiguration.current
    val side = config.screenWidthDp >= 840 && config.screenWidthDp > config.screenHeightDp
    var q by remember { mutableStateOf(question) }
    var chance by remember { mutableFloatStateOf((start * 100).roundToInt().toFloat().coerceIn(1f, 99f)) }
    var withChance by remember { mutableStateOf(!optionalChance) }
    var check by remember { mutableStateOf(fixedCheckOn.ifBlank { LocalDate.now().plusDays(checkDays).toString() }) }
    var why by remember { mutableStateOf("") }
    val checkOk = runCatching { LocalDate.parse(check) }.isSuccess
    val canCall = q.isNotBlank() && checkOk

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            // The rest of the screen: a tap there closes the sheet.
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss
                )
            )
            val shape = if (side) RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp) else RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            val placed = if (side) Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(460.dp)
            else Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().heightIn(max = (config.screenHeightDp * 0.9f).dp)
            Column(
                placed
                    .clip(shape)
                    .background(P.Card)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!side) Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(P.BorderStrong))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Call it", color = P.Text, fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(where, color = P.Faint, fontSize = 12.sp)
                }
                if (editable) {
                    FormField("The question", q, { q = it.take(240) }, lines = 2)
                    Text("Make it a question that will have an answer by the day you check.", color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
                } else {
                    Text(q, color = P.Text, fontSize = 19.sp, lineHeight = 26.sp, fontFamily = FontFamily.Serif)
                }
                if (context.isNotBlank()) Text(context, color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                if (optionalChance) Toggle("Give it a chance now", withChance) { withChance = it }
                if (withChance) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${chance.roundToInt()}%", color = P.Accent, fontSize = 40.sp, fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium, modifier = Modifier.width(100.dp)
                        )
                        Slider(
                            value = chance, onValueChange = { chance = it.roundToInt().toFloat() }, valueRange = 1f..99f, steps = 97,
                            colors = SliderDefaults.colors(thumbColor = P.Accent, activeTrackColor = P.Accent, inactiveTrackColor = P.Elevated),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (presets.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            presets.forEach { p ->
                                val pct = (p.chance * 100).roundToInt().coerceIn(1, 99)
                                ChoicePill(p.label, chance.roundToInt() == pct, { chance = pct.toFloat() })
                            }
                        }
                    }
                } else {
                    Text("Without a chance it's counted (it happened or it didn't) but not scored.", color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
                }
                if (settles != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = P.Accent, modifier = Modifier.size(16.dp))
                        Text(settles, color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Check", color = P.Muted, fontSize = 12.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("In a week" to 7L, "In a month" to 30L, "In 3 months" to 91L).forEach { (label, days) ->
                                val d = LocalDate.now().plusDays(days).toString()
                                ChoicePill(label, check == d, { check = d })
                            }
                        }
                        DateField("On", check) { if (it.isNotBlank()) check = it }
                    }
                }
                FormField("Why, in a line (optional)", why, { why = it.take(300) })
                if (note.isNotBlank()) Text(note, color = P.Faint, fontSize = 12.sp, lineHeight = 16.sp)
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = P.Muted) }
                    Button(
                        onClick = {
                            onCall(q.trim(), if (withChance) chance / 100.0 else null, check, why.trim())
                            onDismiss()
                        },
                        enabled = canCall,
                        colors = ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.OnAccent)
                    ) { Text("Call it") }
                }
            }
        }
    }
}
