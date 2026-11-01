package com.xdl.upward.ui.config

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import com.xdl.upward.reminder.GlobalReminderScheduler
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ReminderSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val reminderDiagnostics = context.getSharedPreferences("reminder_diagnostics", Context.MODE_PRIVATE)
    val globalMorningReminderTime by viewModel.globalMorningReminderTime.collectAsStateWithLifecycle()
    val globalAfternoonReminderTime by viewModel.globalAfternoonReminderTime.collectAsStateWithLifecycle()
    val globalEveningReminderTime by viewModel.globalEveningReminderTime.collectAsStateWithLifecycle()
    val globalMorningReminderEnabled by viewModel.globalMorningReminderEnabled.collectAsStateWithLifecycle()
    val globalAfternoonReminderEnabled by viewModel.globalAfternoonReminderEnabled.collectAsStateWithLifecycle()
    val globalEveningReminderEnabled by viewModel.globalEveningReminderEnabled.collectAsStateWithLifecycle()
    var editingReminderPeriod by remember { mutableStateOf("") }
    var canScheduleExactAlarms by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms())
    }
    var reminderDiagnosticsVersion by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canScheduleExactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    alarmManager.canScheduleExactAlarms()
                reminderDiagnosticsVersion++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (editingReminderPeriod.isNotBlank()) {
        val currentTime = when (editingReminderPeriod) {
            "morning" -> globalMorningReminderTime
            "afternoon" -> globalAfternoonReminderTime
            else -> globalEveningReminderTime
        }
        val parts = currentTime.split(":")
        val timePickerState = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: when (editingReminderPeriod) {
                "morning" -> 7
                "afternoon" -> 13
                else -> 19
            },
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { editingReminderPeriod = "" },
            title = { Text("选择提醒时间") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateGlobalReminderTime(editingReminderPeriod, timePickerState.hour, timePickerState.minute)
                        editingReminderPeriod = ""
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingReminderPeriod = "" }) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SettingsHeaderBar(
            title = "提醒设置",
            onBack = onBack,
            actions = {
                SettingsActionButton(
                    icon = LucideR.drawable.lucide_ic_check,
                    contentDescription = "保存",
                    onClick = { viewModel.save(onSaved) }
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (!canScheduleExactAlarms) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                })
                            }
                        }
                        .padding(14.dp)
                ) {
                    Text(
                        text = "警告：精确提醒权限未开启",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "当前不会创建上午、下午和晚上的提醒。点击这里前往系统设置开启“闹钟和提醒”权限，返回后再保存一次设置。",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_sunrise,
                title = "上午提醒：$globalMorningReminderTime",
                description = "上午提醒到点后每 10 分钟重复，直到手动停止本段提醒。",
                onClick = { editingReminderPeriod = "morning" },
                showChevron = false,
                trailing = {
                    Switch(
                        checked = globalMorningReminderEnabled,
                        onCheckedChange = { viewModel.updateGlobalReminderEnabled("morning", it) }
                    )
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_sun,
                title = "下午提醒：$globalAfternoonReminderTime",
                description = "下午提醒到点后每 10 分钟重复，直到手动停止本段提醒。",
                onClick = { editingReminderPeriod = "afternoon" },
                showChevron = false,
                trailing = {
                    Switch(
                        checked = globalAfternoonReminderEnabled,
                        onCheckedChange = { viewModel.updateGlobalReminderEnabled("afternoon", it) }
                    )
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_moon,
                title = "晚上提醒：$globalEveningReminderTime",
                description = "晚上提醒到点后每 10 分钟重复，直到手动停止本段提醒。",
                onClick = { editingReminderPeriod = "evening" },
                showChevron = false,
                trailing = {
                    Switch(
                        checked = globalEveningReminderEnabled,
                        onCheckedChange = { viewModel.updateGlobalReminderEnabled("evening", it) }
                    )
                }
            )

            Spacer(modifier = Modifier.height(22.dp))
            SettingsSectionTitle("提醒诊断")
            Spacer(modifier = Modifier.height(10.dp))
            val diagnosticsFormatter = remember {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
            }
            @Suppress("UNUSED_EXPRESSION")
            reminderDiagnosticsVersion
            listOf(
                "morning" to "上午",
                "afternoon" to "下午",
                "evening" to "晚上"
            ).forEachIndexed { index, (period, periodName) ->
                val scheduledAt = reminderDiagnostics.getLong("${period}_scheduled_at", 0L)
                val receivedAt = reminderDiagnostics.getLong("${period}_received_at", 0L)
                val delayMillis = reminderDiagnostics.getLong("${period}_delay_millis", 0L)
                val scheduleStatus = reminderDiagnostics.getString("${period}_schedule_status", null) ?: "尚未创建提醒"
                val notificationStatus = reminderDiagnostics.getString("${period}_notification_status", null) ?: "尚无通知记录"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "$periodName：$scheduleStatus",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val alarmInSystem = GlobalReminderScheduler.hasActiveAlarm(context, period)
                    Text(
                        text = if (alarmInSystem) {
                            "系统内闹钟：存在"
                        } else {
                            "系统内闹钟：不存在（可能已被系统清理）"
                        },
                        color = if (alarmInSystem) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (scheduledAt > 0L) {
                            "当前计划：${diagnosticsFormatter.format(Instant.ofEpochMilli(scheduledAt))}"
                        } else {
                            "当前计划：无"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (receivedAt > 0L) {
                            val delaySeconds = delayMillis.coerceAtLeast(0L) / 1000L
                            "最近触发：${diagnosticsFormatter.format(Instant.ofEpochMilli(receivedAt))}，延迟 ${delaySeconds} 秒"
                        } else {
                            "最近触发：尚无记录"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "通知结果：$notificationStatus",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (index < 2) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}
