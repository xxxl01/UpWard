package com.xdl.upward.data.repository

import com.xdl.upward.data.local.ChallengeCheckInDao
import com.xdl.upward.data.local.ChallengeCheckInEntity
import com.xdl.upward.data.local.ChallengeDao
import com.xdl.upward.data.local.ChallengeEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * 项目挑战数据访问与业务逻辑。
 * 状态机：active（进行中）→ failed（缺卡进入惩罚，中间态）/ success（成功结算）；
 * failed 经捐款截图审核通过后 → settled（失败结算终态）；success / settled 可开启新一轮。
 * 每轮原子态：开启新一轮时重置挑战记录并清空旧打卡记录。
 */
class ChallengeRepository(
    private val challengeDao: ChallengeDao,
    private val checkInDao: ChallengeCheckInDao
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_FAILED = "failed"
        const val STATUS_SUCCESS = "success"
        const val STATUS_SETTLED = "settled"
    }

    fun observeByProjectId(projectId: Long): Flow<ChallengeEntity?> = challengeDao.observeByProjectId(projectId)

    fun observeCheckIns(challengeId: Long): Flow<List<ChallengeCheckInEntity>> = checkInDao.observeByChallengeId(challengeId)

    suspend fun getByProjectId(projectId: Long): ChallengeEntity? = challengeDao.getByProjectId(projectId)

    suspend fun getById(id: Long): ChallengeEntity? = challengeDao.getById(id)

    suspend fun getCheckIns(challengeId: Long): List<ChallengeCheckInEntity> = checkInDao.getByChallengeId(challengeId)

    // 挑战与普通打卡统一在凌晨 4 点换日。
    fun currentCheckInDate(): LocalDate {
        val now = OffsetDateTime.now()
        return if (now.toLocalTime().isBefore(LocalTime.of(4, 0))) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate()
        }
    }

    // 判断某个打卡日当天，该项目是否处于挑战模式（挑战优先于普通打卡）。返回非 null 表示该天按挑战规则判定。
    // 判据是“那天当时处于哪种模式”，不是“挑战现在是什么状态”：挑战结束后回看历史日期，
    // 必须仍按挑战规则判定，否则挑战日会被当成普通打卡日，把没打的普通时段算成缺卡违规。
    // endDate 卡住上界：挑战期结束后的日子恢复普通打卡；
    // 终态挑战再用 finishedAt（状态变更时刻）卡一次：挑战中途失败时，失败当天仍算挑战日，次日起恢复普通模式。
    suspend fun challengeOfDate(projectId: Long, date: LocalDate): ChallengeEntity? {
        val challenge = challengeDao.getByProjectId(projectId) ?: return null
        if (date.isBefore(LocalDate.parse(challenge.startDate)) || date.isAfter(LocalDate.parse(challenge.endDate))) return null
        if (challenge.status == STATUS_ACTIVE) return challenge

        val finishedTime = try {
            OffsetDateTime.parse(challenge.finishedAt)
        } catch (e: Exception) {
            // 旧数据没有 finishedAt：整个挑战周期都按挑战日算，宁可豁免也不误判违规
            return challenge
        }
        val finishedDate = if (finishedTime.toLocalTime().isBefore(LocalTime.of(4, 0))) {
            finishedTime.toLocalDate().minusDays(1)
        } else {
            finishedTime.toLocalDate()
        }
        return if (date.isAfter(finishedDate)) null else challenge
    }

    // 挑战总天数（含开始和截止当天）。
    fun totalDays(challenge: ChallengeEntity): Long {
        return ChronoUnit.DAYS.between(LocalDate.parse(challenge.startDate), LocalDate.parse(challenge.endDate)) + 1
    }

    // 挑战期内已打卡天数（截止到今天，未到的未来日期不计）。
    suspend fun checkInDaysSoFar(challenge: ChallengeEntity): Long {
        val today = currentCheckInDate()
        val end = minOf(LocalDate.parse(challenge.endDate), today)
        if (end.isBefore(LocalDate.parse(challenge.startDate))) return 0
        return checkInDao.countBetween(challenge.id, challenge.startDate, end.toString()).toLong()
    }

    // 创建挑战（项目还没有挑战记录时）。开始日期由用户手动设置（可为今天或未来），status 为 active。
    suspend fun createChallenge(
        projectId: Long,
        goal: String,
        dailyStandardPrompt: String,
        failPenaltyPrompt: String,
        startDate: String,
        endDate: String,
        transitionEndDate: String
    ): Long {
        return challengeDao.insert(
            ChallengeEntity(
                projectId = projectId,
                goal = goal,
                dailyStandardPrompt = dailyStandardPrompt,
                failPenaltyPrompt = failPenaltyPrompt,
                startDate = startDate,
                endDate = endDate,
                transitionEndDate = transitionEndDate,
                status = STATUS_ACTIVE,
                createdAt = OffsetDateTime.now().toString()
            )
        )
    }

    // 开启新一轮挑战：仅允许在 success / settled 终态下修改同一条记录，并清空旧打卡记录，保证每轮干净开始。
    // 开始日期由用户手动设置（可为今天或未来）。
    suspend fun startNewRound(
        projectId: Long,
        goal: String,
        dailyStandardPrompt: String,
        failPenaltyPrompt: String,
        startDate: String,
        endDate: String,
        transitionEndDate: String
    ) {
        val oldChallenge = challengeDao.getByProjectId(projectId)
        val now = OffsetDateTime.now().toString()
        if (oldChallenge == null) {
            challengeDao.insert(
                ChallengeEntity(
                    projectId = projectId,
                    goal = goal,
                    dailyStandardPrompt = dailyStandardPrompt,
                    failPenaltyPrompt = failPenaltyPrompt,
                    startDate = startDate,
                    endDate = endDate,
                    transitionEndDate = transitionEndDate,
                    status = STATUS_ACTIVE,
                    createdAt = now
                )
            )
        } else {
            if (oldChallenge.status != STATUS_SUCCESS && oldChallenge.status != STATUS_SETTLED) return
            checkInDao.deleteByChallengeId(oldChallenge.id)
            challengeDao.update(
                oldChallenge.copy(
                    goal = goal,
                    dailyStandardPrompt = dailyStandardPrompt,
                    failPenaltyPrompt = failPenaltyPrompt,
                    startDate = startDate,
                    endDate = endDate,
                    transitionEndDate = transitionEndDate,
                    status = STATUS_ACTIVE,
                    finishedAt = ""
                )
            )
        }
    }

    // 修改进行中的挑战：仅 active（含开始日期未到的 pending）状态允许。
    // 只更新目标、每日标准、失败惩罚与截止日期；开始日期不可改，已打卡记录保留。
    // 难度增强/减弱的判定由调用方完成（本地规则或 AI 判定），这里直接覆盖。
    suspend fun updateActiveChallenge(
        projectId: Long,
        goal: String,
        dailyStandardPrompt: String,
        failPenaltyPrompt: String,
        endDate: String,
        transitionEndDate: String
    ): Boolean {
        val challenge = challengeDao.getByProjectId(projectId) ?: return false
        if (challenge.status != STATUS_ACTIVE) return false
        challengeDao.update(
            challenge.copy(
                goal = goal,
                dailyStandardPrompt = dailyStandardPrompt,
                failPenaltyPrompt = failPenaltyPrompt,
                endDate = endDate,
                transitionEndDate = transitionEndDate
            )
        )
        return true
    }

    // AI 每日打卡：当天已打卡或不在挑战期则忽略，返回是否成功写入。
    suspend fun checkInToday(challengeId: Long, date: String, summary: String): Boolean {
        val existing = checkInDao.getByChallengeIdAndDate(challengeId, date)
        if (existing != null) return false
        val inserted = checkInDao.insert(
            ChallengeCheckInEntity(
                challengeId = challengeId,
                date = date,
                summary = summary,
                createdAt = OffsetDateTime.now().toString()
            )
        )
        return inserted > 0
    }

    // 手动成功结算：校验通过（挑战期内每天都打卡）后置为 success。挑战成功没有额外奖励，不改变违规次数。
    suspend fun settleSuccess(challengeId: Long) {
        challengeDao.updateStatus(challengeId, STATUS_SUCCESS, OffsetDateTime.now().toString())
    }

    // 判定失败：active → failed，进入失败惩罚流程（中间态，不可开新一轮）。
    suspend fun markAsFailed(challengeId: Long) {
        challengeDao.updateStatus(challengeId, STATUS_FAILED, OffsetDateTime.now().toString())
    }

    // 失败惩罚结算：failed → settled（由捐款截图审核通过后触发，见 PaymentVerifier）。
    suspend fun settleFailed(challengeId: Long) {
        challengeDao.updateStatus(challengeId, STATUS_SETTLED, OffsetDateTime.now().toString())
    }

    // 捐款结算违规时一并结算：所有处于失败惩罚中（failed）的挑战直接置为 settled，不再重复惩罚。返回结算数量。
    // 缺卡判定是懒执行的（进入项目页才检测），结算前先对所有进行中的挑战补做一次缺卡检测，
    // 否则已缺卡但还没被打开过的项目仍是 active，会漏结算，之后一打开又变成“惩罚中”。
    suspend fun settleAllFailed(): Int {
        challengeDao.getAllForBackup()
            .filter { it.status == STATUS_ACTIVE }
            .forEach { checkForMissedDays(it.projectId) }
        val failed = challengeDao.getFailed()
        val now = OffsetDateTime.now().toString()
        failed.forEach { challengeDao.updateStatus(it.id, STATUS_SETTLED, now) }
        return failed.size
    }

    // 缺卡检测：active 挑战若在 start ~ (今天-1, 且不超过 end) 之间有任意一天没打卡，立即置为 failed 并返回 true。
    suspend fun checkForMissedDays(projectId: Long): Boolean {
        val challenge = challengeDao.getByProjectId(projectId) ?: return false
        if (challenge.status != STATUS_ACTIVE) return false

        val today = currentCheckInDate()
        val start = LocalDate.parse(challenge.startDate)
        val end = LocalDate.parse(challenge.endDate)
        val lastDayToCheck = minOf(end, today.minusDays(1))
        if (lastDayToCheck.isBefore(start)) return false

        val checkIns = checkInDao.getBetween(challenge.id, start.toString(), lastDayToCheck.toString())
            .map { it.date }
            .toSet()
        var day = start
        while (!day.isAfter(lastDayToCheck)) {
            if (day.toString() !in checkIns) {
                markAsFailed(challenge.id)
                return true
            }
            day = day.plusDays(1)
        }
        return false
    }
}
