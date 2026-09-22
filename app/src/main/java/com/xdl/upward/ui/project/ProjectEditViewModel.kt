package com.xdl.upward.ui.project

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

class ProjectEditViewModel(application: Application) : AndroidViewModel(application) {
    private val defaultDailyRecordPrompt = "请根据今天的对话，总结用户在本项目中的进展、问题、情绪状态和下一步建议。"

    private val database = AppDatabase.getInstance(application)
    private val repository = ProjectRepository(database.projectDao())

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _dailyRecordPrompt = MutableStateFlow(defaultDailyRecordPrompt)
    val dailyRecordPrompt: StateFlow<String> = _dailyRecordPrompt.asStateFlow()

    private val _checkInMode = MutableStateFlow("once_per_day")
    val checkInMode: StateFlow<String> = _checkInMode.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    private var loadedProjectId = 0L

    fun loadProject(projectId: Long) {
        if (projectId == 0L || loadedProjectId == projectId) return
        loadedProjectId = projectId
        viewModelScope.launch {
            val project = repository.getProject(projectId) ?: return@launch
            _name.value = project.name
            _systemPrompt.value = project.systemPrompt
            _dailyRecordPrompt.value = project.dailyRecordPrompt
            _checkInMode.value = project.checkInMode
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

    fun updateCheckInMode(value: String) {
        if (loadedProjectId == 0L) {
            _checkInMode.value = value
        }
    }

    fun save(projectId: Long, onSaved: () -> Unit) {
        val finalName = _name.value.trim()
        val finalSystemPrompt = _systemPrompt.value.trim()
        val finalDailyRecordPrompt = _dailyRecordPrompt.value.trim()
        if (finalName.isEmpty() || finalSystemPrompt.isEmpty() || finalDailyRecordPrompt.isEmpty()) return

        viewModelScope.launch {
            repository.saveProject(
                projectId,
                finalName,
                finalSystemPrompt,
                finalDailyRecordPrompt,
                _checkInMode.value
            )
            onSaved()
        }
    }

    fun deleteProject(onDeleted: () -> Unit) {
        val projectId = loadedProjectId
        if (projectId == 0L || _deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            try {
                // 先清理该项目消息里的本地图片文件（尽力而为，失败不影响数据库删除）
                runCatching {
                    database.messageDao().getAllByProjectId(projectId).forEach { message ->
                        if (message.imagePaths.isNotBlank()) {
                            val array = JSONArray(message.imagePaths)
                            for (i in 0 until array.length()) {
                                val path = array.optString(i).trim()
                                if (path.isNotEmpty()) File(path).delete()
                            }
                        }
                    }
                }
                // 级联删除：消息 → 日报 → 挑战打卡 → 挑战 → 项目，放同一事务保证原子性
                database.withTransaction {
                    database.messageDao().deleteByProjectId(projectId)
                    database.dailyRecordDao().deleteByProjectId(projectId)
                    database.challengeDao().getByProjectId(projectId)?.let { challenge ->
                        database.challengeCheckInDao().deleteByChallengeId(challenge.id)
                    }
                    database.challengeDao().deleteByProjectId(projectId)
                    database.projectDao().deleteById(projectId)
                }
                onDeleted()
            } finally {
                _deleting.value = false
            }
        }
    }
}
