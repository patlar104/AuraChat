package com.personal.aurachat.data.remote

import com.personal.aurachat.domain.model.AiErrorType
import com.personal.aurachat.domain.model.AiReply
import com.personal.aurachat.domain.model.AiRequest
import com.personal.aurachat.domain.model.AiResult
import com.personal.aurachat.domain.model.AiRole
import com.personal.aurachat.domain.model.AiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class GeminiAiService(
    private val httpClient: OkHttpClient,
    private val apiKeyProvider: suspend () -> String?
) : AiService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generateReply(request: AiRequest): AiResult<AiReply> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()?.trim().orEmpty()
        if (apiKey.isBlank()) {
            return@withContext AiResult.Error(
                type = AiErrorType.UNAUTHORIZED,
                message = "Missing API key. Add it in Settings."
            )
        }

        if (request.messages.isEmpty()) {
            return@withContext AiResult.Error(
                type = AiErrorType.EMPTY_RESPONSE,
                message = "No conversation context to send."
            )
        }

        val requestBody = buildRequestBody(request)
            .toRequestBody("application/json".toMediaType())

        val requestUrl =
            "https://generativelanguage.googleapis.com/v1beta/models/${request.model}:generateContent?key=$apiKey"

        val callClient = httpClient.newBuilder()
            .callTimeout(request.timeoutMillis, TimeUnit.MILLISECONDS)
            .build()

        val httpRequest = Request.Builder()
            .url(requestUrl)
            .post(requestBody)
            .build()

        try {
            callClient.newCall(httpRequest).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    return@withContext AiResult.Error(
                        AiErrorType.UNAUTHORIZED,
                        "API key is invalid or unauthorized."
                    )
                }

                if (!response.isSuccessful) {
                    return@withContext AiResult.Error(
                        AiErrorType.NETWORK,
                        "AI request failed with HTTP ${response.code}."
                    )
                }

                val bodyText = response.body?.string().orEmpty()
                if (bodyText.isBlank()) {
                    return@withContext AiResult.Error(
                        AiErrorType.EMPTY_RESPONSE,
                        "AI returned an empty response body."
                    )
                }

                val parsed = try {
                    json.parseToJsonElement(bodyText).jsonObject
                } catch (_: SerializationException) {
                    return@withContext AiResult.Error(
                        AiErrorType.MALFORMED_RESPONSE,
                        "Unable to parse AI response."
                    )
                } catch (_: IllegalStateException) {
                    return@withContext AiResult.Error(
                        AiErrorType.MALFORMED_RESPONSE,
                        "AI response format is unexpected."
                    )
                }

                val text = extractText(parsed)
                if (text.isNullOrBlank()) {
                    return@withContext AiResult.Error(
                        AiErrorType.EMPTY_RESPONSE,
                        "AI did not return any text content."
                    )
                }

                AiResult.Success(AiReply(text = text.trim()))
            }
        } catch (_: SocketTimeoutException) {
            AiResult.Error(AiErrorType.TIMEOUT, "Request timed out. Try again.")
        } catch (_: UnknownHostException) {
            AiResult.Error(AiErrorType.NETWORK, "Unable to reach AI service.")
        } catch (_: IOException) {
            AiResult.Error(AiErrorType.NETWORK, "Network request failed.")
        } catch (_: Exception) {
            AiResult.Error(AiErrorType.UNKNOWN, "Unexpected AI service error.")
        }
    }

    private fun buildRequestBody(request: AiRequest): String {
        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                request.messages.forEach { message ->
                    add(
                        buildJsonObject {
                            put("role", JsonPrimitive(if (message.role == AiRole.USER) "user" else "model"))
                            put(
                                "parts",
                                JsonArray(
                                    listOf(
                                        buildJsonObject {
                                            put("text", JsonPrimitive(message.text))
                                        }
                                    )
                                )
                            )
                        }
                    )
                }
            })
        }
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    private fun extractText(response: JsonObject): String? {
        val candidates = response["candidates"]?.jsonArray ?: return null
        return candidates.firstNotNullOfOrNull { candidate ->
            val parts = candidate
                .jsonObject["content"]
                ?.jsonObject
                ?.get("parts")
                ?.jsonArray
                ?: return@firstNotNullOfOrNull null

            val text = parts.joinToString(separator = "") { part ->
                part.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }.trim()

            text.takeIf { it.isNotBlank() }
        }
    }
}
