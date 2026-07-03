package com.xdl.upward.ui.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xdl.upward.data.local.MessageEntity
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
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "返回",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Text(
                text = project?.name ?: "项目详情",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
            Text(
                text = "编辑",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onEditProject() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "系统提示词",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = project?.systemPrompt ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(22.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "每日记录",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onOpenDailyRecords() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "生成今日记录",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { viewModel.generateTodayRecord(projectId) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        if (loading) {
            Text(
                text = "AI 请求中...",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
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

        if (messages.isEmpty()) {
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageItem(message = message)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = viewModel::updateInput,
                label = { Text("输入今天的状态、计划或阻力") },
                minLines = 1,
                maxLines = 4,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "发送",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { viewModel.sendUserMessage(projectId) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun MessageItem(message: MessageEntity) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(12.dp)
        ) {
            Text(
                text = if (isUser) "我" else "AI",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            MarkdownContent(
                markdown = message.content,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                linkColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MarkdownContent(
    markdown: String,
    color: Color,
    linkColor: Color
) {
    val parser = remember { Parser.builder().build() }
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
