package com.xdl.upward.ui.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AiApiEntity
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.repository.AiApiRepository
import com.xdl.upward.data.repository.ConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GeneralSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = ConfigRepository(database.configDao())
    private val aiApiRepository = AiApiRepository(database.aiApiDao())

    val apis: Flow<List<AiApiEntity>> = aiApiRepository.observeAiApis()

    private val _messageContextCount = MutableStateFlow("20")
    val messageContextCount: StateFlow<String> = _messageContextCount.asStateFlow()

    private val _contextSummaryPrompt = MutableStateFlow(ConfigRepository.DEFAULT_CONTEXT_SUMMARY_PROMPT)
    val contextSummaryPrompt: StateFlow<String> = _contextSummaryPrompt.asStateFlow()

    private val _contextSummaryApiId = MutableStateFlow<Long?>(null)
    val contextSummaryApiId: StateFlow<Long?> = _contextSummaryApiId.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            _messageContextCount.value = repository.getMessageContextCount().toString()
            _contextSummaryPrompt.value = repository.getContextSummaryPrompt()
            _contextSummaryApiId.value = repository.getValue("context_summary_api_id")?.toLongOrNull()
        }
    }

    fun updateMessageContextCount(value: String) {
        _messageContextCount.value = value
        _errorMessage.value = ""
    }

    fun updateContextSummaryPrompt(value: String) {
        _contextSummaryPrompt.value = value
        _errorMessage.value = ""
    }

    fun selectContextSummaryApi(id: Long) {
        _contextSummaryApiId.value = id
        _errorMessage.value = ""
    }

    fun save(onSaved: () -> Unit) {
        val count = _messageContextCount.value.trim().toIntOrNull()
        if (count == null || count <= 0) {
            _errorMessage.value = "上下文消息数量必须是大于 0 的整数"
            return
        }
        val prompt = _contextSummaryPrompt.value.trim()
        if (prompt.isEmpty()) {
            _errorMessage.value = "整理提示词不能为空"
            return
        }
        viewModelScope.launch {
            repository.setValue("message_context_count", count.toString())
            repository.setValue("context_summary_prompt", prompt)
            repository.setValue("context_summary_api_id", _contextSummaryApiId.value?.toString() ?: "")
            onSaved()
        }
    }
}
