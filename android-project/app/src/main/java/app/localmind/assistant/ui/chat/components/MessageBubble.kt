package app.localmind.assistant.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.model.ChatMessage
import app.localmind.assistant.domain.model.MessageRole
import app.localmind.assistant.domain.model.MessageStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: ChatMessage,
    isLatestAssistantMessage: Boolean,
    onEditUserMessage: (ChatMessage) -> Unit,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (message.role) {
        MessageRole.USER -> UserMessageBubble(
            message = message,
            onEdit = { onEditUserMessage(message) },
            modifier = modifier
        )
        MessageRole.ASSISTANT -> AssistantMessageBubble(
            message = message,
            isLatest = isLatestAssistantMessage,
            onRegenerate = onRegenerate,
            onRetry = onRetry,
            modifier = modifier
        )
        MessageRole.SYSTEM -> {
            // Internal system directives are hidden or displayed minimally
            SystemDirectiveNotice(message = message, modifier = modifier)
        }
        MessageRole.TOOL -> {
            ToolInvocationNotice(message = message, modifier = modifier)
        }
    }
}

@Composable
fun UserMessageBubble(
    message: ChatMessage,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatted = remember(message.createdAt) { formatTimestamp(message.createdAt) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                    .background(Color(0xFF1E222A))
                    .border(1.dp, Color(0xFF2A2E38), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    color = Color(0xFFF4F4F6),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }

            // Timestamp & Edit Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp, end = 4.dp)
            ) {
                Text(
                    text = timeFormatted,
                    color = Color(0xFF8E929F),
                    fontSize = 11.sp
                )
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit message",
                        tint = Color(0xFF8E929F),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AssistantMessageBubble(
    message: ChatMessage,
    isLatest: Boolean,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timeFormatted = remember(message.createdAt) { formatTimestamp(message.createdAt) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (message.status) {
                                MessageStatus.STREAMING -> Color(0xFF38BDF8)
                                MessageStatus.COMPLETE -> Color(0xFF34D399)
                                MessageStatus.FAILED -> Color(0xFFF87171)
                                MessageStatus.CANCELLED -> Color(0xFFFBBF24)
                                MessageStatus.PENDING -> Color(0xFF8E929F)
                            }
                        )
                )
                Text(
                    text = message.modelIdUsed ?: "LocalMind",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "• $timeFormatted",
                    color = Color(0xFF8E929F),
                    fontSize = 11.sp
                )

                if (message.status == MessageStatus.CANCELLED) {
                    Text(
                        text = "(Stopped)",
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp
                    )
                }
            }

            // Bubble body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(Color(0xFF0C0E12))
                    .border(1.dp, Color(0xFF191D24), RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .padding(14.dp)
            ) {
                when (message.status) {
                    MessageStatus.PENDING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF38BDF8),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Preparing local model...",
                                color = Color(0xFF8E929F),
                                fontSize = 14.sp
                            )
                        }
                    }
                    MessageStatus.FAILED -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = message.content.ifBlank { "Inference failed to complete." },
                                color = Color(0xFFF87171),
                                fontSize = 14.sp
                            )
                            if (message.errorCode != null) {
                                Text(
                                    text = "Error code: ${message.errorCode}",
                                    color = Color(0xFF8E929F),
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2A1B1E),
                                    contentColor = Color(0xFFF87171)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", fontSize = 12.sp)
                            }
                        }
                    }
                    else -> {
                        Column {
                            MarkdownContent(content = message.content)

                            if (message.status == MessageStatus.STREAMING) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = Color(0xFF38BDF8),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Generating...",
                                        color = Color(0xFF8E929F),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Stats footer & action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tokens/speed stat
                if (message.generationSpeedTps != null && message.generationSpeedTps > 0) {
                    Text(
                        text = "%.1f tok/s • %d tokens".format(message.generationSpeedTps, message.completionTokens),
                        color = Color(0xFF6B7280),
                        fontSize = 11.sp
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Action buttons (Copy, Share, Regenerate)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.content.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Response", message.content))
                                Toast.makeText(context, "Copied response to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Response",
                                tint = Color(0xFF8E929F),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, message.content)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share response via")
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Response",
                                tint = Color(0xFF8E929F),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (isLatest && message.status != MessageStatus.STREAMING) {
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate Response",
                                tint = Color(0xFF8E929F),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SystemDirectiveNotice(message: ChatMessage, modifier: Modifier = Modifier) {
    // Phase 1: Keep system directive non-intrusive
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "System instruction applied",
            color = Color(0xFF4B5563),
            fontSize = 11.sp
        )
    }
}

@Composable
fun ToolInvocationNotice(message: ChatMessage, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color(0xFF131A26), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "Deterministic Tool Output: ${message.content}",
            color = Color(0xFF38BDF8),
            fontSize = 12.sp
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
