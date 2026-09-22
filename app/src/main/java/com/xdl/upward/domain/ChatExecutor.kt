package com.xdl.upward.domain

import android.content.Context
import com.xdl.upward.data.local.AiApiEntity
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.remote.AiApiService
import com.xdl.upward.data.repository.AiApiRepository
import com.xdl.upward.data.repository.ChallengeRepository
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.data.repository.DailyRecordRepository
import com.xdl.upward.data.repository.MessageRepository
import com.xdl.upward.data.repository.ProjectRepository
import com.xdl.upward.reminder.RequiredMessageReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 项目聊天的后台执行器。
 * 聊天请求的生命周期从页面（ViewModel）挪到这里：进程存活期间，即使退出聊天页面或 App 退到后台，
 * 正在进行的流式请求也会继续执行，回复写入数据库；重新进入页面后通过 observeProjectChat 恢复执行状态。
 */
object ChatExecutor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class ProjectChatState(
        val loading: Boolean = false,
        val aiThinking: Boolean = false,
        val errorMessage: String = ""
    )

    private val projectStates = mutableMapOf<Long, MutableStateFlow<ProjectChatState>>()

    @Synchronized
    fun observeProjectChat(projectId: Long): StateFlow<ProjectChatState> =
        stateOf(projectId).asStateFlow()

    @Synchronized
    private fun stateOf(projectId: Long): MutableStateFlow<ProjectChatState> =
        projectStates.getOrPut(projectId) { MutableStateFlow(ProjectChatState()) }

    private fun update(projectId: Long, transform: (ProjectChatState) -> ProjectChatState) {
        val flow = stateOf(projectId)
        flow.value = transform(flow.value)
    }

    // 一次聊天执行所需的全部依赖，按 projectId 无关，仅依赖 context。
    private class ProjectChatDeps(context: Context) {
        val appContext = context.applicationContext
        val database = AppDatabase.getInstance(appContext)
        val repository = ProjectRepository(database.projectDao())
        val dailyRecordRepository = DailyRecordRepository(database.dailyRecordDao())
        val messageRepository = MessageRepository(database.messageDao())
        val aiApiRepository = AiApiRepository(database.aiApiDao())
        val configRepository = ConfigRepository(database.configDao())
        val challengeRepository = ChallengeRepository(database.challengeDao(), database.challengeCheckInDao())
        val aiApiService = AiApiService()
        val aiMessageBuilder = AiMessageBuilder()
    }

    // 发送一条用户消息并流式接收 AI 回复，逻辑与页面是否存活无关。
    fun sendUserMessage(context: Context, projectId: Long, content: String, imagePaths: List<String>) {
        scope.launch {
            val deps = ProjectChatDeps(context)
            var shouldRestoreRequiredTime = false
            var originalRequiredMessageStartAt = ""
            var originalRequiredMessageEndAt = ""
            var sendCompleted = false
            try {
                update(projectId) { it.copy(loading = true, aiThinking = false, errorMessage = "") }
                val project = deps.repository.getProject(projectId)
                if (project == null) {
                    update(projectId) { it.copy(errorMessage = "项目不存在") }
                    return@launch
                }
                // 挑战缺卡自动检测：挑战期内有日期没打卡则进入失败惩罚流程（状态置为 failed）。
                deps.challengeRepository.checkForMissedDays(projectId)

                var nextRequiredMessageStartAt = project.nextRequiredMessageStartAt
                var nextRequiredMessageEndAt = project.nextRequiredMessageEndAt
                if (project.nextRequiredMessageEndAt.isNotBlank()) {
                    val startTime = try {
                        OffsetDateTime.parse(project.nextRequiredMessageStartAt)
                    } catch (e: Exception) {
                        null
                    }
                    val endTime = try {
                        OffsetDateTime.parse(project.nextRequiredMessageEndAt)
                    } catch (e: Exception) {
                        null
                    }
                    val now = OffsetDateTime.now()
                    if (startTime == null || endTime == null) {
                        deps.repository.setNextRequiredMessagePeriod(projectId, "", "")
                        RequiredMessageReminderScheduler.cancel(deps.appContext, projectId)
                        nextRequiredMessageStartAt = ""
                        nextRequiredMessageEndAt = ""
                    } else if (!now.isBefore(endTime.plusMinutes(1))) {
                        // 违规次数持续累加；达到 10 次后进入惩罚状态，每笔捐款由告诫页 AI 确认后抵扣 10 次
                        val currentCount = deps.configRepository.getValue("violation_count")?.toIntOrNull() ?: 0
                        val nextStartAt = OffsetDateTime.now()
                        val nextEndAt = nextStartAt.plusMinutes(25)
                        deps.repository.setNextRequiredMessagePeriod(projectId, nextStartAt.toString(), nextEndAt.toString())
                        RequiredMessageReminderScheduler.schedule(deps.appContext, projectId, nextStartAt.toString(), nextEndAt.toString())
                        nextRequiredMessageStartAt = nextStartAt.toString()
                        nextRequiredMessageEndAt = nextEndAt.toString()
                        val finalCount = currentCount + 1
                        deps.configRepository.setValue("violation_count", finalCount.toString())
                        update(projectId) {
                            it.copy(
                                errorMessage = if (finalCount >= 10) {
                                    "已错过指定回复时间，违规累计已达到惩罚线：需捐款 5 元并把截图发给违规告诫页的 AI，每笔捐款只能抵扣 10 次违规。"
                                } else {
                                    "已错过指定回复时间，已记为违规 $finalCount/10。"
                                }
                            )
                        }
                    } else if (!now.isBefore(startTime)) {
                        shouldRestoreRequiredTime = true
                        originalRequiredMessageStartAt = project.nextRequiredMessageStartAt
                        originalRequiredMessageEndAt = project.nextRequiredMessageEndAt
                        deps.repository.setNextRequiredMessagePeriod(projectId, "", "")
                        RequiredMessageReminderScheduler.cancel(deps.appContext, projectId)
                        nextRequiredMessageStartAt = ""
                        nextRequiredMessageEndAt = ""
                    }
                }

                deps.messageRepository.addUserMessage(projectId, content, imagePaths)

                val api = deps.aiApiRepository.getSelected()
                if (api == null) {
                    if (shouldRestoreRequiredTime) {
                        deps.repository.setNextRequiredMessagePeriod(projectId, originalRequiredMessageStartAt, originalRequiredMessageEndAt)
                        RequiredMessageReminderScheduler.schedule(deps.appContext, projectId, originalRequiredMessageStartAt, originalRequiredMessageEndAt)
                    }
                    update(projectId) { it.copy(errorMessage = "请先在 API 设置中选择一个 API") }
                    return@launch
                }

                val count = deps.configRepository.getMessageContextCount()
                val dailyRecords = deps.dailyRecordRepository.getAllByProjectId(projectId)
                val recentMessages = deps.messageRepository.getRecentMessages(projectId, count)
                val nextRequiredTimeRule = if (nextRequiredMessageEndAt.isBlank()) {
                    "当前没有已指定的下一次汇报时间。"
                } else {
                    "当前已指定的下一次汇报时间：$nextRequiredMessageStartAt 到 $nextRequiredMessageEndAt。如果要修改，只允许提前：新的 start_at 必须早于当前 start_at，且新时间段长度不能超过当前时间段长度，不能延后。"
                }
                val requestMessages = deps.aiMessageBuilder.buildChatMessages(project, dailyRecords, recentMessages).toMutableList()
                if (requestMessages.isNotEmpty()) {
                    val systemMessage = requestMessages[0]
                    requestMessages[0] = systemMessage.copy(
                        content = systemMessage.content + """

                        当前项目 id：$projectId。
                        当前时间：${OffsetDateTime.now()}。
                        $nextRequiredTimeRule

                        你可以强制指定用户下一次汇报时间。这个工具适合在用户状态不佳、拖延风险高、需要被你推一把时使用：如果你认为某个时间段用户需要向你汇报进展，你就可以指定用户必须在这个时间段内给你发消息，从而让用户被你“推”到。
                        如果用户在你指定的时间段内没有给你发消息，会算作一次违规；违规累计达到 10 次会触发惩罚：用户在所有项目里发消息都会被拦截，必须到“违规告诫”页，在任意慈善平台捐款 5 元并把捐款截图发给 AI；每笔捐款只能抵扣 10 次违规，剩余违规继续保留。
                        如果你决定强制指定下一次汇报时间，请在回复末尾额外输出一个本地工具调用块。工具调用块必须严格使用下面格式，不能放在代码块里：
                         <tool_call>{"name":"set_next_resume_time","arguments":{"start_at":"ISO-8601 时间"}}</tool_call>
                         工具规则：AI 只能填写 start_at，系统会把结束时间固定为开始时间之后 25 分钟，AI 提供的 end_at 会被忽略。start_at 不能早于当前时间；已有汇报时间时，只允许提前，新的开始时间必须更早。只在确实要安排恢复窗口时调用一次；窗口结束且用户未回复时会记一次违规，并立即以当时当前时间为新的开始时间顺延 25 分钟。自然语言回复中不要解释工具 JSON。
                        """.trimIndent()
                    )
                }
                buildChallengeSystemMessage(deps, projectId)?.let { challengeMessage ->
                    requestMessages.add(challengeMessage)
                }
                if (imagePaths.isNotEmpty()) {
                    bindImagesToLastUserMessage(deps, projectId, api, requestMessages, imagePaths)
                }

                var assistantMessageId = 0L
                val reply = StringBuilder()
                var lastUpdateAt = 0L
                val finalReply = deps.aiApiService.streamChat(
                    api = api,
                    messages = requestMessages,
                    onContentDelta = { delta ->
                        update(projectId) { it.copy(aiThinking = false) }
                        reply.append(delta)
                        if (assistantMessageId == 0L) {
                            assistantMessageId = deps.messageRepository.addAssistantMessage(projectId, visibleStreamingText(reply.toString()))
                            lastUpdateAt = System.currentTimeMillis()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateAt >= 250L) {
                                deps.messageRepository.updateMessageContent(assistantMessageId, visibleStreamingText(reply.toString()))
                                lastUpdateAt = now
                            }
                        }
                    },
                    onReasoningDelta = {
                        if (reply.isEmpty()) {
                            update(projectId) { it.copy(aiThinking = true) }
                        }
                    }
                )
                val visibleReply = executeToolCalls(deps, projectId, finalReply)
                if (assistantMessageId != 0L) {
                    // 回复只有工具调用块、剥完什么都不剩时不留空气泡
                    if (visibleReply.isEmpty()) {
                        deps.messageRepository.deleteMessage(assistantMessageId)
                    } else {
                        deps.messageRepository.updateMessageContent(assistantMessageId, visibleReply)
                    }
                } else if (visibleReply.isNotEmpty()) {
                    deps.messageRepository.addAssistantMessage(projectId, visibleReply)
                } else {
                    throw IllegalStateException("AI 回复为空")
                }
                sendCompleted = true
            } catch (e: Exception) {
                if (shouldRestoreRequiredTime && !sendCompleted) {
                    deps.repository.setNextRequiredMessagePeriod(projectId, originalRequiredMessageStartAt, originalRequiredMessageEndAt)
                    RequiredMessageReminderScheduler.schedule(deps.appContext, projectId, originalRequiredMessageStartAt, originalRequiredMessageEndAt)
                }
                update(projectId) { it.copy(errorMessage = e.message ?: "AI 请求失败") }
            } finally {
                update(projectId) { it.copy(loading = false, aiThinking = false) }
            }
        }
    }

    // 用当前最后一条消息重新请求 AI，不重复追加这条消息。
    fun resendLastMessage(context: Context, projectId: Long, messageId: Long) {
        scope.launch {
            val deps = ProjectChatDeps(context)
            try {
                update(projectId) { it.copy(loading = true, aiThinking = false, errorMessage = "") }
                val project = deps.repository.getProject(projectId)
                if (project == null) {
                    update(projectId) { it.copy(errorMessage = "项目不存在") }
                    return@launch
                }
                // 挑战缺卡自动检测：挑战期内有日期没打卡则进入失败惩罚流程（状态置为 failed）。
                deps.challengeRepository.checkForMissedDays(projectId)

                val lastMessage = deps.messageRepository.getLastMessage(projectId)
                if (lastMessage == null || lastMessage.id != messageId) {
                    update(projectId) { it.copy(errorMessage = "只能重新发送当前最后一条消息") }
                    return@launch
                }

                val api = deps.aiApiRepository.getSelected()
                if (api == null) {
                    update(projectId) { it.copy(errorMessage = "请先在 API 设置中选择一个 API") }
                    return@launch
                }

                val count = deps.configRepository.getMessageContextCount()
                val dailyRecords = deps.dailyRecordRepository.getAllByProjectId(projectId)
                val recentMessages = deps.messageRepository.getRecentMessages(projectId, count)
                val nextRequiredTimeRule = if (project.nextRequiredMessageEndAt.isBlank()) {
                    "当前没有已指定的下一次汇报时间。"
                } else {
                    "当前已指定的下一次汇报时间：${project.nextRequiredMessageStartAt} 到 ${project.nextRequiredMessageEndAt}。如果要修改，只允许提前：新的 start_at 必须早于当前 start_at，且新时间段长度不能超过当前时间段长度，不能延后。"
                }
                val requestMessages = deps.aiMessageBuilder.buildChatMessages(project, dailyRecords, recentMessages).toMutableList()
                if (requestMessages.isNotEmpty()) {
                    val systemMessage = requestMessages[0]
                    requestMessages[0] = systemMessage.copy(
                        content = systemMessage.content + """

                        当前项目 id：$projectId。
                        当前时间：${OffsetDateTime.now()}。
                        $nextRequiredTimeRule

                        你可以强制指定用户下一次汇报时间。这个工具适合在用户状态不佳、拖延风险高、需要被你推一把时使用：如果你认为某个时间段用户需要向你汇报进展，你就可以指定用户必须在这个时间段内给你发消息，从而让用户被你“推”到。
                        如果用户在你指定的时间段内没有给你发消息，会算作一次违规；违规累计达到 10 次会触发惩罚：用户在所有项目里发消息都会被拦截，必须到“违规告诫”页，在任意慈善平台捐款 5 元并把捐款截图发给 AI；每笔捐款只能抵扣 10 次违规，剩余违规继续保留。
                        如果你决定强制指定下一次汇报时间，请在回复末尾额外输出一个本地工具调用块。工具调用块必须严格使用下面格式，不能放在代码块里：
                         <tool_call>{"name":"set_next_resume_time","arguments":{"start_at":"ISO-8601 时间"}}</tool_call>
                         工具规则：AI 只能填写 start_at，系统会把结束时间固定为开始时间之后 25 分钟，AI 提供的 end_at 会被忽略。start_at 不能早于当前时间；已有汇报时间时，只允许提前，新的开始时间必须更早。只在确实要安排恢复窗口时调用一次；窗口结束且用户未回复时会记一次违规，并立即以当时当前时间为新的开始时间顺延 25 分钟。自然语言回复中不要解释工具 JSON。
                        """.trimIndent()
                    )
                }
                buildChallengeSystemMessage(deps, projectId)?.let { challengeMessage ->
                    requestMessages.add(challengeMessage)
                }
                if (lastMessage.role == "user" && lastMessage.imagePaths.isNotBlank()) {
                    val imagePaths = mutableListOf<String>()
                    try {
                        val imagePathArray = JSONArray(lastMessage.imagePaths)
                        for (index in 0 until imagePathArray.length()) {
                            val path = imagePathArray.optString(index).trim()
                            if (path.isNotEmpty()) {
                                imagePaths.add(path)
                            }
                        }
                    } catch (e: Exception) {
                    }
                    if (imagePaths.isNotEmpty()) {
                        bindImagesToLastUserMessage(deps, projectId, api, requestMessages, imagePaths)
                    }
                }

                var assistantMessageId = 0L
                val reply = StringBuilder()
                var lastUpdateAt = 0L
                val finalReply = deps.aiApiService.streamChat(
                    api = api,
                    messages = requestMessages,
                    onContentDelta = { delta ->
                        update(projectId) { it.copy(aiThinking = false) }
                        reply.append(delta)
                        if (assistantMessageId == 0L) {
                            assistantMessageId = deps.messageRepository.addAssistantMessage(projectId, visibleStreamingText(reply.toString()))
                            lastUpdateAt = System.currentTimeMillis()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateAt >= 250L) {
                                deps.messageRepository.updateMessageContent(assistantMessageId, visibleStreamingText(reply.toString()))
                                lastUpdateAt = now
                            }
                        }
                    },
                    onReasoningDelta = {
                        if (reply.isEmpty()) {
                            update(projectId) { it.copy(aiThinking = true) }
                        }
                    }
                )
                val visibleReply = executeToolCalls(deps, projectId, finalReply)
                if (assistantMessageId != 0L) {
                    if (visibleReply.isEmpty()) {
                        deps.messageRepository.deleteMessage(assistantMessageId)
                    } else {
                        deps.messageRepository.updateMessageContent(assistantMessageId, visibleReply)
                    }
                } else if (visibleReply.isNotEmpty()) {
                    deps.messageRepository.addAssistantMessage(projectId, visibleReply)
                }
            } catch (e: Exception) {
                update(projectId) { it.copy(errorMessage = e.message ?: "AI 请求失败") }
            } finally {
                update(projectId) { it.copy(loading = false, aiThinking = false) }
            }
        }
    }

    // 构建挑战相关的临时系统消息（仅拼进请求，不写入消息记录）。
    // 开始日期未到时返回 null：挑战对 AI 完全不可见；从开始日期当天起生效。
    // active：告知挑战目标、进度、每日标准与打卡工具；failed：进入失败惩罚流程，告知惩罚要求与结算工具。
    private suspend fun buildChallengeSystemMessage(deps: ProjectChatDeps, projectId: Long): AiChatMessage? {
        val challenge = deps.challengeRepository.getByProjectId(projectId) ?: return null
        val checkInDate = deps.challengeRepository.currentCheckInDate()
        if (checkInDate.isBefore(LocalDate.parse(challenge.startDate))) return null
        val totalDays = deps.challengeRepository.totalDays(challenge)
        val checkInDays = deps.challengeRepository.checkInDaysSoFar(challenge)
        val base = """
            【项目挑战】
            挑战目标：${challenge.goal}
            挑战周期：${challenge.startDate} ~ ${challenge.endDate}
            当前打卡进度：已打卡 $checkInDays / 共 $totalDays 天。
        """.trimIndent()
        return when (challenge.status) {
            ChallengeRepository.STATUS_ACTIVE -> AiChatMessage(
                role = "system",
                content = """
                    $base
                    每日打卡标准：
                    ${challenge.dailyStandardPrompt}

                    判定规则：当用户明确表达此消息为挑战打卡相关时（例如“这条消息是今天的挑战打卡”），你需要根据上面的每日打卡标准，判断用户是否完成了今天的目标。只有用户明确表达本条消息与挑战打卡相关时才进行判定，其他日常聊天不要擅自打卡。如果用户完成了今天的目标，请在回复末尾额外输出一个本地工具调用块，不能放在代码块里：
                    <tool_call>{"name":"complete_daily_challenge","arguments":{"date":"当天日期 yyyy-MM-dd","summary":"简要说明判定依据"}}</tool_call>
                    工具规则：date 必须是当前打卡日的日期（每天凌晨 4 点换日，00:00 至 04:00 仍算前一天）；同一天只能打卡一次；工具调用块必须严格使用上面格式；自然语言回复中不要解释工具 JSON。
                    挑战成功：坚持到截止日期且每天都完成打卡，即可在挑战页手动结算为挑战成功。
                    失败后果：挑战期内任何一天未打卡即判定挑战失败，随后必须进入失败惩罚流程。
                """.trimIndent()
            )
            ChallengeRepository.STATUS_FAILED -> AiChatMessage(
                role = "system",
                content = """
                    $base
                    该挑战已因某天未完成打卡而失败，现在进入失败惩罚流程。
                    失败惩罚要求：
                    ${challenge.failPenaltyPrompt}

                    你要与用户对话，督促用户完成上述惩罚要求，并根据对话内容判断用户是否真正完成了惩罚。当用户确实完成了惩罚要求时，请在回复末尾额外输出一个本地工具调用块，完成失败结算，不能放在代码块里：
                    <tool_call>{"name":"settle_failed_challenge","arguments":{"summary":"简要说明惩罚完成情况"}}</tool_call>
                    工具规则：只有在用户明确完成惩罚要求后才能调用；工具调用块必须严格使用上面格式；自然语言回复中不要解释工具 JSON。
                    结算完成后，用户才可以开启新一轮挑战。
                """.trimIndent()
            )
            else -> null
        }
    }

    // 把用户消息的图片绑定到请求里最后一条 user 消息。
    // 支持图片的 API 直接带图；不支持但配置了视觉辅助模型时，先用视觉模型把图片描述成文字注入消息；
    // 都没有时降级为只发文字并提示，不阻断对话。
    private suspend fun bindImagesToLastUserMessage(
        deps: ProjectChatDeps,
        projectId: Long,
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
            val visionApi = deps.aiApiRepository.getById(api.visionApiId)
            if (visionApi != null) {
                try {
                    // 视觉模型只描述本次输入，剥离 buildChatMessages 加的"[消息发送时间:]"前缀行
                    val userText = if (currentMessage.content.startsWith("[消息发送时间:")) {
                        currentMessage.content.substringAfter("\n")
                    } else {
                        currentMessage.content
                    }
                    val description = deps.aiApiService.describeImages(visionApi, userText, imagePaths)
                    requestMessages[lastIndex] = currentMessage.copy(
                        content = currentMessage.content + "\n\n[用户附带了 ${imagePaths.size} 张图片，以下是视觉辅助模型对图片的描述：]\n$description"
                    )
                    return
                } catch (e: Exception) {
                    update(projectId) { it.copy(errorMessage = "图片描述失败（${e.message ?: "未知错误"}），本次只发送文字。") }
                }
            } else {
                update(projectId) { it.copy(errorMessage = "视觉辅助模型不存在，本次只发送文字。") }
            }
        } else {
            update(projectId) { it.copy(errorMessage = "当前 API 不支持图片输入，图片已保存到聊天记录，本次只发送文字。") }
        }
        requestMessages[lastIndex] = currentMessage.copy(
            content = currentMessage.content + "\n\n[用户附带了 ${imagePaths.size} 张图片，但当前 API 不支持图片输入，图片未发送给 AI。]"
        )
    }

    // 流式期间落库用的文本：截掉 <tool_call> 开始标签及之后的内容。
    // 流式过程中工具块可能还没输完，直接落库会让用户看到裸的指令 JSON；
    // 而且请求中断时这段原文会留在库里、下一轮作为 assistant 历史回灌，模型会照着模仿输出纯文本。
    private fun visibleStreamingText(text: String): String {
        val startIndex = text.indexOf("<tool_call>")
        return if (startIndex < 0) text else text.substring(0, startIndex).trimEnd()
    }

    // 解析并执行项目聊天里的本地工具调用，返回剥掉所有 <tool_call> 块后的纯净回复。
    // 执行结果只通过页面状态临时提示，绝不写进消息正文：正文会作为 assistant 历史回灌给 AI，
    // 一旦里面有"[工具已执行…]"这类日志，模型（尤其 DeepSeek）会照着模仿输出纯文本，真正的工具就不再被触发。
    private suspend fun executeToolCalls(deps: ProjectChatDeps, projectId: Long, replyText: String): String {
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
                when (toolJson.optString("name")) {
                    "set_next_resume_time" -> executeSetNextResumeTime(deps, projectId, toolJson)
                    "complete_daily_challenge" -> executeCompleteDailyChallenge(deps, projectId, toolJson)
                    "settle_failed_challenge" -> executeSettleFailedChallenge(deps, projectId)
                    else -> ""
                }
            } catch (e: Exception) {
                "工具调用失败：时间或 JSON 格式不正确。"
            }
            if (toolMessage.isNotEmpty()) {
                update(projectId) { it.copy(errorMessage = toolMessage) }
            }
        }
        return result
    }

    // 执行“强制指定下次汇报时间”工具，返回给用户看的执行结果提示。
    private suspend fun executeSetNextResumeTime(deps: ProjectChatDeps, projectId: Long, toolJson: JSONObject): String {
        val arguments = toolJson.getJSONObject("arguments")
        val startAt = OffsetDateTime.parse(arguments.optString("start_at", "").trim())
        val endAt = startAt.plusMinutes(25)
        val project = deps.repository.getProject(projectId)
        val now = OffsetDateTime.now()
        if (project == null) {
            return "工具调用失败：项目不存在。"
        }
        if (startAt.isBefore(now)) {
            return "工具调用失败：开始时间不能早于当前时间。"
        }
        if (project.nextRequiredMessageEndAt.isNotBlank()) {
            val oldStartTime = OffsetDateTime.parse(project.nextRequiredMessageStartAt)
            if (!startAt.isBefore(oldStartTime)) {
                return "工具调用失败：已有指定回复时间时，新的开始时间必须早于原开始时间。"
            }
            deps.repository.setNextRequiredMessagePeriod(projectId, startAt.toString(), endAt.toString())
            RequiredMessageReminderScheduler.schedule(deps.appContext, projectId, startAt.toString(), endAt.toString())
            return "工具已执行：已提前下次恢复时间 ${startAt.toLocalDate()} ${startAt.toLocalTime()} - ${endAt.toLocalDate()} ${endAt.toLocalTime()}。"
        }
        deps.repository.setNextRequiredMessagePeriod(projectId, startAt.toString(), endAt.toString())
        RequiredMessageReminderScheduler.schedule(deps.appContext, projectId, startAt.toString(), endAt.toString())
        return "工具已执行：已指定下次恢复时间 ${startAt.toLocalDate()} ${startAt.toLocalTime()} - ${endAt.toLocalDate()} ${endAt.toLocalTime()}。"
    }

    // 执行“完成今日挑战”工具：AI 判断用户当天达成目标后为当天打卡。
    private suspend fun executeCompleteDailyChallenge(deps: ProjectChatDeps, projectId: Long, toolJson: JSONObject): String {
        val arguments = toolJson.getJSONObject("arguments")
        val date = arguments.optString("date", "").trim()
        val summary = arguments.optString("summary", "").trim()
        val challenge = deps.challengeRepository.getByProjectId(projectId)
        if (challenge == null) {
            return "工具调用失败：当前没有挑战记录。"
        }
        if (challenge.status != ChallengeRepository.STATUS_ACTIVE) {
            return "工具调用失败：当前挑战不在进行中，无法打卡。"
        }
        if (date.isEmpty()) {
            return "工具调用失败：date 不能为空。"
        }
        if (date != deps.challengeRepository.currentCheckInDate().toString()) {
            return "工具调用失败：只能为今天打卡。"
        }
        if (date < challenge.startDate || date > challenge.endDate) {
            return "工具调用失败：今天不在挑战周期内。"
        }
        val inserted = deps.challengeRepository.checkInToday(challenge.id, date, summary)
        if (!inserted) {
            return "工具调用失败：今天已经打卡过了。"
        }
        val totalDays = deps.challengeRepository.totalDays(challenge)
        val checkInDays = deps.challengeRepository.checkInDaysSoFar(challenge)
        // 最后一天打卡完成且周期内全勤：直接自动结算为成功，不再等用户手动点结算。
        // 否则挑战期已过却停在 active，AI 侧仍按挑战模式提示用户，违规计算侧却已按普通模式追责。
        if (date == challenge.endDate && checkInDays == totalDays) {
            deps.challengeRepository.settleSuccess(challenge.id)
            return "工具已执行：今日挑战已打卡（$checkInDays/$totalDays 天）。这是挑战最后一天且全程无缺卡，挑战已自动结算为成功。"
        }
        return "工具已执行：今日挑战已打卡（$checkInDays/$totalDays 天）。"
    }

    // 执行“失败后结算挑战”工具：AI 判断用户完成失败惩罚后，把挑战从 failed 结算为 settled。
    private suspend fun executeSettleFailedChallenge(deps: ProjectChatDeps, projectId: Long): String {
        val challenge = deps.challengeRepository.getByProjectId(projectId)
        if (challenge == null) {
            return "工具调用失败：当前没有挑战记录。"
        }
        if (challenge.status != ChallengeRepository.STATUS_FAILED) {
            return "工具调用失败：当前挑战不是失败状态，无需惩罚结算。"
        }
        deps.challengeRepository.settleFailed(challenge.id)
        return "工具已执行：失败惩罚已完成，挑战已结算，可以开启新一轮挑战。"
    }
}
