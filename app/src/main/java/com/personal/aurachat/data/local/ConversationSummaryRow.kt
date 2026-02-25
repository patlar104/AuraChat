package com.personal.aurachat.data.local

data class ConversationSummaryRow(
    val id: Long,
    val title: String,
    val latestMessagePreview: String,
    val updatedAtEpochMs: Long
)
