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
import androidx.core.content.ContextCompat
import com.xdl.upward.MainActivity
import com.xdl.upward.R
import com.xdl.upward.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class RequiredMessageReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val projectId = intent.getLongExtra(RequiredMessageReminderScheduler.EXTRA_PROJECT_ID, 0L)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (projectId > 0L) {
                    handleReminder(context.applicationContext, projectId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReminder(context: Context, projectId: Long) {
        val project = AppDatabase.getInstance(context).projectDao().getProject(projectId) ?: return
        if (project.nextRequiredMessageStartAt.isBlank() || project.nextRequiredMessageEndAt.isBlank()) {
            RequiredMessageReminderScheduler.cancel(context, projectId)
            return
        }

        val startTime = try {
            OffsetDateTime.parse(project.nextRequiredMessageStartAt)
        } catch (e: Exception) {
            RequiredMessageReminderScheduler.cancel(context, projectId)
            return
        }
        val endTime = try {
            OffsetDateTime.parse(project.nextRequiredMessageEndAt)
        } catch (e: Exception) {
            RequiredMessageReminderScheduler.cancel(context, projectId)
            return
        }
        val now = OffsetDateTime.now()
        if (now.isBefore(startTime)) {
            RequiredMessageReminderScheduler.schedule(context, project)
            return
        }
        if (!now.isBefore(endTime.plusMinutes(1))) {
            RequiredMessageReminderScheduler.cancel(context, projectId)
            return
        }

        showNotification(context, project.id, project.name, startTime, endTime)
        val nextTime = now.plusMinutes(10)
        if (nextTime.isBefore(endTime)) {
            RequiredMessageReminderScheduler.scheduleNext(context, project.id, nextTime.toInstant().toEpochMilli())
        } else {
            RequiredMessageReminderScheduler.cancel(context, project.id)
        }
    }

    private fun showNotification(
        context: Context,
        projectId: Long,
        projectName: String,
        startTime: OffsetDateTime,
        endTime: OffsetDateTime
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "指定回复提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "提醒你在指定时间段内完成项目回复"
        }
        manager.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            context,
            projectId.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(projectName)
            .setContentText("${startTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))} - ${endTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))}")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        manager.notify((2000L + projectId).toInt(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "required_message_reminder"
    }
}
