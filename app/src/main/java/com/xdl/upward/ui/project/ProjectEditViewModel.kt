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
    private val repository = ProjectRepository(
        AppDatabase.getInstance(application).projectDao()
    )

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private var loadedProjectId = 0L

    fun loadProject(projectId: Long) {
        if (projectId == 0L || loadedProjectId == projectId) return
        loadedProjectId = projectId
        viewModelScope.launch {
            val project = repository.getProject(projectId) ?: return@launch
            _name.value = project.name
            _systemPrompt.value = project.systemPrompt
        }
    }

    fun updateName(value: String) {
        _name.value = value
    }

    fun updateSystemPrompt(value: String) {
        _systemPrompt.value = value
    }

    fun save(projectId: Long, onSaved: () -> Unit) {
        val finalName = _name.value.trim()
        val finalSystemPrompt = _systemPrompt.value.trim()
        if (finalName.isEmpty() || finalSystemPrompt.isEmpty()) return

        viewModelScope.launch {
            repository.saveProject(projectId, finalName, finalSystemPrompt)
            onSaved()
        }
    }
}
