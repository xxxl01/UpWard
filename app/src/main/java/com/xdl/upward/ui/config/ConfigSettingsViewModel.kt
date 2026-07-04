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

class ConfigSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConfigRepository(
        AppDatabase.getInstance(application).configDao()
    )
    private val aiApiRepository = AiApiRepository(
        AppDatabase.getInstance(application).aiApiDao()
    )

    val apis: Flow<List<AiApiEntity>> = aiApiRepository.observeAiApis()

    private val _messageContextCount = MutableStateFlow("20")
    val messageContextCount: StateFlow<String> = _messageContextCount.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            _messageContextCount.value = repository.getMessageContextCount().toString()
        }
    }

    fun updateMessageContextCount(value: String) {
        _messageContextCount.value = value
        _errorMessage.value = ""
    }

    fun save(onSaved: () -> Unit) {
        val count = _messageContextCount.value.trim().toIntOrNull()
        if (count == null || count <= 0) {
            _errorMessage.value = "上下文消息数量必须是大于 0 的整数"
            return
        }

        viewModelScope.launch {
            repository.setValue("message_context_count", count.toString())
            onSaved()
        }
    }

    fun selectApi(id: Long) {
        viewModelScope.launch {
            aiApiRepository.selectApi(id)
        }
    }
}
