package com.xdl.upward.ui.project

import android.app.Application
import android.app.AlarmManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.DailyStatusEntity
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

    private val _selectedDate = MutableStateFlow(challengeRepository.currentCheckInDate())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _dayStatuses = MutableStateFlow<List<ProjectYesterdayCheckStatus>>(emptyList())
    val dayStatuses: StateFlow<List<ProjectYesterdayCheckStatus>> = _dayStatuses.asStateFlow()

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
            // 先把昨天落库（昨天已结束，会定成成功/失败），再回到今天
            computeAndPersistDayStatuses(challengeRepository.currentCheckInDate().minusDays(1))
            selectToday()
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

    /** 加载并持久化指定打卡日的达标情况；结果通过 [dayStatuses] 发布。 */
    fun loadDayStatuses(date: LocalDate) {
        _selectedDate.value = date
        viewModelScope.launch {
            val items = computeAndPersistDayStatuses(date)
            if (_selectedDate.value == date) _dayStatuses.value = items
        }
    }

    fun todayCheckInDate(): LocalDate = challengeRepository.currentCheckInDate()

    fun selectToday() = loadDayStatuses(challengeRepository.currentCheckInDate())

    fun shiftSelectedDate(days: Long) {
        val today = challengeRepository.currentCheckInDate()
        val target = _selectedDate.value.plusDays(days)
        loadDayStatuses(if (target.isAfter(today)) today else target)
    }

    /**
     * 计算某个打卡日各项目各组件的状态（true 成功 / false 失败 / null 待定或不适用），
     * 并写入 daily_status：库里已是成功/失败的直接沿用，不再重算；待定只会被升级为成功/失败。
     */
    private suspend fun computeAndPersistDayStatuses(date: LocalDate): List<ProjectYesterdayCheckStatus> {
        val dao = database.dailyStatusDao()
        val saved = dao.getByDate(date.toString())
            .associateBy { it.projectId to it.component }
        val now = OffsetDateTime.now()
        val offset = now.offset
        val dayStart = date.atTime(4, 0).atOffset(offset)
        val noon = date.atTime(13, 0).atOffset(offset)
        val evening = date.atTime(19, 30).atOffset(offset)
        val dayEnd = date.plusDays(1).atTime(4, 0).atOffset(offset)
        val initialDate = configRepository.getValue("initial_date")?.let {
            try { LocalDate.parse(it) } catch (e: Exception) { null }
        } ?: challengeRepository.currentCheckInDate()

        // 返回 Pair(是否适用, 状态)；start/end 为该时段窗口。
        suspend fun windowStatus(
            projectId: Long,
            component: String,
            type: String,
            created: OffsetDateTime,
            start: OffsetDateTime,
            end: OffsetDateTime
        ): Boolean? {
            val old = saved[projectId to component]
            if (old != null && old.status != DailyStatusEntity.PENDING) {
                return old.status == DailyStatusEntity.SUCCESS
            }
            if (now.isBefore(start)) return null
            if (created.isAfter(start) && component != "daily") return null
            val ended = !now.isBefore(end)
            if (ended && !initialDate.isBefore(date)) return null
            if (ended && component == "daily" && !created.isBefore(end)) return null
            val has = messageRepository.hasUserMessagesBetween(projectId, start.toString(), end.toString())
            val result: Boolean? = if (has) true else if (ended) false else null
            dao.record(
                projectId, date.toString(), component, type,
                when (result) {
                    true -> DailyStatusEntity.SUCCESS
                    false -> DailyStatusEntity.FAILED
                    null -> DailyStatusEntity.PENDING
                }
            )
            return result
        }

        return repository.getProjects().map { project ->
            val challenge = challengeRepository.challengeOfDate(project.id, date)
            if (challenge != null) {
                val old = saved[project.id to "challenge"]
                val result: Boolean? = if (old != null && old.status != DailyStatusEntity.PENDING) {
                    old.status == DailyStatusEntity.SUCCESS
                } else if (now.isBefore(dayStart)) {
                    null
                } else {
                    val done = challengeRepository.getCheckIns(challenge.id).any { it.date == date.toString() }
                    val r: Boolean? = if (done) true else if (!now.isBefore(dayEnd)) false else null
                    dao.record(
                        project.id, date.toString(), "challenge", "challenge",
                        when (r) {
                            true -> DailyStatusEntity.SUCCESS
                            false -> DailyStatusEntity.FAILED
                            null -> DailyStatusEntity.PENDING
                        }
                    )
                    r
                }
                return@map ProjectYesterdayCheckStatus(project.name, "challenge", result, null, null, result == false, true)
            }
            val created = try { OffsetDateTime.parse(project.createdAt) } catch (e: Exception) { now }
            if (project.checkInMode == "once_per_day") {
                val r = windowStatus(project.id, "daily", "once_per_day", created, dayStart, dayEnd)
                ProjectYesterdayCheckStatus(project.name, project.checkInMode, r, null, null, r == false)
            } else {
                val m = windowStatus(project.id, "morning", "three_times", created, dayStart, noon)
                val a = windowStatus(project.id, "afternoon", "three_times", created, noon, evening)
                val e = windowStatus(project.id, "evening", "three_times", created, evening, dayEnd)
                ProjectYesterdayCheckStatus(
                    project.name, project.checkInMode, m, a, e,
                    m == false || a == false || e == false
                )
            }
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
