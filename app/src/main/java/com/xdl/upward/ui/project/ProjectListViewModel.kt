package com.xdl.upward.ui.project

import android.app.Application
import android.app.AlarmManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.ProjectEntity
import com.xdl.upward.data.local.ViolationRecordEntity
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.data.repository.ChallengeRepository
import com.xdl.upward.data.repository.MessageRepository
import com.xdl.upward.data.repository.ProjectRepository
import com.xdl.upward.domain.DailyStatusRecorder
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
            AppDatabase.getInstance(application).violationRecordDao().observeActiveCount().collect { value ->
                _violationCount.value = value
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
            DailyStatusRecorder.sync(getApplication())
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
            val items = DailyStatusRecorder.computeAndPersist(getApplication(), date)
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

    private suspend fun checkViolation() {
        val today = challengeRepository.currentCheckInDate()
        val currentMonth = YearMonth.from(today).toString()
        val oldMonth = configRepository.getValue("violation_month")
        if (oldMonth != currentMonth) {
            configRepository.setValue("violation_month", currentMonth)
            // 处于惩罚状态（累计至少 10 次）时换月不清零，必须通过捐款抵扣后降到 10 次以下才会解除
            val violationDao = database.violationRecordDao()
            if (violationDao.activeCount() < 10) {
                violationDao.logicalDeleteAllActive(OffsetDateTime.now().toString(), "monthly_reset")
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
        val violationDetails = mutableListOf<String>()
        var firstViolationProjectId = 0L
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
                    if (firstViolationProjectId == 0L) firstViolationProjectId = project.id
                    violationDetails.add("「${project.name}」每日未汇报")
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
                    if (firstViolationProjectId == 0L) firstViolationProjectId = project.id
                    val missed = listOfNotNull(
                        "上午".takeIf { shouldCheckMorning && !hasMorningMessage },
                        "下午".takeIf { shouldCheckAfternoon && !hasAfternoonMessage },
                        "晚上".takeIf { shouldCheckEvening && !hasEveningMessage }
                    ).joinToString("、")
                    violationDetails.add("「${project.name}」$missed 未汇报")
                }
            }
        }

        configRepository.setValue("violation_checked_date", todayText)
        configRepository.setValue("violation_rule_version", ruleVersion)
        if (violationDetails.isNotEmpty()) {
            // 一天最多记一次违规（ref_key 保证幂等），次数 = 未被逻辑删除的记录数
            database.violationRecordDao().insert(
                ViolationRecordEntity(
                    refKey = "checkin:$yesterday",
                    source = "checkin",
                    projectId = firstViolationProjectId,
                    date = yesterday.toString(),
                    detail = "$yesterday 缺卡：" + violationDetails.joinToString("；"),
                    createdAt = OffsetDateTime.now().toString()
                )
            )
        }
    }
}
