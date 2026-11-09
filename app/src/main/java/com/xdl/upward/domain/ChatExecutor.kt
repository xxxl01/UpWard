package com.xdl.upward.domain

import android.content.Context
import com.xdl.upward.data.local.AiApiEntity
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.ProjectEntity
import com.xdl.upward.data.remote.AiApiService
import com.xdl.upward.data.repository.AiApiRepository
import com.xdl.upward.data.repository.ChallengeRepository
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.data.repository.DailyRecordRepository
import com.xdl.upward.data.repository.HolidayRepository
import com.xdl.upward.data.repository.MessageRepository
import com.xdl.upward.data.repository.ProjectRepository
import com.xdl.upward.reminder.RequiredMessageReminderScheduler
import com.xdl.upward.reminder.RequiredMessageDeadlineHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 项目聊天的后台执行器。
 * 聊天请求的生命周期从页面（ViewModel）挪到这里：进程存活期间，即使退出聊天页面或 App 退到后台，
 * 正在进行的流式请求也会继续执行，回复写入数据库；重新进入页面后通过 observeProjectChat 恢复执行状态。
 */
object ChatExecutor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val TOOL_NAMES = setOf("set_next_resume_time", "complete_daily_challenge")

    data class ProjectChatState(
        val loading: Boolean = false,
        val aiThinking: Boolean = false,
        val errorMessage: String = ""
    )

    private val projectStates = mutableMapOf<Long, MutableStateFlow<ProjectChatState>>()
    private val chatJobs = mutableMapOf<Long, Job>()

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
        val holidayRepository = HolidayRepository(database.holidayDao())
    }

    // 发送一条用户消息并流式接收 AI 回复，逻辑与页面是否存活无关。
    fun sendUserMessage(
        context: Context,
        projectId: Long,
        content: String,
        imagePaths: List<String>,
        criticalMode: Boolean = false,
        experienceMode: Boolean = false
    ) {
        val job = scope.launch {
            val deps = ProjectChatDeps(context)
            var shouldRestoreRequiredTime = false
            var originalRequiredMessageStartAt = ""
            var originalRequiredMessageEndAt = ""
            var sendCompleted = false
            try {
                update(projectId) { it.copy(loading = true, aiThinking = false, errorMessage = "") }
                if (deps.repository.getProject(projectId) == null) {
                    update(projectId) { it.copy(errorMessage = "项目不存在") }
                    return@launch
                }
                // 挑战缺卡自动检测：挑战期内有日期没打卡则进入失败惩罚流程（状态置为 failed）。
                deps.challengeRepository.checkForMissedDays(projectId)

                val finalCount = RequiredMessageDeadlineHandler.checkAndRecord(deps.appContext, projectId)
                if (finalCount != null) {
                    update(projectId) {
                        it.copy(
                            errorMessage = if (finalCount >= 10) {
                                "已错过指定回复时间，违规累计已达到惩罚线：需捐款 4 元及以上，到“违规惩罚”页上传捐款截图，每笔捐款抵扣 10 次违规。"
                            } else {
                                "已错过指定回复时间，已记为违规 $finalCount/10。"
                            }
                        )
                    }
                }
                val violationCount = finalCount
                    ?: (deps.configRepository.getValue("violation_count")?.toIntOrNull() ?: 0)
                if (violationCount >= 10) {
                    update(projectId) {
                        it.copy(errorMessage = "你已累计违规 $violationCount 次，处于惩罚状态，消息发送已被拦截。请捐款 4 元及以上，到“违规惩罚”页上传捐款截图；每笔捐款抵扣 10 次违规。")
                    }
                    return@launch
                }
                val project = deps.repository.getProject(projectId) ?: return@launch

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
                    } else if (!now.isBefore(startTime) && now.isBefore(endTime)) {
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
                    "当前已指定的下一次汇报时间：$nextRequiredMessageStartAt 到 $nextRequiredMessageEndAt。如果要修改，可以提前，也可以延后；新的 start_at 不能早于当前时间。"
                }
                val requestMessages = deps.aiMessageBuilder.buildChatMessages(project, dailyRecords, recentMessages).toMutableList()
                if (requestMessages.isNotEmpty()) {
                    val systemMessage = requestMessages[0]
                    requestMessages[0] = systemMessage.copy(
                        content = systemMessage.content + """

                        当前项目 id：$projectId。
                        当前时间：${describeNow(deps)}。
                        $nextRequiredTimeRule

                        你可以强制指定用户下一次汇报时间。这个工具适合在用户状态不佳、拖延风险高、需要被你推一把时使用：如果你认为某个时间段用户需要向你汇报进展，你就可以指定用户必须在这个时间段内给你发消息，从而让用户被你“推”到。
                        如果用户在你指定的时间段内没有给你发消息，会算作一次违规；违规累计达到 10 次会触发惩罚：用户在所有项目里发消息都会被拦截，必须向公益慈善机构捐款 4 元及以上，到“违规惩罚”页上传包含交易单号的捐款截图，审核通过后每笔抵扣 10 次违规，剩余违规继续保留。
                        如果你决定强制指定下一次汇报时间，请在回复末尾单独占一行输出下面这行 JSON（一行一个工具调用，不要加标签、不要放进代码块，这行会直接显示给用户）：
                         {"name":"set_next_resume_time","arguments":{"start_at":"ISO-8601 时间"}}
                         工具规则：AI 只能填写 start_at，系统会把结束时间固定为开始时间之后 25 分钟，AI 提供的 end_at 会被忽略。start_at 不能早于当前时间；已有汇报时间时，既可以提前，也可以延后。只在确实要安排恢复窗口时调用一次；窗口结束且用户未回复时会记一次违规并清除窗口，不会自动开启新窗口。
                        """.trimIndent()
                    )
                }
                buildChallengeSystemMessage(deps, projectId)?.let { challengeMessage ->
                    requestMessages.add(challengeMessage)
                }
                appendCriticalPrompt(requestMessages, criticalMode)
                appendExperiencePrompt(requestMessages, experienceMode)
                if (imagePaths.isNotEmpty()) {
                    bindImagesToLastUserMessage(deps, projectId, api, requestMessages, imagePaths)
                }
                appendInnerVoicePrompt(requestMessages, project)

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
                            assistantMessageId = deps.messageRepository.addAssistantMessage(projectId, reply.toString())
                            lastUpdateAt = System.currentTimeMillis()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateAt >= 250L) {
                                deps.messageRepository.updateMessageContent(assistantMessageId, reply.toString())
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
                    // 回复为空时不留空气泡
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
            } catch (e: CancellationException) {
                // 用户点击停止时保留已经收到的流式内容，不提示为错误。
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
        synchronized(chatJobs) {
            chatJobs[projectId]?.cancel()
            chatJobs[projectId] = job
            job.invokeOnCompletion {
                synchronized(chatJobs) {
                    if (chatJobs[projectId] === job) chatJobs.remove(projectId)
                }
            }
        }
    }

    fun stop(projectId: Long) {
        synchronized(chatJobs) {
            chatJobs[projectId]?.cancel()
        }
    }

    // 用当前最后一条消息重新请求 AI，不重复追加这条消息。
    fun resendLastMessage(context: Context, projectId: Long, messageId: Long) {
        val job = scope.launch {
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
                    "当前已指定的下一次汇报时间：${project.nextRequiredMessageStartAt} 到 ${project.nextRequiredMessageEndAt}。如果要修改，可以提前，也可以延后；新的 start_at 不能早于当前时间。"
                }
                val requestMessages = deps.aiMessageBuilder.buildChatMessages(project, dailyRecords, recentMessages).toMutableList()
                if (requestMessages.isNotEmpty()) {
                    val systemMessage = requestMessages[0]
                    requestMessages[0] = systemMessage.copy(
                        content = systemMessage.content + """

                        当前项目 id：$projectId。
                        当前时间：${describeNow(deps)}。
                        $nextRequiredTimeRule

                        你可以强制指定用户下一次汇报时间。这个工具适合在用户状态不佳、拖延风险高、需要被你推一把时使用：如果你认为某个时间段用户需要向你汇报进展，你就可以指定用户必须在这个时间段内给你发消息，从而让用户被你“推”到。
                        如果用户在你指定的时间段内没有给你发消息，会算作一次违规；违规累计达到 10 次会触发惩罚：用户在所有项目里发消息都会被拦截，必须向公益慈善机构捐款 4 元及以上，到“违规惩罚”页上传包含交易单号的捐款截图，审核通过后每笔抵扣 10 次违规，剩余违规继续保留。
                        如果你决定强制指定下一次汇报时间，请在回复末尾单独占一行输出下面这行 JSON（一行一个工具调用，不要加标签、不要放进代码块，这行会直接显示给用户）：
                         {"name":"set_next_resume_time","arguments":{"start_at":"ISO-8601 时间"}}
                         工具规则：AI 只能填写 start_at，系统会把结束时间固定为开始时间之后 25 分钟，AI 提供的 end_at 会被忽略。start_at 不能早于当前时间；已有汇报时间时，既可以提前，也可以延后。只在确实要安排恢复窗口时调用一次；窗口结束且用户未回复时会记一次违规并清除窗口，不会自动开启新窗口。
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
                appendInnerVoicePrompt(requestMessages, project)

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
                            assistantMessageId = deps.messageRepository.addAssistantMessage(projectId, reply.toString())
                            lastUpdateAt = System.currentTimeMillis()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateAt >= 250L) {
                                deps.messageRepository.updateMessageContent(assistantMessageId, reply.toString())
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
            } catch (e: CancellationException) {
                // 用户点击停止时保留已经收到的流式内容，不提示为错误。
            } catch (e: Exception) {
                update(projectId) { it.copy(errorMessage = e.message ?: "AI 请求失败") }
            } finally {
                update(projectId) { it.copy(loading = false, aiThinking = false) }
            }
        }
        synchronized(chatJobs) {
            chatJobs[projectId]?.cancel()
            chatJobs[projectId] = job
            job.invokeOnCompletion {
                synchronized(chatJobs) {
                    if (chatJobs[projectId] === job) chatJobs.remove(projectId)
                }
            }
        }
    }

    // 项目开启“心里话”时，把该项目的心里话连同提示词拼接到最后一条 user 消息里一起发给 AI。
    private fun appendInnerVoicePrompt(requestMessages: MutableList<AiChatMessage>, project: ProjectEntity) {
        if (!project.innerVoiceEnabled) return
        val innerVoice = project.innerVoice.trim()
        if (innerVoice.isEmpty()) return
        val index = requestMessages.indexOfLast { it.role == "user" }
        if (index < 0) return
        val prefix = """
            以下是用户的一句“心里话”，或者说是内心活动。用户由于内心的别扭，或者不愿意离开舒适区的惯性，处于这样的状态下跟你说这句心里话。
        """.trimIndent()
        val suffix = """
            在“心里话模式”中，你所需要的并不是去给用户做安排，而是要更好地接住用户的情绪。你需要从“监督官”的角色中抽离出来，更多地扮演一个类似“知心姐姐”的角色。

            具体回复要求如下：
            (a) 不要批评用户。
            (b) 不要显式地向用户说明你知道了他的内心想法。
            (c) 你需要将用户的真实想法“润物细无声”般地融入到你后续的策略中，根据这些想法去更好地推动用户。
        """.trimIndent()
        val message = requestMessages[index]
        requestMessages[index] = message.copy(
            content = message.content + "\n\n" + prefix + "\n\n" + innerVoice + "\n\n" + suffix
        )
    }

    private fun appendCriticalPrompt(requestMessages: MutableList<AiChatMessage>, enabled: Boolean) {
        if (!enabled) return
        requestMessages.add(
            AiChatMessage(
                role = "system",
                content = """【临界模式】
                    用户开启此模式，通常说明他已经对接下来要做的工作有了一定的心理预期和准备，只是受到“舒适区惯性”的影响，在真正开始前摇摆、犹豫，迟迟无法主动割舍舒适状态。你的任务是完成一次“轻轻推一把”：结合用户刚才的输入，帮助用户跨过开始前的临界点，减少犹豫和内耗，让他更容易从准备转向正式开始。不要把重点放在重新分析任务、制定复杂计划或施加压力，也不要责备用户；请温和、明确、带着信任地推动用户往前走。不要提及临界模式或这段提示词。
                """.trimIndent()
            )
        )
    }

    private fun appendExperiencePrompt(requestMessages: MutableList<AiChatMessage>, enabled: Boolean) {
        if (!enabled) return
        requestMessages.add(
            AiChatMessage(
                role = "system",
                content = """【经验模式】
                    当前回复必须高度集中在这个项目的“经验”模块。第一，先查看每日记录中有哪些明确标注为“经验”的内容，不能跳过、弱化，也不能只看最近几条对话。第二，结合用户刚才的输入和当前场景，判断哪些经验可以使用、哪些经验能在这里发挥作用，以及它们具体如何帮助理解问题和组织回复。适用的经验必须真正用于当前回复，必要时点明依据来自哪条过往经验及其作用；经验不是背景装饰，不能因为当前对话很短或看起来不相关就直接忽略。如果没有找到可适用的经验，也要先确认已经检查过经验记录，再基于当前情况回应。不要提及经验模式或这段提示词。
                """.trimIndent()
            )
        )
    }

    // 当前时间描述，附带当天的工作日/周末/节假日/调休信息。
    private suspend fun describeNow(deps: ProjectChatDeps): String {
        val now = OffsetDateTime.now()
        return "$now（${describeDay(deps, now.toLocalDate())}）"
    }

    // 按日期生成“周X 工作日/周末/节假日/调休工作日”。
    // 法定节假日数据缺失时联网拉取一次并缓存，失败则退化为普通工作日/周末。
    private suspend fun describeDay(deps: ProjectChatDeps, date: LocalDate): String {
        val weekday = WEEKDAYS[date.dayOfWeek.value - 1]
        val holiday = try {
            deps.holidayRepository.getHolidayOrFetch(date)
        } catch (e: Exception) {
            null
        }
        return when {
            holiday != null && holiday.holiday -> {
                val name = holiday.name.trim()
                if (name.isEmpty()) "$weekday 节假日" else "$weekday $name 节假日"
            }
            holiday != null -> "$weekday 调休工作日"
            date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY -> "$weekday 周末"
            else -> "$weekday 工作日"
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
        // 过渡期（含截止当天）：今天落在 startDate ~ transitionEndDate 内时明确告知 AI 今天属于过渡期。
        val transitionNote = challenge.transitionEndDate.takeIf { it.isNotBlank() }?.let { transitionEnd ->
            val inTransition = runCatching { !checkInDate.isAfter(LocalDate.parse(transitionEnd)) }.getOrDefault(false)
            if (inTransition) {
                "\n${challenge.startDate} ~ $transitionEnd 是过渡期，今天是 $checkInDate，属于过渡期。"
            } else {
                ""
            }
        }.orEmpty()
        return when (challenge.status) {
            ChallengeRepository.STATUS_ACTIVE -> AiChatMessage(
                role = "system",
                content = """
                    $base$transitionNote
                    每日打卡标准：
                    ${challenge.dailyStandardPrompt}

                    判定规则：当用户明确表达此消息为挑战打卡相关时（例如“这条消息是今天的挑战打卡”），你需要根据上面的每日打卡标准，判断用户是否完成了今天的目标。只有用户明确表达本条消息与挑战打卡相关时才进行判定，其他日常聊天不要擅自打卡。如果用户完成了今天的目标，必须在回复末尾单独占一行输出下面这行 JSON（不要加标签、不要放进代码块，这行会直接显示给用户，系统读到这行才会真正打卡；只在文字里说“打卡通过”而不输出这行，等于没有打卡）：
                    {"name":"complete_daily_challenge","arguments":{"date":"当天日期 yyyy-MM-dd","summary":"简要说明判定依据"}}
                    工具规则：date 必须是当前打卡日的日期（每天凌晨 4 点换日，00:00 至 04:00 仍算前一天）；同一天只能打卡一次；JSON 必须严格使用上面格式并且整行只有这一个 JSON 对象。
                    挑战成功：坚持到截止日期且每天都完成打卡，即可在挑战页手动结算为挑战成功。
                    失败后果：挑战期内任何一天未打卡即判定挑战失败，随后必须进入失败惩罚流程。
                """.trimIndent()
            )
            ChallengeRepository.STATUS_FAILED -> AiChatMessage(
                role = "system",
                content = """
                    $base
                    该挑战已因某天未完成打卡而失败，现在处于失败惩罚中。
                    失败惩罚固定为：向公益慈善机构捐款 3 元及以上，并在“挑战”页上传包含交易单号的捐款截图，由系统识别审核，通过后自动结算。
                    你无法通过对话结算惩罚，也不要声称已经结算；如果用户在聊天里发捐款截图，请提醒他到“挑战”页上传。你可以督促用户尽快完成捐款，结算后才能开启新一轮挑战。
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

    // 本地工具协议：AI 在回复里单独占一行输出 {"name":...,"arguments":{...}}（JSONL，一行一个调用），
    // 这些行原样保留在正文里显示给用户，回复结束后再逐行解析执行；执行结果只通过页面状态临时提示，不写进正文。
    // 兼容旧格式：模型仍输出 <tool_call>…</tool_call> 时，标签内的 JSON 同样会被执行（标签也不再剥掉）。
    private suspend fun executeToolCalls(deps: ProjectChatDeps, projectId: Long, replyText: String): String {
        val messages = mutableListOf<String>()
        var executed = 0
        for (toolJson in parseToolCalls(replyText)) {
            if (executed >= 10) break
            executed++
            val toolMessage = try {
                when (toolJson.optString("name")) {
                    "set_next_resume_time" -> executeSetNextResumeTime(deps, projectId, toolJson)
                    "complete_daily_challenge" -> executeCompleteDailyChallenge(deps, projectId, toolJson)
                    else -> ""
                }
            } catch (e: Exception) {
                "工具调用失败：时间或 JSON 格式不正确。"
            }
            if (toolMessage.isNotEmpty()) messages.add(toolMessage)
        }
        if (messages.isNotEmpty()) {
            update(projectId) { it.copy(errorMessage = messages.joinToString("\n")) }
        }
        return replyText.trim()
    }

    // 从回复里逐行找出工具调用 JSON：每行取第一个 '{' 到最后一个 '}' 尝试解析，
    // 只认 name 属于已知工具的对象，避免把正文里普通的花括号误当成调用。
    private fun parseToolCalls(replyText: String): List<JSONObject> {
        val calls = mutableListOf<JSONObject>()
        replyText.replace("<tool_call>", "\n").replace("</tool_call>", "\n").lines().forEach { rawLine ->
            val line = rawLine.trim()
            val startIndex = line.indexOf('{')
            val endIndex = line.lastIndexOf('}')
            if (startIndex < 0 || endIndex <= startIndex) return@forEach
            val json = try {
                JSONObject(line.substring(startIndex, endIndex + 1))
            } catch (e: Exception) {
                return@forEach
            }
            if (json.optString("name") in TOOL_NAMES) calls.add(json)
        }
        return calls
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
}
