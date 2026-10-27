package app.localmind.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
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
import app.localmind.assistant.ui.chat.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Sync drawer state with view model if needed
    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen && drawerState.isClosed) {
            drawerState.open()
        } else if (!uiState.isDrawerOpen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen != uiState.isDrawerOpen) {
            viewModel.setDrawerOpen(drawerState.isOpen)
        }
    }

    // Handle error message snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(message = error)
            viewModel.clearError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF090A0D),
                modifier = Modifier.width(300.dp)
            ) {
                ConversationDrawerContent(
                    conversations = uiState.conversations,
                    archivedConversations = uiState.archivedConversations,
                    activeConversationId = uiState.activeConversationId,
                    onSelectConversation = { id ->
                        viewModel.selectConversation(id)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNewConversation = {
                        viewModel.createNewConversation()
                        coroutineScope.launch { drawerState.close() }
                    },
                    onRenameConversation = { conv -> viewModel.requestRenameConversation(conv) },
                    onArchiveConversation = { conv -> viewModel.archiveConversation(conv) },
                    onDeleteConversation = { conv -> viewModel.requestDeleteConversation(conv) },
                    onClearAll = { viewModel.requestClearAll() }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = uiState.activeConversation?.title ?: "SlothLlama",
                                color = Color(0xFFF4F4F6),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Model badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (uiState.modelStatus) {
                                                ModelRuntimeStatus.GENERATING -> Color(0xFF38BDF8)
                                                ModelRuntimeStatus.READY -> Color(0xFF34D399)
                                                ModelRuntimeStatus.NO_MODEL_LOADED -> Color(0xFFFBBF24)
                                                ModelRuntimeStatus.ERROR -> Color(0xFFF87171)
                                            }
                                        )
                                )
                                Text(
                                    text = if (uiState.isGenerating) "Generating..." else uiState.activeModelName,
                                    color = Color(0xFF8E929F),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open conversation history",
                                tint = Color(0xFFF4F4F6)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.createNewConversation() }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Conversation",
                                tint = Color(0xFF38BDF8)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF090A0D)
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = modifier
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Black)
            ) {
                // Message list occupying upper area
                Box(modifier = Modifier.weight(1f)) {
                    MessageList(
                        messages = uiState.messages,
                        isGenerating = uiState.isGenerating,
                        onEditUserMessage = { msg -> viewModel.startEditingMessage(msg) },
                        onRegenerate = { viewModel.regenerateLastResponse() },
                        onRetry = { viewModel.regenerateLastResponse() },
                        onStarterPromptSelected = { prompt ->
                            viewModel.onInputTextChanged(prompt)
                            viewModel.sendMessage()
                        }
                    )
                }

                // Chat Input bar docked at bottom
                ChatInputBar(
                    inputText = uiState.inputText,
                    onInputTextChanged = { text -> viewModel.onInputTextChanged(text) },
                    onSendMessage = { viewModel.sendMessage() },
                    onStopGeneration = { viewModel.stopGeneration() },
                    isGenerating = uiState.isGenerating,
                    editingMessage = uiState.editingMessage,
                    onCancelEdit = { viewModel.cancelEditingMessage() },
                    onSubmitEdit = { viewModel.submitEditedMessage() }
                )
            }
        }
    }

    // Dialogs
    uiState.renameTarget?.let { conv ->
        RenameConversationDialog(
            conversation = conv,
            onConfirm = { newTitle -> viewModel.confirmRenameConversation(newTitle) },
            onDismiss = { viewModel.dismissRenameDialog() }
        )
    }

    uiState.deleteTarget?.let { conv ->
        DeleteConversationDialog(
            conversation = conv,
            onConfirm = { viewModel.confirmDeleteConversation() },
            onDismiss = { viewModel.dismissDeleteDialog() }
        )
    }

    if (uiState.isClearAllConfirmOpen) {
        ClearAllConversationsDialog(
            onConfirm = { viewModel.confirmClearAll() },
            onDismiss = { viewModel.dismissClearAllDialog() }
        )
    }
}
