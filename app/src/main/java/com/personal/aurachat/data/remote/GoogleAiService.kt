package com.personal.aurachat.data.remote

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.InvalidAPIKeyException
import com.google.ai.client.generativeai.type.PromptBlockedException
import com.google.ai.client.generativeai.type.QuotaExceededException
import com.google.ai.client.generativeai.type.RequestTimeoutException
import com.google.ai.client.generativeai.type.ServerException
import com.google.ai.client.generativeai.type.UnsupportedUserLocationException
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
    private var cachedTimeout: Long? = null

    private suspend fun getModel(modelName: String, timeoutMillis: Long): GenerativeModel {
        val apiKey = apiKeyProvider()?.trim().orEmpty()
        if (apiKey.isBlank()) throw IllegalArgumentException("API Key is missing")

        val currentModel = cachedModel
        if (currentModel != null && cachedApiKey == apiKey && cachedModelName == modelName && cachedTimeout == timeoutMillis) {
            Log.d(TAG, "Using cached model: $modelName")
            return currentModel
        }

        Log.i(TAG, "Initializing model: $modelName (timeout=${timeoutMillis}ms)")
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
        cachedTimeout = timeoutMillis
        return newModel
    }

    override suspend fun generateReply(request: AiRequest): AiResult<AiReply> {
        Log.d(TAG, "generateReply: model=${request.model} messages=${request.messages.size}")
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
                Log.w(TAG, "generateReply: empty response from model")
                AiResult.Error(AiErrorType.EMPTY_RESPONSE, "AI returned no text.")
            } else {
                Log.d(TAG, "generateReply: success")
                AiResult.Success(AiReply(text))
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "generateReply: unauthorized - ${e.message}")
            AiResult.Error(AiErrorType.UNAUTHORIZED, e.message)
        } catch (e: Exception) {
            Log.e(TAG, "generateReply: error ${e::class.simpleName}: ${e.message}", e)
            mapError(e)
        }
    }

    override fun streamReply(request: AiRequest): Flow<AiResult<String>> = flow {
        Log.d(TAG, "streamReply: start model=${request.model} messages=${request.messages.size}")
        val model = try {
            getModel(request.model, request.timeoutMillis)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "streamReply: unauthorized - ${e.message}")
            emit(AiResult.Error(AiErrorType.UNAUTHORIZED, e.message))
            return@flow
        }

        val prompt = content {
            request.messages.forEach { msg ->
                role = if (msg.role == AiRole.USER) "user" else "model"
                text(msg.text)
            }
        }

        var chunkCount = 0
        model.generateContentStream(prompt).collect { response ->
            val text = response.text
            if (!text.isNullOrBlank()) {
                chunkCount++
                emit(AiResult.Success(text))
            }
        }
        Log.d(TAG, "streamReply: complete chunks=$chunkCount")
    }.catch { e ->
        if (e is Exception) {
            Log.e(TAG, "streamReply: error ${e::class.simpleName}: ${e.message}", e)
            emit(mapError(e))
        } else {
            Log.e(TAG, "streamReply: fatal error - ${e::class.simpleName}")
            emit(AiResult.Error(AiErrorType.UNKNOWN, e.message ?: "Unknown error"))
        }
    }

    private fun mapError(e: Exception): AiResult.Error {
        return when (e) {
            is InvalidAPIKeyException ->
                AiResult.Error(AiErrorType.UNAUTHORIZED, e.message ?: "Invalid API key.")
            is RequestTimeoutException ->
                AiResult.Error(AiErrorType.TIMEOUT, e.message ?: "Request timed out.")
            is QuotaExceededException ->
                AiResult.Error(AiErrorType.NETWORK, e.message ?: "Quota exceeded.")
            is UnsupportedUserLocationException ->
                AiResult.Error(AiErrorType.NETWORK, e.message ?: "User location is not supported.")
            is PromptBlockedException ->
                AiResult.Error(AiErrorType.EMPTY_RESPONSE, e.message ?: "Prompt was blocked.")
            is com.google.ai.client.generativeai.type.ResponseStoppedException ->
                AiResult.Error(AiErrorType.EMPTY_RESPONSE, "Response stopped unexpectedly.")
            is com.google.ai.client.generativeai.type.SerializationException ->
                AiResult.Error(AiErrorType.MALFORMED_RESPONSE, "Error parsing AI response.")
            is ServerException ->
                AiResult.Error(AiErrorType.NETWORK, e.message ?: "AI server error.")
            else -> AiResult.Error(AiErrorType.UNKNOWN, e.message ?: "An unexpected error occurred.")
        }
    }

    companion object {
        private const val TAG = "GoogleAiService"
    }
}
