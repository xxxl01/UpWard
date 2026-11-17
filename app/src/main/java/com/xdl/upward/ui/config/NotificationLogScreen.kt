package com.xdl.upward.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import com.xdl.upward.data.local.NotificationRecordEntity
import com.xdl.upward.domain.NotificationLog

@Composable
fun NotificationLogScreen(
    onBack: () -> Unit,
    viewModel: NotificationLogViewModel = viewModel()
) {
    val dates by viewModel.dates.collectAsStateWithLifecycle()
    val pageIndex by viewModel.pageIndex.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val index = pageIndex.coerceIn(0, (dates.size - 1).coerceAtLeast(0))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SettingsHeaderBar(title = "通知记录", onBack = onBack)
        if (dates.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "还没有通知记录。之后每次推送的提醒都会记在这里。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            PageSwitcher(
                label = "${dates[index]}（${records.size} 条）",
                position = "${index + 1}/${dates.size}",
                hasOlder = index < dates.size - 1,
                hasNewer = index > 0,
                onOlder = { viewModel.shift(1) },
                onNewer = { viewModel.shift(-1) }
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    NotificationItem(record)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/** 上一页/下一页切换条，通知记录与违规页的月份分页共用。 */
@Composable
fun PageSwitcher(
    label: String,
    position: String,
    hasOlder: Boolean,
    hasNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SwitchArrow(LucideR.drawable.lucide_ic_chevron_left, "更早", hasOlder, onOlder)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = position,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SwitchArrow(LucideR.drawable.lucide_ic_chevron_right, "更新", hasNewer, onNewer)
    }
}

@Composable
private fun SwitchArrow(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun NotificationItem(record: NotificationRecordEntity) {
    val kindText = if (record.kind == NotificationLog.KIND_REQUIRED_MESSAGE) "指定回复提醒" else "固定提醒"
    // created_at 形如 2026-11-16T07:00:05.123+08:00，取时分秒
    val time = record.createdAt.substringAfter('T', "").take(8)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = time,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = record.content, style = MaterialTheme.typography.bodySmall)
        Text(
            text = kindText,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
