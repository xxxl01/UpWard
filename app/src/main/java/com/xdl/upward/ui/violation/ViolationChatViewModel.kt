package com.xdl.upward.ui.violation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.remote.AiApiService
import com.xdl.upward.data.repository.AiApiRepository
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.data.repository.DailyRecordRepository
import com.xdl.upward.data.repository.MessageRepository
import com.xdl.upward.data.repository.ProjectRepository
import com.xdl.upward.domain.AiChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class ProjectLastMessageTime(
    val projectName: String,
    val lastMessageTime: String
)

class ViolationChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val configRepository = ConfigRepository(database.configDao())
    private val projectRepository = ProjectRepository(database.projectDao())
    private val messageRepository = MessageRepository(database.messageDao())
    private val dailyRecordRepository = DailyRecordRepository(database.dailyRecordDao())
    private val aiApiRepository = AiApiRepository(database.aiApiDao())
    private val aiApiService = AiApiService()

    private val _violationCount = MutableStateFlow(0)
    val violationCount: StateFlow<Int> = _violationCount.asStateFlow()

    private val _messages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _projectLastMessageTimes = MutableStateFlow<List<ProjectLastMessageTime>>(emptyList())
    val projectLastMessageTimes: StateFlow<List<ProjectLastMessageTime>> = _projectLastMessageTimes.asStateFlow()

    private var initialWarningSent = false

    init {
        viewModelScope.launch {
            configRepository.observeViolationCount().collect { value ->
                _violationCount.value = value?.toIntOrNull() ?: 0
            }
        }
        loadProjectLastMessageTimes()
    }

    fun updateInput(value: String) {
        _input.value = value
    }

    fun sendInitialWarning() {
        val count = _violationCount.value
        if (count <= 0 || initialWarningSent || _loading.value) return

        initialWarningSent = true
        sendMessage("我现在已经累计违规 ${count} 次，请认真告诫我。", showUserMessage = false)
    }

    fun sendUserMessage() {
        val content = _input.value.trim()
        if (content.isEmpty() || _loading.value) return

        _input.value = ""
        sendMessage(content, showUserMessage = true)
    }

    fun loadProjectLastMessageTimes() {
        viewModelScope.launch {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val projects = projectRepository.getProjects()
            val items = projects.map { project ->
                val lastMessage = messageRepository.getLastMessage(project.id)
                val timeText = if (lastMessage == null) {
                    "暂无聊天消息"
                } else {
                    try {
                        OffsetDateTime.parse(lastMessage.createdAt).format(formatter)
                    } catch (e: Exception) {
                        lastMessage.createdAt
                    }
                }
                ProjectLastMessageTime(
                    projectName = project.name,
                    lastMessageTime = timeText
                )
            }
            _projectLastMessageTimes.value = items
        }
    }

    private fun sendMessage(content: String, showUserMessage: Boolean) {
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
                    oldMessages + AiChatMessage("user", content)
                } else {
                    oldMessages
                }
                _messages.value = visibleMessages

                val projects = projectRepository.getProjects()
                val projectContext = StringBuilder()
                if (projects.isEmpty()) {
                    projectContext.append("当前没有项目。")
                } else {
                    projects.forEach { project ->
                        projectContext.append("项目：${project.name}\n")
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
                    违规定义：以自然日 24 点为准，昨天任一项目没有聊天消息记录，就记为一次违规；违规达到 3 次会清空这个 App 的全部数据和所有积累过程。
                    当前违规次数：${_violationCount.value}/3。

                    当前项目和每日记录：
                    $projectContext

                    请用中文回应。语气要晓之以理、动之以情，真诚、具体、有压力感，但不要羞辱用户。
                    你要提醒用户珍惜已经积累的过程，说明继续拖延的代价，并给出今天马上恢复打卡的简短行动建议。
                """.trimIndent()
                val requestMessages = mutableListOf(AiChatMessage("system", systemPrompt))
                oldMessages.forEach { message ->
                    requestMessages.add(message)
                }
                requestMessages.add(AiChatMessage("user", "[消息发送时间: $nowText]\n$content"))

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
                            _messages.value = visibleMessages + AiChatMessage("assistant", reply.toString())
                            lastUpdateAt = now
                        }
                    },
                    onReasoningDelta = {}
                )
                if (finalReply.isNotEmpty()) {
                    _messages.value = visibleMessages + AiChatMessage("assistant", finalReply)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "AI 请求失败"
            } finally {
                _loading.value = false
            }
        }
    }
}
