package com.xdl.upward.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.xdl.upward.MainActivity
import com.xdl.upward.R
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.repository.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime

class GlobalReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val dateText = intent.getStringExtra(GlobalReminderScheduler.EXTRA_DATE).orEmpty()
        val period = intent.getStringExtra(GlobalReminderScheduler.EXTRA_PERIOD).orEmpty()
        CoroutineScope(Dispatchers.IO).launch {
            var repository: ConfigRepository? = null
            try {
                if (period.isBlank()) return@launch
                val currentRepository = ConfigRepository(AppDatabase.getInstance(context).configDao())
                repository = currentRepository
                if (currentRepository.getValue("global_reminder_${period}_enabled") == "0") {
                    GlobalReminderScheduler.cancel(context, period)
                    context.getSystemService(NotificationManager::class.java).cancel(notificationId(period))
                    return@launch
                }
                if (GlobalReminderScheduler.isStopTodayAction(intent.action)) {
                    val today = if (dateText.isBlank()) LocalDate.now().toString() else dateText
                    currentRepository.setValue("global_reminder_stopped_${period}_date", today)
                    GlobalReminderScheduler.cancel(context, period)
                    context.getSystemService(NotificationManager::class.java).cancel(notificationId(period))
                    GlobalReminderScheduler.schedule(context, period, timeText(currentRepository, period))
                    return@launch
                }

                val today = LocalDate.now().toString()
                val receivedAt = System.currentTimeMillis()
                val diagnostics = context.getSharedPreferences("reminder_diagnostics", Context.MODE_PRIVATE)
                val scheduledAt = diagnostics.getLong("${period}_scheduled_at", 0L)
                diagnostics.edit()
                    .putLong("${period}_received_at", receivedAt)
                    .putLong("${period}_delay_millis", if (scheduledAt > 0L) receivedAt - scheduledAt else 0L)
                    .remove("${period}_receiver_error")
                    .apply()
                if (currentRepository.getValue("global_reminder_stopped_${period}_date") == today) {
                    GlobalReminderScheduler.schedule(context, period, timeText(currentRepository, period))
                    return@launch
                }
                val notificationResult = showNotification(context, period, today)
                diagnostics.edit()
                    .putString("${period}_notification_status", notificationResult)
                    .apply()
                val nextTime = OffsetDateTime.now().plusMinutes(10)
                if (nextTime.toLocalDate() == LocalDate.now()) {
                    // 还在当天：每 10 分钟重复提醒
                    GlobalReminderScheduler.scheduleNext(context, period, today, nextTime.toInstant().toEpochMilli())
                } else {
                    // 已跨过午夜：停止当天的重复循环，由第二天该时段的正常提醒接管
                    GlobalReminderScheduler.schedule(context, period, timeText(currentRepository, period))
                }
            } catch (error: Throwable) {
                Log.e(TAG, "处理 $period 提醒失败，尝试重新排程", error)
                context.getSharedPreferences("reminder_diagnostics", Context.MODE_PRIVATE)
                    .edit()
                    .putString("${period}_receiver_error", error.message ?: error.javaClass.simpleName)
                    .apply()
                try {
                    if (GlobalReminderScheduler.isStopTodayAction(intent.action)) {
                        GlobalReminderScheduler.schedule(
                            context,
                            period,
                            repository?.let { timeText(it, period) } ?: GlobalReminderScheduler.defaultTime(period)
                        )
                    } else {
                        val nextTime = OffsetDateTime.now().plusMinutes(10)
                        if (nextTime.toLocalDate() == LocalDate.now()) {
                            GlobalReminderScheduler.scheduleNext(
                                context,
                                period,
                                LocalDate.now().toString(),
                                nextTime.toInstant().toEpochMilli()
                            )
                        } else {
                            GlobalReminderScheduler.schedule(
                                context,
                                period,
                                repository?.let { timeText(it, period) }
                                    ?: GlobalReminderScheduler.defaultTime(period)
                            )
                        }
                    }
                } catch (rescheduleError: Throwable) {
                    Log.e(TAG, "重新排程 $period 提醒失败", rescheduleError)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun timeText(repository: ConfigRepository, period: String): String {
        return when (period) {
            "morning" -> repository.getValue("global_reminder_morning_time") ?: "07:00"
            "afternoon" -> repository.getValue("global_reminder_afternoon_time") ?: "13:00"
            else -> repository.getValue("global_reminder_evening_time") ?: "19:30"
        }
    }

    private fun showNotification(context: Context, period: String, dateText: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return "通知权限未开启，未发送通知"
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "固定提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "按设置时间重复提醒"
            }
        )
        val openIntent = PendingIntent.getActivity(
            context,
            notificationId(period),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val periodName = when (period) {
            "morning" -> "上午"
            "afternoon" -> "下午"
            else -> "晚上"
        }
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("提醒一下")
            .setContentText("$periodName 该看看进展了")
            .setContentIntent(openIntent)
            .addAction(0, "停止本段提醒", GlobalReminderScheduler.stopTodayPendingIntent(context, period, dateText))
            .setAutoCancel(false)
            .build()

        manager.notify(notificationId(period), notification)
        return "通知已提交给系统"
    }

    companion object {
        private const val TAG = "GlobalReminderReceiver"
        private const val CHANNEL_ID = "global_reminder"

        private fun notificationId(period: String): Int {
            return when (period) {
                "morning" -> 9201
                "afternoon" -> 9202
                else -> 9203
            }
        }
    }
}
