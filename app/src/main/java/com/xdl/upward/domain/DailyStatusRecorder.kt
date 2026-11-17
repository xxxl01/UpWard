package com.xdl.upward.domain

import android.content.Context
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.DailyStatusEntity
import com.xdl.upward.data.local.ViolationRecordEntity
import com.xdl.upward.data.repository.ChallengeRepository
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.data.repository.MessageRepository
import com.xdl.upward.data.repository.ProjectRepository
import com.xdl.upward.ui.project.ProjectYesterdayCheckStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.OffsetDateTime

/** 把每日达标情况落库到 daily_status。后台闹钟、开机/覆盖安装、打开 App 都走这里。 */
object DailyStatusRecorder {
    private const val KEY_SYNCED_DATE = "daily_status_synced_date"
    private const val KEY_TRACKING_START = "tracking_start_at"
    private const val KEY_STREAK_LAST = "clean_streak_last_date"
    private const val KEY_STREAK_DAYS = "clean_streak_days"
    private const val STREAK_DAYS = 7
    private const val MAX_BACKFILL_DAYS = 90L
    private val mutex = Mutex()

    /**
     * 补齐并落库：把“上次同步日期”到昨天之间的每一天定成成功/失败，再记录今天的进行中状态。
     * 即使长时间没打开 App 或闹钟被杀，下次同步也会补上。
     */
    suspend fun sync(context: Context) = mutex.withLock {
        val database = AppDatabase.getInstance(context)
        val configRepository = ConfigRepository(database.configDao())
        val challengeRepository = ChallengeRepository(database.challengeDao(), database.challengeCheckInDao())
        val today = challengeRepository.currentCheckInDate()
        val yesterday = today.minusDays(1)
        // 以本次更新后第一次打开（或覆盖安装）的时间为起点，之前的日子不回溯、不落库
        val startDate = trackingStartDate(configRepository)
        val synced = configRepository.getValue(KEY_SYNCED_DATE)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        var start = (synced?.plusDays(1) ?: startDate).let { if (it.isBefore(startDate)) startDate else it }
        val earliest = yesterday.minusDays(MAX_BACKFILL_DAYS)
        if (start.isBefore(earliest)) start = earliest
        var date = start
        while (!date.isAfter(yesterday)) {
            computeAndPersist(context, date)
            date = date.plusDays(1)
        }
        if (synced == null || synced.isBefore(yesterday)) {
            configRepository.setValue(KEY_SYNCED_DATE, yesterday.toString())
        }
        computeAndPersist(context, today)
        recordViolations(context, today)
        applyStreakReward(context, today)
    }

    /** 追踪起点（首次运行时写入）；打卡日口径（24 点换日，即自然日）。 */
    suspend fun trackingStartAt(configRepository: ConfigRepository): OffsetDateTime {
        configRepository.getValue(KEY_TRACKING_START)
            ?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
            ?.let { return it }
        val now = OffsetDateTime.now()
        configRepository.setValue(KEY_TRACKING_START, now.toString())
        return now
    }

    private suspend fun trackingStartDate(configRepository: ConfigRepository): LocalDate {
        val at = trackingStartAt(configRepository)
        return if (at.toLocalTime().isBefore(java.time.LocalTime.MIDNIGHT)) at.toLocalDate().minusDays(1) else at.toLocalDate()
    }

    /**
     * 连续全勤奖励：从追踪起点后的第一个整天开始，按天累计“连续无缺卡”的天数——
     * 当天有打卡成功、没有任何失败/缺卡、也没有违规记录，连续天数 +1；当天有失败或违规则清零；
     * 当天没有任何打卡记录（比如没有项目）不增不减。每攒满 7 天，逻辑删除最早的 1 条有效违规
     * （原因 streak_reward），然后重新开始数。不要求周一开始、周日结束。
     */
    private suspend fun applyStreakReward(context: Context, today: LocalDate) {
        val database = AppDatabase.getInstance(context)
        val configRepository = ConfigRepository(database.configDao())
        val startDate = trackingStartDate(configRepository)
        val yesterday = today.minusDays(1)
        val last = configRepository.getValue(KEY_STREAK_LAST)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: startDate
        var streak = configRepository.getValue(KEY_STREAK_DAYS)?.toIntOrNull() ?: 0
        val statusDao = database.dailyStatusDao()
        val violationDao = database.violationRecordDao()
        var date = last.plusDays(1)
        if (date.isAfter(yesterday)) return
        while (!date.isAfter(yesterday)) {
            val day = date.toString()
            val failed = statusDao.countByStatus(day, day, DailyStatusEntity.FAILED)
            val success = statusDao.countByStatus(day, day, DailyStatusEntity.SUCCESS)
            val violations = violationDao.countByDateRange(day, day)
            if (failed > 0 || violations > 0) {
                streak = 0
            } else if (success > 0) {
                streak++
                if (streak >= STREAK_DAYS) {
                    violationDao.logicalDeleteOldest(1, OffsetDateTime.now().toString(), "streak_reward", 0)
                    streak = 0
                }
            }
            date = date.plusDays(1)
        }
        configRepository.setValue(KEY_STREAK_DAYS, streak.toString())
        configRepository.setValue(KEY_STREAK_LAST, yesterday.toString())
    }

