package com.xdl.upward.data.remote

import android.util.Base64
import com.xdl.upward.data.local.AiApiEntity
import com.xdl.upward.domain.AiChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AiApiService {
    // 拼接 chat/completions 请求地址。
    // 兼容两种填法：只填域名/版本前缀（如 https://open.bigmodel.cn/api/paas/v4，自动补全），
    // 或直接填完整接口地址（如 https://open.bigmodel.cn/api/paas/v4/chat/completions，不重复拼接）。
    private fun chatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    // 用支持视觉的桥接模型描述图片，把图片转成文字描述，供无视觉 API（如 DeepSeek）使用。
    // 全部图片放在同一条 user 消息的 content 数组里一次请求（text + 多张 image_url），
    // 不带任何历史记录，只携带本次输入的文字和图片。
    suspend fun describeImages(visionApi: AiApiEntity, userText: String, imagePaths: List<String>): String {
        return withContext(Dispatchers.IO) {
            val describePrompt = """
                请客观、完整地描述这些图片的内容。请按图片顺序逐张描述每张图片，每张用「图片 N」开头，方便阅读时对应顺序。如果图片里有文字（截图、手写、海报、界面文字等），请逐字转写关键文字信息；同时描述画面中的物体、人物动作、表情、场景、布局等对理解有帮助的视觉细节。不要添加没有依据的猜测和评价。
            """.trimIndent()

            val contentArray = JSONArray()
            val userTextPart = if (userText.isNotBlank()) {
                "用户发送了 ${imagePaths.size} 张图片，并留言：$userText\n"
            } else {
                "用户发送了 ${imagePaths.size} 张图片。\n"
            }
            contentArray.put(JSONObject().put("type", "text").put("text", "$userTextPart$describePrompt"))

            imagePaths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                    contentArray.put(
                        JSONObject()
                            .put("type", "image_url")
                            .put("image_url", JSONObject().put("url", "data:image/webp;base64,$base64"))
                    )
                }
            }
            // contentArray 里只有 text 没有图片，说明所有图片文件都不存在，直接提示，不发请求
            if (contentArray.length() == 1) {
                return@withContext "所有图片文件都不存在，无法描述。"
            }

            val messageArray = JSONArray()
            messageArray.put(JSONObject().put("role", "user").put("content", contentArray))

            val requestJson = JSONObject()
            requestJson.put("model", visionApi.model)
            requestJson.put("temperature", visionApi.temperature)
            requestJson.put("messages", messageArray)

            val requestBuilder = Request.Builder()
                .url(chatCompletionsUrl(visionApi.baseUrl))
                .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

            if (visionApi.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${visionApi.apiKey}")
            }

            AiHttpClient.client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("视觉模型请求失败：${response.code} $bodyText")
                }
                val bodyJson = JSONObject(bodyText)
                val choices = bodyJson.getJSONArray("choices")
                if (choices.length() == 0) {
                    throw IllegalStateException("视觉模型响应 choices 为空")
                }
                choices
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }
        }
    }

    suspend fun chat(api: AiApiEntity, messages: List<AiChatMessage>): String {
        return withContext(Dispatchers.IO) {
            val requestJson = JSONObject()
            requestJson.put("model", api.model)
            requestJson.put("temperature", api.temperature)

            val messageArray = JSONArray()
            messages.forEach { message ->
                val item = JSONObject()
                item.put("role", message.role)
                if (message.imagePaths.isEmpty()) {
                    item.put("content", message.content)
                } else {
                    val contentArray = JSONArray()
                    if (message.content.isNotBlank()) {
                        contentArray.put(JSONObject().put("type", "text").put("text", message.content))
                    }
                    message.imagePaths.forEach { path ->
                        val file = File(path)
                        if (file.exists()) {
                            val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                            contentArray.put(
                                JSONObject()
                                    .put("type", "image_url")
                                    .put("image_url", JSONObject().put("url", "data:image/webp;base64,$base64"))
                            )
                        }
                    }
                    item.put("content", contentArray)
                }
                messageArray.put(item)
            }
            requestJson.put("messages", messageArray)

            val requestBuilder = Request.Builder()
                .url(chatCompletionsUrl(api.baseUrl))
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
                val choices = bodyJson.getJSONArray("choices")
                if (choices.length() == 0) {
                    throw IllegalStateException("AI 请求失败：响应 choices 为空")
                }
                choices
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
            // 不再强制对 DeepSeek 设置 reasoning_effort / thinking 参数。
            // reasoning_effort 是 OpenAI 专有参数，DeepSeek 不支持，会导致请求失败。
            // thinking 会改变 DeepSeek 的输出行为，可能使 <tool_call> 标签丢失，导致本地 function call 无法触发。

            val messageArray = JSONArray()
            messages.forEach { message ->
                val item = JSONObject()
                item.put("role", message.role)
                if (message.imagePaths.isEmpty()) {
                    item.put("content", message.content)
                } else {
                    val contentArray = JSONArray()
                    if (message.content.isNotBlank()) {
                        contentArray.put(JSONObject().put("type", "text").put("text", message.content))
                    }
                    message.imagePaths.forEach { path ->
                        val file = File(path)
                        if (file.exists()) {
                            val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                            contentArray.put(
                                JSONObject()
                                    .put("type", "image_url")
                                    .put("image_url", JSONObject().put("url", "data:image/webp;base64,$base64"))
                            )
                        }
                    }
                    item.put("content", contentArray)
                }
                messageArray.put(item)
            }
            requestJson.put("messages", messageArray)

            val requestBuilder = Request.Builder()
                .url(chatCompletionsUrl(api.baseUrl))
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
                    val choices = bodyJson.getJSONArray("choices")
                    if (choices.length() == 0) continue

                    val choice = choices.getJSONObject(0)
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
