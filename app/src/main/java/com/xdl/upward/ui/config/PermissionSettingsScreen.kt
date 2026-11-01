package com.xdl.upward.ui.config

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.composables.icons.lucide.R as LucideR
import com.xdl.upward.service.KeepAliveService

@Composable
fun PermissionSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    var keepAliveEnabled by remember { mutableStateOf(KeepAliveService.isEnabled(context)) }
    var canScheduleExactAlarms by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms())
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canScheduleExactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    alarmManager.canScheduleExactAlarms()
                keepAliveEnabled = KeepAliveService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SettingsHeaderBar(
            title = "权限设置",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_shield,
                title = if (keepAliveEnabled) "前台常驻服务：已开启" else "前台常驻服务：未开启",
                description = if (keepAliveEnabled) "点击停止常驻服务；停止后开机也不会自动恢复。" else "点击启动常驻服务；开启后会显示一条常驻通知，并在开机后自动恢复。",
                onClick = {
                    if (keepAliveEnabled) {
                        KeepAliveService.stop(context)
                        keepAliveEnabled = false
                    } else {
                        KeepAliveService.start(context)
                        keepAliveEnabled = true
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_bell,
                title = "通知权限",
                description = "允许应用发送提醒通知；已授权后可继续管理通知开关。",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_clock,
                title = if (canScheduleExactAlarms) "精确提醒权限：已开启" else "精确提醒权限：未开启",
                description = if (canScheduleExactAlarms) {
                    "提醒将准点触发。点击可进入应用详情。"
                } else {
                    "未开启时不会创建任何提醒，请点击开启“闹钟和提醒”权限。"
                },
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (!alarmManager.canScheduleExactAlarms()) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } else {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            })
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_hard_drive,
                title = "后台常驻权限",
                description = "打开电池优化设置，将 UpWard 设为不受限制，减少后台被清理。",
                onClick = {
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } else {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_settings,
                title = "自启动、关联启动权限",
                description = "跳转到手机管家或系统应用管理，请允许自启动、关联启动和后台运行。",
                onClick = { openAutoStartSettings(context) }
            )
        }
    }
}

private fun openAutoStartSettings(context: Context) {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val intents = when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"))
        )
        manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> listOf(
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"))
        )
        manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
        )
        manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
        )
        manufacturer.contains("meizu") -> listOf(
            Intent().setComponent(ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"))
        )
        manufacturer.contains("samsung") -> listOf(
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"))
        )
        else -> emptyList()
    }

    for (intent in intents) {
        if (runCatching { context.startActivity(intent) }.isSuccess) {
            return
        }
    }

    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(fallbackIntent)
}
