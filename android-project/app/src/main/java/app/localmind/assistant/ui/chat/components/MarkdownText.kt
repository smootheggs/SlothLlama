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
import androidx.compose.ui.unit.TextUnit
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlinx.coroutines.delay

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    textColor: Color = P.Text
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }
    val codeBackground = P.Elevated
    val codeColor = P.Text
    val hanScale = LocalChineseScale.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildAnnotatedMarkdown(block.text, codeBackground, codeColor).scaleHan(hanScale),
                        color = textColor,
                        fontSize = 16.sp,
                        lineHeight = lineHeightFor(block.text, hanScale)
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
                        Text("•", color = P.Muted, fontSize = 16.sp, lineHeight = 24.sp)
                        Text(
                            text = buildAnnotatedMarkdown(block.text, codeBackground, codeColor).scaleHan(hanScale),
                            color = textColor,
                            fontSize = 16.sp,
                            lineHeight = lineHeightFor(block.text, hanScale)
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
            .clip(RoundedCornerShape(12.dp))
            .background(P.CodeBackground)
            .border(1.dp, P.Border, RoundedCornerShape(12.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" }.lowercase(),
                color = P.Faint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
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
                    tint = if (isCopied) P.Success else P.Faint,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Code content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp, top = 4.dp)
        ) {
            Text(
                text = code,
                color = P.CodeText,
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

/** Size of Chinese characters relative to the text around them (Settings → Chinese). */
val LocalChineseScale = compositionLocalOf { 1f }

private val HAN_RUN = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF\\u3000-\\u303F\\uFF00-\\uFFEF]+")
private val HAN_CHAR = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")

/** Enlarges runs of Chinese characters (and their punctuation) so strokes and tones are easy to read. */
fun AnnotatedString.scaleHan(scale: Float, base: TextUnit = 16.sp): AnnotatedString {
    if (scale == 1f || !HAN_CHAR.containsMatchIn(text)) return this
    val builder = AnnotatedString.Builder(this)
    HAN_RUN.findAll(text).forEach { run ->
        builder.addStyle(SpanStyle(fontSize = base * scale), run.range.first, run.range.last + 1)
    }
    return builder.toAnnotatedString()
}

/** Taller lines for paragraphs with enlarged Chinese, so lines never overlap. */
private fun lineHeightFor(text: String, scale: Float): TextUnit =
    if (scale > 1f && HAN_CHAR.containsMatchIn(text)) (24 * scale).sp else 24.sp

fun buildAnnotatedMarkdown(
    text: String,
    codeBackground: Color = Color(0x1F8E929F),
    codeColor: Color = Color.Unspecified
): AnnotatedString {
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
                            background = codeBackground,
                            color = codeColor
                        )
                    ) {
                        append("\u2009${match.groups[6]?.value ?: ""}\u2009")
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
