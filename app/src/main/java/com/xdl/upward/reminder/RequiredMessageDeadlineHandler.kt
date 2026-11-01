package com.xdl.upward.reminder

import android.content.Context
import androidx.room.withTransaction
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.ConfigEntity
import java.time.OffsetDateTime

/** 统一结算指定回复窗口，事务保证同一窗口只记录一次违规。 */
object RequiredMessageDeadlineHandler {
    private data class Settlement(val clearReminder: Boolean, val violationCount: Int? = null)

    suspend fun checkAndRecord(context: Context, projectId: Long): Int? {
        val database = AppDatabase.getInstance(context)
        val settlement = database.withTransaction {
            val project = database.projectDao().getProject(projectId)
                ?: return@withTransaction Settlement(false)
            if (project.nextRequiredMessageEndAt.isBlank()) {
                return@withTransaction Settlement(false)
            }

            val startTime = runCatching { OffsetDateTime.parse(project.nextRequiredMessageStartAt) }.getOrNull()
            val endTime = runCatching { OffsetDateTime.parse(project.nextRequiredMessageEndAt) }.getOrNull()
            if (startTime == null || endTime == null) {
                database.projectDao().updateNextRequiredMessagePeriod(projectId, "", "")
                return@withTransaction Settlement(true)
            }
            if (OffsetDateTime.now().isBefore(endTime)) {
                return@withTransaction Settlement(false)
            }

            database.projectDao().updateNextRequiredMessagePeriod(projectId, "", "")
            val configDao = database.configDao()
            val oldConfig = configDao.getByKey("violation_count")
            val nextCount = (oldConfig?.value?.toIntOrNull() ?: 0) + 1
            if (oldConfig == null) {
                configDao.insert(ConfigEntity(key = "violation_count", value = nextCount.toString()))
            } else {
                configDao.update(oldConfig.copy(value = nextCount.toString()))
            }
            Settlement(true, nextCount)
        }
        if (settlement.clearReminder) {
            RequiredMessageReminderScheduler.cancel(context, projectId)
        }
        return settlement.violationCount
    }
}
