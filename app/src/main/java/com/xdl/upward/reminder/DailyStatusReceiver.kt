package com.xdl.upward.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xdl.upward.domain.DailyStatusRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DailyStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 先排下一次，保证即使落库失败闹钟链也不断
                DailyStatusScheduler.scheduleNext(appContext)
                DailyStatusRecorder.sync(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