    /**
     * 违规规则：每一次失败/缺卡（daily_status 里一个 failed 的时段，含挑战缺卡；挑战因缺卡被判失败本身不再额外记违规）记一次违规，每日没有上限。
     * 起算日 violation_from_date 第一次取旧规则的 violation_checked_date（该日之前已按旧规则计过），
     * 避免升级后把历史失败重复算一遍。ref_key 唯一，重复同步不会重复入账。
     */
    private suspend fun recordViolations(context: Context, today: LocalDate) {
        val database = AppDatabase.getInstance(context)
        val configRepository = ConfigRepository(database.configDao())
        val from = trackingStartDate(configRepository)
        val projectRepository = ProjectRepository(database.projectDao())
        val violationDao = database.violationRecordDao()
        val now = OffsetDateTime.now().toString()
        database.dailyStatusDao().getFailedFrom(from.toString()).forEach { row ->
            val name = projectRepository.getProject(row.projectId)?.name ?: "已删除项目"
            val label = when (row.component) {
                "morning" -> "上午"
                "afternoon" -> "下午"
                "evening" -> "晚上"
                "challenge" -> "挑战"
                else -> "每日"
            }
            violationDao.insert(
                ViolationRecordEntity(
                    refKey = "checkin:${row.date}:${row.projectId}:${row.component}",
                    source = "checkin",
                    projectId = row.projectId,
                    date = row.date,
                    detail = if (row.component == "challenge") "「$name」挑战缺卡" else "「$name」$label 未汇报",
                    createdAt = now
                )
            )
        }
    }

    /**
     * 用户发出一条消息的当下，把该项目当前时段记为成功。
     * 挑战模式的项目不在这里记（挑战以打卡为准）；项目在该时段开始之后才创建的，不适用，不记。
     */
    suspend fun recordMessageSent(context: Context, projectId: Long) {
        val database = AppDatabase.getInstance(context)
        val project = ProjectRepository(database.projectDao()).getProject(projectId) ?: return
        val challengeRepository = ChallengeRepository(database.challengeDao(), database.challengeCheckInDao())
        val now = OffsetDateTime.now()
        val date = challengeRepository.currentCheckInDate()
        if (challengeRepository.challengeOfDate(projectId, date) != null) return
        val dateText = date.toString()
        val dao = database.dailyStatusDao()
        if (project.checkInMode == "once_per_day") {
            dao.record(projectId, dateText, "daily", "once_per_day", DailyStatusEntity.SUCCESS)
            return
        }
        val dayStart = date.atTime(0, 0).atOffset(now.offset)
        val noon = date.atTime(13, 0).atOffset(now.offset)
        val evening = date.atTime(19, 30).atOffset(now.offset)
        val (component, start) = when {
            now.isBefore(noon) -> "morning" to dayStart
            now.isBefore(evening) -> "afternoon" to noon
            else -> "evening" to evening
        }
        val created = runCatching { OffsetDateTime.parse(project.createdAt) }.getOrDefault(now)
        if (created.isAfter(start)) return
        dao.record(projectId, dateText, component, "three_times", DailyStatusEntity.SUCCESS)
    }

    /**
     * 计算某个打卡日各项目各组件的状态（true 成功 / false 失败 / null 待定或不适用），
     * 并写入 daily_status：库里已是成功/失败的直接沿用，不再重算；待定只会被升级为成功/失败。
     */
    suspend fun computeAndPersist(context: Context, date: LocalDate): List<ProjectYesterdayCheckStatus> {
        val database = AppDatabase.getInstance(context)
        val repository = ProjectRepository(database.projectDao())
        val messageRepository = MessageRepository(database.messageDao())
        val configRepository = ConfigRepository(database.configDao())
        val challengeRepository = ChallengeRepository(database.challengeDao(), database.challengeCheckInDao())
        val dao = database.dailyStatusDao()
        val saved = dao.getByDate(date.toString())
            .associateBy { it.projectId to it.component }
        val now = OffsetDateTime.now()
        val offset = now.offset
        val dayStart = date.atTime(0, 0).atOffset(offset)
        val noon = date.atTime(13, 0).atOffset(offset)
        val evening = date.atTime(19, 30).atOffset(offset)
        val dayEnd = date.plusDays(1).atTime(0, 0).atOffset(offset)
        val trackingStart = trackingStartAt(configRepository)

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
            // 追踪起点之前已经结束的时段不管、不落库
            if (!end.isAfter(trackingStart)) return null
            if (now.isBefore(start)) return null
            if (created.isAfter(start) && component != "daily") return null
            val ended = !now.isBefore(end)
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
                } else if (now.isBefore(dayStart) || !dayEnd.isAfter(trackingStart)) {
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
}
