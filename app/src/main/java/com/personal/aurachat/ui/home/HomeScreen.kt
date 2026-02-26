package com.personal.aurachat.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.aurachat.domain.model.ConversationSummary
import com.personal.aurachat.presentation.home.HomeUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onNewChat: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onRenameConversation: (Long, String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedConversationId by remember { mutableLongStateOf(-1L) }
    var renameDraft by remember { mutableStateOf("") }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HomeHeader(
                    onNewChat = onNewChat,
                    onOpenSettings = onOpenSettings
                )
            }

            if (uiState.conversations.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "No conversations yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Start a new chat and your local history will appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.conversations, key = { it.id }) { conversation ->
                    ConversationCard(
                        conversation = conversation,
                        isExpanded = expandedConversationId == conversation.id,
                        renameDraft = renameDraft,
                        onOpen = { onOpenConversation(conversation.id) },
                        onExpandRename = {
                            expandedConversationId =
                                if (expandedConversationId == conversation.id) -1L else conversation.id
                            renameDraft = conversation.title
                        },
                        onRenameDraftChange = { renameDraft = it },
                        onRenameSave = {
                            onRenameConversation(conversation.id, renameDraft)
                            expandedConversationId = -1L
                        },
                        onRenameCancel = { expandedConversationId = -1L }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HomeHeader(
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(16.dp)
            .clip(RoundedCornerShape(28.dp))
    ) {
        HomeHeaderBackground(modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.15f),
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "AuraChat",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Private local history, AI online responses.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.92f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = onNewChat) {
                    Text("New Chat")
                }
            }
        }
    }
}

@Composable
private fun HomeHeaderBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF7ECED4),
                    Color(0xFF3E7A94)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 26.dp, end = 32.dp)
                .size(120.dp)
                .background(color = Color(0x55FFF5D6), shape = CircleShape)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(110.dp)
                .background(
                    color = Color(0xFF3E6D57),
                    shape = RoundedCornerShape(topStart = 120.dp, topEnd = 120.dp)
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(width = 240.dp, height = 95.dp)
                .background(
                    color = Color(0xFF2F5B47),
                    shape = RoundedCornerShape(topStart = 100.dp, topEnd = 140.dp)
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(width = 220.dp, height = 90.dp)
                .background(
                    color = Color(0xFF254A3B),
                    shape = RoundedCornerShape(topStart = 130.dp, topEnd = 90.dp)
                )
        )
    }
}

@Composable
private fun ConversationCard(
    conversation: ConversationSummary,
    isExpanded: Boolean,
    renameDraft: String,
    onOpen: () -> Unit,
    onExpandRename: () -> Unit,
    onRenameDraftChange: (String) -> Unit,
    onRenameSave: () -> Unit,
    onRenameCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = conversation.latestMessagePreview.ifBlank { "No messages yet" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = onExpandRename) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Rename conversation"
                        )
                    }
                    Text(
                        text = formatTimestamp(conversation.updatedAtEpochMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = onRenameDraftChange,
                    singleLine = true,
                    label = { Text("Conversation title") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onRenameCancel) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = onRenameSave,
                        enabled = renameDraft.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.ofEpochMilli(timestamp)
    val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}
