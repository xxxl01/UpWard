package com.xdl.upward.ui.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.reminder.GlobalReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReminderSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = ConfigRepository(database.configDao())

    private val _globalMorningReminderTime = MutableStateFlow("07:00")
    val globalMorningReminderTime: StateFlow<String> = _globalMorningReminderTime.asStateFlow()

    private val _globalAfternoonReminderTime = MutableStateFlow("13:00")
    val globalAfternoonReminderTime: StateFlow<String> = _globalAfternoonReminderTime.asStateFlow()

    private val _globalEveningReminderTime = MutableStateFlow("19:30")
    val globalEveningReminderTime: StateFlow<String> = _globalEveningReminderTime.asStateFlow()

    private val _globalMorningReminderEnabled = MutableStateFlow(true)
    val globalMorningReminderEnabled: StateFlow<Boolean> = _globalMorningReminderEnabled.asStateFlow()

    private val _globalAfternoonReminderEnabled = MutableStateFlow(true)
    val globalAfternoonReminderEnabled: StateFlow<Boolean> = _globalAfternoonReminderEnabled.asStateFlow()

    private val _globalEveningReminderEnabled = MutableStateFlow(true)
    val globalEveningReminderEnabled: StateFlow<Boolean> = _globalEveningReminderEnabled.asStateFlow()

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            _globalMorningReminderTime.value = repository.getValue("global_reminder_morning_time") ?: "07:00"
            _globalAfternoonReminderTime.value = repository.getValue("global_reminder_afternoon_time") ?: "13:00"
            _globalEveningReminderTime.value = repository.getValue("global_reminder_evening_time") ?: "19:30"
            _globalMorningReminderEnabled.value = repository.getValue("global_reminder_morning_enabled") != "0"
            _globalAfternoonReminderEnabled.value = repository.getValue("global_reminder_afternoon_enabled") != "0"
            _globalEveningReminderEnabled.value = repository.getValue("global_reminder_evening_enabled") != "0"
        }
    }

    fun updateGlobalReminderTime(period: String, hour: Int, minute: Int) {
        val value = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        when (period) {
            "morning" -> _globalMorningReminderTime.value = value
            "afternoon" -> _globalAfternoonReminderTime.value = value
            "evening" -> _globalEveningReminderTime.value = value
        }
    }

    fun updateGlobalReminderEnabled(period: String, enabled: Boolean) {
        when (period) {
            "morning" -> _globalMorningReminderEnabled.value = enabled
            "afternoon" -> _globalAfternoonReminderEnabled.value = enabled
            "evening" -> _globalEveningReminderEnabled.value = enabled
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.setValue("global_reminder_morning_time", _globalMorningReminderTime.value)
            repository.setValue("global_reminder_afternoon_time", _globalAfternoonReminderTime.value)
            repository.setValue("global_reminder_evening_time", _globalEveningReminderTime.value)
            repository.setValue("global_reminder_morning_enabled", if (_globalMorningReminderEnabled.value) "1" else "0")
            repository.setValue("global_reminder_afternoon_enabled", if (_globalAfternoonReminderEnabled.value) "1" else "0")
            repository.setValue("global_reminder_evening_enabled", if (_globalEveningReminderEnabled.value) "1" else "0")
            repository.setValue("global_reminder_stopped_morning_date", "")
            repository.setValue("global_reminder_stopped_afternoon_date", "")
            repository.setValue("global_reminder_stopped_evening_date", "")
            GlobalReminderScheduler.reschedule(getApplication())
            onSaved()
        }
    }
}
