package app.localmind.assistant.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.model.ChatMessage
import app.localmind.assistant.domain.model.MessageRole
import app.localmind.assistant.domain.model.MessageStatus
import app.localmind.assistant.ui.speech.LocalSpeakAction
import app.localmind.assistant.ui.study.LocalSaveWordAction
import app.localmind.assistant.ui.theme.SlothPalette as P

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
        MessageRole.USER -> UserMessageBubble(message, onEdit = { onEditUserMessage(message) }, modifier = modifier)
        MessageRole.ASSISTANT -> AssistantMessageBubble(
            message = message,
            isLatest = isLatestAssistantMessage,
            onRegenerate = onRegenerate,
            onRetry = onRetry,
            modifier = modifier
        )
        MessageRole.SYSTEM -> SystemDirectiveNotice(message, modifier)
        MessageRole.TOOL -> ToolInvocationNotice(message, modifier)
    }
}

/** Your message: a soft rounded bubble on the right, edit action beneath. */
@Composable
fun UserMessageBubble(message: ChatMessage, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.End
    ) {
        SelectionContainer {
            Text(
                text = message.content,
                color = P.Text,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(P.Elevated)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        ActionIcon(Icons.Outlined.Edit, "Edit message", Modifier.padding(top = 2.dp), onClick = onEdit)
    }
}

/** The assistant's reply: plain text across the reading column, quiet actions underneath. */
@Composable
fun AssistantMessageBubble(
    message: ChatMessage,
    isLatest: Boolean,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val speak = LocalSpeakAction.current
    val saveWord = LocalSaveWordAction.current

    Column(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        when (message.status) {
            MessageStatus.PENDING -> ThinkingIndicator("Thinking")

            MessageStatus.FAILED -> Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(P.DangerSoft)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    message.content.ifBlank { "Something went wrong while generating." },
                    color = P.Danger, fontSize = 14.sp, lineHeight = 20.sp
                )
                message.errorCode?.let { Text(it, color = P.Muted, fontSize = 11.sp) }
                Text(
                    "Try again",
                    color = P.Danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRetry).padding(vertical = 4.dp)
                )
            }

            else -> {
                if (message.content.isBlank() && message.status == MessageStatus.STREAMING) {
                    ThinkingIndicator("Thinking")
                } else {
                    SelectionContainer {
                        MarkdownContent(content = message.content)
                    }
                }
                if (message.status == MessageStatus.CANCELLED) {
                    Text("Stopped", color = P.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        val finished = message.status == MessageStatus.COMPLETE || message.status == MessageStatus.CANCELLED
        if (finished && message.content.isNotBlank()) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ActionIcon(Icons.Outlined.ContentCopy, "Copy") {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Response", message.content))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
                if (speak != null) {
                    ActionIcon(Icons.AutoMirrored.Filled.VolumeUp, "Read aloud") { speak(message.content) }
                }
                if (saveWord != null && HAN_CHARS.containsMatchIn(message.content)) {
                    ActionIcon(Icons.Outlined.StarOutline, "Save words to flashcards") { saveWord(message.content) }
                }
                ActionIcon(Icons.Outlined.Share, "Share") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, message.content)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(send, "Share response"))
                }
                if (isLatest) {
                    ActionIcon(Icons.Outlined.Refresh, "Regenerate", onClick = onRegenerate)
                }
            }
        }
    }
}

private val HAN_CHARS = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")

@Composable
private fun ActionIcon(icon: ImageVector, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = P.Faint, modifier = Modifier.size(18.dp))
    }
}

/** Three softly pulsing dots and a label, shown before the first token arrives. */
@Composable
private fun ThinkingIndicator(label: String) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val pulse by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(6.dp)
                    .alpha((if (i == 1) 1.3f - pulse else pulse).coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(P.Accent)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = P.Muted, fontSize = 14.sp)
    }
}

@Composable
fun SystemDirectiveNotice(message: ChatMessage, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text("System instruction applied", color = P.Faint, fontSize = 11.sp)
    }
}

@Composable
fun ToolInvocationNotice(message: ChatMessage, modifier: Modifier = Modifier) {
    Text(
        text = "Calculator: ${message.content}",
        color = P.Accent,
        fontSize = 12.sp,
        modifier = modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(P.AccentSoft)
            .border(1.dp, Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
