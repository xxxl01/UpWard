package com.xdl.upward.ui.violation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.PaymentRecordEntity
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.domain.PaymentVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * 违规惩罚页：不再与 AI 对话。用户上传捐款截图，由 PaymentVerifier 识别并按本地规则判定，
 * 通过后违规次数抵扣 10 次，并一并结算所有失败惩罚中的挑战。
 */
class ViolationChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val configRepository = ConfigRepository(database.configDao())

    private val _violationCount = MutableStateFlow(0)
    val violationCount: StateFlow<Int> = _violationCount.asStateFlow()

    // 所有通过审核的捐款记录（挑战惩罚与违规惩罚都展示，方便回看）。
    val paymentRecords: StateFlow<List<PaymentRecordEntity>> = database.paymentRecordDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _verifying = MutableStateFlow(false)
    val verifying: StateFlow<Boolean> = _verifying.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _noticeMessage = MutableStateFlow("")
    val noticeMessage: StateFlow<String> = _noticeMessage.asStateFlow()

    init {
        viewModelScope.launch {
            configRepository.observeViolationCount().collect { value ->
                _violationCount.value = value?.toIntOrNull() ?: 0
            }
        }
    }

    // 保存截图 → LLM 识别 → 本地规则判定；不通过不入库，并删除本次保存的截图。
    fun submitPayment(uri: Uri) {
        if (_verifying.value) return
        _verifying.value = true
        _errorMessage.value = ""
        _noticeMessage.value = ""
        viewModelScope.launch {
            var imagePath: String? = null
            try {
                val context = getApplication<Application>()
                imagePath = PaymentVerifier.saveImage(context, uri)
                when (val result = PaymentVerifier.verifyViolationPenalty(context, imagePath)) {
                    is PaymentVerifier.Result.Accepted -> _noticeMessage.value = result.message
                    is PaymentVerifier.Result.Rejected -> {
                        _errorMessage.value = result.message
                        deleteQuietly(imagePath)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "提交失败：${e.message ?: "未知错误"}"
                imagePath?.let { deleteQuietly(it) }
            } finally {
                _verifying.value = false
            }
        }
    }

    private fun deleteQuietly(path: String) {
        viewModelScope.launch(Dispatchers.IO) { File(path).delete() }
    }
}
