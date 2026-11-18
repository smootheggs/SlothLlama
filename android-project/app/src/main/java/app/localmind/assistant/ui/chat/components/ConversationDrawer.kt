package app.localmind.assistant.ui.chat.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import app.localmind.assistant.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.model.Conversation
import app.localmind.assistant.ui.theme.SlothPalette as P

/**
 * The app's navigation, Claude-style: new chat, the main destinations, then recent chats.
 */
@Composable
fun ConversationDrawerContent(
    conversations: List<Conversation>,
    archivedConversations: List<Conversation>,
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onRenameConversation: (Conversation) -> Unit,
    onArchiveConversation: (Conversation) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    onClearAll: () -> Unit,
    onOpenPersonas: () -> Unit = {},
    onOpenModels: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenFlashcards: () -> Unit = {},
    onOpenDictionary: () -> Unit = {},
    onOpenHome: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    flashcardsDue: Int = 0,
    modelStatus: String = "",
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {}
) {
    var archivedOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxHeight().background(P.Card).padding(horizontal = 12.dp)) {
        Row(
            Modifier.padding(start = 10.dp, top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.sloth_llama),
                contentDescription = null,
                modifier = Modifier.size(width = 37.dp, height = 32.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text("SlothLlama", color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }

        NavRow(Icons.Outlined.Home, "Home", onOpenHome)
        NavRow(Icons.Outlined.EditNote, "New chat", onNewConversation, highlighted = true)
        NavRow(Icons.Outlined.PersonOutline, "Personas", onOpenPersonas)
        NavRow(Icons.Outlined.Style, "Flashcards", onOpenFlashcards, trailing = if (flashcardsDue > 0) "$flashcardsDue due" else "")
        NavRow(Icons.Outlined.Translate, "Dictionary", onOpenDictionary)
        NavRow(Icons.Outlined.LocalLibrary, "Library", onOpenLibrary)
        NavRow(Icons.Outlined.Memory, "Models", onOpenModels, trailing = modelStatus)
        NavRow(Icons.Outlined.Settings, "Settings", onOpenSettings)

        HorizontalDivider(color = P.Border, modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp))

        SearchField(searchQuery, onSearchQueryChange)
        Text(
            if (searchQuery.isBlank()) "Recent" else "Matches", color = P.Faint, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
        )

        LazyColumn(Modifier.weight(1f)) {
            if (conversations.isEmpty()) {
                item {
                    Text(
                        if (searchQuery.isBlank()) "No conversations yet" else "No chats mention “${searchQuery.trim()}”",
                        color = P.Faint, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            items(conversations, key = { it.id }) { conversation ->
                ConversationDrawerItem(
                    conversation = conversation,
                    isActive = conversation.id == activeConversationId,
                    onSelect = { onSelectConversation(conversation.id) },
                    onRename = { onRenameConversation(conversation) },
                    onArchive = { onArchiveConversation(conversation) },
                    onDelete = { onDeleteConversation(conversation) }
                )
            }
            if (archivedConversations.isNotEmpty()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { archivedOpen = !archivedOpen }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Archived (${archivedConversations.size})",
                            color = P.Faint, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            if (archivedOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null, tint = P.Faint, modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (archivedOpen) {
                    items(archivedConversations, key = { "archived-${it.id}" }) { conversation ->
                        ConversationDrawerItem(
                            conversation = conversation,
                            isActive = conversation.id == activeConversationId,
                            onSelect = { onSelectConversation(conversation.id) },
                            onRename = { onRenameConversation(conversation) },
                            onArchive = { onArchiveConversation(conversation) },
                            onDelete = { onDeleteConversation(conversation) }
                        )
                    }
                }
            }
        }

        if (conversations.isNotEmpty() || archivedConversations.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClearAll)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = P.Faint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Delete all conversations", color = P.Faint, fontSize = 13.sp)
            }
        }
    }
}

/** Searches chat titles and everything said in them. */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(P.Elevated)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = P.Faint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = P.Text, fontSize = 14.sp),
            cursorBrush = SolidColor(P.Accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text("Search chats", color = P.Faint, fontSize = 14.sp)
                    inner()
                }
            }
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close, contentDescription = "Clear search", tint = P.Faint,
                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).clickable { onChange("") }
            )
        }
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    trailing: String = ""
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (highlighted) P.Accent else P.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        // The label keeps its full width; a long status (e.g. "Loading …") is cut with "…" instead.
        Text(
            label, color = if (highlighted) P.Accent else P.Text, fontSize = 15.sp,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, softWrap = false
        )
        Text(
            trailing, color = P.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 12.dp)
        )
    }
}

@Composable
fun ConversationDrawerItem(
    conversation: Conversation,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isActive) P.Elevated else P.Card)
                .clickable(onClick = onSelect)
                .padding(start = 12.dp, end = 2.dp)
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                conversation.title,
                color = if (isActive) P.Text else P.Muted,
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "Conversation options", tint = P.Faint, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.align(Alignment.TopEnd)) {
            DropdownMenuItem(
                text = { Text("Rename", color = P.Text, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null, tint = P.Muted, modifier = Modifier.size(18.dp)) },
                onClick = { menuOpen = false; onRename() }
            )
            DropdownMenuItem(
                text = { Text(if (conversation.isArchived) "Unarchive" else "Archive", color = P.Text, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        if (conversation.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        null, tint = P.Muted, modifier = Modifier.size(18.dp)
                    )
                },
                onClick = { menuOpen = false; onArchive() }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = P.Danger, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = P.Danger, modifier = Modifier.size(18.dp)) },
                onClick = { menuOpen = false; onDelete() }
            )
        }
    }
}
