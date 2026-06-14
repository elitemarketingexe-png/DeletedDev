package com.unshoo.pixelmusic.data.ai.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Gemini AI provider implementation using the public REST API.
 *
 * Do not use the deprecated `com.google.ai.client.generativeai` Android SDK here. That SDK
 * pins Ktor 2.x while the app uses Ktor 3.x for its streaming stack, which caused release
 * crashes such as `NoClassDefFoundError: io.ktor.client.plugins.HttpTimeout`.
 */
class GeminiAiClient(private val apiKey: String) : AiClient {

    companion object {
        private const val DEFAULT_GEMINI_MODEL = "gemini-3-flash-preview"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }

    override suspend fun generateContent(
        model: String,
        systemPrompt: String,
        prompt: String,
        temperature: Float
    ): String = withContext(Dispatchers.IO) {
        val resolvedModel = normalizeModelName(model)
        try {
            val body = JSONObject().apply {
                if (systemPrompt.isNotBlank()) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    })
                }
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", temperature.toDouble())
                    put("topK", 64)
                    put("topP", 0.95)
                })
            }

            val response = postJson(
                endpoint = "models/${urlEncodePathSegment(resolvedModel)}:generateContent",
                apiKey = apiKey,
                jsonBody = body.toString()
            )

            parseGeneratedText(response).ifBlank {
                throw AiProviderSupport.createException(
                    providerName = "Gemini",
                    statusCode = null,
                    transportMessage = "Gemini returned an empty response. The model may have filtered the content.",
                    responseBody = response,
                    requestedModel = resolvedModel
                )
            }
        } catch (e: Exception) {
            throw AiProviderSupport.wrapThrowable("Gemini", e, resolvedModel)
        }
    }

    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int =
        withContext(Dispatchers.IO) {
            val resolvedModel = normalizeModelName(model)
            try {
                val combinedPrompt = if (systemPrompt.isBlank()) prompt else "$systemPrompt\n\n$prompt"
                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", combinedPrompt)))
                    }))
                }
                val response = postJson(
                    endpoint = "models/${urlEncodePathSegment(resolvedModel)}:countTokens",
                    apiKey = apiKey,
                    jsonBody = body.toString()
                )
                JSONObject(response).optInt("totalTokens", estimateTokens(systemPrompt, prompt))
            } catch (_: Exception) {
                estimateTokens(systemPrompt, prompt)
            }
        }

    override suspend fun getAvailableModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = getText("$BASE_URL/models?key=${urlEncodeQuery(apiKey)}")
            parseModelsFromResponse(response)
        } catch (_: Exception) {
            getDefaultModels()
        }
    }

    override suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = getText("$BASE_URL/models?key=${urlEncodeQuery(apiKey)}")
            response.contains("models/", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    override fun getDefaultModel(): String = DEFAULT_GEMINI_MODEL

    private fun postJson(endpoint: String, apiKey: String, jsonBody: String): String {
        val url = "$BASE_URL/$endpoint?key=${urlEncodeQuery(apiKey)}"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        connection.outputStream.use { output ->
            output.write(jsonBody.toByteArray(Charsets.UTF_8))
        }

        return readResponse(connection, requestedModel = endpoint.substringAfter("models/").substringBefore(':'))
    }

    private fun getText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
        return readResponse(connection, requestedModel = null)
    }

    private fun readResponse(connection: HttpURLConnection, requestedModel: String?): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw AiProviderSupport.createException(
                providerName = "Gemini",
                statusCode = code,
                transportMessage = connection.responseMessage,
                responseBody = body,
                requestedModel = requestedModel
            )
        }
        return body
    }

    private fun parseGeneratedText(jsonResponse: String): String {
        val root = JSONObject(jsonResponse)
        val candidates = root.optJSONArray("candidates") ?: return ""
        val builder = StringBuilder()
        for (i in 0 until candidates.length()) {
            val content = candidates.optJSONObject(i)
                ?.optJSONObject("content")
                ?: continue
            val parts = content.optJSONArray("parts") ?: continue
            for (j in 0 until parts.length()) {
                val text = parts.optJSONObject(j)?.optString("text").orEmpty()
                if (text.isNotBlank()) {
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(text)
                }
            }
        }
        return builder.toString().trim()
    }

    private fun parseModelsFromResponse(jsonResponse: String): List<String> {
        return runCatching {
            val models = mutableListOf<String>()
            val array = JSONObject(jsonResponse).optJSONArray("models") ?: JSONArray()
            for (i in 0 until array.length()) {
                val name = array.optJSONObject(i)
                    ?.optString("name")
                    ?.removePrefix("models/")
                    .orEmpty()
                if (name.startsWith("gemini", ignoreCase = true) &&
                    !name.contains("embedding", ignoreCase = true)
                ) {
                    models.add(name)
                }
            }
            models.distinct().ifEmpty { getDefaultModels() }
        }.getOrDefault(getDefaultModels())
    }

    private fun estimateTokens(systemPrompt: String, prompt: String): Int =
        ((prompt.length + systemPrompt.length) / 4).coerceAtLeast(1)

    private fun urlEncodeQuery(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun urlEncodePathSegment(value: String): String =
        value.trim().replace("/", "%2F")

    private fun normalizeModelName(model: String): String =
        model.trim().removePrefix("models/").ifBlank { DEFAULT_GEMINI_MODEL }

    private fun getDefaultModels(): List<String> = listOf(
        "gemini-3-flash-preview",
        "gemini-3.1-pro-preview",
        "gemini-3.1-flash-lite-preview",
        "gemini-flash-latest",
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemini-2.0-flash"
    )
}
