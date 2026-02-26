package com.personal.aurachat.data.remote

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.content
import com.personal.aurachat.domain.model.AiErrorType
import com.personal.aurachat.domain.model.AiReply
import com.personal.aurachat.domain.model.AiRequest
import com.personal.aurachat.domain.model.AiResult
import com.personal.aurachat.domain.model.AiRole
import com.personal.aurachat.domain.model.AiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds

class GoogleAiService(
    private val apiKeyProvider: suspend () -> String?
) : AiService {

    private var cachedModel: GenerativeModel? = null
    private var cachedApiKey: String? = null
    private var cachedModelName: String? = null

    private suspend fun getModel(modelName: String, timeoutMillis: Long): GenerativeModel {
        val apiKey = apiKeyProvider()?.trim().orEmpty()
        if (apiKey.isBlank()) throw IllegalArgumentException("API Key is missing")

        val currentModel = cachedModel
        if (currentModel != null && cachedApiKey == apiKey && cachedModelName == modelName) {
            return currentModel
        }

        val newModel = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            requestOptions = RequestOptions(timeout = timeoutMillis.milliseconds),
            safetySettings = listOf(
                SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.MEDIUM_AND_ABOVE),
                SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.MEDIUM_AND_ABOVE),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE)
            ),
            systemInstruction = content { text("You are Aura, a helpful and friendly AI assistant.") }
        )
        cachedModel = newModel
        cachedApiKey = apiKey
        cachedModelName = modelName
        return newModel
    }

    override suspend fun generateReply(request: AiRequest): AiResult<AiReply> {
        return try {
            val model = getModel(request.model, request.timeoutMillis)
            val prompt = content {
                request.messages.forEach { msg ->
                    role = if (msg.role == AiRole.USER) "user" else "model"
                    text(msg.text)
                }
            }
            val response = model.generateContent(prompt)
            val text = response.text
            if (text.isNullOrBlank()) {
                AiResult.Error(AiErrorType.EMPTY_RESPONSE, "AI returned no text.")
            } else {
                AiResult.Success(AiReply(text))
            }
        } catch (e: IllegalArgumentException) {
            AiResult.Error(AiErrorType.UNAUTHORIZED, e.message)
        } catch (e: Exception) {
            mapError(e)
        }
    }

    override fun streamReply(request: AiRequest): Flow<AiResult<String>> = flow {
        val model = try {
            getModel(request.model, request.timeoutMillis)
        } catch (e: IllegalArgumentException) {
            emit(AiResult.Error(AiErrorType.UNAUTHORIZED, e.message))
            return@flow
        }

        val prompt = content {
            request.messages.forEach { msg ->
                role = if (msg.role == AiRole.USER) "user" else "model"
                text(msg.text)
            }
        }

        model.generateContentStream(prompt).collect { response ->
            val text = response.text
            if (!text.isNullOrBlank()) {
                emit(AiResult.Success(text))
            }
        }
    }.catch { e ->
        if (e is Exception) {
            emit(mapError(e))
        } else {
            emit(AiResult.Error(AiErrorType.UNKNOWN, e.message ?: "Unknown error"))
        }
    }

    private fun mapError(e: Exception): AiResult.Error {
        return when (e) {
            is com.google.ai.client.generativeai.type.ResponseStoppedException ->
                AiResult.Error(AiErrorType.EMPTY_RESPONSE, "Response stopped unexpectedly.")
            is com.google.ai.client.generativeai.type.SerializationException ->
                AiResult.Error(AiErrorType.MALFORMED_RESPONSE, "Error parsing AI response.")
            is com.google.ai.client.generativeai.type.ServerException ->
                AiResult.Error(AiErrorType.NETWORK, "AI server error.")
            else -> AiResult.Error(AiErrorType.UNKNOWN, e.message ?: "An unexpected error occurred.")
        }
    }
}
