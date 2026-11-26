package com.xdl.upward.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * 在每个打卡时段的边界（04:00 / 13:00 / 19:30）后 30 秒触发一次后台落库，
 * 时段刚结束就能把成功/失败记进 daily_status，不依赖用户打开 App。
 * 有精确闹钟权限时用精确闹钟，否则退化为非精确（可能晚几分钟，下一次同步会补齐）。
 */
object DailyStatusScheduler {
    private const val ACTION = "com.xdl.upward.action.DAILY_STATUS_SYNC"
    private const val REQUEST_CODE = 9201
    private val BOUNDARIES = listOf(LocalTime.of(4, 0), LocalTime.of(13, 0), LocalTime.of(19, 30))

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val now = OffsetDateTime.now()
        val triggerAt = (0..1).flatMap { dayOffset ->
            BOUNDARIES.map { now.toLocalDate().plusDays(dayOffset.toLong()).atTime(it).atOffset(now.offset).plusSeconds(30) }
        }.first { it.isAfter(now) }
        val intent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DailyStatusReceiver::class.java).apply { action = ACTION },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val millis = triggerAt.toInstant().toEpochMilli()
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }
}
