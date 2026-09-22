package com.xdl.upward.ui.project

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import com.xdl.upward.data.local.ProjectEntity

@Composable
fun ProjectListScreen(
    onCreateProject: () -> Unit,
    onOpenProject: (Long) -> Unit,
    onOpenConfigSettings: () -> Unit,
    onOpenViolationChat: () -> Unit,
    viewModel: ProjectListViewModel = viewModel()
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val violationCount by viewModel.violationCount.collectAsStateWithLifecycle()
    val projectYesterdayStatuses by viewModel.projectYesterdayStatuses.collectAsStateWithLifecycle()
    val projectTodayStatuses by viewModel.projectTodayStatuses.collectAsStateWithLifecycle()
    val globalReminderStatuses by viewModel.globalReminderStatuses.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showYesterdayStatusDialog by remember { mutableStateOf(false) }
    var showTodayStatusDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkViolationOnEnter()
                viewModel.refreshGlobalReminderStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        ProjectListHeader(
            violationCount = violationCount,
            onShowTodayStatus = {
                viewModel.loadProjectTodayStatuses()
                showTodayStatusDialog = true
            },
            onShowYesterdayStatus = {
                viewModel.loadProjectYesterdayStatuses()
                showYesterdayStatusDialog = true
            },
            onOpenViolationChat = onOpenViolationChat,
            onOpenConfigSettings = onOpenConfigSettings,
            onCreateProject = onCreateProject
        )

        if (projects.isEmpty()) {
            EmptyProjectView(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectListItem(
                        project = project,
                        onClick = { onOpenProject(project.id) }
                    )
                }
            }
        }

        ReminderStatusBar(
            statuses = globalReminderStatuses,
            onClick = onOpenConfigSettings
        )
    }

    if (showYesterdayStatusDialog) {
        ProjectDayStatusDialog(
            title = "昨日打卡状态",
            statuses = projectYesterdayStatuses,
            onDismiss = { showYesterdayStatusDialog = false }
        )
    }
    if (showTodayStatusDialog) {
        ProjectDayStatusDialog(
            title = "今日完成情况",
            statuses = projectTodayStatuses,
            onDismiss = { showTodayStatusDialog = false }
        )
    }
}

@Composable
private fun ProjectListHeader(
    violationCount: Int,
    onShowTodayStatus: () -> Unit,
    onShowYesterdayStatus: () -> Unit,
    onOpenViolationChat: () -> Unit,
    onOpenConfigSettings: () -> Unit,
    onCreateProject: () -> Unit
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
            Text(
                text = "UpWard",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            HeaderIconButton(
                icon = LucideR.drawable.lucide_ic_shield_alert,
                contentDescription = "违规记录",
                onClick = onOpenViolationChat,
                iconTint = violationBadgeColor(violationCount, MaterialTheme.colorScheme.error),
                badgeText = violationCount.toString(),
                badgeColor = violationBadgeColor(violationCount, MaterialTheme.colorScheme.error)
            )
            Spacer(modifier = Modifier.width(4.dp))
            HeaderIconButton(
                icon = LucideR.drawable.lucide_ic_circle_check,
                contentDescription = "今日完成情况",
                onClick = onShowTodayStatus,
                iconTint = Color(0xFF2E7D32)
            )
            Spacer(modifier = Modifier.width(4.dp))
            HeaderIconButton(
                icon = LucideR.drawable.lucide_ic_calendar,
                contentDescription = "昨日打卡状态",
                onClick = onShowYesterdayStatus
            )
            Spacer(modifier = Modifier.width(4.dp))
            HeaderIconButton(
                icon = LucideR.drawable.lucide_ic_settings,
                contentDescription = "设置",
                onClick = onOpenConfigSettings
            )
            Spacer(modifier = Modifier.width(4.dp))
            HeaderIconButton(
                icon = LucideR.drawable.lucide_ic_plus,
                contentDescription = "新建项目",
                onClick = onCreateProject
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun HeaderIconButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.error
) {
    // 带徽标时外层放大到 48dp，圆形按钮 40dp 居中：徽标悬在按钮右上角外侧，
    // 不会被圆按钮的圆形裁剪截掉。
    Box(
        modifier = Modifier.size(if (badgeText != null) 48.dp else 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        if (badgeText != null) {
            Text(
                text = badgeText,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

// 违规次数档位色：0 绿（安全）、1-3 黄（提醒）、4-8 红（危险）、9 及以上深红（达到惩罚线后仍会继续累加）。
private fun violationBadgeColor(violationCount: Int, errorColor: Color): Color = when (violationCount) {
    0 -> Color(0xFF2E7D32)
    1, 2, 3 -> Color(0xFFF9A825)
    in 4..8 -> errorColor
    else -> Color(0xFF7F0000)
}

@Composable
private fun ProjectDayStatusDialog(
    title: String,
    statuses: List<ProjectYesterdayCheckStatus>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (statuses.isEmpty()) {
                    Text(
                        text = "当前没有项目",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    statuses.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.projectName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (item.isViolation) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (item.isChallenge) {
                                ProjectPeriodIcon(checked = item.hasMorningMessage, label = "挑战")
                            } else if (item.checkInMode == "once_per_day") {
                                ProjectPeriodIcon(checked = item.hasMorningMessage, label = "每日")
                            } else {
                                ProjectPeriodIcon(checked = item.hasMorningMessage, label = "4-13")
                                Spacer(modifier = Modifier.width(8.dp))
                                ProjectPeriodIcon(checked = item.hasAfternoonMessage, label = "13-19:30")
                                Spacer(modifier = Modifier.width(8.dp))
                                ProjectPeriodIcon(checked = item.hasEveningMessage, label = "19:30-次日4")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun ProjectPeriodIcon(
    checked: Boolean?,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            painter = painterResource(
                when (checked) {
                    true -> LucideR.drawable.lucide_ic_circle_check
                    false -> LucideR.drawable.lucide_ic_circle_x
                    null -> LucideR.drawable.lucide_ic_circle_question_mark
                }
            ),
            contentDescription = label,
            tint = when (checked) {
                true -> Color(0xFF2E7D32)
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            },
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun EmptyProjectView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_target),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "还没有项目",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "点击右上角 + 新建一个自律项目",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReminderStatusBar(
    statuses: List<GlobalReminderStatus>,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_bell),
                contentDescription = "提醒状态",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "定时提醒",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "点击管理",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            statuses.forEach { status ->
                ReminderStatusItem(
                    status = status,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ReminderStatusItem(
    status: GlobalReminderStatus,
    modifier: Modifier = Modifier
) {
    val periodIcon = when (status.period) {
        "morning" -> LucideR.drawable.lucide_ic_sunrise
        "afternoon" -> LucideR.drawable.lucide_ic_sun
        else -> LucideR.drawable.lucide_ic_moon
    }
    val statusColor = if (status.enabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            painter = painterResource(periodIcon),
            contentDescription = status.label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = status.time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            painter = painterResource(
                if (status.enabled) LucideR.drawable.lucide_ic_circle_check
                else LucideR.drawable.lucide_ic_circle_x
            ),
            contentDescription = if (status.enabled) "已开启" else "未开启",
            tint = statusColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ProjectListItem(
    project: ProjectEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_target),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = project.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(LucideR.drawable.lucide_ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}
