package com.personal.aurachat.data.remote

import com.personal.aurachat.domain.model.AiErrorType
import com.personal.aurachat.domain.model.AiMessage
import com.personal.aurachat.domain.model.AiRequest
import com.personal.aurachat.domain.model.AiResult
import com.personal.aurachat.domain.model.AiRole
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAiServiceTest {

    @Test
    fun `missing api key returns unauthorized error`() = runTest {
        val service = GeminiAiService(
            httpClient = OkHttpClient(),
            apiKeyProvider = { null }
        )

        val result = service.generateReply(
            AiRequest(
                messages = listOf(AiMessage(AiRole.USER, "Hello")),
                model = "gemini-flash-latest",
                timeoutMillis = 30_000L
            )
        )

        assertTrue(result is AiResult.Error)
        assertEquals(AiErrorType.UNAUTHORIZED, (result as AiResult.Error).type)
    }
}
