package com.xdl.upward.ui.project

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.MessageEntity
import com.xdl.upward.data.local.ProjectEntity
import com.xdl.upward.data.remote.AiApiService
import com.xdl.upward.data.repository.AiApiRepository
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.data.repository.DailyRecordRepository
import com.xdl.upward.data.repository.MessageRepository
import com.xdl.upward.data.repository.ProjectRepository
import com.xdl.upward.domain.AiMessageBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ProjectDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProjectRepository(
        AppDatabase.getInstance(application).projectDao()
    )
    private val dailyRecordRepository = DailyRecordRepository(
        AppDatabase.getInstance(application).dailyRecordDao()
    )
    private val messageRepository = MessageRepository(
        AppDatabase.getInstance(application).messageDao()
    )
    private val aiApiRepository = AiApiRepository(
        AppDatabase.getInstance(application).aiApiDao()
    )
    private val configRepository = ConfigRepository(
        AppDatabase.getInstance(application).configDao()
    )
    private val aiApiService = AiApiService()
    private val aiMessageBuilder = AiMessageBuilder()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _aiThinking = MutableStateFlow(false)
    val aiThinking: StateFlow<Boolean> = _aiThinking.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    fun observeProject(projectId: Long): Flow<ProjectEntity?> = repository.observeProject(projectId)

    fun observeMessages(projectId: Long): Flow<List<MessageEntity>> {
        return messageRepository.observeMessages(projectId)
    }

    fun updateInput(value: String) {
        _input.value = value
    }

    fun sendUserMessage(projectId: Long) {
        val content = _input.value.trim()
        if (content.isEmpty() || _loading.value) return

        _input.value = ""
        _errorMessage.value = ""
        viewModelScope.launch {
            try {
                _loading.value = true
                _aiThinking.value = false
                messageRepository.addUserMessage(projectId, content)

                val project = repository.getProject(projectId)
                if (project == null) {
                    _errorMessage.value = "项目不存在"
                    return@launch
                }

                val api = aiApiRepository.getSelected()
                if (api == null) {
                    _errorMessage.value = "请先在 API 设置中选择一个 API"
                    return@launch
                }

                val count = configRepository.getMessageContextCount()
                val dailyRecords = dailyRecordRepository.getAllByProjectId(projectId)
                val recentMessages = messageRepository.getRecentMessages(projectId, count)
                val requestMessages = aiMessageBuilder.buildChatMessages(project, dailyRecords, recentMessages)

                var assistantMessageId = 0L
                val reply = StringBuilder()
                var lastUpdateAt = 0L
                val finalReply = aiApiService.streamChat(
                    api = api,
                    messages = requestMessages,
                    onContentDelta = { delta ->
                        _aiThinking.value = false
                        reply.append(delta)
                        if (assistantMessageId == 0L) {
                            assistantMessageId = messageRepository.addAssistantMessage(projectId, reply.toString())
                            lastUpdateAt = System.currentTimeMillis()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateAt >= 250L) {
                                messageRepository.updateMessageContent(assistantMessageId, reply.toString())
                                lastUpdateAt = now
                            }
                        }
                    },
                    onReasoningDelta = {
                        if (reply.isEmpty()) {
                            _aiThinking.value = true
                        }
                    }
                )
                if (assistantMessageId != 0L) {
                    messageRepository.updateMessageContent(assistantMessageId, finalReply)
                } else if (finalReply.isNotEmpty()) {
                    messageRepository.addAssistantMessage(projectId, finalReply)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "AI 请求失败"
            } finally {
                _loading.value = false
                _aiThinking.value = false
            }
        }
    }

    fun generateTodayRecord(projectId: Long) {
        if (_loading.value) return

        _errorMessage.value = ""
        viewModelScope.launch {
            try {
                _loading.value = true
                _aiThinking.value = false
                val project = repository.getProject(projectId)
                if (project == null) {
                    _errorMessage.value = "项目不存在"
                    return@launch
                }

                val prompt = project.dailyRecordPrompt
                val dailyRecords = dailyRecordRepository.getAllByProjectId(projectId)
                val todayMessages = messageRepository.getTodayMessages(projectId)
                if (todayMessages.isEmpty()) {
                    dailyRecordRepository.saveTodayRecord(
                        projectId = projectId,
                        content = "${dailyRecordRepository.todayDisplayText()} 没有数据"
                    )
                    return@launch
                }

                val api = aiApiRepository.getSelected()
                if (api == null) {
                    _errorMessage.value = "请先在 API 设置中选择一个 API"
                    return@launch
                }

                val requestMessages = aiMessageBuilder.buildDailyRecordMessages(
                    project = project,
                    dailyRecords = dailyRecords,
                    recentMessages = todayMessages,
                    prompt = prompt
                )
                val recordContent = aiApiService.chat(api, requestMessages)
                if (recordContent.isNotEmpty()) {
                    dailyRecordRepository.saveTodayRecord(projectId, recordContent)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "生成今日记录失败"
            } finally {
                _loading.value = false
                _aiThinking.value = false
            }
        }
    }

    fun updateMessage(messageId: Long, content: String) {
        val newContent = content.trim()
        if (newContent.isEmpty()) return

        viewModelScope.launch {
            messageRepository.updateMessageContent(messageId, newContent)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            messageRepository.deleteMessage(messageId)
        }
    }

    fun importMessages(projectId: Long, text: String) {
        _errorMessage.value = ""
        viewModelScope.launch {
            try {
                val trimmedText = text.trim()
                if (trimmedText.isEmpty()) {
                    _errorMessage.value = "导入失败：文件内容为空"
                    return@launch
                }

                val messageArray = if (trimmedText.startsWith("[")) {
                    JSONArray(trimmedText)
                } else {
                    val root = JSONObject(trimmedText)
                    root.getJSONArray("messages")
                }

                val importedMessages = mutableListOf<MessageEntity>()
                for (index in 0 until messageArray.length()) {
                    val item = messageArray.getJSONObject(index)
                    val role = item.optString("role", "").trim()
                    val content = item.optString("content", "").trim()
                    val createdAt = if (item.has("createdAt") && !item.isNull("createdAt")) {
                        item.getString("createdAt").trim()
                    } else {
                        ""
                    }

                    if (role != "user" && role != "assistant") {
                        _errorMessage.value = "导入失败：第 ${index + 1} 条消息 role 只能是 user 或 assistant"
                        return@launch
                    }
                    if (content.isEmpty()) {
                        _errorMessage.value = "导入失败：第 ${index + 1} 条消息内容为空"
                        return@launch
                    }

                    importedMessages.add(
                        MessageEntity(
                            projectId = projectId,
                            role = role,
                            content = content,
                            createdAt = createdAt
                        )
                    )
                }

                if (importedMessages.isEmpty()) {
                    _errorMessage.value = "导入失败：没有可导入的消息"
                    return@launch
                }

                messageRepository.importMessages(importedMessages)
            } catch (e: Exception) {
                _errorMessage.value = "导入失败：${e.message ?: "文件格式不正确"}"
            }
        }
    }
}
