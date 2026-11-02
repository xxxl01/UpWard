package com.xdl.upward.ui.project

import android.app.Application
import android.app.AlarmManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.ProjectEntity
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.data.repository.ChallengeRepository
import com.xdl.upward.data.repository.MessageRepository
import com.xdl.upward.data.repository.ProjectRepository
import com.xdl.upward.reminder.RequiredMessageDeadlineHandler
import com.xdl.upward.reminder.GlobalReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth

data class ProjectYesterdayCheckStatus(
    val projectName: String,
    val checkInMode: String,
    val hasMorningMessage: Boolean?,
    val hasAfternoonMessage: Boolean?,
    val hasEveningMessage: Boolean?,
    val isViolation: Boolean,
    val isChallenge: Boolean = false
)

data class GlobalReminderStatus(
    val period: String,
    val label: String,
    val time: String,
    val switchOn: Boolean,
    val enabled: Boolean
)

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
    private val challengeRepository = ChallengeRepository(
        database.challengeDao(),
        database.challengeCheckInDao()
    )

    val projects: Flow<List<ProjectEntity>> = repository.observeProjects()

    private val _violationCount = MutableStateFlow(0)
    val violationCount: StateFlow<Int> = _violationCount.asStateFlow()

    private val _projectYesterdayStatuses = MutableStateFlow<List<ProjectYesterdayCheckStatus>>(emptyList())
    val projectYesterdayStatuses: StateFlow<List<ProjectYesterdayCheckStatus>> = _projectYesterdayStatuses.asStateFlow()

    private val _projectTodayStatuses = MutableStateFlow<List<ProjectYesterdayCheckStatus>>(emptyList())
    val projectTodayStatuses: StateFlow<List<ProjectYesterdayCheckStatus>> = _projectTodayStatuses.asStateFlow()

    private val _globalReminderStatuses = MutableStateFlow(
        listOf(
            GlobalReminderStatus("morning", "上午", "07:00", true, false),
            GlobalReminderStatus("afternoon", "下午", "13:00", true, false),
            GlobalReminderStatus("evening", "晚上", "19:30", true, false)
        )
    )
    val globalReminderStatuses: StateFlow<List<GlobalReminderStatus>> = _globalReminderStatuses.asStateFlow()

    init {
        viewModelScope.launch {
            configRepository.observeViolationCount().collect { value ->
                _violationCount.value = value?.toIntOrNull() ?: 0
            }
        }
        checkViolationOnEnter()
        refreshGlobalReminderStatuses()
    }

    fun refreshGlobalReminderStatuses() {
        viewModelScope.launch {
            val alarmManager = getApplication<Application>().getSystemService(AlarmManager::class.java)
            val canSchedule = alarmManager.canScheduleExactAlarms()
            val periods = listOf(
                "morning" to "上午",
                "afternoon" to "下午",
                "evening" to "晚上"
            )
            _globalReminderStatuses.value = periods.map { (period, label) ->
                val time = configRepository.getValue("global_reminder_${period}_time")
                    ?: GlobalReminderScheduler.defaultTime(period)
                val switchOn = configRepository.getValue("global_reminder_${period}_enabled") != "0"
                GlobalReminderStatus(
                    period = period,
                    label = label,
                    time = time,
                    switchOn = switchOn,
                    enabled = switchOn && canSchedule && GlobalReminderScheduler.hasActiveAlarm(getApplication(), period)
                )
            }
        }
    }

    fun checkViolationOnEnter() {
        viewModelScope.launch {
            checkRequiredMessageDeadlines()
            checkViolation()
            loadProjectTodayStatuses()
            loadProjectYesterdayStatuses()
        }
    }

    private suspend fun checkRequiredMessageDeadlines() {
        val projects = repository.getProjects()
        for (project in projects) {
            if (project.nextRequiredMessageEndAt.isNotBlank()) {
                RequiredMessageDeadlineHandler.checkAndRecord(getApplication(), project.id)
            }
        }
    }

    fun loadProjectYesterdayStatuses() {
        viewModelScope.launch {
            val checkInToday = challengeRepository.currentCheckInDate()
            val yesterday = checkInToday.minusDays(1)
            val now = OffsetDateTime.now()
            val yesterdayStartTime = yesterday.atTime(4, 0).atOffset(now.offset)
            val morningStartTime = yesterday.atTime(4, 0).atOffset(now.offset)
            val noonStartTime = yesterday.atTime(13, 0).atOffset(now.offset)
            val eveningStartTime = yesterday.atTime(19, 30).atOffset(now.offset)
            val dailyStart = yesterdayStartTime.toString()
            val morningStart = morningStartTime.toString()
            val noonStart = noonStartTime.toString()
            val eveningStart = eveningStartTime.toString()
            val todayStartTime = checkInToday.atTime(4, 0).atOffset(now.offset)
            val todayStart = todayStartTime.toString()
            val initialDate = configRepository.getValue("initial_date")?.let { value ->
                try {
                    LocalDate.parse(value)
                } catch (e: Exception) {
                    checkInToday
                }
            } ?: checkInToday
            val items = repository.getProjects().map { project ->
                val challenge = challengeRepository.challengeOfDate(project.id, yesterday)
                if (challenge != null) {
                    val done = challengeRepository.getCheckIns(challenge.id).any { it.date == yesterday.toString() }
                    return@map ProjectYesterdayCheckStatus(project.name, "challenge", done, null, null, !done, true)
                }
                val projectCreatedAt = try {
                    OffsetDateTime.parse(project.createdAt)
                } catch (e: Exception) {
                    now
                }
                if (project.checkInMode == "once_per_day") {
                    val shouldCheckDaily = initialDate.isBefore(yesterday) && projectCreatedAt.isBefore(todayStartTime)
                    val hasDailyMessage = if (shouldCheckDaily) messageRepository.hasUserMessagesBetween(project.id, dailyStart, todayStart) else true
                    ProjectYesterdayCheckStatus(
                        projectName = project.name,
                        checkInMode = project.checkInMode,
                        hasMorningMessage = hasDailyMessage,
                        hasAfternoonMessage = null,
                        hasEveningMessage = null,
                        isViolation = shouldCheckDaily && !hasDailyMessage
                    )
                } else {
                    val shouldCheckMorning = initialDate.isBefore(yesterday) && !projectCreatedAt.isAfter(morningStartTime)
                    val shouldCheckAfternoon = initialDate.isBefore(yesterday) && !projectCreatedAt.isAfter(noonStartTime)
                    val shouldCheckEvening = initialDate.isBefore(yesterday) && !projectCreatedAt.isAfter(eveningStartTime)
                    val hasMorningMessage = if (shouldCheckMorning) messageRepository.hasUserMessagesBetween(project.id, morningStart, noonStart) else true
                    val hasAfternoonMessage = if (shouldCheckAfternoon) messageRepository.hasUserMessagesBetween(project.id, noonStart, eveningStart) else true
                    val hasEveningMessage = if (shouldCheckEvening) messageRepository.hasUserMessagesBetween(project.id, eveningStart, todayStart) else true
                    ProjectYesterdayCheckStatus(
                        projectName = project.name,
                        checkInMode = project.checkInMode,
                        hasMorningMessage = hasMorningMessage,
                        hasAfternoonMessage = hasAfternoonMessage,
                        hasEveningMessage = hasEveningMessage,
                        isViolation = (shouldCheckMorning && !hasMorningMessage) ||
                            (shouldCheckAfternoon && !hasAfternoonMessage) ||
                            (shouldCheckEvening && !hasEveningMessage)
                    )
                }
            }
            _projectYesterdayStatuses.value = items
        }
    }

    fun loadProjectTodayStatuses() {
        viewModelScope.launch {
            val today = challengeRepository.currentCheckInDate()
            val tomorrow = today.plusDays(1)
            val now = OffsetDateTime.now()
            val dailyStartTime = today.atTime(4, 0).atOffset(now.offset)
            val morningStartTime = today.atTime(4, 0).atOffset(now.offset)
            val noonStartTime = today.atTime(13, 0).atOffset(now.offset)
            val eveningStartTime = today.atTime(19, 30).atOffset(now.offset)
            val tomorrowStartTime = tomorrow.atTime(4, 0).atOffset(now.offset)
            val dailyStart = dailyStartTime.toString()
            val morningStart = morningStartTime.toString()
            val noonStart = noonStartTime.toString()
            val eveningStart = eveningStartTime.toString()
            val tomorrowStart = tomorrowStartTime.toString()
            val items = repository.getProjects().map { project ->
                val challenge = challengeRepository.challengeOfDate(project.id, today)
                if (challenge != null) {
                    val hasMessage = if (!now.isBefore(dailyStartTime)) {
                        challengeRepository.getCheckIns(challenge.id).any { it.date == today.toString() }
                    } else {
                        false
                    }
                    return@map ProjectYesterdayCheckStatus(project.name, "challenge", if (hasMessage) true else null, null, null, false, true)
                }
                val projectCreatedAt = try {
                    OffsetDateTime.parse(project.createdAt)
                } catch (e: Exception) {
                    now
                }
                if (project.checkInMode == "once_per_day") {
                    val hasMessage = if (!now.isBefore(dailyStartTime)) {
                        messageRepository.hasUserMessagesBetween(project.id, dailyStart, tomorrowStart)
                    } else {
                        false
                    }
                    val hasDailyMessage = if (hasMessage) true else null
                    ProjectYesterdayCheckStatus(
                        projectName = project.name,
                        checkInMode = project.checkInMode,
                        hasMorningMessage = hasDailyMessage,
                        hasAfternoonMessage = null,
                        hasEveningMessage = null,
                        isViolation = false
                    )
                } else {
                    val shouldCheckMorning = !now.isBefore(noonStartTime) && !projectCreatedAt.isAfter(morningStartTime)
                    val shouldCheckAfternoon = !now.isBefore(eveningStartTime) && !projectCreatedAt.isAfter(noonStartTime)
                    val shouldCheckEvening = !now.isBefore(tomorrowStartTime) && !projectCreatedAt.isAfter(eveningStartTime)
                    val shouldShowMorningDone = !now.isBefore(morningStartTime) && !projectCreatedAt.isAfter(morningStartTime)
                    val shouldShowAfternoonDone = !now.isBefore(noonStartTime) && !projectCreatedAt.isAfter(noonStartTime)
                    val shouldShowEveningDone = !now.isBefore(eveningStartTime) && !projectCreatedAt.isAfter(eveningStartTime)
                    val hasMorningMessage = if (shouldShowMorningDone) {
                        val hasMessage = messageRepository.hasUserMessagesBetween(project.id, morningStart, noonStart)
                        if (hasMessage || shouldCheckMorning) hasMessage else null
                    } else {
                        null
                    }
                    val hasAfternoonMessage = if (shouldShowAfternoonDone) {
                        val hasMessage = messageRepository.hasUserMessagesBetween(project.id, noonStart, eveningStart)
                        if (hasMessage || shouldCheckAfternoon) hasMessage else null
                    } else {
                        null
                    }
                    val hasEveningMessage = if (shouldShowEveningDone) {
                        val hasMessage = messageRepository.hasUserMessagesBetween(project.id, eveningStart, tomorrowStart)
                        if (hasMessage || shouldCheckEvening) hasMessage else null
                    } else {
                        null
                    }
                    ProjectYesterdayCheckStatus(
                        projectName = project.name,
                        checkInMode = project.checkInMode,
                        hasMorningMessage = hasMorningMessage,
                        hasAfternoonMessage = hasAfternoonMessage,
                        hasEveningMessage = hasEveningMessage,
                        isViolation = (shouldCheckMorning && hasMorningMessage == false) ||
                            (shouldCheckAfternoon && hasAfternoonMessage == false) ||
                            (shouldCheckEvening && hasEveningMessage == false)
                    )
                }
            }
            _projectTodayStatuses.value = items
        }
    }

    private suspend fun checkViolation() {
        val today = challengeRepository.currentCheckInDate()
        val currentMonth = YearMonth.from(today).toString()
        val oldMonth = configRepository.getValue("violation_month")
        if (oldMonth != currentMonth) {
            configRepository.setValue("violation_month", currentMonth)
            // 处于惩罚状态（累计至少 10 次）时换月不清零，必须通过捐款抵扣后降到 10 次以下才会解除
            val currentCount = configRepository.getValue("violation_count")?.toIntOrNull() ?: 0
            if (currentCount < 10) {
                configRepository.setValue("violation_count", "0")
                _violationCount.value = 0
            }
            configRepository.setValue("violation_checked_date", "")
        }

        val todayText = today.toString()
        val ruleVersion = "challenge_priority_check_in_v3"
        if (
            configRepository.getValue("violation_checked_date") == todayText &&
            configRepository.getValue("violation_rule_version") == ruleVersion
        ) {
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
        if (!initialDate.isBefore(yesterday)) {
            configRepository.setValue("violation_checked_date", todayText)
            configRepository.setValue("violation_rule_version", ruleVersion)
            return
        }

        val now = OffsetDateTime.now()
        val yesterdayStartTime = yesterday.atTime(4, 0).atOffset(now.offset)
        val dailyStart = yesterdayStartTime.toString()
        val morningStartTime = yesterday.atTime(4, 0).atOffset(now.offset)
        val noonStartTime = yesterday.atTime(13, 0).atOffset(now.offset)
        val eveningStartTime = yesterday.atTime(19, 30).atOffset(now.offset)
        val morningStart = morningStartTime.toString()
        val noonStart = noonStartTime.toString()
        val eveningStart = eveningStartTime.toString()
        val todayStartTime = today.atTime(4, 0).atOffset(now.offset)
        val todayStart = todayStartTime.toString()
        val projects = repository.getProjects()
        var hasViolationProject = false
        for (project in projects) {
            val projectCreatedAt = try {
                OffsetDateTime.parse(project.createdAt)
            } catch (e: Exception) {
                now
            }
            // 昨天处于挑战模式时以挑战打卡为准，不按普通模式追责：缺卡只判挑战失败，不累加违规次数。
            val challenge = challengeRepository.challengeOfDate(project.id, yesterday)
            if (challenge != null) {
                val done = challengeRepository.getCheckIns(challenge.id).any { it.date == yesterday.toString() }
                if (!done && challenge.status == ChallengeRepository.STATUS_ACTIVE) {
                    challengeRepository.markAsFailed(challenge.id)
                }
                continue
            }
            if (project.checkInMode == "once_per_day") {
                val shouldCheckDaily = projectCreatedAt.isBefore(todayStartTime)
                if (!shouldCheckDaily) {
                    continue
                }
                val hasDailyMessage = messageRepository.hasUserMessagesBetween(project.id, dailyStart, todayStart)
                if (!hasDailyMessage) {
                    // 不能 break：后面项目的挑战缺卡也要在这里判定失败，违规次数在循环后只加一次
                    hasViolationProject = true
                }
            } else {
                val shouldCheckMorning = !projectCreatedAt.isAfter(morningStartTime)
                val shouldCheckAfternoon = !projectCreatedAt.isAfter(noonStartTime)
                val shouldCheckEvening = !projectCreatedAt.isAfter(eveningStartTime)
                if (!shouldCheckMorning && !shouldCheckAfternoon && !shouldCheckEvening) {
                    continue
                }
                val hasMorningMessage = !shouldCheckMorning || messageRepository.hasUserMessagesBetween(project.id, morningStart, noonStart)
                val hasAfternoonMessage = !shouldCheckAfternoon || messageRepository.hasUserMessagesBetween(project.id, noonStart, eveningStart)
                val hasEveningMessage = !shouldCheckEvening || messageRepository.hasUserMessagesBetween(project.id, eveningStart, todayStart)
                if ((shouldCheckMorning && !hasMorningMessage) ||
                    (shouldCheckAfternoon && !hasAfternoonMessage) ||
                    (shouldCheckEvening && !hasEveningMessage)
                ) {
                    hasViolationProject = true
                }
            }
        }

        configRepository.setValue("violation_checked_date", todayText)
        configRepository.setValue("violation_rule_version", ruleVersion)
        if (hasViolationProject) {
            // 违规次数持续累加，达到 10 次后仍继续累计；每笔捐款抵扣 10 次
            val currentCount = configRepository.getValue("violation_count")?.toIntOrNull() ?: 0
            val finalCount = currentCount + 1
            configRepository.setValue("violation_count", finalCount.toString())
            _violationCount.value = finalCount
        }
    }
}
