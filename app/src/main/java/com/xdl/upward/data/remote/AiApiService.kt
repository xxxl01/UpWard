package com.xdl.upward.data.remote

import com.xdl.upward.data.local.AiApiEntity
import com.xdl.upward.domain.AiChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AiApiService {
    suspend fun chat(api: AiApiEntity, messages: List<AiChatMessage>): String {
        return withContext(Dispatchers.IO) {
            val requestJson = JSONObject()
            requestJson.put("model", api.model)
            requestJson.put("temperature", api.temperature)

            val messageArray = JSONArray()
            messages.forEach { message ->
                val item = JSONObject()
                item.put("role", message.role)
                item.put("content", message.content)
                messageArray.put(item)
            }
            requestJson.put("messages", messageArray)

            val baseUrl = api.baseUrl.trim().trimEnd('/')
            val requestBuilder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

            if (api.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${api.apiKey}")
            }

            val request = requestBuilder.build()

            AiHttpClient.client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("AI 请求失败：${response.code} $bodyText")
                }

                val bodyJson = JSONObject(bodyText)
                bodyJson
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }
        }
    }

    suspend fun streamChat(
        api: AiApiEntity,
        messages: List<AiChatMessage>,
        onContentDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit
    ): String {
        return withContext(Dispatchers.IO) {
            val requestJson = JSONObject()
            requestJson.put("model", api.model)
            requestJson.put("temperature", api.temperature)
            requestJson.put("stream", true)
            if (api.baseUrl.contains("deepseek", ignoreCase = true) || api.model.contains("deepseek", ignoreCase = true)) {
                requestJson.put("reasoning_effort", "high")
                requestJson.put("thinking", JSONObject().put("type", "enabled"))
            }

            val messageArray = JSONArray()
            messages.forEach { message ->
                val item = JSONObject()
                item.put("role", message.role)
                item.put("content", message.content)
                messageArray.put(item)
            }
            requestJson.put("messages", messageArray)

            val baseUrl = api.baseUrl.trim().trimEnd('/')
            val requestBuilder = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

            if (api.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${api.apiKey}")
            }

            val request = requestBuilder.build()
            val fullContent = StringBuilder()

            AiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyText = response.body?.string().orEmpty()
                    throw IllegalStateException("AI 请求失败：${response.code} $bodyText")
                }

                val source = response.body?.source()
                    ?: throw IllegalStateException("AI 请求失败：响应内容为空")

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue

                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break

                    val bodyJson = JSONObject(data)
                    val choice = bodyJson.getJSONArray("choices").getJSONObject(0)
                    if (choice.has("delta")) {
                        val delta = choice.getJSONObject("delta")

                        var deepSeekReasoningText = ""
                        if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
                            deepSeekReasoningText = delta.getString("reasoning_content")
                        }
                        if (deepSeekReasoningText.isNotEmpty()) {
                            onReasoningDelta(deepSeekReasoningText)
                            continue
                        }

                        var fallbackReasoningText = ""
                        if (delta.has("reasoning") && !delta.isNull("reasoning")) {
                            fallbackReasoningText = delta.getString("reasoning")
                        }
                        if (fallbackReasoningText.isNotEmpty()) {
                            onReasoningDelta(fallbackReasoningText)
                            continue
                        }

                        var contentText = ""
                        if (delta.has("content") && !delta.isNull("content")) {
                            contentText = delta.getString("content")
                        }
                        if (contentText.isNotEmpty()) {
                            fullContent.append(contentText)
                            onContentDelta(contentText)
                        }
                    } else if (choice.has("message")) {
                        val message = choice.getJSONObject("message")
                        var reasoningText = ""
                        if (message.has("reasoning_content") && !message.isNull("reasoning_content")) {
                            reasoningText = message.getString("reasoning_content")
                        }
                        if (reasoningText.isNotEmpty()) {
                            onReasoningDelta(reasoningText)
                        } else {
                            var contentText = ""
                            if (message.has("content") && !message.isNull("content")) {
                                contentText = message.getString("content")
                            }
                            if (contentText.isNotEmpty()) {
                                fullContent.append(contentText)
                                onContentDelta(contentText)
                            }
                        }
                    } else {
                        var reasoningText = ""
                        if (choice.has("reasoning_content") && !choice.isNull("reasoning_content")) {
                            reasoningText = choice.getString("reasoning_content")
                        }
                        if (reasoningText.isNotEmpty()) {
                            onReasoningDelta(reasoningText)
                        } else {
                            var fallbackReasoningText = ""
                            if (choice.has("reasoning") && !choice.isNull("reasoning")) {
                                fallbackReasoningText = choice.getString("reasoning")
                            }
                            if (fallbackReasoningText.isNotEmpty()) {
                                onReasoningDelta(fallbackReasoningText)
                            }
                        }
                    }
                }
            }

            fullContent.toString().trim()
        }
    }
}
