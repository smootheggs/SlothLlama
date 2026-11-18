package app.slothlobster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val PanelShape = RoundedCornerShape(16.dp)

/** A dark card with a hairline border, the building block of every screen. */
@Composable
fun Panel(modifier: Modifier = Modifier, border: Color = Sea.Border, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Sea.Card)
            .border(1.dp, border, PanelShape),
        content = content
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.semantics { heading() },
        color = Sea.Faint,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp
    )
}

@Composable
fun Section(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        content()
    }
}

@Composable
fun Pill(text: String, hot: Boolean = false) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (hot) Sea.CoralSoft else Sea.Raised)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = if (hot) Sea.Coral else Sea.Muted,
        fontSize = 12.sp,
        maxLines = 1
    )
}

@Composable
fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Sea.Divider)
    )
}

/** A row in a panel: title, a line under it, and something on the right. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    titleLines: Int = 1,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Sea.Text, fontSize = 15.sp, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = Sea.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke(this)
        }
        if (divider) Divider()
    }
}

@Composable
fun CoralSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Sea.OnCoral,
            checkedTrackColor = Sea.Coral,
            checkedBorderColor = Sea.Coral,
            uncheckedThumbColor = Sea.Soft,
            uncheckedTrackColor = Sea.Off,
            uncheckedBorderColor = Sea.Off
        )
    )
}

/** A row with a label and a switch; the whole row toggles. */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, divider: Boolean = true) {
    ListRow(title, subtitle, onClick = { onChange(!checked) }, divider = divider) {
        CoralSwitch(checked, onChange, title)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Sea.Coral else Sea.Off)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) Icon(icon, null, tint = if (enabled) Sea.OnCoral else Sea.Muted, modifier = Modifier.size(18.dp))
        Text(text, color = if (enabled) Sea.OnCoral else Sea.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, icon: ImageVector? = null) {
    Row(
        modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (danger) Sea.DangerSoft else Color.Transparent)
            .border(1.dp, if (danger) Sea.DangerLine else Sea.Off, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) Icon(icon, null, tint = if (danger) Sea.Danger else Sea.Text, modifier = Modifier.size(18.dp))
        Text(text, color = if (danger) Sea.Danger else Sea.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** A choice chip: coral when picked, dashed-looking and dim when not available. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(shape)
            .background(
                when {
                    selected -> Sea.CoralSoft
                    enabled -> Sea.Raised
                    else -> Color.Transparent
                }
            )
            .border(1.dp, if (selected) Sea.CoralSoft else if (enabled) Sea.BorderStrong else Sea.Off, shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                selected -> Sea.Coral
                enabled -> Sea.Soft
                else -> Sea.Faint
            },
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Sea.Raised)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, color = if (accent) Sea.Coral else Sea.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Sea.Muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun StatusDot(on: Boolean) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (on) Sea.Awake else Sea.Faint)
    )
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) 8.dp else 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(LobsterIcons.Back, contentDescription = "Back", tint = Sea.Soft) }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                color = Sea.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) Text(subtitle, color = Sea.Muted, fontSize = 13.sp)
        }
        actions()
    }
}

/** A thin coral progress bar. */
@Composable
fun Progress(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Sea.Border)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(6.dp)
                .background(Sea.Coral)
        )
    }
}

// --- words for times ---

fun ago(at: Long, now: Long): String {
    if (at <= 0) return "never"
    val minutes = (now - at) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 48 * 60 -> "yesterday"
        else -> SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date(at))
    }
}

fun clockTime(at: Long): String = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(at))

fun isToday(at: Long, now: Long): Boolean = dayOf(at) == dayOf(now)

fun dayOf(at: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = at }
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

fun dayLabel(at: Long, now: Long): String = when (dayOf(now) - dayOf(at)) {
    0 -> "Today"
    1 -> "Yesterday"
    else -> SimpleDateFormat("EEEE d MMMM", Locale.ENGLISH).format(Date(at))
}
