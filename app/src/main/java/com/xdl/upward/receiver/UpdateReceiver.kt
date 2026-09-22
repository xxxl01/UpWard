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

/**
 * 覆盖安装（MY_PACKAGE_REPLACED）后自动恢复：
 * 1. 重排全部闹钟（覆盖安装可能清掉系统里的闹钟，代码里也没有其他时机补排）
 * 2. 拉起后台常驻服务（覆盖安装会杀掉进程，KeepAliveService 会随之熄火）
 */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // 尽力拉起常驻服务；广播接收器阶段启动前台服务在部分系统上可能受限，失败不影响闹钟重排
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
