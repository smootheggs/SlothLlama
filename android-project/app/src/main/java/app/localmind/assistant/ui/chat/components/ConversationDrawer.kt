package app.localmind.assistant.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.model.Conversation

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
    modifier: Modifier = Modifier
) {
    var isArchivedExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(Color(0xFF090A0D))
            .padding(16.dp)
    ) {
        // App header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "SlothLlama",
                color = Color(0xFFF4F4F6),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // "+ New Chat" Button
        Button(
            onClick = onNewConversation,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E222A),
                contentColor = Color(0xFFF4F4F6)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .border(1.dp, Color(0xFF2E3440), RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Chat", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Conversations",
            color = Color(0xFF6B7280),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        // Conversation List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (conversations.isEmpty() && archivedConversations.isEmpty()) {
                item {
                    Text(
                        text = "No conversations yet",
                        color = Color(0xFF4B5563),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            items(conversations, key = { it.id }) { conversation ->
                ConversationDrawerItem(
                    conversation = conversation,
                    isSelected = conversation.id == activeConversationId,
                    onSelect = { onSelectConversation(conversation.id) },
                    onRename = { onRenameConversation(conversation) },
                    onArchive = { onArchiveConversation(conversation) },
                    onDelete = { onDeleteConversation(conversation) }
                )
            }

            // Archived section
            if (archivedConversations.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isArchivedExpanded = !isArchivedExpanded }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Archived (${archivedConversations.size})",
                            color = Color(0xFF8E929F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = if (isArchivedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color(0xFF8E929F),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (isArchivedExpanded) {
                    items(archivedConversations, key = { "archived-${it.id}" }) { conv ->
                        ConversationDrawerItem(
                            conversation = conv,
                            isSelected = conv.id == activeConversationId,
                            onSelect = { onSelectConversation(conv.id) },
                            onRename = { onRenameConversation(conv) },
                            onArchive = { onArchiveConversation(conv) },
                            onDelete = { onDeleteConversation(conv) }
                        )
                    }
                }
            }
        }

        // Clear all conversations footer
        Divider(color = Color(0xFF1E222A), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClearAll)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = null,
                tint = Color(0xFFF87171),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Delete all conversations",
                color = Color(0xFFF87171),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ConversationDrawerItem(
    conversation: Conversation,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF151922) else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (conversation.isArchived) Icons.Default.Archive else Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF6B7280),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = conversation.title,
                color = if (isSelected) Color(0xFFF4F4F6) else Color(0xFF9CA3AF),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(
                onClick = { isMenuOpen = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
                modifier = Modifier.background(Color(0xFF12151C))
            ) {
                DropdownMenuItem(
                    text = { Text("Rename", color = Color(0xFFF4F4F6), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF8E929F), modifier = Modifier.size(16.dp))
                    },
                    onClick = {
                        isMenuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (conversation.isArchived) "Unarchive" else "Archive",
                            color = Color(0xFFF4F4F6),
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (conversation.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = null,
                            tint = Color(0xFF8E929F),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = {
                        isMenuOpen = false
                        onArchive()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFF87171), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                    },
                    onClick = {
                        isMenuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}
