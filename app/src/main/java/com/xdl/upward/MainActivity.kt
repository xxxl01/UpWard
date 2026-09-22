package com.xdl.upward

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.ConfigEntity
import com.xdl.upward.reminder.GlobalReminderScheduler
import com.xdl.upward.service.KeepAliveService
import com.xdl.upward.ui.navigation.AppNavGraph
import com.xdl.upward.ui.theme.UpWardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            val configDao = AppDatabase.getInstance(applicationContext).configDao()
            configDao.getValue("message_context_count")
            if (configDao.getByKey("initial_date") == null) {
                configDao.insert(ConfigEntity(key = "initial_date", value = LocalDate.now().toString()))
            }
            GlobalReminderScheduler.reschedule(applicationContext)
        }
        // 后台常驻服务开关开启时，每次启动 App 都拉起它：
        // 覆盖安装/系统清理会杀掉进程，只有打开 App 时才能可靠恢复常驻保护
        if (KeepAliveService.isEnabled(applicationContext)) {
            runCatching { KeepAliveService.start(applicationContext) }
        }
        enableEdgeToEdge()
        setContent {
            UpWardTheme {
                AppNavGraph()
            }
        }
        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val preferences = getSharedPreferences("permission_prompts", MODE_PRIVATE)
        if (preferences.getBoolean("notification_requested", false)) {
            return
        }
        preferences.edit().putBoolean("notification_requested", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION_PERMISSION)
    }

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }
}
