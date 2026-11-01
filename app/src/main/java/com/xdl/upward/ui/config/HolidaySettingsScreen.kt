package com.xdl.upward.ui.config

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import java.time.LocalDate

@Composable
fun HolidaySettingsScreen(
    onBack: () -> Unit,
    viewModel: HolidaySettingsViewModel = viewModel()
) {
    val yearCounts by viewModel.yearCounts.collectAsStateWithLifecycle()
    val updatingYears by viewModel.updatingYears.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    val currentYear = remember { LocalDate.now().year }
    val years = listOf(currentYear, currentYear + 1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SettingsHeaderBar(
            title = "节假日",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "这里缓存中国法定节假日与调休数据。AI 聊天时会读取当天信息，缺少对应年份数据时自动联网获取；也可以在这里手动更新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            years.forEach { year ->
                val count = yearCounts[year] ?: 0
                val updating = updatingYears.contains(year)
                val description = when {
                    updating -> "正在更新…"
                    count > 0 -> "已缓存 $count 条，点击重新更新"
                    else -> "尚未缓存，点击从网络获取"
                }
                SettingsEntryItem(
                    icon = LucideR.drawable.lucide_ic_calendar_days,
                    title = "$year 年",
                    description = description,
                    onClick = { viewModel.refresh(year) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
