package com.xdl.upward.ui.config

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.R as LucideR
import java.time.LocalDate

@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    viewModel: BackupSettingsViewModel = viewModel()
) {
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val backupRunning by viewModel.backupRunning.collectAsStateWithLifecycle()
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
        }
    }

    if (pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("恢复全量备份") },
            text = { Text("恢复后会清空当前项目、消息、日报、API 配置和通用设置，并用备份文件覆盖。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingRestoreUri
                        pendingRestoreUri = null
                        if (uri != null) {
                            viewModel.restoreBackup(uri)
                        }
                    }
                ) {
                    Text("确认恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text("取消")
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
            title = "数据备份",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_download,
                title = if (backupRunning) "正在处理数据备份" else "导出全量备份",
                description = "导出项目、消息、日报、API 配置、通用设置和聊天图片。",
                onClick = {
                    if (!backupRunning) {
                        exportBackupLauncher.launch("upward-backup-${LocalDate.now()}.zip")
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsEntryItem(
                icon = LucideR.drawable.lucide_ic_upload,
                title = "恢复全量备份",
                description = "选择备份文件恢复；恢复会覆盖当前所有本地数据。",
                onClick = {
                    if (!backupRunning) {
                        restoreBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                }
            )
            if (backupMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = backupMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
