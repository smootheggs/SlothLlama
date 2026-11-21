package app.localmind.assistant.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Editors shared by the record pages: a dialog with Save, Cancel and room for Delete, text fields,
 * a calendar date, a switch, a name, and a confirmation.
 */

private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private fun dayOf(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()

/** "20 Nov 2026"; blank or unreadable → as it is. */
internal fun showDay(text: String): String = dayOf(text)?.format(dayFormat) ?: text


@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = P.Accent,
    unfocusedBorderColor = P.Border,
    focusedTextColor = P.Text,
    unfocusedTextColor = P.Text,
    cursorColor = P.Accent,
    focusedLabelColor = P.Accent,
    unfocusedLabelColor = P.Muted
)

@Composable
internal fun FormField(label: String, value: String, onChange: (String) -> Unit, lines: Int = 1, hint: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (hint == null) null else { { Text(hint, color = P.Faint) } },
        singleLine = lines == 1,
        minLines = lines,
        maxLines = if (lines == 1) 1 else 8,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

/** An editor: title, fields that scroll, Save and Cancel, and room for Delete. */
@Composable
internal fun EditDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean = true,
    saveLabel: String = "Save",
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Card,
        title = { Text(title, color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) { Text(saveLabel, color = if (canSave) P.Accent else P.Faint) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = P.Danger) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = P.Muted) }
            }
        }
    )
}

/** A day picked from a calendar, kept as yyyy-MM-dd; it can be cleared. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(label: String, value: String, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = P.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { picking = true }) {
            Text(if (value.isBlank()) "Set a date" else showDay(value), color = P.Accent, fontSize = 15.sp)
        }
        if (value.isNotBlank()) {
            IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear the date", tint = P.Muted, modifier = Modifier.size(20.dp)) }
        }
    }
    if (picking) {
        val initial = dayOf(value)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms -> onChange(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    picking = false
                }) { Text("Set", color = P.Accent) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel", color = P.Muted) } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
internal fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = P.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = on, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = P.Accent, checkedThumbColor = P.OnAccent)
        )
    }
}

@Composable
internal fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit, saveLabel: String = "Save", maxLength: Int = 120) {
    var name by remember { mutableStateOf(initial) }
    EditDialog(title, onDismiss, { onSave(name); onDismiss() }, canSave = name.isNotBlank(), saveLabel = saveLabel) {
        FormField("Name", name, { name = it.take(maxLength) })
    }
}

@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Card,
        title = { Text(title, color = P.Text) },
        text = { Text(text, color = P.Muted, fontSize = 15.sp, lineHeight = 21.sp) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = P.Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = P.Muted) } }
    )
}
