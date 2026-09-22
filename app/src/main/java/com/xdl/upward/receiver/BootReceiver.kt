package com.xdl.upward.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xdl.upward.reminder.GlobalReminderScheduler
import com.xdl.upward.reminder.RequiredMessageReminderScheduler
import com.xdl.upward.service.KeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (KeepAliveService.isEnabled(context)) {
                runCatching { KeepAliveService.start(context) }
            }
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RequiredMessageReminderScheduler.rescheduleAll(context.applicationContext)
                    GlobalReminderScheduler.reschedule(context.applicationContext)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
