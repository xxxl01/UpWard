package com.xdl.upward.ui.violation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import com.xdl.upward.data.local.PaymentRecordEntity
import com.xdl.upward.domain.PaymentVerifier

@Composable
fun ViolationChatScreen(
    onBack: () -> Unit,
    viewModel: ViolationChatViewModel = viewModel()
) {
    val violationCount by viewModel.violationCount.collectAsStateWithLifecycle()
    val records by viewModel.paymentRecords.collectAsStateWithLifecycle()
    val verifying by viewModel.verifying.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val noticeMessage by viewModel.noticeMessage.collectAsStateWithLifecycle()
    val leaveDateThisWeek by viewModel.leaveDateThisWeek.collectAsStateWithLifecycle()
    val leaving by viewModel.leaving.collectAsStateWithLifecycle()
    var leaveDialogVisible by remember { mutableStateOf(false) }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.submitPayment(uri)
    }
    val punished = violationCount >= 10

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        ViolationHeaderBar(
            title = "违规惩罚 $violationCount 次",
            onBack = onBack,
            leaveAvailable = leaveDateThisWeek.isEmpty(),
            leaveEnabled = !leaving,
            onLeave = { leaveDialogVisible = true }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            item {
                ViolationWarningBanner(violationCount = violationCount)
                Spacer(modifier = Modifier.height(12.dp))
                if (punished) {
                    Button(
                        onClick = {
                            pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        enabled = !verifying,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (verifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在识别审核…")
                        } else {
                            Text("上传捐款截图")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (noticeMessage.isNotEmpty()) {
                    Text(
                        text = noticeMessage,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "捐款记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (records.isEmpty()) {
                    Text(
                        text = "暂无捐款记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            items(records, key = { it.id }) { record ->
                PaymentRecordItem(record)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (leaveDialogVisible) {
        AlertDialog(
            onDismissRequest = { leaveDialogVisible = false },
            title = { Text("请假") },
            text = {
                Text(
                    if (leaveDateThisWeek.isEmpty()) {
                        "请假会清除所有项目当前的汇报窗口，本周（周一到周日）只能请假一次。已经过期未汇报的窗口仍会照常记违规。确认请假？"
                    } else {
                        "本周已经在 $leaveDateThisWeek 请过假了，下周一凌晨 4 点后可再次请假。"
                    }
                )
            },
            confirmButton = {
                if (leaveDateThisWeek.isEmpty()) {
                    TextButton(
                        onClick = {
                            leaveDialogVisible = false
                            viewModel.requestLeave()
                        }
                    ) {
                        Text("确认请假")
                    }
                } else {
                    TextButton(onClick = { leaveDialogVisible = false }) {
                        Text("知道了")
                    }
                }
            },
            dismissButton = {
                if (leaveDateThisWeek.isEmpty()) {
                    TextButton(onClick = { leaveDialogVisible = false }) {
                        Text("取消")
                    }
                }
            }
        )
    }
}

@Composable
private fun ViolationHeaderBar(
    title: String,
    onBack: () -> Unit,
    leaveAvailable: Boolean,
    leaveEnabled: Boolean,
    onLeave: () -> Unit
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
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = leaveEnabled) { onLeave() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_calendar_off),
                    contentDescription = "请假",
                    // 本周还能请假时高亮，已用完时置灰（仍可点开查看说明）
                    tint = if (leaveAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun ViolationWarningBanner(violationCount: Int) {
    val punished = violationCount >= 10
    val containerColor = if (punished) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (punished) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    val title = if (punished) {
        "你已累计违规 $violationCount 次，处于惩罚状态"
    } else {
        "已累计违规 $violationCount 次"
    }
    val body = if (punished) {
        "所有项目已拦截消息发送。请向公益慈善机构捐款 4 元及以上，上传包含交易单号的捐款截图；审核通过后抵扣 10 次违规，并一并结算所有失败中的挑战惩罚。每笔捐款只能使用一次。"
    } else {
        "累计违规达到 10 次将触发惩罚：拦截所有项目消息发送。届时需向公益慈善机构捐款 4 元及以上并上传截图，审核通过后抵扣 10 次违规。"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            color = contentColor.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PaymentRecordItem(record: PaymentRecordEntity) {
    val purposeText = if (record.purpose == PaymentVerifier.PURPOSE_CHALLENGE) "挑战失败惩罚" else "违规惩罚"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${PaymentVerifier.formatYuan(record.amountCents)} 元",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = purposeText,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "收款方：${record.payee}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "单号：${record.orderNo}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        if (record.paidAt.isNotBlank()) {
            Text(
                text = "支付时间：${record.paidAt}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
