package com.xdl.upward.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.repository.ConfigRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

object GlobalReminderScheduler {
    private const val ACTION_REMIND = "com.xdl.upward.action.GLOBAL_REMIND"
    private const val ACTION_STOP_TODAY = "com.xdl.upward.action.STOP_GLOBAL_REMINDER_TODAY"
    const val EXTRA_DATE = "date"
    const val EXTRA_PERIOD = "period"

    suspend fun reschedule(context: Context) {
        val repository = ConfigRepository(AppDatabase.getInstance(context).configDao())
        scheduleAll(
            context,
            repository.getValue("global_reminder_morning_time") ?: "07:00",
            repository.getValue("global_reminder_afternoon_time") ?: "13:00",
            repository.getValue("global_reminder_evening_time") ?: "19:30"
        )
    }

    fun scheduleAll(context: Context, morningTime: String, afternoonTime: String, eveningTime: String) {
        schedule(context, "morning", morningTime)
        schedule(context, "afternoon", afternoonTime)
        schedule(context, "evening", eveningTime)
    }

    fun schedule(context: Context, period: String, timeText: String) {
        val time = try {
            LocalTime.parse(timeText)
        } catch (e: Exception) {
            when (period) {
                "morning" -> LocalTime.of(7, 0)
                "afternoon" -> LocalTime.of(13, 0)
                else -> LocalTime.of(19, 30)
            }
        }
        val now = OffsetDateTime.now()
        var triggerAt = now.toLocalDate().atTime(time).atOffset(now.offset)
        if (!triggerAt.isAfter(now)) {
            triggerAt = now.toLocalDate().plusDays(1).atTime(time).atOffset(now.offset)
        }
        scheduleAt(context, period, triggerAt.toLocalDate().toString(), triggerAt.toInstant().toEpochMilli())
    }

    fun scheduleNext(context: Context, period: String, dateText: String, triggerAtMillis: Long) {
        scheduleAt(context, period, dateText, triggerAtMillis)
    }

    fun cancel(context: Context, period: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(period),
            Intent(context, GlobalReminderReceiver::class.java).apply {
                action = ACTION_REMIND
                putExtra(EXTRA_PERIOD, period)
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        context.getSharedPreferences("reminder_diagnostics", Context.MODE_PRIVATE)
            .edit()
            .remove("${period}_scheduled_at")
            .apply()
    }

    fun cancelAll(context: Context) {
        cancel(context, "morning")
        cancel(context, "afternoon")
        cancel(context, "evening")
    }

    /**
     * 判断应用最近一次排程是否仍在未来，并且当前仍有精确闹钟权限。
     * Android 没有公开查询任意 setExact 闹钟是否仍存在的 API，
     * 因此这里使用成功排程后保存的时间作为应用侧状态，避免把残留 PendingIntent 当成有效闹钟。
     */
    fun hasActiveAlarm(context: Context, period: String): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (!alarmManager.canScheduleExactAlarms()) return false
        val scheduledAt = context.getSharedPreferences("reminder_diagnostics", Context.MODE_PRIVATE)
            .getLong("${period}_scheduled_at", 0L)
        return scheduledAt > System.currentTimeMillis()
    }

    fun stopTodayPendingIntent(context: Context, period: String, dateText: String): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode("stop_$period"),
            Intent(context, GlobalReminderReceiver::class.java).apply {
                action = ACTION_STOP_TODAY
                putExtra(EXTRA_PERIOD, period)
                putExtra(EXTRA_DATE, dateText)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun isStopTodayAction(action: String?): Boolean {
        return action == ACTION_STOP_TODAY
    }

    private fun scheduleAt(context: Context, period: String, dateText: String, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context,
            requestCode(period),
            Intent(context, GlobalReminderReceiver::class.java).apply {
                action = ACTION_REMIND
                putExtra(EXTRA_PERIOD, period)
                putExtra(EXTRA_DATE, dateText)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (!alarmManager.canScheduleExactAlarms()) {
            alarmManager.cancel(intent)
            intent.cancel()
            context.getSharedPreferences("reminder_diagnostics", Context.MODE_PRIVATE)
                .edit()
                .remove("${period}_scheduled_at")
                .putString("${period}_schedule_status", "精确提醒权限未开启，未创建提醒")
                .apply()
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, intent)
        context.getSharedPreferences("reminder_diagnostics", Context.MODE_PRIVATE)
            .edit()
            .putLong("${period}_scheduled_at", triggerAtMillis)
            .putString("${period}_schedule_status", "已创建精确提醒")
            .apply()
    }

    private fun requestCode(period: String): Int {
        return when (period) {
            "morning" -> 9101
            "afternoon" -> 9102
            "evening" -> 9103
            "stop_morning" -> 9111
            "stop_afternoon" -> 9112
            else -> 9113
        }
    }
}
