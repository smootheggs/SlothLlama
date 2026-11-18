package app.localmind.assistant.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.R
import app.localmind.assistant.domain.model.ChatMessage
import app.localmind.assistant.domain.model.MessageRole
import kotlinx.coroutines.launch
import app.localmind.assistant.ui.theme.SlothPalette as P

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    onEditUserMessage: (ChatMessage) -> Unit,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit,
    onStarterPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyBadge: String = "✦",
    emptyTitle: String = "How can I help?",
    emptySubtitle: String = "Private · on-device · offline",
    starterPrompts: List<String> = DefaultStarterPrompts,
    showMascot: Boolean = false,
    /** Shown instead of the default placeholder when there are no messages (the start page). */
    emptyContent: (@Composable () -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            total == 0 || (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= total - 2
        }
    }

    // Follow the reply as it streams in, unless the user scrolled up to read.
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty() && isAtBottom) listState.scrollToItem(messages.size - 1, Int.MAX_VALUE / 2)
    }

    // As the keyboard slides up, the list gets shorter; keep the newest message in view
    // (the same thing Claude and Messages do when you tap into the composer).
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && messages.isNotEmpty()) listState.scrollToItem(messages.size - 1, Int.MAX_VALUE / 2)
    }

    Box(modifier.fillMaxSize()) {
        if (messages.isEmpty() && emptyContent != null) {
            emptyContent()
        } else if (messages.isEmpty()) {
            EmptyConversationPlaceholder(
                onPromptSelected = onStarterPromptSelected,
                badge = emptyBadge,
                title = emptyTitle,
                subtitle = emptySubtitle,
                starterPrompts = starterPrompts,
                showMascot = showMascot,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(messages, key = { _, msg -> msg.id }) { index, message ->
                    val isLatestAssistant = index == messages.indexOfLast { it.role == MessageRole.ASSISTANT }
                    MessageBubble(
                        message = message,
                        isLatestAssistantMessage = isLatestAssistant,
                        onEditUserMessage = onEditUserMessage,
                        onRegenerate = onRegenerate,
                        onRetry = onRetry,
                        modifier = Modifier
                            .widthIn(max = ChatColumnMaxWidth)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !isAtBottom && messages.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(P.Card)
                    .border(1.dp, P.Border, CircleShape)
                    .clickable { coroutineScope.launch { listState.animateScrollToItem(messages.size - 1, Int.MAX_VALUE / 2) } },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Scroll to latest", tint = P.Text, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun EmptyConversationPlaceholder(
    onPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    badge: String = "✦",
    title: String = "How can I help?",
    subtitle: String = "Private · on-device · offline",
    starterPrompts: List<String> = DefaultStarterPrompts,
    showMascot: Boolean = false
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val twoColumns = maxWidth >= 600.dp
        Column(
            Modifier
                .widthIn(max = ChatColumnMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showMascot) {
                Image(
                    painter = painterResource(R.drawable.sloth_llama),
                    contentDescription = null,
                    modifier = Modifier.size(width = 80.dp, height = 70.dp)
                )
            } else {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(P.AccentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(badge, color = P.Accent, fontSize = 24.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(title, color = P.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = P.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))

            val rows = if (twoColumns) starterPrompts.chunked(2) else starterPrompts.map { listOf(it) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { prompt ->
                            StarterCard(prompt, onPromptSelected, Modifier.weight(1f))
                        }
                        if (row.size == 1 && twoColumns) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StarterCard(prompt: String, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = prompt,
        color = P.Text,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        maxLines = 2,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, P.Border, RoundedCornerShape(14.dp))
            .clickable { onClick(prompt) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

val DefaultStarterPrompts = listOf(
    "Explain quantum computing in simple terms",
    "Write a Python function to parse JSON with error handling",
    "Draft a polite email asking for project feedback",
    "Summarize the pros and cons of electric cars"
)
