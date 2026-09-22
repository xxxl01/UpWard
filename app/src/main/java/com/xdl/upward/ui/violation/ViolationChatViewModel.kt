package com.xdl.upward.ui.violation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AiApiEntity
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.remote.AiApiService
import com.xdl.upward.data.repository.AiApiRepository
import com.xdl.upward.data.repository.ChallengeRepository
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.data.repository.DailyRecordRepository
import com.xdl.upward.data.repository.MessageRepository
import com.xdl.upward.data.repository.ProjectRepository
import com.xdl.upward.domain.AiChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.OffsetDateTime

class ViolationChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val configRepository = ConfigRepository(database.configDao())
    private val projectRepository = ProjectRepository(database.projectDao())
    private val messageRepository = MessageRepository(database.messageDao())
    private val dailyRecordRepository = DailyRecordRepository(database.dailyRecordDao())
    private val challengeRepository = ChallengeRepository(database.challengeDao(), database.challengeCheckInDao())
    private val aiApiRepository = AiApiRepository(database.aiApiDao())
    private val aiApiService = AiApiService()

    private val _violationCount = MutableStateFlow(0)
    val violationCount: StateFlow<Int> = _violationCount.asStateFlow()

    private val _messages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _selectedImagePaths = MutableStateFlow<List<String>>(emptyList())
    val selectedImagePaths: StateFlow<List<String>> = _selectedImagePaths.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private var initialWarningSent = false

    init {
        viewModelScope.launch {
            configRepository.observeViolationCount().collect { value ->
                _violationCount.value = value?.toIntOrNull() ?: 0
            }
        }
    }

    fun updateInput(value: String) {
        _input.value = value
    }

    // 把用户选择的捐款截图保存到应用目录，供本次告诫对话发送给 AI 审核。
    // 一次可选多张，按选择顺序保存后追加；单张失败只跳过该张，不影响其余图片。
    fun addSelectedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return

        _errorMessage.value = ""
        viewModelScope.launch {
            val savedPaths = mutableListOf<String>()
            var failedCount = 0
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val imageDir = File(context.filesDir, "chat_images")
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }
                uris.forEachIndexed { index, uri ->
                    try {
                        val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            BitmapFactory.decodeStream(inputStream)
                        } ?: throw IllegalStateException("图片读取失败")

                        val maxSide = 1600
                        val width = bitmap.width
                        val height = bitmap.height
                        val outputBitmap = if (width > maxSide || height > maxSide) {
                            val scale = maxSide.toFloat() / maxOf(width, height).toFloat()
                            Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
                        } else {
                            bitmap
                        }

                        // 多张图片可能在同一毫秒保存，文件名带上序号避免互相覆盖
                        val imageFile = File(imageDir, "chat_${System.currentTimeMillis()}_$index.webp")
                        FileOutputStream(imageFile).use { outputStream ->
                            outputBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, outputStream)
                        }
                        if (outputBitmap !== bitmap) {
                            outputBitmap.recycle()
                        }
                        bitmap.recycle()
                        savedPaths.add(imageFile.absolutePath)
                    } catch (e: Exception) {
                        failedCount++
                    }
                }
            }
            if (savedPaths.isNotEmpty()) {
                _selectedImagePaths.value = _selectedImagePaths.value + savedPaths
            }
            if (failedCount > 0) {
                _errorMessage.value = "有 $failedCount 张图片保存失败，已跳过。"
            }
        }
    }

    fun removeSelectedImage(path: String) {
        _selectedImagePaths.value = _selectedImagePaths.value.filter { it != path }
        viewModelScope.launch(Dispatchers.IO) {
            File(path).delete()
        }
    }

    fun sendInitialWarning() {
        val count = _violationCount.value
        if (count <= 0 || initialWarningSent || _loading.value) return

        initialWarningSent = true
        sendMessage("我现在已经累计违规 ${count} 次，请认真告诫我。", showUserMessage = false)
    }

    fun sendUserMessage() {
        val content = _input.value.trim()
        val imagePaths = _selectedImagePaths.value
        if ((content.isEmpty() && imagePaths.isEmpty()) || _loading.value) return

        _input.value = ""
        _selectedImagePaths.value = emptyList()
        sendMessage(content, showUserMessage = true, imagePaths = imagePaths)
    }

    fun resendLastMessage() {
        if (_messages.value.isEmpty() || _loading.value) return

        sendMessage(content = "", showUserMessage = false, includePromptInRequest = false)
    }

    private fun sendMessage(
        content: String,
        showUserMessage: Boolean,
        includePromptInRequest: Boolean = true,
        imagePaths: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            try {
                _loading.value = true
                _errorMessage.value = ""

                val api = aiApiRepository.getSelected()
                if (api == null) {
                    _errorMessage.value = "请先在 API 设置中选择一个 API"
                    return@launch
                }

                val oldMessages = _messages.value
                val nowText = OffsetDateTime.now().toString()
                val visibleMessages = if (showUserMessage) {
                    oldMessages + AiChatMessage("user", content, imagePaths)
                } else {
                    oldMessages
                }
                _messages.value = visibleMessages

                val projects = projectRepository.getProjects()
                val projectContext = StringBuilder()
                if (projects.isEmpty()) {
                    projectContext.append("当前没有项目。")
                } else {
                    val currentTime = OffsetDateTime.now()
                    val today = if (currentTime.toLocalTime().isBefore(java.time.LocalTime.of(4, 0))) {
                        currentTime.toLocalDate().minusDays(1)
                    } else {
                        currentTime.toLocalDate()
                    }
                    val yesterday = today.minusDays(1)
                    val now = OffsetDateTime.now()
                    val yesterdayStartTime = yesterday.atTime(4, 0).atOffset(now.offset)
                    val morningStartTime = yesterday.atTime(4, 0).atOffset(now.offset)
                    val noonStartTime = yesterday.atTime(13, 0).atOffset(now.offset)
                    val eveningStartTime = yesterday.atTime(19, 30).atOffset(now.offset)
                    val dailyStart = yesterdayStartTime.toString()
                    val morningStart = morningStartTime.toString()
                    val noonStart = noonStartTime.toString()
                    val eveningStart = eveningStartTime.toString()
                    val todayStart = today.atTime(4, 0).atOffset(now.offset).toString()
                    val initialDate = configRepository.getValue("initial_date")?.let { value ->
                        try {
                            LocalDate.parse(value)
                        } catch (e: Exception) {
                            today
                        }
                    } ?: today
                    projects.forEach { project ->
                        val projectCreatedAt = try {
                            OffsetDateTime.parse(project.createdAt)
                        } catch (e: Exception) {
                            now
                        }
                        projectContext.append("项目：${project.name}")
                        val challenge = challengeRepository.challengeOfDate(project.id, yesterday)
                        if (challenge != null) {
                            // 昨天处于挑战模式：以挑战打卡为准，不按普通打卡模式判违规
                            val done = challengeRepository.getCheckIns(challenge.id).any { it.date == yesterday.toString() }
                            projectContext.append("\n打卡模式：挑战模式（挑战目标：${challenge.goal}）")
                            projectContext.append("\n昨日挑战打卡状态：")
                            projectContext.append(if (done) "已完成" else "缺失（已判定挑战失败，但不计入违规次数）")
                            projectContext.append("\n")
                        } else if (project.checkInMode == "once_per_day") {
                            val shouldCheckDaily = initialDate.isBefore(yesterday) && projectCreatedAt.isBefore(today.atTime(4, 0).atOffset(now.offset))
                            val hasDailyMessage = !shouldCheckDaily || messageRepository.hasUserMessagesBetween(project.id, dailyStart, todayStart)
                            if (shouldCheckDaily && !hasDailyMessage) {
                                projectContext.append("（违规项目）")
                            }
                            projectContext.append("\n打卡模式：每日一次")
                            projectContext.append("\n昨日打卡状态：")
                            projectContext.append(if (hasDailyMessage) "已打卡" else "缺失")
                            projectContext.append("\n")
                        } else {
                            val shouldCheckMorning = initialDate.isBefore(yesterday) && !projectCreatedAt.isAfter(morningStartTime)
                            val shouldCheckAfternoon = initialDate.isBefore(yesterday) && !projectCreatedAt.isAfter(noonStartTime)
                            val shouldCheckEvening = initialDate.isBefore(yesterday) && !projectCreatedAt.isAfter(eveningStartTime)
                            val hasMorningMessage = !shouldCheckMorning || messageRepository.hasUserMessagesBetween(project.id, morningStart, noonStart)
                            val hasAfternoonMessage = !shouldCheckAfternoon || messageRepository.hasUserMessagesBetween(project.id, noonStart, eveningStart)
                            val hasEveningMessage = !shouldCheckEvening || messageRepository.hasUserMessagesBetween(project.id, eveningStart, todayStart)
                            val isViolation = (shouldCheckMorning && !hasMorningMessage) ||
                                (shouldCheckAfternoon && !hasAfternoonMessage) ||
                                (shouldCheckEvening && !hasEveningMessage)
                            if (isViolation) {
                                projectContext.append("（违规项目）")
                            }
                            projectContext.append("\n打卡模式：三时段")
                            projectContext.append("\n昨日打卡状态：4:00-13:00 ")
                            projectContext.append(if (hasMorningMessage) "已打卡" else "缺失")
                            projectContext.append("；13:00-19:30 ")
                            projectContext.append(if (hasAfternoonMessage) "已打卡" else "缺失")
                            projectContext.append("；19:30-次日4:00 ")
                            projectContext.append(if (hasEveningMessage) "已打卡" else "缺失")
                            projectContext.append("\n")
                        }
                        val records = dailyRecordRepository.getAllByProjectId(project.id)
                        if (records.isEmpty()) {
                            projectContext.append("每日记录：暂无\n")
                        } else {
                            projectContext.append("每日记录：\n")
                            records.forEach { record ->
                                projectContext.append("- ${record.date}：${record.content}\n")
                            }
                        }
                        projectContext.append("\n")
                    }
                }

                val systemPrompt = """
                    你是用户自律项目的监督者。
                    违规定义：三时段项目要求昨天 4:00-13:00、13:00-19:30、19:30-今天 4:00 三个时间段内分别至少有一条 user 打卡消息；每日一次项目要求昨天 4:00 到今天 4:00 之间至少有一条 user 打卡消息。任一项目按自身打卡模式缺失要求，就记为一次违规。违规累计达到 10 次会触发惩罚：用户在项目里发消息会被拦截；违规次数仍会继续累加，之后每笔 5 元捐款只能抵扣 10 次违规。
                    项目昨天处于挑战期时以挑战打卡为准，不适用上面的普通打卡模式要求：挑战当天完成打卡即视为达标；挑战缺卡只判定挑战失败并进入失败惩罚，不计入违规次数。
                    当前违规次数：${_violationCount.value} 次。

                    惩罚与重置规则：
                    1. 累计违规达到 10 次后，用户在任意项目发送消息都会被拦截，无法继续打卡；之后违规次数仍会继续累加。
                    2. 解除方式：用户需在任意慈善平台捐款 5 元，并把捐款截图以图片形式发给你审核；每笔捐款只能抵扣 10 次违规。
                    3. 你审核截图，确认用户确实完成了一笔金额不低于 5 元的慈善捐款后，请在回复末尾额外输出一个本地工具调用块，把违规次数减少 10 次。若抵扣后仍达到 10 次，项目消息继续拦截；只有降到 10 次以下才解除惩罚。工具调用块必须严格使用下面格式，不能放在代码块里：
                    <tool_call>{"name":"reset_violation_count","arguments":{"summary":"简要说明审核依据"}}</tool_call>
                    工具规则：只有当前违规次数达到 10 次且用户确实完成捐款（截图金额 ≥ 5 元）后才能调用；未达到 10 次时禁止调用，只进行告诫；每次调用只抵扣 10 次，不能一次性全部清零；自然语言回复中不要解释工具 JSON。

                    当前项目和每日记录：
                    $projectContext

                    如果项目后面标了“违规项目”，你在回复中提到该项目名时必须使用 HTML：<font color="red">项目名</font>。
                    请用中文回应。语气要晓之以理、动之以情，真诚、具体、有压力感，但不要羞辱用户。
                    你要提醒用户珍惜已经积累的过程，说明继续拖延的代价，并给出今天马上恢复打卡的简短行动建议。
                """.trimIndent()
                val requestMessages = mutableListOf(AiChatMessage("system", systemPrompt))
                oldMessages.forEach { message ->
                    requestMessages.add(message)
                }
                if (includePromptInRequest) {
                    requestMessages.add(AiChatMessage("user", "[消息发送时间: $nowText]\n$content"))
                }
                if (imagePaths.isNotEmpty()) {
                    bindImagesToLastUserMessage(api, requestMessages, imagePaths)
                }

                val reply = StringBuilder()
                _messages.value = visibleMessages + AiChatMessage("assistant", "")
                var lastUpdateAt = 0L
                val finalReply = aiApiService.streamChat(
                    api = api,
                    messages = requestMessages,
                    onContentDelta = { delta ->
                        reply.append(delta)
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateAt >= 250L) {
                            // 流式期间工具块可能还没输完，截掉开始标签及之后的内容，别让用户看到裸的指令 JSON
                            _messages.value = visibleMessages + AiChatMessage("assistant", reply.toString().substringBefore("<tool_call>").trimEnd())
                            lastUpdateAt = now
                        }
                    },
                    onReasoningDelta = {}
                )
                val finalDisplay = executeToolCalls(finalReply)
                // 回复只有工具调用块、剥完什么都不剩时不留空气泡
                _messages.value = if (finalDisplay.isEmpty()) {
                    visibleMessages
                } else {
                    visibleMessages + AiChatMessage("assistant", finalDisplay)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "AI 请求失败"
            } finally {
                _loading.value = false
            }
        }
    }

    // 把用户消息的图片绑定到请求里最后一条 user 消息（与项目聊天一致的降级策略）：
    // 支持图片的 API 直接带图；不支持但配置了视觉辅助模型时，先用视觉模型把图片描述成文字注入消息；
    // 都没有时降级为只发文字并提示，不阻断对话。
    private suspend fun bindImagesToLastUserMessage(
        api: AiApiEntity,
        requestMessages: MutableList<AiChatMessage>,
        imagePaths: List<String>
    ) {
        if (imagePaths.isEmpty()) return
        val lastIndex = requestMessages.indexOfLast { it.role == "user" }
        if (lastIndex < 0) return
        val currentMessage = requestMessages[lastIndex]

        if (api.supportImage) {
            requestMessages[lastIndex] = currentMessage.copy(imagePaths = imagePaths)
            return
        }

        if (api.visionApiId > 0L) {
            val visionApi = aiApiRepository.getById(api.visionApiId)
            if (visionApi != null) {
                try {
                    // 视觉模型只描述本次输入，剥离 buildChatMessages 加的"[消息发送时间:]"前缀行
                    val userText = if (currentMessage.content.startsWith("[消息发送时间:")) {
                        currentMessage.content.substringAfter("\n")
                    } else {
                        currentMessage.content
                    }
                    val description = aiApiService.describeImages(visionApi, userText, imagePaths)
                    requestMessages[lastIndex] = currentMessage.copy(
                        content = currentMessage.content + "\n\n[用户附带了 ${imagePaths.size} 张图片，以下是视觉辅助模型对图片的描述：]\n$description"
                    )
                    return
                } catch (e: Exception) {
                    _errorMessage.value = "图片描述失败（${e.message ?: "未知错误"}），本次只发送文字。"
                }
            } else {
                _errorMessage.value = "视觉辅助模型不存在，本次只发送文字。"
            }
        } else {
            _errorMessage.value = "当前 API 不支持图片输入，图片未发送给 AI。"
        }
        requestMessages[lastIndex] = currentMessage.copy(
            content = currentMessage.content + "\n\n[用户附带了 ${imagePaths.size} 张图片，但当前 API 不支持图片输入，图片未发送给 AI。]"
        )
    }

    // 解析并执行告诫页的本地工具调用，返回剥掉所有 <tool_call> 块后的纯净回复。
    // 执行结果只放到 errorMessage 临时提示，绝不写进消息正文：正文会作为 assistant 历史回灌给 AI，
    // 一旦里面有"[工具已执行…]"这类日志，模型（尤其 DeepSeek）会照着模仿输出纯文本，真正的工具就不再被触发。
    private suspend fun executeToolCalls(replyText: String): String {
        var result = replyText
        var guard = 0
        while (guard < 10) {
            guard++
            val startTag = "<tool_call>"
            val endTag = "</tool_call>"
            val startIndex = result.indexOf(startTag)
            if (startIndex < 0) {
                break
            }
            val endIndex = result.indexOf(endTag, startIndex)
            // 标签没闭合（流式截断、模型漏写结尾）时，把开始标签及之后的内容整段丢掉，别让指令文本留在正文里
            if (endIndex < 0) {
                result = result.substring(0, startIndex).trim()
                break
            }
            val toolText = result.substring(startIndex + startTag.length, endIndex).trim()
            result = (result.substring(0, startIndex) + result.substring(endIndex + endTag.length)).trim()
            val toolMessage = try {
                val toolJson = JSONObject(toolText)
                if (toolJson.optString("name") == "reset_violation_count") {
                    executeResetViolationCount()
                } else {
                    ""
                }
            } catch (e: Exception) {
                "工具调用失败：JSON 格式不正确。"
            }
            if (toolMessage.isNotEmpty()) {
                _errorMessage.value = toolMessage
            }
        }
        return result
    }

    // 执行“抵扣违规次数”工具：AI 确认用户完成捐款（金额 ≥ 5 元）后调用，每次抵扣 10 次。
    private suspend fun executeResetViolationCount(): String {
        val currentCount = _violationCount.value
        if (currentCount < 10) {
            return "工具调用失败：当前违规次数为 $currentCount，未达到惩罚线，无需抵扣。"
        }
        val remainingCount = currentCount - 10
        configRepository.setValue("violation_count", remainingCount.toString())
        _violationCount.value = remainingCount
        return if (remainingCount >= 10) {
            "工具已执行：已确认捐款完成，违规次数抵扣 10 次，剩余违规 $remainingCount 次，项目消息仍处于拦截状态；每笔捐款只能再抵扣 10 次。"
        } else {
            "工具已执行：已确认捐款完成，违规次数抵扣 10 次，剩余违规 $remainingCount 次，惩罚已解除，你可以在项目里正常发送消息了。"
        }
    }
}
