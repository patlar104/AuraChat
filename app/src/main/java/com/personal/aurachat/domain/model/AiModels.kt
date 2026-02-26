package com.personal.aurachat.domain.model

import kotlinx.coroutines.flow.Flow

enum class AiRole {
    USER,
    ASSISTANT
}

data class AiMessage(
    val role: AiRole,
    val text: String
)

data class AiRequest(
    val messages: List<AiMessage>,
    val model: String,
    val timeoutMillis: Long
)

data class AiReply(
    val text: String
)

sealed interface AiResult<out T> {
    data class Success<T>(val value: T) : AiResult<T>
    data class Error(val type: AiErrorType, val message: String? = null) : AiResult<Nothing>
}

enum class AiErrorType {
    OFFLINE,
    TIMEOUT,
    NETWORK,
    EMPTY_RESPONSE,
    MALFORMED_RESPONSE,
    UNAUTHORIZED,
    UNKNOWN
}

interface AiService {
    suspend fun generateReply(request: AiRequest): AiResult<AiReply>
    fun streamReply(request: AiRequest): Flow<AiResult<String>>
}
