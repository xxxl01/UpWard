package com.xdl.upward.ui.config

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR

@Composable
fun ConfigSettingsScreen(
    onBack: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenHoliday: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenPermission: () -> Unit,
    onOpenApi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SettingsHeaderBar(
            title = "设置",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_settings,
                title = "通用设置",
                description = "上下文消息数量、整理上下文提示词与所用 API。",
                onClick = onOpenGeneral
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_bell,
                title = "提醒设置",
                description = "上午、下午、晚上的定时提醒时间与提醒诊断。",
                onClick = onOpenReminder
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_calendar_days,
                title = "节假日",
                description = "维护中国法定节假日与调休数据，供 AI 参考当天日期。",
                onClick = onOpenHoliday
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_database,
                title = "数据备份",
                description = "导出或恢复全量备份。",
                onClick = onOpenBackup
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_shield,
                title = "权限设置",
                description = "常驻服务、通知、精确提醒、后台运行等权限。",
                onClick = onOpenPermission
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_server,
                title = "API 设置",
                description = "管理 AI 接口配置，选择当前使用的 API。",
                onClick = onOpenApi
            )
        }
    }
}
