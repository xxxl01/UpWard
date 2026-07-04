package com.xdl.upward.ui.project

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xdl.upward.data.local.MessageEntity
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
import java.io.InputStreamReader

@Composable
fun ProjectDetailScreen(
    projectId: Long,
    onBack: () -> Unit,
    onEditProject: () -> Unit,
    onOpenDailyRecords: () -> Unit,
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
    val displayMessages = remember(messages) { messages.asReversed() }
    val isNearBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex <= 1 }
    }

    LaunchedEffect(projectId) {
        listState.scrollToItem(0)
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
                        onEdit = viewModel::updateMessage,
                        onDelete = viewModel::deleteMessage
                    )
                }
            }
        }

        ProjectChatInputBar(
            input = input,
            loading = loading,
            onInputChange = viewModel::updateInput,
            onSend = { viewModel.sendUserMessage(projectId) }
        )
    }
}

@Composable
private fun ProjectHeaderBar(
    title: String,
    onBack: () -> Unit,
    onEditProject: () -> Unit,
    onOpenDailyRecords: () -> Unit,
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

@Composable
private fun ProjectChatInputBar(
    input: String,
    loading: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
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
        val canSend = !loading && input.isNotBlank()
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (canSend) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
                .clickable(enabled = canSend) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(LucideR.drawable.lucide_ic_send),
                contentDescription = "发送",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    message: MessageEntity,
    onEdit: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    var editDialogVisible by remember { mutableStateOf(false) }
    var editText by remember(message.id, message.content) { mutableStateOf(message.content) }

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
                MarkdownContent(
                    markdown = message.content,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    linkColor = MaterialTheme.colorScheme.primary
                )
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
