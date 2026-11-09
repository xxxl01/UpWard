package com.xdl.upward.ui.violation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.PaymentRecordEntity
import com.xdl.upward.data.repository.ChallengeRepository
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.data.repository.ProjectRepository
import com.xdl.upward.domain.PaymentVerifier
import com.xdl.upward.reminder.RequiredMessageDeadlineHandler
import com.xdl.upward.reminder.RequiredMessageReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * 违规惩罚页：不再与 AI 对话。用户上传捐款截图，由 PaymentVerifier 识别并按本地规则判定，
 * 通过后违规次数抵扣 10 次，并一并结算所有失败惩罚中的挑战。
 */
class ViolationChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val configRepository = ConfigRepository(database.configDao())
    private val projectRepository = ProjectRepository(database.projectDao())
    private val challengeRepository = ChallengeRepository(database.challengeDao(), database.challengeCheckInDao())

    // 请假：每个自然周（周一 ~ 周日，按凌晨 4 点换日）一次，效果是清除所有项目当前的汇报窗口。
    // 本周已请假时为请假当天日期，否则为空。
    private val _leaveDateThisWeek = MutableStateFlow("")
    val leaveDateThisWeek: StateFlow<String> = _leaveDateThisWeek.asStateFlow()

    private val _leaving = MutableStateFlow(false)
    val leaving: StateFlow<Boolean> = _leaving.asStateFlow()

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
        viewModelScope.launch { refreshLeaveState() }
    }

    private suspend fun refreshLeaveState() {
        val lastLeave = configRepository.getValue(KEY_LAST_LEAVE_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val today = challengeRepository.currentCheckInDate()
        _leaveDateThisWeek.value = if (lastLeave != null && sameWeek(lastLeave, today)) lastLeave.toString() else ""
    }

    private fun sameWeek(a: LocalDate, b: LocalDate): Boolean {
        val week = WeekFields.ISO
        return a.get(week.weekBasedYear()) == b.get(week.weekBasedYear()) &&
            a.get(week.weekOfWeekBasedYear()) == b.get(week.weekOfWeekBasedYear())
    }

    // 请假：先结算已经到期的窗口（到期未汇报照常记违规，不能靠请假抹掉），再清除剩下的全部汇报窗口。
    // 没有可清除的窗口时不消耗本周的请假次数。
    fun requestLeave() {
        if (_leaving.value) return
        _leaving.value = true
        _errorMessage.value = ""
        _noticeMessage.value = ""
        viewModelScope.launch {
            try {
                refreshLeaveState()
                if (_leaveDateThisWeek.value.isNotEmpty()) {
                    _errorMessage.value = "本周已经请过假了（${_leaveDateThisWeek.value}），下周一凌晨 4 点后可再次请假。"
                    return@launch
                }
                val context = getApplication<Application>()
                val projects = projectRepository.getProjects()
                projects.forEach { RequiredMessageDeadlineHandler.checkAndRecord(context, it.id) }
                val withWindow = projectRepository.getProjects().filter { it.nextRequiredMessageEndAt.isNotBlank() }
                if (withWindow.isEmpty()) {
                    _errorMessage.value = "当前没有汇报窗口，无需请假，本周请假次数未消耗。"
                    return@launch
                }
                withWindow.forEach { project ->
                    projectRepository.setNextRequiredMessagePeriod(project.id, "", "")
                    RequiredMessageReminderScheduler.cancel(context, project.id)
                }
                configRepository.setValue(KEY_LAST_LEAVE_DATE, challengeRepository.currentCheckInDate().toString())
                refreshLeaveState()
                _noticeMessage.value = "请假成功：已清除 ${withWindow.size} 个项目的汇报窗口（${withWindow.joinToString("、") { it.name }}）。本周请假次数已用完。"
            } catch (e: Exception) {
                _errorMessage.value = "请假失败：${e.message ?: "未知错误"}"
            } finally {
                _leaving.value = false
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

    companion object {
        private const val KEY_LAST_LEAVE_DATE = "leave_last_date"
    }

    private fun deleteQuietly(path: String) {
        viewModelScope.launch(Dispatchers.IO) { File(path).delete() }
    }
}
