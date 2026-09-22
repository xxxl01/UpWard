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
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.composables.icons.lucide.R as LucideR
import com.xdl.upward.data.local.AiApiEntity
import com.xdl.upward.reminder.GlobalReminderScheduler
import com.xdl.upward.service.KeepAliveService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigSettingsScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onCreateApi: () -> Unit,
    onEditApi: (Long) -> Unit,
    viewModel: ConfigSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val reminderDiagnostics = context.getSharedPreferences("reminder_diagnostics", Context.MODE_PRIVATE)
    val messageContextCount by viewModel.messageContextCount.collectAsStateWithLifecycle()
    val contextSummaryPrompt by viewModel.contextSummaryPrompt.collectAsStateWithLifecycle()
    val contextSummaryApiId by viewModel.contextSummaryApiId.collectAsStateWithLifecycle()
    val globalMorningReminderTime by viewModel.globalMorningReminderTime.collectAsStateWithLifecycle()
    val globalAfternoonReminderTime by viewModel.globalAfternoonReminderTime.collectAsStateWithLifecycle()
    val globalEveningReminderTime by viewModel.globalEveningReminderTime.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val backupRunning by viewModel.backupRunning.collectAsStateWithLifecycle()
    val apis by viewModel.apis.collectAsStateWithLifecycle(initialValue = emptyList())
    var keepAliveEnabled by remember { mutableStateOf(KeepAliveService.isEnabled(context)) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var editingReminderPeriod by remember { mutableStateOf("") }
    var selectingContextSummaryApi by remember { mutableStateOf(false) }
    var canScheduleExactAlarms by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms())
    }
    var reminderDiagnosticsVersion by remember { mutableStateOf(0) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

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

    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("恢复全量备份") },
            text = { Text("恢复后会清空当前项目、消息、日报、API 配置和通用设置，并用备份文件覆盖。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingRestoreUri
                        pendingRestoreUri = null
                        if (uri != null) {
                            viewModel.restoreBackup(uri)
                        }
                    }
                ) {
                    Text("确认恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text("取消")
                }
            }
        )
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

    if (selectingContextSummaryApi) {
        AlertDialog(
            onDismissRequest = { selectingContextSummaryApi = false },
            title = { Text("选择整理上下文 API") },
            text = {
                Column {
                    if (apis.isEmpty()) {
                        Text("请先新建 API 配置")
                    } else {
                        apis.forEach { api ->
                            TextButton(
                                onClick = {
                                    viewModel.selectContextSummaryApi(api.id)
                                    selectingContextSummaryApi = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (api.id == contextSummaryApiId) "${api.model}（当前）" else api.model,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectingContextSummaryApi = false }) {
                    Text("关闭")
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
        ConfigSettingsHeaderBar(
            onBack = onBack,
            onCreateApi = onCreateApi,
            onSave = { viewModel.save(onSaved) }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "\u901a\u7528\u8bbe\u7f6e",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = messageContextCount,
                onValueChange = viewModel::updateMessageContextCount,
                label = { Text("\u4e0a\u4e0b\u6587\u6d88\u606f\u6570\u91cf") },
                supportingText = { Text("AI \u804a\u5929\u65f6\u643a\u5e26\u6700\u8fd1\u591a\u5c11\u6761\u6d88\u606f\uff0c\u9ed8\u8ba4 20\u3002") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = contextSummaryPrompt,
                onValueChange = viewModel::updateContextSummaryPrompt,
                label = { Text("整理上下文提示词") },
                supportingText = { Text("用于项目聊天里的“整理上下文”功能。") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            val contextSummaryApi = apis.firstOrNull { it.id == contextSummaryApiId }
            GlobalReminderTimeItem(
                title = "整理上下文 API：${contextSummaryApi?.model ?: "未选择"}",
                description = "批量整理上下文时使用此 API，不使用聊天默认 API。",
                onClick = { selectingContextSummaryApi = true }
            )
            if (!canScheduleExactAlarms) {
                Spacer(modifier = Modifier.height(10.dp))
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
            }
            Spacer(modifier = Modifier.height(10.dp))
            GlobalReminderTimeItem(
                title = "上午提醒：$globalMorningReminderTime",
                description = "上午提醒到点后每 10 分钟重复，直到手动停止本段提醒。",
                onClick = { editingReminderPeriod = "morning" }
            )
            Spacer(modifier = Modifier.height(10.dp))
            GlobalReminderTimeItem(
                title = "下午提醒：$globalAfternoonReminderTime",
                description = "下午提醒到点后每 10 分钟重复，直到手动停止本段提醒。",
                onClick = { editingReminderPeriod = "afternoon" }
            )
            Spacer(modifier = Modifier.height(10.dp))
            GlobalReminderTimeItem(
                title = "晚上提醒：$globalEveningReminderTime",
                description = "晚上提醒到点后每 10 分钟重复，直到手动停止本段提醒。",
                onClick = { editingReminderPeriod = "evening" }
            )

            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "提醒诊断",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
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

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "数据备份",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            PermissionSettingsItem(
                icon = LucideR.drawable.lucide_ic_settings,
                title = if (backupRunning) "正在处理数据备份" else "导出全量备份",
                description = "导出项目、消息、日报、API 配置、通用设置和聊天图片。",
                onClick = {
                    if (!backupRunning) {
                        exportBackupLauncher.launch("upward-backup-${LocalDate.now()}.zip")
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            PermissionSettingsItem(
                icon = LucideR.drawable.lucide_ic_settings,
                title = "恢复全量备份",
                description = "选择备份文件恢复；恢复会覆盖当前所有本地数据。",
                onClick = {
                    if (!backupRunning) {
                        restoreBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                }
            )
            if (backupMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = backupMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "权限设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            PermissionSettingsItem(
                icon = LucideR.drawable.lucide_ic_settings,
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
            PermissionSettingsItem(
                icon = LucideR.drawable.lucide_ic_settings,
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
            PermissionSettingsItem(
                icon = LucideR.drawable.lucide_ic_settings,
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
            PermissionSettingsItem(
                icon = LucideR.drawable.lucide_ic_settings,
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
            PermissionSettingsItem(
                icon = LucideR.drawable.lucide_ic_settings,
                title = "自启动、关联启动权限",
                description = "跳转到手机管家或系统应用管理，请允许自启动、关联启动和后台运行。",
                onClick = { openAutoStartSettings(context) }
            )

            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "API \u8bbe\u7f6e",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (apis.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onCreateApi() }
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(LucideR.drawable.lucide_ic_server),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "\u8fd8\u6ca1\u6709 API \u914d\u7f6e\uff0c\u70b9\u51fb\u65b0\u5efa",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                apis.forEach { api ->
                    ApiSettingsItem(
                        api = api,
                        onEdit = { onEditApi(api.id) },
                        onSelect = { viewModel.selectApi(api.id) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ConfigSettingsHeaderBar(
    onBack: () -> Unit,
    onCreateApi: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_arrow_left),
                    contentDescription = "\u8fd4\u56de",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "\u8bbe\u7f6e",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onCreateApi() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_plus),
                    contentDescription = "\u65b0\u5efa API",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onSave() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_check),
                    contentDescription = "\u4fdd\u5b58",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun GlobalReminderTimeItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    PermissionSettingsItem(
        icon = LucideR.drawable.lucide_ic_settings,
        title = title,
        description = description,
        onClick = onClick
    )
}

@Composable
private fun PermissionSettingsItem(
    icon: Int,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            painter = painterResource(LucideR.drawable.lucide_ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
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

@Composable
private fun ApiSettingsItem(
    api: AiApiEntity,
    onEdit: () -> Unit,
    onSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_server),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = api.model,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (api.selected) {
                        Icon(
                            painter = painterResource(LucideR.drawable.lucide_ic_circle_check),
                            contentDescription = "\u5f53\u524d",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "\u5f53\u524d",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = api.baseUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (api.supportImage) "温度 ${api.temperature} · 支持图片" else "温度 ${api.temperature}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onEdit() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_square_pen),
                    contentDescription = "\u7f16\u8f91",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "\u7f16\u8f91",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!api.selected) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_check),
                        contentDescription = "\u8bbe\u4e3a\u5f53\u524d",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "\u8bbe\u4e3a\u5f53\u524d",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
