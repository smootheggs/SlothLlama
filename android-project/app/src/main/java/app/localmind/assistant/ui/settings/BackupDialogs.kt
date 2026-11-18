package app.localmind.assistant.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.ui.theme.SlothPalette as P

/** Optional password for a new backup. Empty = no password. */
@Composable
fun ExportPasswordDialog(onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val mismatch = password.isNotEmpty() && repeat.isNotEmpty() && password != repeat
    val ok = password.isEmpty() || (password.length >= 6 && password == repeat)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Elevated,
        title = { Text("Protect this backup?", color = P.Text) },
        text = {
            Column {
                Text(
                    "Chats, custom personas, flashcards and settings go into one file. With a password it's " +
                        "encrypted (AES-256), and nobody can read it without the password — including you, " +
                        "if you forget it. Leave empty for no password.",
                    color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                PasswordField("Password (optional, 6+ characters)", password) { password = it }
                if (password.isNotEmpty()) PasswordField("Repeat password", repeat) { repeat = it }
                if (mismatch) Text("The passwords don't match.", color = P.Danger, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { onConfirm(password.ifEmpty { null }) }) {
                Text("Choose where to save", color = if (ok) P.Accent else P.Faint)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = P.Muted) } }
    )
}

/** The backup being restored is encrypted. */
@Composable
fun RestorePasswordDialog(wrongBefore: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Elevated,
        title = { Text("Backup password", color = P.Text) },
        text = {
            Column {
                Text("This backup is password-protected.", color = P.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                PasswordField("Password", password) { password = it }
                if (wrongBefore) Text("That password didn't work. Try again.", color = P.Danger, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty(), onClick = { onConfirm(password) }) {
                Text("Restore", color = if (password.isNotEmpty()) P.Accent else P.Faint)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = P.Muted) } }
    )
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = P.Text, unfocusedTextColor = P.Text,
            focusedContainerColor = P.Card, unfocusedContainerColor = P.Card,
            focusedBorderColor = P.Accent, unfocusedBorderColor = P.Border,
            focusedLabelColor = P.Accent, unfocusedLabelColor = P.Muted, cursorColor = P.Accent
        )
    )
}

/** The password for automatic backups (asked twice; it can't be recovered). */
@Composable
fun AutoBackupPasswordDialog(changing: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val min = app.localmind.assistant.data.backup.AutoBackup.MIN_PASSWORD
    val mismatch = repeat.isNotEmpty() && password != repeat
    val ok = password.length >= min && password == repeat
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Elevated,
        title = { Text(if (changing) "New backup password" else "Backup password", color = P.Text) },
        text = {
            Column {
                Text(
                    "Automatic backups are always encrypted. You'll need this password to restore one on a " +
                        "new or reset tablet, and nobody can recover it for you, so write it down somewhere " +
                        "safe. SlothLlama keeps it locked by the tablet's secure hardware." +
                        if (changing) " Older backups keep their old password." else "",
                    color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                PasswordField("Password ($min+ characters)", password) { password = it }
                PasswordField("Repeat password", repeat) { repeat = it }
                if (mismatch) Text("The passwords don't match.", color = P.Danger, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { onConfirm(password) }) {
                Text("Save", color = if (ok) P.Accent else P.Faint)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = P.Muted) } }
    )
}

/** After a restore: what the backup listed that isn't on this tablet yet. */
@Composable
fun ToImportDialog(items: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Elevated,
        title = { Text("Still to import", color = P.Text) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Models and Library files are too big for a backup. These were on the tablet the " +
                        "backup came from; import them again from Models and Library.",
                    color = P.Muted, fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                items.forEach { Text("• $it", color = P.Text, fontSize = 13.sp, lineHeight = 19.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it", color = P.Accent) } }
    )
}

/** Settings → Diagnostics: the report, to copy into a bug report. */
@Composable
fun DiagnosticsDialog(report: String?, onCopy: () -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Elevated,
        title = { Text("Diagnostics", color = P.Text) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Stays on this tablet unless you copy it. Crash notes can include bits of what was on " +
                        "screen, so read before you share.",
                    color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(report ?: "Gathering…", color = P.Text, fontSize = 11.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = { TextButton(enabled = report != null, onClick = onCopy) { Text("Copy", color = P.Accent) } },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Clear crashes", color = P.Muted) }
                TextButton(onClick = onDismiss) { Text("Close", color = P.Muted) }
            }
        }
    )
}
