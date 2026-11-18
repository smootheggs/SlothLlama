package app.localmind.assistant.ui.chat.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.domain.attachments.DocumentText
import app.localmind.assistant.ui.chat.PendingAttachments
import app.localmind.assistant.ui.theme.SlothPalette as P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Finds a stored photo by name (provided by the chat screen). */
val LocalPhotoFile = compositionLocalOf<(String) -> File?> { { null } }

/** A stored photo, decoded small enough for [maxSide]. Shows a placeholder if it's gone. */
@Composable
fun PhotoThumbnail(name: String, maxSide: Dp, modifier: Modifier = Modifier) {
    val findFile = LocalPhotoFile.current
    val pixels = with(androidx.compose.ui.platform.LocalDensity.current) { maxSide.roundToPx() }
    val image by produceState<ImageBitmap?>(initialValue = null, name, pixels) {
        value = withContext(Dispatchers.IO) {
            val file = findFile(name) ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= pixels) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(14.dp)
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Photo",
            contentScale = ContentScale.Fit,
            modifier = modifier.sizeIn(maxWidth = maxSide, maxHeight = maxSide).clip(shape)
        )
    } else {
        Box(
            modifier.size(72.dp).clip(shape).background(P.Elevated).border(1.dp, P.Border, shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.ImageNotSupported, contentDescription = "Photo not available", tint = P.Faint)
        }
    }
}

/** A document as a compact chip: name, size, and an optional remove button. */
@Composable
fun DocumentChip(doc: DocumentText, onRemove: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .widthIn(max = 360.dp)
            .clip(shape)
            .background(P.Elevated)
            .border(1.dp, P.Border, shape)
            .padding(start = 10.dp, end = if (onRemove != null) 4.dp else 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Description, contentDescription = null, tint = P.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(doc.name, color = P.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = buildList {
                if (doc.pages > 0) add("${doc.pages} page${if (doc.pages == 1) "" else "s"}")
                add("%,d characters".format(doc.text.length))
                if (doc.truncated) add("first part only")
            }.joinToString(" · ")
            Text(detail, color = P.Faint, fontSize = 11.sp, maxLines = 1)
        }
        if (onRemove != null) {
            Spacer(Modifier.width(4.dp))
            RemoveButton("Remove ${doc.name}", onRemove)
        }
    }
}

/** What's attached to the message being written, above the text box. */
@Composable
fun PendingAttachmentRow(
    attachments: PendingAttachments,
    onRemovePhoto: () -> Unit,
    onRemoveDocument: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        attachments.photo?.let { name ->
            Box {
                PhotoThumbnail(name, maxSide = 72.dp)
                Box(Modifier.align(Alignment.TopEnd).padding(2.dp)) { RemoveButton("Remove photo", onRemovePhoto, onImage = true) }
            }
        }
        attachments.document?.let { DocumentChip(it, onRemove = onRemoveDocument) }
        if (attachments.busy) {
            CircularProgressIndicator(color = P.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            Text("Reading…", color = P.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun RemoveButton(description: String, onClick: () -> Unit, onImage: Boolean = false) {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (onImage) P.Canvas.copy(alpha = 0.8f) else P.Elevated)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Close, contentDescription = description, tint = P.Muted, modifier = Modifier.size(14.dp))
    }
}
