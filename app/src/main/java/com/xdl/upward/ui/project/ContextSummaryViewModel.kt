package com.xdl.upward.ui.project

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.MessageEntity
import com.xdl.upward.data.remote.AiApiService
import com.xdl.upward.data.repository.AiApiRepository
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.data.repository.MessageRepository
import com.xdl.upward.domain.AiChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime

class ContextSummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val messageRepository = MessageRepository(database.messageDao())
    private val aiApiRepository = AiApiRepository(database.aiApiDao())
    private val configRepository = ConfigRepository(database.configDao())
    private val aiApiService = AiApiService()

    private val _startInput = MutableStateFlow(OffsetDateTime.now().toLocalDate().minusDays(1).toString())
    val startInput: StateFlow<String> = _startInput.asStateFlow()

    private val _endInput = MutableStateFlow(OffsetDateTime.now().toLocalDate().toString())
    val endInput: StateFlow<String> = _endInput.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _summary = MutableStateFlow("")
    val summary: StateFlow<String> = _summary.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private data class DaySummary(
        val date: LocalDate,
        val messages: List<MessageEntity>,
        val content: String
    )

    private var daySummaries = emptyList<DaySummary>()

    fun updateStartInput(value: String) {
        _startInput.value = value
        _errorMessage.value = ""
    }

    fun updateEndInput(value: String) {
        _endInput.value = value
        _errorMessage.value = ""
    }

    fun loadMessages(projectId: Long) {
        val range = parseRange() ?: return
        _summary.value = ""
        daySummaries = emptyList()
        viewModelScope.launch {
            _messages.value = messageRepository.getMessagesBetween(projectId, range.first, range.second)
            if (_messages.value.isEmpty()) {
                _errorMessage.value = "这个时间段没有消息"
            }
        }
    }

    fun summarize(projectId: Long) {
        if (_loading.value) return
        val range = parseRange() ?: return
        viewModelScope.launch {
            try {
                _loading.value = true
                _errorMessage.value = ""
                val messages = messageRepository.getMessagesBetween(projectId, range.first, range.second)
                _messages.value = messages
                if (messages.isEmpty()) {
                    _errorMessage.value = "这个时间段没有消息"
                    return@launch
                }
                val apiId = configRepository.getValue("context_summary_api_id")?.toLongOrNull()
                val api = if (apiId == null) null else aiApiRepository.getById(apiId)
                if (api == null) {
                    _errorMessage.value = "请先在设置中选择整理上下文使用的 API"
                    return@launch
                }
                val prompt = configRepository.getContextSummaryPrompt()
                val summaries = mutableListOf<DaySummary>()
                var date = LocalDate.parse(_startInput.value)
                val endDate = LocalDate.parse(_endInput.value)
                while (!date.isAfter(endDate)) {
                    val dayStart = date.atStartOfDay().atOffset(OffsetDateTime.now().offset).toString()
                    val dayEnd = date.plusDays(1).atStartOfDay().atOffset(OffsetDateTime.now().offset).toString()
                    val dayMessages = messageRepository.getMessagesBetween(projectId, dayStart, dayEnd)
                    if (dayMessages.isNotEmpty()) {
                        val messageText = dayMessages.joinToString("\n\n") { message ->
                            val roleName = if (message.role == "user") "用户" else "AI"
                            "[$roleName ${message.createdAt}]\n${message.content}"
                        }
                        val content = aiApiService.chat(
                            api = api,
                            messages = listOf(
                                AiChatMessage("system", prompt),
                                AiChatMessage("user", "整理日期：$date\n\n原始消息：\n$messageText")
                            )
                        ).trim()
                        if (content.isNotEmpty()) {
                            summaries += DaySummary(date, dayMessages, content)
                        }
                    }
                    date = date.plusDays(1)
                }
                daySummaries = summaries
                _summary.value = summaries.joinToString("\n\n") { "【${it.date}】\n${it.content}" }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "整理失败"
            } finally {
                _loading.value = false
            }
        }
    }

    fun applySummary(projectId: Long, onApplied: () -> Unit) {
        if (_summary.value.trim().isEmpty()) {
            _errorMessage.value = "请先整理出总结"
            return
        }
        if (daySummaries.isEmpty()) {
            _errorMessage.value = "没有可替换的消息"
            return
        }
        viewModelScope.launch {
            daySummaries.forEach { summary ->
                val offset = OffsetDateTime.now().offset
                messageRepository.replaceMessagesWithSummary(
                    projectId = projectId,
                    startTime = summary.date.atStartOfDay().atOffset(offset).toString(),
                    endTime = summary.date.plusDays(1).atStartOfDay().atOffset(offset).toString(),
                    content = summary.content,
                    createdAt = summary.messages.last().createdAt
                )
            }
            onApplied()
        }
    }

    private fun parseRange(): Pair<String, String>? {
        val startDate = try {
            LocalDate.parse(_startInput.value)
        } catch (e: Exception) {
            null
        }
        if (startDate == null) {
            _errorMessage.value = "请选择开始日期"
            return null
        }
        val endDate = try {
            LocalDate.parse(_endInput.value)
        } catch (e: Exception) {
            null
        }
        if (endDate == null) {
            _errorMessage.value = "请选择结束日期"
            return null
        }
        if (endDate.isBefore(startDate)) {
            _errorMessage.value = "结束日期不能早于开始日期"
            return null
        }
        _errorMessage.value = ""
        val offset = OffsetDateTime.now().offset
        return startDate.atStartOfDay().atOffset(offset).toString() to endDate.plusDays(1).atStartOfDay().atOffset(offset).toString()
    }
}
