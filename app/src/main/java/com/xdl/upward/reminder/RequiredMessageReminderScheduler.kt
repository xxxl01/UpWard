package com.xdl.upward.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.ProjectEntity
import java.time.OffsetDateTime

object RequiredMessageReminderScheduler {
    private const val ACTION_REMIND_REQUIRED_MESSAGE = "com.xdl.upward.action.REMIND_REQUIRED_MESSAGE"
    const val EXTRA_PROJECT_ID = "project_id"

    fun schedule(context: Context, project: ProjectEntity) {
        schedule(context, project.id, project.nextRequiredMessageStartAt, project.nextRequiredMessageEndAt)
    }

    fun schedule(context: Context, projectId: Long, startTimeText: String, endTimeText: String) {
        val startTime = try {
            OffsetDateTime.parse(startTimeText)
        } catch (e: Exception) {
            cancel(context, projectId)
            return
        }
        val endTime = try {
            OffsetDateTime.parse(endTimeText)
        } catch (e: Exception) {
            cancel(context, projectId)
            return
        }
        val now = OffsetDateTime.now()
        val triggerAtMillis = if (now.isBefore(startTime) && now.isBefore(endTime)) {
            startTime.toInstant().toEpochMilli()
        } else {
            // 已过期的窗口也交给接收器结算；启动或重启后不能直接丢弃。
            System.currentTimeMillis() + 1000L
        }
        scheduleAt(context, projectId, triggerAtMillis)
    }

    fun scheduleNext(context: Context, projectId: Long, triggerAtMillis: Long) {
        scheduleAt(context, projectId, triggerAtMillis)
    }

    fun cancel(context: Context, projectId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, RequiredMessageReminderReceiver::class.java).apply {
            action = ACTION_REMIND_REQUIRED_MESSAGE
            putExtra(EXTRA_PROJECT_ID, projectId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (projectId and 0x7fffffff).toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    suspend fun rescheduleAll(context: Context) {
        val projects = AppDatabase.getInstance(context).projectDao().getProjects()
        for (project in projects) {
            if (project.nextRequiredMessageStartAt.isNotBlank() && project.nextRequiredMessageEndAt.isNotBlank()) {
                schedule(context, project)
            }
        }
    }

    private fun scheduleAt(context: Context, projectId: Long, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = pendingIntent(context, projectId, PendingIntent.FLAG_UPDATE_CURRENT)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, intent)
        }
    }

    private fun pendingIntent(context: Context, projectId: Long, flag: Int): PendingIntent {
        val intent = Intent(context, RequiredMessageReminderReceiver::class.java).apply {
            action = ACTION_REMIND_REQUIRED_MESSAGE
            putExtra(EXTRA_PROJECT_ID, projectId)
        }
        return PendingIntent.getBroadcast(
            context,
            (projectId and 0x7fffffff).toInt(),
            intent,
            flag or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
