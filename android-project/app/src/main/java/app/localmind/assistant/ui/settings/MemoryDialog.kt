package app.localmind.assistant.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.data.memory.Memory
import app.localmind.assistant.data.memory.MemoryRules
import app.localmind.assistant.ui.theme.SlothPalette as P

/** Settings → Memory: everything SlothLlama keeps about the user, to add to, edit or delete. */
@Composable
fun MemoryDialog(
    memories: List<Memory>,
    onSave: (id: String?, text: String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Elevated,
        title = { Text("What SlothLlama remembers", color = P.Text) },
        text = {
            Column {
                Text(
                    "Only what you add is kept, in your words, on this tablet. Tap one to edit it. In a chat, " +
                        "\"remember that …\" adds one and \"forget that …\" removes one.",
                    color = P.Muted, fontSize = 12.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    if (memories.isEmpty()) {
                        Text("Nothing yet.", color = P.Faint, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    memories.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                m.text,
                                color = if (editing == m.id) P.Accent else P.Text,
                                fontSize = 14.sp, lineHeight = 19.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { editing = m.id; draft = m.text }
                                    .padding(vertical = 8.dp)
                            )
                            IconButton(onClick = {
                                onDelete(m.id)
                                if (editing == m.id) { editing = null; draft = "" }
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = P.Muted, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = P.Border)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(MemoryRules.MAX_CHARS) },
                    label = { Text(if (editing == null) "Add one, e.g. I'm vegetarian" else "Edit this memory") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = P.Text, unfocusedTextColor = P.Text,
                        focusedContainerColor = P.Card, unfocusedContainerColor = P.Card,
                        focusedBorderColor = P.Accent, unfocusedBorderColor = P.Border,
                        focusedLabelColor = P.Accent, unfocusedLabelColor = P.Muted, cursorColor = P.Accent
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (memories.isNotEmpty()) {
                        TextButton(onClick = {
                            if (confirmAll) { onDeleteAll(); confirmAll = false; editing = null; draft = "" } else confirmAll = true
                        }) {
                            Text(if (confirmAll) "Tap again to delete all" else "Forget everything", color = P.Danger, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (editing != null) {
                        TextButton(onClick = { editing = null; draft = "" }) { Text("Cancel", color = P.Muted) }
                    }
                    TextButton(
                        enabled = draft.isNotBlank(),
                        onClick = {
                            onSave(editing, draft)
                            editing = null
                            draft = ""
                        }
                    ) { Text(if (editing == null) "Add" else "Save", color = if (draft.isNotBlank()) P.Accent else P.Faint) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = P.Accent) } }
    )
}
