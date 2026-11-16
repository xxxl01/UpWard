package com.xdl.upward.domain

import android.content.Context
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.DailyStatusEntity
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
        val initialDate = configRepository.getValue("initial_date")?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        } ?: today
        val synced = configRepository.getValue(KEY_SYNCED_DATE)?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        var start = (synced?.plusDays(1) ?: initialDate).let { if (it.isBefore(initialDate)) initialDate else it }
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
        val dayStart = date.atTime(4, 0).atOffset(now.offset)
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
}
