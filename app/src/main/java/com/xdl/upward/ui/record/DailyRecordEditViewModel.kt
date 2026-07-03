package com.xdl.upward.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.repository.DailyRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DailyRecordEditViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DailyRecordRepository(
        AppDatabase.getInstance(application).dailyRecordDao()
    )

    private val _date = MutableStateFlow(repository.todayText())
    val date: StateFlow<String> = _date.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private var loadedRecordId = 0L

    fun loadRecord(recordId: Long) {
        if (recordId == 0L || loadedRecordId == recordId) return
        loadedRecordId = recordId
        viewModelScope.launch {
            val record = repository.getById(recordId) ?: return@launch
            _date.value = record.date
            _content.value = record.content
        }
    }

    fun updateDate(value: String) {
        _date.value = value
    }

    fun updateContent(value: String) {
        _content.value = value
    }

    fun save(recordId: Long, projectId: Long, onSaved: () -> Unit) {
        val finalDate = _date.value.trim()
        val finalContent = _content.value.trim()
        if (finalDate.isEmpty() || finalContent.isEmpty()) return

        viewModelScope.launch {
            repository.saveRecord(recordId, projectId, finalDate, finalContent)
            onSaved()
        }
    }
}
