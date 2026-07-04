package com.xdl.upward.ui.project

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.ProjectEntity
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.data.repository.MessageRepository
import com.xdl.upward.data.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth

class ProjectListViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = ProjectRepository(
        database.projectDao()
    )
    private val messageRepository = MessageRepository(
        database.messageDao()
    )
    private val configRepository = ConfigRepository(
        database.configDao()
    )

    val projects: Flow<List<ProjectEntity>> = repository.observeProjects()

    private val _violationCount = MutableStateFlow(0)
    val violationCount: StateFlow<Int> = _violationCount.asStateFlow()

    init {
        viewModelScope.launch {
            configRepository.observeViolationCount().collect { value ->
                _violationCount.value = value?.toIntOrNull() ?: 0
            }
        }
        viewModelScope.launch {
            checkViolationOnEnter()
        }
    }

    private suspend fun checkViolationOnEnter() {
        val today = LocalDate.now()
        val currentMonth = YearMonth.from(today).toString()
        val oldMonth = configRepository.getValue("violation_month")
        if (oldMonth != currentMonth) {
            configRepository.setValue("violation_month", currentMonth)
            configRepository.setValue("violation_count", "0")
            configRepository.setValue("violation_checked_date", "")
            _violationCount.value = 0
        }

        val todayText = today.toString()
        if (configRepository.getValue("violation_checked_date") == todayText) {
            return
        }

        val yesterday = today.minusDays(1)
        val initialDate = configRepository.getValue("initial_date")?.let { value ->
            try {
                LocalDate.parse(value)
            } catch (e: Exception) {
                today
            }
        } ?: today
        if (initialDate.isAfter(yesterday)) {
            configRepository.setValue("violation_checked_date", todayText)
            return
        }

        val now = OffsetDateTime.now()
        val yesterdayStart = yesterday.atStartOfDay().atOffset(now.offset).toString()
        val todayStart = today.atStartOfDay().atOffset(now.offset).toString()
        val projects = repository.getProjects()
        var hasViolationProject = false
        for (project in projects) {
            val projectCreatedDate = try {
                OffsetDateTime.parse(project.createdAt).toLocalDate()
            } catch (e: Exception) {
                today
            }
            if (projectCreatedDate.isAfter(yesterday)) {
                continue
            }
            if (!messageRepository.hasMessagesBetween(project.id, yesterdayStart, todayStart)) {
                hasViolationProject = true
                break
            }
        }

        configRepository.setValue("violation_checked_date", todayText)
        if (hasViolationProject) {
            val newCount = (configRepository.getValue("violation_count")?.toIntOrNull() ?: 0) + 1
            if (newCount >= 3) {
                database.clearAllTables()
                _violationCount.value = 0
            } else {
                configRepository.setValue("violation_count", newCount.toString())
                _violationCount.value = newCount
            }
        }
    }
}
