package com.xdl.upward.ui.challenge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import com.xdl.upward.data.local.ChallengeCheckInEntity
import com.xdl.upward.data.local.ChallengeEntity
import com.xdl.upward.data.repository.ChallengeRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeScreen(
    projectId: Long,
    onBack: () -> Unit,
    viewModel: ChallengeViewModel = viewModel()
) {
    val challenge by viewModel.observeChallenge(projectId).collectAsStateWithLifecycle(initialValue = null)
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val goal by viewModel.goal.collectAsStateWithLifecycle()
    val dailyStandardPrompt by viewModel.dailyStandardPrompt.collectAsStateWithLifecycle()
    val paymentVerifying by viewModel.paymentVerifying.collectAsStateWithLifecycle()
    val paymentPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val currentChallenge = challenge
        if (uri != null && currentChallenge != null) {
            viewModel.submitPenaltyPayment(currentChallenge.id, uri)
        }
    }
    val startDate by viewModel.startDate.collectAsStateWithLifecycle()
    val endDate by viewModel.endDate.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val noticeMessage by viewModel.noticeMessage.collectAsStateWithLifecycle()
    val aiJudging by viewModel.aiJudging.collectAsStateWithLifecycle()
    val currentCheckInDate = viewModel.currentCheckInDate()

    var checkIns by remember(challenge?.id) { mutableStateOf(emptyList<ChallengeCheckInEntity>()) }
    LaunchedEffect(challenge?.id) {
        val challengeId = challenge?.id ?: return@LaunchedEffect
        viewModel.observeCheckIns(challengeId).collect { checkIns = it }
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var confirmSettleVisible by remember { mutableStateOf(false) }
    var confirmManualFailVisible by remember { mutableStateOf(false) }
    // 修改进行中的挑战（active，含开始日期未到的 pending）：编辑表单按修改模式展示。
    val isModifyMode = challenge != null && challenge!!.status == ChallengeRepository.STATUS_ACTIVE

    LaunchedEffect(projectId) {
        viewModel.load(projectId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        ChallengeHeaderBar(
            title = when {
                !editing -> "挑战"
                isModifyMode -> "修改挑战"
                else -> "设置挑战"
            },
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (noticeMessage.isNotEmpty()) {
                Text(
                    text = noticeMessage,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            when {
                editing -> ChallengeEditForm(
                    goal = goal,
                    dailyStandardPrompt = dailyStandardPrompt,
                    startDate = startDate,
                    endDate = endDate,
                    isModifyMode = isModifyMode,
                    oldStartDate = challenge?.startDate.orEmpty(),
                    oldEndDate = challenge?.endDate.orEmpty(),
                    aiJudging = aiJudging,
                    onGoalChange = viewModel::updateGoal,
                    onDailyStandardPromptChange = viewModel::updateDailyStandardPrompt,
                    onPickStartDate = { showStartDatePicker = true },
                    onPickEndDate = { showEndDatePicker = true },
                    onSave = { viewModel.save(projectId) },
                    onCancel = viewModel::cancelEditing
                )

                challenge == null -> EmptyChallengeView(
                    onStart = { viewModel.startEditing(null) }
                )

                else -> ChallengeDetailContent(
                    challenge = challenge!!,
                    checkIns = checkIns,
                    canSettle = challenge!!.status == ChallengeRepository.STATUS_ACTIVE &&
                        !LocalDate.parse(challenge!!.endDate).isAfter(currentCheckInDate),
                    canStartNewRound = challenge!!.status == ChallengeRepository.STATUS_SUCCESS ||
                        challenge!!.status == ChallengeRepository.STATUS_SETTLED,
                    onSettle = { confirmSettleVisible = true },
                    onManualFail = { confirmManualFailVisible = true },
                    onModify = { viewModel.startEditing(challenge) },
                    onStartNewRound = { viewModel.startEditing(challenge) },
                    paymentVerifying = paymentVerifying,
                    onUploadPayment = {
                        paymentPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    currentCheckInDate = currentCheckInDate
                )
            }
        }
    }

    if (showStartDatePicker) {
        ChallengeDatePickerDialog(
            initialDate = startDate,
            onDismiss = { showStartDatePicker = false },
            onConfirm = viewModel::updateStartDate
        )
    }

    if (showEndDatePicker) {
        ChallengeDatePickerDialog(
            initialDate = endDate,
            // 修改进行中的挑战时，截止日期只能选原截止日当天或之后（只能延后）。
            minDateMillis = if (isModifyMode) {
                challenge?.endDate?.let { oldEnd ->
                    runCatching {
                        LocalDate.parse(oldEnd).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    }.getOrNull()
                }
            } else null,
            onDismiss = { showEndDatePicker = false },
            onConfirm = viewModel::updateEndDate
        )
    }

    if (confirmSettleVisible) {
        val confirmChallenge = challenge
        AlertDialog(
            onDismissRequest = { confirmSettleVisible = false },
            title = { Text("结算挑战") },
            text = {
                Text(
                    if (confirmChallenge != null && confirmChallenge.status == ChallengeRepository.STATUS_ACTIVE) {
                        "确认结算本轮挑战？挑战期内每天都打卡则挑战成功；有缺卡则判定失败并进入惩罚流程。"
                    } else {
                        "确认结算本轮挑战？"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSettleVisible = false
                        viewModel.settle(projectId)
                    }
                ) {
                    Text("确认结算")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSettleVisible = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (confirmManualFailVisible) {
        AlertDialog(
            onDismissRequest = { confirmManualFailVisible = false },
            title = { Text("手动标记失败") },
            text = {
                Text("确认将当前挑战手动标记为失败？标记后立即进入失败惩罚流程，需捐款 3 元及以上并上传捐款截图，审核通过后结算，期间挑战无法继续打卡与修改。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmManualFailVisible = false
                        viewModel.failManually(projectId)
                    }
                ) {
                    Text("确认失败", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmManualFailVisible = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChallengeDatePickerDialog(
    initialDate: String,
    minDateMillis: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val initialMillis = runCatching {
        LocalDate.parse(initialDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())
    // minDateMillis 不为空时，只允许选择该日期当天及之后（用于修改模式下截止日期只能延后）。
    val datePickerState = if (minDateMillis == null) {
        rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    } else {
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= minDateMillis
            }
        )
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    onDismiss()
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun ChallengeHeaderBar(
    title: String,
    onBack: () -> Unit
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
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun ChallengeEditForm(
    goal: String,
    dailyStandardPrompt: String,
    startDate: String,
    endDate: String,
    isModifyMode: Boolean,
    oldStartDate: String,
    oldEndDate: String,
    aiJudging: Boolean,
    onGoalChange: (String) -> Unit,
    onDailyStandardPromptChange: (String) -> Unit,
    onPickStartDate: () -> Unit,
    onPickEndDate: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Text(
            text = "规则说明",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when {
                isModifyMode && !ChallengeViewModel.TEMP_ENABLE_AI_JUDGE_ON_MODIFY ->
                    "每天都必须完成目标并打卡，缺一天即挑战失败。修改进行中的挑战：截止日期只能延后；其余字段可直接修改（当前为临时设置，暂不做 AI 难度判定）。已打卡记录保留，从今天起按新标准考核。"
                isModifyMode ->
                    "每天都必须完成目标并打卡，缺一天即挑战失败。修改进行中的挑战：截止日期只能延后；修改目标或每日标准时，AI 会判断难度是增强还是减弱，只有增强才允许保存，减弱会被拒绝，挑战按原标准继续到截止日期。已打卡记录保留，从今天起按新标准考核。"
                !ChallengeViewModel.TEMP_ENABLE_AI_JUDGE_ON_MODIFY ->
                    "每天都必须完成目标并打卡，缺一天即挑战失败。开始日期前 AI 不会感知到挑战；挑战开始后可修改（当前为临时设置，暂不做 AI 难度判定），截止日期只能延后。"
                else ->
                    "每天都必须完成目标并打卡，缺一天即挑战失败。开始日期前 AI 不会感知到挑战；挑战开始后可修改，但只允许增强难度（由 AI 判定），截止日期只能延后。"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = goal,
        onValueChange = onGoalChange,
        label = { Text("挑战目标（每天的目标）") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = dailyStandardPrompt,
        onValueChange = onDailyStandardPromptChange,
        label = { Text("每日打卡标准") },
        supportingText = { Text("定义每天怎样才算完成，AI 将依据它判断你当天是否达成目标。") },
        minLines = 4,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    ChallengeInfoCard(title = "失败惩罚", content = ChallengeViewModel.FIXED_FAIL_PENALTY)
    Spacer(modifier = Modifier.height(12.dp))
    if (isModifyMode) {
        // 修改模式：开始日期锁定不可改，只读展示。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "开始日期",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (oldStartDate.isBlank()) "未设置" else
                        LocalDate.parse(oldStartDate).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "挑战已开始，开始日期不可修改",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_lock),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onPickStartDate() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "开始日期",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (startDate.isBlank()) "请选择开始日期（今天或未来）" else
                        LocalDate.parse(startDate).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                    color = if (startDate.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_calendar),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPickEndDate() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "截止日期",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    endDate.isBlank() -> "请选择截止日期（必须晚于开始日期）"
                    isModifyMode -> {
                        val formatted = LocalDate.parse(endDate).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
                        val oldFormatted = if (oldEndDate.isBlank()) "" else
                            LocalDate.parse(oldEndDate).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
                        if (endDate == oldEndDate) "$formatted（只能延后）" else "$formatted（已延后）"
                    }
                    else -> LocalDate.parse(endDate).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
                },
                color = if (endDate.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Icon(
            painter = painterResource(LucideR.drawable.lucide_ic_calendar),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextButton(
            onClick = onCancel,
            enabled = !aiJudging,
            modifier = Modifier.weight(1f)
        ) {
            Text("取消")
        }
        TextButton(
            onClick = onSave,
            enabled = !aiJudging,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                when {
                    aiJudging -> "AI 判定中…"
                    isModifyMode -> "保存修改"
                    else -> "开始挑战"
                }
            )
        }
    }
}

@Composable
private fun EmptyChallengeView(
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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
            text = "还没有设置挑战",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "设置每天的目标与截止日期，坚持打卡到截止日即挑战成功。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        TextButton(onClick = onStart) {
            Text("设置挑战")
        }
    }
}

@Composable
private fun ChallengeDetailContent(
    challenge: ChallengeEntity,
    checkIns: List<ChallengeCheckInEntity>,
    canSettle: Boolean,
    canStartNewRound: Boolean,
    onSettle: () -> Unit,
    onManualFail: () -> Unit,
    onModify: () -> Unit,
    onStartNewRound: () -> Unit,
    paymentVerifying: Boolean,
    onUploadPayment: () -> Unit,
    currentCheckInDate: LocalDate
) {
    // 开始日期未到：挑战处于“未开始”状态，AI 感知不到，也不产生打卡。
    val isPending = currentCheckInDate.isBefore(LocalDate.parse(challenge.startDate))
    val statusText = when {
        isPending -> "未开始"
        challenge.status == ChallengeRepository.STATUS_ACTIVE -> "进行中"
        challenge.status == ChallengeRepository.STATUS_FAILED -> "已失败 · 惩罚中"
        challenge.status == ChallengeRepository.STATUS_SUCCESS -> "已成功"
        else -> "已结算"
    }
    val statusColor = when {
        isPending -> MaterialTheme.colorScheme.onSurfaceVariant
        challenge.status == ChallengeRepository.STATUS_ACTIVE -> MaterialTheme.colorScheme.primary
        challenge.status == ChallengeRepository.STATUS_FAILED -> MaterialTheme.colorScheme.error
        challenge.status == ChallengeRepository.STATUS_SUCCESS -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 只渲染到今天为止的天数：未来日期尚未到来，不展示"未打卡"占位行，避免长周期挑战刷屏。
    val dayRange = remember(challenge.id, challenge.startDate, challenge.endDate) {
        val start = LocalDate.parse(challenge.startDate)
        val end = LocalDate.parse(challenge.endDate)
        val lastDay = if (end.isAfter(currentCheckInDate)) currentCheckInDate else end
        if (lastDay.isBefore(start)) emptyList()
        else generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(lastDay) }.toList()
    }
    val checkInDateSet = checkIns.map { it.date }.toSet()
    val totalDays = dayRange.size
    val doneCount = dayRange.count { it.toString() in checkInDateSet }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusText,
                    color = statusColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "挑战周期：${challenge.startDate} ~ ${challenge.endDate}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "打卡进度：$doneCount / ${dayRange.size} 天",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        ChallengeInfoCard(title = "挑战目标", content = challenge.goal)
        Spacer(modifier = Modifier.height(12.dp))
        ChallengeInfoCard(title = "每日打卡标准", content = challenge.dailyStandardPrompt)
        Spacer(modifier = Modifier.height(12.dp))
        // 失败惩罚已固定为捐款，旧挑战里存的自定义惩罚文本不再展示
        ChallengeInfoCard(title = "失败惩罚", content = ChallengeViewModel.FIXED_FAIL_PENALTY)

        when {
            // 进行中（含未开始的 pending）：允许修改，只能增强难度；到截止日当天后可同时结算。
            challenge.status == ChallengeRepository.STATUS_ACTIVE -> {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onModify,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("修改挑战")
                }
                if (canSettle) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onSettle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("结算挑战")
                    }
                }
                // 已开始的进行中挑战：可手动标记失败、放弃挑战并进入失败惩罚流程（未开始的不提供）。
                if (!isPending) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onManualFail,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("手动失败")
                    }
                }
            }
            canStartNewRound -> {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onStartNewRound,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开启新一轮挑战")
                }
            }
            challenge.status == ChallengeRepository.STATUS_FAILED -> {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "挑战已失败：向公益慈善机构捐款 3 元及以上，上传包含交易单号的捐款截图，识别审核通过后自动结算，结算后即可开启新一轮挑战。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onUploadPayment,
                    enabled = !paymentVerifying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (paymentVerifying) {
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
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "打卡记录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (isPending) {
            Text(
                text = "挑战尚未开始，将于 ${challenge.startDate} 生效，开始前 AI 不会感知到该挑战。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else if (dayRange.isEmpty()) {
            Text(
                text = "暂无打卡记录",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            // 今日打卡状态：挑战进行中且今天在周期内时，一眼看出今天打没打卡、漏了几天。
            val today = currentCheckInDate
            val challengeStartDate = LocalDate.parse(challenge.startDate)
            val challengeEndDate = LocalDate.parse(challenge.endDate)
            if (challenge.status == ChallengeRepository.STATUS_ACTIVE &&
                !today.isBefore(challengeStartDate) &&
                !today.isAfter(challengeEndDate)
            ) {
                val todayDone = today.toString() in checkInDateSet
                val missedDays = dayRange.count { it.isBefore(today) && it.toString() !in checkInDateSet }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (todayDone) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(
                            if (todayDone) LucideR.drawable.lucide_ic_circle_check
                            else LucideR.drawable.lucide_ic_clock
                        ),
                        contentDescription = null,
                        tint = if (todayDone) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (todayDone) "今天已打卡" else "今天还未打卡",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (todayDone) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                        if (missedDays > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "已漏卡 $missedDays 天，缺卡会被判定为挑战失败",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (todayDone) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            // 月历概览：从开始月到截止月按月排成日历表，日期格子标记已打卡/漏卡/今天/未来，
            // 打卡天数再多也是一眼扫完的几页月历，最近月份在上。
            var summaryDialog by remember(challenge.id) { mutableStateOf<Pair<LocalDate, String>?>(null) }
            val months = remember(challengeStartDate, challengeEndDate) {
                generateSequence(YearMonth.from(challengeEndDate)) { it.minusMonths(1) }
                    .takeWhile { !it.isBefore(YearMonth.from(challengeStartDate)) }
                    .toList()
            }
            months.forEach { month ->
                ChallengeCalendarMonth(
                    month = month,
                    challengeStartDate = challengeStartDate,
                    challengeEndDate = challengeEndDate,
                    today = today,
                    checkInDateSet = checkInDateSet,
                    checkIns = checkIns,
                    onDayClick = { date, summary -> summaryDialog = date to summary }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
            summaryDialog?.let { (date, summary) ->
                AlertDialog(
                    onDismissRequest = { summaryDialog = null },
                    title = {
                        Text(date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")))
                    },
                    text = { Text(summary) },
                    confirmButton = {
                        TextButton(onClick = { summaryDialog = null }) {
                            Text("关闭")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ChallengeInfoCard(
    title: String,
    content: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChallengeCalendarMonth(
    month: YearMonth,
    challengeStartDate: LocalDate,
    challengeEndDate: LocalDate,
    today: LocalDate,
    checkInDateSet: Set<String>,
    checkIns: List<ChallengeCheckInEntity>,
    onDayClick: (LocalDate, String) -> Unit
) {
    val daysInMonth = month.lengthOfMonth()
    // 当月挑战周期内、且已过（<= 今天）的天数与打卡数，用于月份标题统计。
    val monthElapsedDays = (1..daysInMonth).count { day ->
        val date = month.atDay(day)
        !date.isBefore(challengeStartDate) && !date.isAfter(challengeEndDate) && !date.isAfter(today)
    }
    val monthDone = (1..daysInMonth).count { day ->
        val date = month.atDay(day)
        !date.isBefore(challengeStartDate) && !date.isAfter(challengeEndDate) && !date.isAfter(today) &&
            date.toString() in checkInDateSet
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = month.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "已打卡 $monthDone / $monthElapsedDays 天",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        // 星期表头（周一开头）
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // 日期网格：周一 = 第 0 列
        val firstDayOffset = month.atDay(1).dayOfWeek.value - 1
        val totalCells = firstDayOffset + daysInMonth
        val weekCount = (totalCells + 6) / 7
        for (week in 0 until weekCount) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNumber = week * 7 + col - firstDayOffset + 1
                    val date = if (dayNumber in 1..daysInMonth) month.atDay(dayNumber) else null
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (date != null) {
                            val inPeriod = !date.isBefore(challengeStartDate) && !date.isAfter(challengeEndDate)
                            if (inPeriod) {
                                val done = date.toString() in checkInDateSet
                                val isToday = date == today
                                val future = date.isAfter(today)
                                val cellBackground = when {
                                    future -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    done -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                }
                                val cellTextColor = when {
                                    future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    done -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onErrorContainer
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(cellBackground)
                                        .then(
                                            if (isToday) {
                                                Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable {
                                            val summary = checkIns.firstOrNull { it.date == date.toString() }?.summary.orEmpty()
                                            if (summary.isNotBlank()) onDayClick(date, summary)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = cellTextColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
