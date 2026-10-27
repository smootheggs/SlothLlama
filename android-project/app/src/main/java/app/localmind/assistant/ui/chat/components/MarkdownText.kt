package app.localmind.assistant.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color(0xFFF4F4F6)
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildAnnotatedMarkdown(block.text),
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
                is MarkdownBlock.CodeFence -> {
                    FencedCodeBlock(
                        language = block.language,
                        code = block.code
                    )
                }
                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("•", color = Color(0xFF38BDF8), fontSize = 15.sp)
                        Text(
                            text = buildAnnotatedMarkdown(block.text),
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FencedCodeBlock(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF070809))
            .border(1.dp, Color(0xFF1E222A), RoundedCornerShape(8.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF11141A))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" }.lowercase(),
                color = Color(0xFF8E929F),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Code", code))
                    isCopied = true
                    Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Done else Icons.Default.ContentCopy,
                    contentDescription = "Copy Code",
                    tint = if (isCopied) Color(0xFF34D399) else Color(0xFF8E929F),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Code content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code,
                color = Color(0xFFE2E8F0),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class CodeFence(val language: String, val code: String) : MarkdownBlock
    data class ListItem(val text: String) : MarkdownBlock
}

fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var inCodeFence = false
    var currentLang = ""
    val codeAccumulator = StringBuilder()
    val paragraphAccumulator = StringBuilder()

    fun flushParagraph() {
        val p = paragraphAccumulator.toString().trim()
        if (p.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(p))
            paragraphAccumulator.clear()
        }
    }

    for (line in lines) {
        if (line.trimStart().startsWith("```")) {
            if (inCodeFence) {
                // End code fence
                blocks.add(MarkdownBlock.CodeFence(currentLang, codeAccumulator.toString().trimEnd()))
                codeAccumulator.clear()
                currentLang = ""
                inCodeFence = false
            } else {
                // Start code fence
                flushParagraph()
                currentLang = line.trim().removePrefix("```").trim()
                inCodeFence = true
            }
            continue
        }

        if (inCodeFence) {
            codeAccumulator.appendLine(line)
        } else {
            val trimmed = line.trim()
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                flushParagraph()
                blocks.add(MarkdownBlock.ListItem(trimmed.substring(2).trim()))
            } else if (trimmed.isEmpty()) {
                flushParagraph()
            } else {
                if (paragraphAccumulator.isNotEmpty()) {
                    paragraphAccumulator.append(" ")
                }
                paragraphAccumulator.append(trimmed)
            }
        }
    }

    if (inCodeFence) {
        blocks.add(MarkdownBlock.CodeFence(currentLang, codeAccumulator.toString().trimEnd()))
    } else {
        flushParagraph()
    }

    return blocks
}

fun buildAnnotatedMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(\\*\\*(.*?)\\*\\*)|(\\*([^*]+)\\*)|(`([^`]+)`)")
        val matches = regex.findAll(text)

        for (match in matches) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            when {
                // Bold: **text**
                match.groups[1] != null -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(match.groups[2]?.value ?: "")
                    }
                }
                // Italic: *text*
                match.groups[3] != null -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(match.groups[4]?.value ?: "")
                    }
                }
                // Inline code: `code`
                match.groups[5] != null -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF1E222A),
                            color = Color(0xFF38BDF8)
                        )
                    ) {
                        append(" ${match.groups[6]?.value ?: ""} ")
                    }
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
