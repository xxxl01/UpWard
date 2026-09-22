package com.xdl.upward.ui.config

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AiApiEntity
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.repository.AiApiRepository
import com.xdl.upward.data.repository.BackupRepository
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.reminder.GlobalReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConfigSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = ConfigRepository(
        database.configDao()
    )
    private val aiApiRepository = AiApiRepository(
        database.aiApiDao()
    )
    private val backupRepository = BackupRepository(
        application.applicationContext,
        database
    )

    val apis: Flow<List<AiApiEntity>> = aiApiRepository.observeAiApis()

    private val _messageContextCount = MutableStateFlow("20")
    val messageContextCount: StateFlow<String> = _messageContextCount.asStateFlow()

    private val _contextSummaryPrompt = MutableStateFlow(ConfigRepository.DEFAULT_CONTEXT_SUMMARY_PROMPT)
    val contextSummaryPrompt: StateFlow<String> = _contextSummaryPrompt.asStateFlow()

    private val _contextSummaryApiId = MutableStateFlow<Long?>(null)
    val contextSummaryApiId: StateFlow<Long?> = _contextSummaryApiId.asStateFlow()

    private val _globalMorningReminderTime = MutableStateFlow("07:00")
    val globalMorningReminderTime: StateFlow<String> = _globalMorningReminderTime.asStateFlow()

    private val _globalAfternoonReminderTime = MutableStateFlow("13:00")
    val globalAfternoonReminderTime: StateFlow<String> = _globalAfternoonReminderTime.asStateFlow()

    private val _globalEveningReminderTime = MutableStateFlow("19:30")
    val globalEveningReminderTime: StateFlow<String> = _globalEveningReminderTime.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _backupMessage = MutableStateFlow("")
    val backupMessage: StateFlow<String> = _backupMessage.asStateFlow()

    private val _backupRunning = MutableStateFlow(false)
    val backupRunning: StateFlow<Boolean> = _backupRunning.asStateFlow()

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            _messageContextCount.value = repository.getMessageContextCount().toString()
            _contextSummaryPrompt.value = repository.getContextSummaryPrompt()
            _contextSummaryApiId.value = repository.getValue("context_summary_api_id")?.toLongOrNull()
            _globalMorningReminderTime.value = repository.getValue("global_reminder_morning_time") ?: "07:00"
            _globalAfternoonReminderTime.value = repository.getValue("global_reminder_afternoon_time") ?: "13:00"
            _globalEveningReminderTime.value = repository.getValue("global_reminder_evening_time") ?: "19:30"
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

    fun updateGlobalReminderTime(period: String, hour: Int, minute: Int) {
        val value = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        when (period) {
            "morning" -> _globalMorningReminderTime.value = value
            "afternoon" -> _globalAfternoonReminderTime.value = value
            "evening" -> _globalEveningReminderTime.value = value
        }
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
            repository.setValue("global_reminder_morning_time", _globalMorningReminderTime.value)
            repository.setValue("global_reminder_afternoon_time", _globalAfternoonReminderTime.value)
            repository.setValue("global_reminder_evening_time", _globalEveningReminderTime.value)
            repository.setValue("global_reminder_stopped_morning_date", "")
            repository.setValue("global_reminder_stopped_afternoon_date", "")
            repository.setValue("global_reminder_stopped_evening_date", "")
            GlobalReminderScheduler.cancelAll(getApplication())
            GlobalReminderScheduler.scheduleAll(
                getApplication(),
                _globalMorningReminderTime.value,
                _globalAfternoonReminderTime.value,
                _globalEveningReminderTime.value
            )
            onSaved()
        }
    }

    fun selectApi(id: Long) {
        viewModelScope.launch {
            aiApiRepository.selectApi(id)
        }
    }

    fun exportBackup(uri: Uri) {
        if (_backupRunning.value) return
        _backupRunning.value = true
        _backupMessage.value = "正在导出备份..."
        viewModelScope.launch {
            try {
                backupRepository.exportTo(uri)
                _backupMessage.value = "全量备份已导出"
            } catch (e: Exception) {
                _backupMessage.value = e.message ?: "导出失败"
            } finally {
                _backupRunning.value = false
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        if (_backupRunning.value) return
        _backupRunning.value = true
        _backupMessage.value = "正在恢复备份..."
        viewModelScope.launch {
            try {
                backupRepository.restoreFrom(uri)
                loaded = false
                load()
                _backupMessage.value = "全量备份已恢复"
            } catch (e: Exception) {
                _backupMessage.value = e.message ?: "恢复失败"
            } finally {
                _backupRunning.value = false
            }
        }
    }
}
