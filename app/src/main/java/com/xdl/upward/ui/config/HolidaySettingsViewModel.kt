package com.xdl.upward.ui.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.repository.HolidayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class HolidaySettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HolidayRepository(AppDatabase.getInstance(application).holidayDao())

    private val _yearCounts = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val yearCounts: StateFlow<Map<Int, Int>> = _yearCounts.asStateFlow()

    private val _updatingYears = MutableStateFlow<Set<Int>>(emptySet())
    val updatingYears: StateFlow<Set<Int>> = _updatingYears.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            refreshCounts()
        }
    }

    private suspend fun refreshCounts() {
        val currentYear = LocalDate.now().year
        _yearCounts.value = listOf(currentYear, currentYear + 1).associateWith { repository.getCachedCount(it) }
    }

    fun refresh(year: Int) {
        if (_updatingYears.value.contains(year)) return
        _updatingYears.value = _updatingYears.value + year
        _message.value = ""
        viewModelScope.launch {
            try {
                repository.refreshYear(year)
                refreshCounts()
                _message.value = "${year} 年节假日数据已更新"
            } catch (e: Exception) {
                _message.value = "${year} 年更新失败：${e.message ?: "未知错误"}"
            } finally {
                _updatingYears.value = _updatingYears.value - year
            }
        }
    }
}
