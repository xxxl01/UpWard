package com.xdl.upward.ui.project

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.composables.icons.lucide.R as LucideR
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xdl.upward.data.local.MessageEntity
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownText
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ProjectDetailScreen(
    projectId: Long,
    onBack: () -> Unit,
    onEditProject: () -> Unit,
    onOpenDailyRecords: () -> Unit,
    onOpenContextSummary: () -> Unit,
    onOpenChallenge: () -> Unit,
    viewModel: ProjectDetailViewModel = viewModel()
) {
    val projectFlow = remember(projectId) { viewModel.observeProject(projectId) }
    val project by projectFlow.collectAsStateWithLifecycle(initialValue = null)
    val messagesFlow = remember(projectId) { viewModel.observeMessages(projectId) }
    val messages by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val input by viewModel.input.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val aiThinking by viewModel.aiThinking.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val selectedImagePaths by viewModel.selectedImagePaths.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }.orEmpty()
            viewModel.importMessages(projectId, text)
        }
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.addSelectedImages(uris)
    }
    val displayMessages = remember(messages) { messages.asReversed() }
    var editingInnerVoice by remember { mutableStateOf(false) }
    var innerVoiceText by remember { mutableStateOf("") }
    var criticalMode by remember { mutableStateOf(false) }
    var experienceMode by remember { mutableStateOf(false) }
    val isNearBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex <= 1 }
    }

    LaunchedEffect(projectId) {
        listState.scrollToItem(0)
        viewModel.setCurrentProject(projectId)
        viewModel.checkRequiredMessageDeadline(projectId)
    }

    LaunchedEffect(project?.nextRequiredMessageEndAt) {
        val endTimeText = project?.nextRequiredMessageEndAt.orEmpty()
        if (endTimeText.isNotBlank()) {
            val endTime = try {
                OffsetDateTime.parse(endTimeText)
            } catch (e: Exception) {
                null
            }
            if (endTime != null) {
                val waitMillis = endTime.toInstant().toEpochMilli() - System.currentTimeMillis() + 1000L
                if (waitMillis > 0) {
                    delay(waitMillis)
                }
                viewModel.checkRequiredMessageDeadline(projectId)
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isNearBottom) {
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        ProjectHeaderBar(
            title = project?.name ?: "项目详情",
            onBack = onBack,
            onEditProject = onEditProject,
            onOpenDailyRecords = onOpenDailyRecords,
            onOpenContextSummary = onOpenContextSummary,
            onOpenChallenge = onOpenChallenge,
            onGenerateTodayRecord = { viewModel.generateTodayRecord(projectId) },
            onImportMessages = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
        )

        if (loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (aiThinking) "AI 思考中..." else "AI 正在回复...",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (!project?.nextRequiredMessageEndAt.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        val startTimeText = project?.nextRequiredMessageStartAt.orEmpty()
                        val startTime = try {
                            OffsetDateTime.parse(startTimeText)
                        } catch (e: Exception) {
                            null
                        }
                        if (startTime == null) {
                            Toast.makeText(context, "指定回复开始时间无效", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }

                        val reminderText = "UW:${project?.name.orEmpty()} DDL:${displayNextRequiredMessageTime(project?.nextRequiredMessageEndAt.orEmpty())}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            component = ComponentName("com.bzzzapp", "com.bzzzapp.ux.BZDetailsActivity")
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, reminderText)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "未安装 BZ Reminder", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "指定回复时间：${displayNextRequiredMessageTime(project?.nextRequiredMessageStartAt.orEmpty())} - ${displayNextRequiredMessageTime(project?.nextRequiredMessageEndAt.orEmpty())}",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "点击在 Reminders 创建提醒",
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (displayMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "还没有对话",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                items(
                    items = displayMessages,
                    key = { it.id },
                    contentType = { it.role }
                ) { message ->
                    MessageItem(
                        message = message,
                        canResend = message.id == messages.lastOrNull()?.id,
                        onEdit = viewModel::updateMessage,
                        onDelete = viewModel::deleteMessage,
                        onResend = { viewModel.resendLastMessage(projectId, message.id, criticalMode, experienceMode) }
                    )
                }
            }
        }

        ProjectChatInputBar(
            input = input,
            selectedImagePaths = selectedImagePaths,
            loading = loading,
            innerVoiceEnabled = project?.innerVoiceEnabled == true,
            criticalMode = criticalMode,
            experienceMode = experienceMode,
            onInputChange = viewModel::updateInput,
            onPickImage = { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemoveImage = viewModel::removeSelectedImage,
            onToggleInnerVoice = {
                if (project?.innerVoiceEnabled == true) {
                    viewModel.setInnerVoiceEnabled(projectId, false)
                } else {
                    criticalMode = false
                    experienceMode = false
                    if (project?.innerVoice.isNullOrBlank()) {
                        innerVoiceText = ""
                        editingInnerVoice = true
                    } else {
                        viewModel.setInnerVoiceEnabled(projectId, true)
                    }
                }
            },
            onEditInnerVoice = {
                innerVoiceText = project?.innerVoice.orEmpty()
                editingInnerVoice = true
            },
            onToggleCritical = {
                val enabled = !criticalMode
                criticalMode = enabled
                if (enabled) {
                    experienceMode = false
                    viewModel.setInnerVoiceEnabled(projectId, false)
                }
            },
            onToggleExperience = {
                val enabled = !experienceMode
                experienceMode = enabled
                if (enabled) {
                    criticalMode = false
                    viewModel.setInnerVoiceEnabled(projectId, false)
                }
            },
            onSend = { viewModel.sendUserMessage(projectId, criticalMode, experienceMode) },
            onStop = { viewModel.stopUserMessage(projectId) }
        )
    }

    if (editingInnerVoice) {
        AlertDialog(
            onDismissRequest = { editingInnerVoice = false },
            title = { Text("心里话") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "写下一句此刻真实的内心活动。开启后，它会随你下一条消息一起悄悄告诉 AI；AI 不会点破，只会更好地接住你的情绪。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp, max = 240.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp)
                    ) {
                        BasicTextField(
                            value = innerVoiceText,
                            onValueChange = { innerVoiceText = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            minLines = 4,
                            maxLines = 10
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val content = innerVoiceText.trim()
                        if (content.isNotBlank()) {
                            criticalMode = false
                            experienceMode = false
                        }
                        viewModel.saveInnerVoice(projectId, content, content.isNotBlank())
                        editingInnerVoice = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingInnerVoice = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ProjectHeaderBar(
    title: String,
    onBack: () -> Unit,
    onEditProject: () -> Unit,
    onOpenDailyRecords: () -> Unit,
    onOpenContextSummary: () -> Unit,
    onOpenChallenge: () -> Unit,
    onGenerateTodayRecord: () -> Unit,
    onImportMessages: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

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
            Box {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { menuExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_ellipsis_vertical),
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑项目") },
                        onClick = {
                            menuExpanded = false
                            onEditProject()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("每日记录") },
                        onClick = {
                            menuExpanded = false
                            onOpenDailyRecords()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("整理上下文") },
                        onClick = {
                            menuExpanded = false
                            onOpenContextSummary()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("挑战") },
                        onClick = {
                            menuExpanded = false
                            onOpenChallenge()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("生成今日记录") },
                        onClick = {
                            menuExpanded = false
                            onGenerateTodayRecord()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导入消息记录") },
                        onClick = {
                            menuExpanded = false
                            onImportMessages()
                        }
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

private fun displayNextRequiredMessageTime(time: String): String {
    if (time.isBlank()) return ""
    return try {
        OffsetDateTime.parse(time).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    } catch (e: Exception) {
        time
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectChatInputBar(
    input: String,
    selectedImagePaths: List<String>,
    loading: Boolean,
    innerVoiceEnabled: Boolean,
    criticalMode: Boolean,
    experienceMode: Boolean,
    onInputChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onToggleInnerVoice: () -> Unit,
    onEditInnerVoice: () -> Unit,
    onToggleCritical: () -> Unit,
    onToggleExperience: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val activeColor = Color(0xFF2E7D32)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        if (selectedImagePaths.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedImagePaths.forEach { path ->
                    Box {
                        ChatLocalImage(
                            path = path,
                            modifier = Modifier
                                .size(74.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
                                .clickable { onRemoveImage(path) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("×", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp, max = 112.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (input.isBlank()) {
                Text(
                    text = "输入今天的状态、计划或阻力",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    fontSize = 15.sp
                )
            }
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = 1,
                maxLines = 5
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPickImage,
                    enabled = !loading
                ) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_image),
                        contentDescription = "选择图片",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            enabled = !loading,
                            onClick = onToggleInnerVoice,
                            onLongClick = onEditInnerVoice
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_heart),
                        contentDescription = "心里话",
                        tint = if (innerVoiceEnabled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onToggleCritical,
                    enabled = !loading
                ) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_zap),
                        contentDescription = "临界模式",
                        tint = if (criticalMode) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onToggleExperience,
                    enabled = !loading
                ) {
                    Icon(
                        painter = painterResource(LucideR.drawable.lucide_ic_book_open_check),
                        contentDescription = "经验模式",
                        tint = if (experienceMode) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            val canSend = !loading && (input.isNotBlank() || selectedImagePaths.isNotEmpty())
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (loading || canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                    .clickable(enabled = loading || canSend) {
                        if (loading) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        if (loading) LucideR.drawable.lucide_ic_square else LucideR.drawable.lucide_ic_send
                    ),
                    contentDescription = if (loading) "停止" else "发送",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatLocalImage(
    path: String,
    modifier: Modifier = Modifier,
    targetMaxDimension: Int = 512,
    contentScale: ContentScale = ContentScale.Fit
) {
    val bitmap = remember(path, targetMaxDimension) {
        decodeSampledBitmap(path, targetMaxDimension)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "聊天图片",
            contentScale = contentScale,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "图片不存在",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * 按最长边不超过 maxDimension 的目标尺寸采样解码图片。
 * 先读边界算出 2 的幂次采样比例，再真正解码，避免多张大图同时全量解码导致内存占用过高。
 */
private fun decodeSampledBitmap(path: String, maxDimension: Int): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    return try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOptions)
        var inSampleSize = 1
        while (boundsOptions.outWidth / (inSampleSize * 2) >= maxDimension
            || boundsOptions.outHeight / (inSampleSize * 2) >= maxDimension
        ) {
            inSampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        BitmapFactory.decodeFile(path, decodeOptions)
    } catch (e: Exception) {
        null
    }
}

/**
 * 多图消息的缩略图网格。
 * 2~3 张两列、4 张及以上三列，每格为正方形缩略图，右上角带 "n/总数" 序号角标。
 */
@Composable
private fun ChatImageGrid(
    imagePaths: List<String>,
    onImageClick: (String) -> Unit
) {
    val columns = if (imagePaths.size <= 3) 2 else 3
    var globalIndex = 0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        imagePaths.chunked(columns).forEach { rowPaths ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowPaths.forEach { path ->
                    globalIndex++
                    Box(modifier = Modifier.weight(1f)) {
                        ChatLocalImage(
                            path = path,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onImageClick(path) }
                        )
                        Text(
                            text = "$globalIndex/${imagePaths.size}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                // 最后一行不足列数时用占位补齐，保证各列对齐
                repeat(columns - rowPaths.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 全屏查看单张图片：黑色背景、双指缩放 + 拖动平移、单击或右上角按钮关闭。
 */
@Composable
private fun FullScreenImagePreview(
    path: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // 按屏幕最长边解码，保证放大后仍有足够清晰度
    val targetMax = maxOf(
        context.resources.displayMetrics.widthPixels,
        context.resources.displayMetrics.heightPixels
    )
    val bitmap = remember(path, targetMax) { decodeSampledBitmap(path, targetMax) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "聊天图片大图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onDismiss() })
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                // 未放大时不响应拖动，图片始终居中；放大后可自由拖动查看细节
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                            }
                        }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "图片加载失败",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            // 右上角关闭按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text("×", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    message: MessageEntity,
    canResend: Boolean,
    onEdit: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onResend: () -> Unit
) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    var editDialogVisible by remember { mutableStateOf(false) }
    var resendDialogVisible by remember { mutableStateOf(false) }
    var editText by remember(message.id, message.content) { mutableStateOf(message.content) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    val imagePaths = remember(message.imagePaths) {
        val paths = mutableListOf<String>()
        try {
            if (message.imagePaths.isNotBlank()) {
                val array = JSONArray(message.imagePaths)
                for (index in 0 until array.length()) {
                    val path = array.optString(index).trim()
                    if (path.isNotEmpty()) {
                        paths.add(path)
                    }
                }
            }
        } catch (e: Exception) {
        }
        paths
    }

    if (editDialogVisible) {
        AlertDialog(
            onDismissRequest = { editDialogVisible = false },
            title = { Text("修改消息") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    BasicTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        minLines = 5,
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEdit(message.id, editText)
                        editDialogVisible = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editDialogVisible = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (resendDialogVisible) {
        AlertDialog(
            onDismissRequest = { resendDialogVisible = false },
            title = { Text("重新发送") },
            text = { Text("确定要用当前最后一条消息重新请求 AI 吗？不会重复追加这条消息。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        resendDialogVisible = false
                        onResend()
                    }
                ) {
                    Text("重新发送")
                }
            },
            dismissButton = {
                TextButton(onClick = { resendDialogVisible = false }) {
                    Text("取消")
                }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menuExpanded = true }
                    )
                    .padding(12.dp)
            ) {
                if (imagePaths.size == 1) {
                    // 单张图保持大图展示，点击可放大查看
                    ChatLocalImage(
                        path = imagePaths[0],
                        targetMaxDimension = 720,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { previewPath = imagePaths[0] }
                    )
                } else if (imagePaths.size >= 2) {
                    // 多张图用自适应网格展示，大幅压缩消息高度
                    ChatImageGrid(
                        imagePaths = imagePaths,
                        onImageClick = { previewPath = it }
                    )
                }
                if (message.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MarkdownContent(
                        markdown = message.content,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        linkColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("复制") },
                    onClick = {
                        menuExpanded = false
                        clipboardManager.setText(AnnotatedString(message.content))
                    }
                )
                DropdownMenuItem(
                    text = { Text("修改") },
                    onClick = {
                        menuExpanded = false
                        editText = message.content
                        editDialogVisible = true
                    }
                )
                if (canResend) {
                    DropdownMenuItem(
                        text = { Text("重新发送") },
                        onClick = {
                            menuExpanded = false
                            resendDialogVisible = true
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = {
                        menuExpanded = false
                        onDelete(message.id)
                    }
                )
            }
        }
    }

    previewPath?.let { path ->
        FullScreenImagePreview(
            path = path,
            onDismiss = { previewPath = null }
        )
    }
}

@Composable
private fun MarkdownContent(
    markdown: String,
    color: Color,
    linkColor: Color
) {
    val parser = remember { Parser.builder().extensions(listOf(TablesExtension.create())).build() }
    val document = remember(markdown) { parser.parse(markdown) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var child = document.firstChild
        while (child != null) {
            MarkdownBlock(node = child, color = color, linkColor = linkColor)
            child = child.next
        }
    }
}

@Composable
private fun MarkdownBlock(
    node: Node,
    color: Color,
    linkColor: Color
) {
    when (node) {
        is Paragraph -> Text(
            text = buildMarkdownText(node, linkColor),
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )

        is Heading -> Text(
            text = buildMarkdownText(node, linkColor),
            style = if (node.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = color
        )

        is BulletList -> MarkdownList(node = node, ordered = false, color = color, linkColor = linkColor)

        is OrderedList -> MarkdownList(node = node, ordered = true, color = color, linkColor = linkColor)

        is TableBlock -> MarkdownTable(node = node, color = color, linkColor = linkColor)

        is FencedCodeBlock -> MarkdownCodeBlock(text = node.literal, color = color)

        is IndentedCodeBlock -> MarkdownCodeBlock(text = node.literal, color = color)

        is BlockQuote -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                var child = node.firstChild
                while (child != null) {
                    MarkdownBlock(node = child, color = color, linkColor = linkColor)
                    child = child.next
                }
            }
        }

        is ThematicBreak -> HorizontalDivider(color = color.copy(alpha = 0.3f))

        else -> {
            var child = node.firstChild
            while (child != null) {
                MarkdownBlock(node = child, color = color, linkColor = linkColor)
                child = child.next
            }
        }
    }
}

@Composable
private fun MarkdownList(
    node: Node,
    ordered: Boolean,
    color: Color,
    linkColor: Color
) {
    var index = 1
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var item = node.firstChild
        while (item != null) {
            if (item is ListItem) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (ordered) "${index}." else "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        modifier = Modifier.width(24.dp)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        var child = item.firstChild
                        while (child != null) {
                            MarkdownBlock(node = child, color = color, linkColor = linkColor)
                            child = child.next
                        }
                    }
                }
                index++
            }
            item = item.next
        }
    }
}

@Composable
private fun MarkdownTable(
    node: TableBlock,
    color: Color,
    linkColor: Color
) {
    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier
                .widthIn(min = 260.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
        ) {
            var section = node.firstChild
            while (section != null) {
                if (section is TableRow) {
                    MarkdownTableRow(
                        row = section,
                        header = false,
                        color = color,
                        linkColor = linkColor
                    )
                } else {
                    var row = section.firstChild
                    while (row != null) {
                        if (row is TableRow) {
                            MarkdownTableRow(
                                row = row,
                                header = section is TableHead,
                                color = color,
                                linkColor = linkColor
                            )
                        }
                        row = row.next
                    }
                }
                section = section.next
            }
        }
    }
}

@Composable
private fun MarkdownTableRow(
    row: TableRow,
    header: Boolean,
    color: Color,
    linkColor: Color
) {
    Row {
        var cell = row.firstChild
        while (cell != null) {
            if (cell is TableCell) {
                Text(
                    text = buildMarkdownText(cell, linkColor),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                    color = color,
                    modifier = Modifier
                        .widthIn(min = 96.dp, max = 180.dp)
                        .border(0.5.dp, color.copy(alpha = 0.25f))
                        .background(
                            if (header) MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            cell = cell.next
        }
    }
}

@Composable
private fun MarkdownCodeBlock(text: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            .padding(8.dp)
    ) {
        Text(
            text = text.trimEnd(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

private fun buildMarkdownText(node: Node, linkColor: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()
    appendMarkdownInline(node, builder, linkColor)
    return builder.toAnnotatedString()
}

private fun appendMarkdownInline(
    node: Node,
    builder: AnnotatedString.Builder,
    linkColor: Color
) {
    when (node) {
        is MarkdownText -> builder.append(node.literal)
        is Code -> builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
            append(node.literal)
        }
        is Emphasis -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            var child = node.firstChild
            while (child != null) {
                appendMarkdownInline(child, builder, linkColor)
                child = child.next
            }
        }
        is StrongEmphasis -> builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            var child = node.firstChild
            while (child != null) {
                appendMarkdownInline(child, builder, linkColor)
                child = child.next
            }
        }
        is Link -> builder.withStyle(
            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
        ) {
            var child = node.firstChild
            while (child != null) {
                appendMarkdownInline(child, builder, linkColor)
                child = child.next
            }
        }
        is SoftLineBreak -> builder.append("\n")
        is HardLineBreak -> builder.append("\n")
        else -> {
            var child = node.firstChild
            while (child != null) {
                appendMarkdownInline(child, builder, linkColor)
                child = child.next
            }
        }
    }
}
