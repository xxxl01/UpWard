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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR

@Composable
fun GeneralSettingsScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: GeneralSettingsViewModel = viewModel()
) {
    val messageContextCount by viewModel.messageContextCount.collectAsStateWithLifecycle()
    val contextSummaryPrompt by viewModel.contextSummaryPrompt.collectAsStateWithLifecycle()
    val contextSummaryApiId by viewModel.contextSummaryApiId.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val apis by viewModel.apis.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectingContextSummaryApi by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    if (selectingContextSummaryApi) {
        AlertDialog(
            onDismissRequest = { selectingContextSummaryApi = false },
            title = { Text("选择整理上下文 API") },
            text = {
                Column {
                    if (apis.isEmpty()) {
                        Text("请先新建 API 配置")
                    } else {
                        apis.forEach { api ->
                            TextButton(
                                onClick = {
                                    viewModel.selectContextSummaryApi(api.id)
                                    selectingContextSummaryApi = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (api.id == contextSummaryApiId) "${api.model}（当前）" else api.model,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectingContextSummaryApi = false }) {
                    Text("关闭")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SettingsHeaderBar(
            title = "通用设置",
            onBack = onBack,
            actions = {
                SettingsActionButton(
                    icon = LucideR.drawable.lucide_ic_check,
                    contentDescription = "保存",
                    onClick = { viewModel.save(onSaved) }
                )
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            OutlinedTextField(
                value = messageContextCount,
                onValueChange = viewModel::updateMessageContextCount,
                label = { Text("上下文消息数量") },
                supportingText = { Text("AI 聊天时携带最近多少条消息，默认 20。") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = contextSummaryPrompt,
                onValueChange = viewModel::updateContextSummaryPrompt,
                label = { Text("整理上下文提示词") },
                supportingText = { Text("用于项目聊天里的“整理上下文”功能。") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            val contextSummaryApi = apis.firstOrNull { it.id == contextSummaryApiId }
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_server,
                title = "整理上下文 API：${contextSummaryApi?.model ?: "未选择"}",
                description = "批量整理上下文时使用此 API，不使用聊天默认 API。",
                onClick = { selectingContextSummaryApi = true }
            )
            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
