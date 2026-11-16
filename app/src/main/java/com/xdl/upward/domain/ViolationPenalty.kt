package com.xdl.upward.domain

import android.content.Context
import androidx.room.withTransaction
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.repository.ConfigRepository
import java.time.Duration
import java.time.OffsetDateTime

/**
 * 违规惩罚状态：有效违规 ≥ 10 次进入惩罚（拦截发送）。
 * 进入惩罚后超过 24 小时没有捐款，每满一天捐款要求 +5 元（累加）。
 * 计时起点 = 第 10 条有效违规的记录时间 与 上次捐款后重新计时的时间（config punish_clock_start）中较晚者，
 * 因此捐款后仍处于惩罚状态时会重新开始 24 小时宽限。
 */
object ViolationPenalty {
    const val THRESHOLD = 10
    const val OVERDUE_FINE_PER_DAY = 5
    private const val KEY_CLOCK_START = "violation_punish_clock_start"

    data class Status(
        val active: Int,
        val punished: Boolean,
        val overdueDays: Int,
        val surchargeYuan: Int
    )

    suspend fun status(context: Context): Status {
        val database = AppDatabase.getInstance(context)
        val dao = database.violationRecordDao()
        val active = dao.activeCount()
        if (active < THRESHOLD) return Status(active, false, 0, 0)
        val config = ConfigRepository(database.configDao())
        val since = listOfNotNull(
            dao.tenthActiveCreatedAt()?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() },
            config.getValue(KEY_CLOCK_START)?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
        ).maxOrNull() ?: return Status(active, true, 0, 0)
        val days = (Duration.between(since, OffsetDateTime.now()).toHours() / 24).toInt().coerceAtLeast(0)
        return Status(active, true, days, days * OVERDUE_FINE_PER_DAY)
    }

    /** 捐款通过后调用：仍处于惩罚状态则重新计时，否则清掉计时起点。 */
    suspend fun afterPayment(context: Context, stillPunished: Boolean) {
        val config = ConfigRepository(AppDatabase.getInstance(context).configDao())
        config.setValue(KEY_CLOCK_START, if (stillPunished) OffsetDateTime.now().toString() else "")
    }
}
