package app.localmind.assistant.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.localmind.assistant.ui.theme.SlothPalette as P
import app.localmind.assistant.domain.model.Conversation

@Composable
fun RenameConversationDialog(
    conversation: Conversation,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(conversation.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Card,
        title = {
            Text(
                text = "Rename conversation",
                color = P.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = P.Accent,
                    unfocusedBorderColor = P.BorderStrong,
                    focusedTextColor = P.Text,
                    unfocusedTextColor = P.Text
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = P.Accent,
                    contentColor = P.OnAccent
                )
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = P.Muted)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteConversationDialog(
    conversation: Conversation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Card,
        title = {
            Text(
                text = "Delete conversation?",
                color = P.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"${conversation.title}\"? This action cannot be undone.",
                color = P.Muted,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = P.Danger,
                    contentColor = Color.White
                )
            ) {
                Text("Delete", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = P.Muted)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ClearAllConversationsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.Card,
        title = {
            Text(
                text = "Delete all conversations?",
                color = P.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text(
                text = "This will permanently delete all conversations and message history from your device. Model weights and personas will not be affected.",
                color = P.Muted,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = P.Danger,
                    contentColor = Color.White
                )
            ) {
                Text("Delete All", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = P.Muted)
            ) {
                Text("Cancel")
            }
        }
    )
}
