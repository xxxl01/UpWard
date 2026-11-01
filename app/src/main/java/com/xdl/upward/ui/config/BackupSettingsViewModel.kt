package com.xdl.upward.ui.config

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.repository.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BackupSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val backupRepository = BackupRepository(application.applicationContext, database)

    private val _backupMessage = MutableStateFlow("")
    val backupMessage: StateFlow<String> = _backupMessage.asStateFlow()

    private val _backupRunning = MutableStateFlow(false)
    val backupRunning: StateFlow<Boolean> = _backupRunning.asStateFlow()

    fun exportBackup(uri: Uri) {
        if (_backupRunning.value) return
        _backupRunning.value = true
        _backupMessage.value = "正在导出备份..."
        viewModelScope.launch {
            try {
                backupRepository.exportTo(uri)
                _backupMessage.value = "全量备份已导出"
            } catch (e: Exception) {
                _backupMessage.value = e.message ?: "导出失败"
            } finally {
                _backupRunning.value = false
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        if (_backupRunning.value) return
        _backupRunning.value = true
        _backupMessage.value = "正在恢复备份..."
        viewModelScope.launch {
            try {
                backupRepository.restoreFrom(uri)
                _backupMessage.value = "全量备份已恢复"
            } catch (e: Exception) {
                _backupMessage.value = e.message ?: "恢复失败"
            } finally {
                _backupRunning.value = false
            }
        }
    }
}
