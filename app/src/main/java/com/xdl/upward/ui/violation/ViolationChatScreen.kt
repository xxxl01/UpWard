package com.xdl.upward.ui.violation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import com.composables.icons.lucide.R as LucideR
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
import com.xdl.upward.domain.AiChatMessage
import java.io.File
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
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

@Composable
fun ViolationChatScreen(
    onBack: () -> Unit,
    viewModel: ViolationChatViewModel = viewModel()
) {
    val violationCount by viewModel.violationCount.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val selectedImagePaths by viewModel.selectedImagePaths.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.addSelectedImages(uris)
    }
    val displayMessages = remember(messages) { messages.asReversed() }
    val isNearBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex <= 1 }
    }

    LaunchedEffect(violationCount) {
        if (violationCount > 0) {
            viewModel.sendInitialWarning()
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
        ViolationHeaderBar(
            title = "违规告诫 $violationCount 次",
            onBack = onBack
        )

        ViolationWarningBanner(violationCount = violationCount)

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
                    text = "AI 正在组织语言...",
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
                    text = if (violationCount > 0) "正在准备告诫..." else "当前没有违规记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                itemsIndexed(displayMessages) { index, message ->
                    ViolationMessageItem(
                        message = message,
                        canResend = index == 0,
                        onResend = viewModel::resendLastMessage
                    )
                }
            }
        }

        ViolationChatInputBar(
            input = input,
            selectedImagePaths = selectedImagePaths,
            loading = loading,
            onInputChange = viewModel::updateInput,
            onPickImage = { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemoveImage = viewModel::removeSelectedImage,
            onSend = viewModel::sendUserMessage
        )
    }

}

@Composable
private fun ViolationHeaderBar(
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
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun ViolationChatInputBar(
    input: String,
    selectedImagePaths: List<String>,
    loading: Boolean,
    onInputChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onSend: () -> Unit
) {
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
                        ViolationLocalImage(
                            path = path,
                            modifier = Modifier.size(74.dp)
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
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(enabled = !loading) { onPickImage() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(LucideR.drawable.lucide_ic_image),
                    contentDescription = "选择图片",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
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
                        text = "回复或反思一下",
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
            val canSend = !loading && (input.isNotBlank() || selectedImagePaths.isNotEmpty())
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
}

// 违规告诫页顶部的警告提示：说明惩罚规则与抵扣方式，达到惩罚线（10 次）时用红色强调。
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
        "所有项目已拦截消息发送。请捐款 3 元并把捐款截图发给 AI；每笔捐款只能抵扣 10 次违规，剩余违规继续保留。"
    } else {
        "累计违规达到 10 次将触发惩罚：拦截所有项目消息发送。捐款 3 元并经 AI 确认后可抵扣 10 次违规，剩余违规继续保留。"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
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

// 本地图片缩略图：按目标尺寸采样解码，避免全量加载大图。
@Composable
private fun ViolationLocalImage(
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

// 按最长边不超过 maxDimension 的目标尺寸采样解码图片。
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViolationMessageItem(
    message: AiChatMessage,
    canResend: Boolean,
    onResend: () -> Unit
) {
    val isUser = message.role == "user"
    val clipboardManager = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    var resendDialogVisible by remember { mutableStateOf(false) }

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
                if (canResend) {
                    DropdownMenuItem(
                        text = { Text("重新发送") },
                        onClick = {
                            menuExpanded = false
                            resendDialogVisible = true
                        }
                    )
                }
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
