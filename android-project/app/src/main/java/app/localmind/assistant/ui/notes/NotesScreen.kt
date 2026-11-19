package app.localmind.assistant.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.localmind.assistant.data.tools.Note
import app.localmind.assistant.ui.common.SlothTopBar
import app.localmind.assistant.ui.theme.SlothPalette as P
import java.text.DateFormat
import java.util.Date

/** Quick notes: written here, or by saying "take a note: …" to an assistant in a chat. */
@Composable
fun NotesScreen(viewModel: NotesViewModel, onBack: () -> Unit, onRead: (String) -> Unit = {}) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Note?>(null) }

    Box(Modifier.fillMaxSize().background(P.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            SlothTopBar(title = "Notes", subtitle = "On this tablet · in backups", onBack = onBack)
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 1280.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(P.Elevated)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            textStyle = TextStyle(color = P.Text, fontSize = 16.sp, lineHeight = 22.sp),
                            cursorBrush = SolidColor(P.Accent),
                            maxLines = 6,
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                Box {
                                    if (draft.isEmpty()) Text("Write a note…", color = P.Faint, fontSize = 16.sp)
                                    inner()
                                }
                            }
                        )
                        if (draft.isNotBlank()) {
                            Text(
                                "Save", color = P.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.add(draft); draft = "" }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (notes.isEmpty()) {
                        Text(
                            "No notes yet. Write one above, or tell an assistant in a chat: \"take a note: the wifi code is on " +
                                "the fridge\". Then ask \"what did I note about wifi?\" or \"show my notes\".",
                            color = P.Muted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 18.dp)
                        )
                    }
                    // One column in portrait, two or three side by side in landscape.
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(360.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(notes, key = { it.id }) { note ->
                            NoteCard(note, onEdit = { editing = note }, onDelete = { viewModel.remove(note) }, onRead = { onRead(note.id) })
                        }
                    }
                }
            }
        }
    }

    editing?.let { note ->
        var text by remember(note.id) { mutableStateOf(note.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = P.Elevated,
            title = { Text("Edit note", color = P.Text) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = P.Text, unfocusedTextColor = P.Text,
                        focusedBorderColor = P.Accent, unfocusedBorderColor = P.Border, cursorColor = P.Accent
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.update(note, text); editing = null }) { Text("Save", color = P.Accent) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel", color = P.Muted) } }
        )
    }
}

@Composable
private fun NoteCard(note: Note, onEdit: () -> Unit, onDelete: () -> Unit, onRead: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(P.Card)
            .border(1.dp, P.Border, shape)
            .clickable(onClick = onEdit)
            .padding(start = 14.dp, top = 12.dp, bottom = 4.dp, end = 4.dp)
    ) {
        Text(
            note.text, color = P.Text, fontSize = 15.sp, lineHeight = 21.sp,
            maxLines = 10, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 10.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.createdAt)),
                color = P.Faint, fontSize = 11.sp, modifier = Modifier.weight(1f)
            )
            Text(
                "Read", color = P.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRead).padding(horizontal = 10.dp, vertical = 8.dp)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete note", tint = P.Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}
