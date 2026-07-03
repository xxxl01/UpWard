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
                val reply = aiApiService.chat(api, requestMessages)
                if (reply.isNotEmpty()) {
                    messageRepository.addAssistantMessage(projectId, reply)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "AI 请求失败"
            } finally {
                _loading.value = false
            }
        }
    }

    fun generateTodayRecord(projectId: Long) {
        if (_loading.value) return

        _errorMessage.value = ""
        viewModelScope.launch {
            try {
                _loading.value = true
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
                val prompt = configRepository.getDailyRecordPrompt()
                val dailyRecords = dailyRecordRepository.getAllByProjectId(projectId)
                val recentMessages = messageRepository.getRecentMessages(projectId, count)
                val requestMessages = aiMessageBuilder.buildDailyRecordMessages(
                    project = project,
                    dailyRecords = dailyRecords,
                    recentMessages = recentMessages,
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
            }
        }
    }
}
