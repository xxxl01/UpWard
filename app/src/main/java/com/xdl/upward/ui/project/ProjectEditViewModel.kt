package com.xdl.upward.ui.project

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProjectEditViewModel(application: Application) : AndroidViewModel(application) {
    private val defaultDailyRecordPrompt = "请根据今天的对话，总结用户在本项目中的进展、问题、情绪状态和下一步建议。"

    private val repository = ProjectRepository(
        AppDatabase.getInstance(application).projectDao()
    )

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _dailyRecordPrompt = MutableStateFlow(defaultDailyRecordPrompt)
    val dailyRecordPrompt: StateFlow<String> = _dailyRecordPrompt.asStateFlow()

    private var loadedProjectId = 0L

    fun loadProject(projectId: Long) {
        if (projectId == 0L || loadedProjectId == projectId) return
        loadedProjectId = projectId
        viewModelScope.launch {
            val project = repository.getProject(projectId) ?: return@launch
            _name.value = project.name
            _systemPrompt.value = project.systemPrompt
            _dailyRecordPrompt.value = project.dailyRecordPrompt
        }
    }

    fun updateName(value: String) {
        _name.value = value
    }

    fun updateSystemPrompt(value: String) {
        _systemPrompt.value = value
    }

    fun updateDailyRecordPrompt(value: String) {
        _dailyRecordPrompt.value = value
    }

    fun save(projectId: Long, onSaved: () -> Unit) {
        val finalName = _name.value.trim()
        val finalSystemPrompt = _systemPrompt.value.trim()
        val finalDailyRecordPrompt = _dailyRecordPrompt.value.trim()
        if (finalName.isEmpty() || finalSystemPrompt.isEmpty() || finalDailyRecordPrompt.isEmpty()) return

        viewModelScope.launch {
            repository.saveProject(projectId, finalName, finalSystemPrompt, finalDailyRecordPrompt)
            onSaved()
        }
    }
}
