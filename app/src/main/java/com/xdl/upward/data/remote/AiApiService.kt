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
}
