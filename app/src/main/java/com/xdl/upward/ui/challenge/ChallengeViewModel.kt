package com.xdl.upward.ui.challenge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AiApiEntity
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.ChallengeCheckInEntity
import com.xdl.upward.data.local.ChallengeEntity
import com.xdl.upward.data.remote.AiApiService
import com.xdl.upward.data.repository.AiApiRepository
import com.xdl.upward.data.repository.ChallengeRepository
import android.net.Uri
import com.xdl.upward.domain.AiChatMessage
import com.xdl.upward.domain.PaymentVerifier
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate

class ChallengeViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        // 【临时开关】修改进行中的挑战时，是否启用"AI 判定难度增强才允许保存"的限制。
        // 当前为 true：启用 AI 判定（仅允许增强）。
        const val TEMP_ENABLE_AI_JUDGE_ON_MODIFY = true

        // 挑战失败惩罚固定为捐款，不再由用户自定义；写入 fail_penalty_prompt 列只作展示用。
        const val FIXED_FAIL_PENALTY = "挑战失败需向公益慈善机构捐款 3 元及以上，在挑战页上传捐款截图，识别审核通过后结算。"
    }

    private val database = AppDatabase.getInstance(application)
    private val challengeRepository = ChallengeRepository(
        database.challengeDao(),
        database.challengeCheckInDao()
    )
    private val aiApiRepository = AiApiRepository(database.aiApiDao())
    private val aiApiService = AiApiService()

    private val _goal = MutableStateFlow("")
    val goal: StateFlow<String> = _goal.asStateFlow()

    private val _dailyStandardPrompt = MutableStateFlow("")
    val dailyStandardPrompt: StateFlow<String> = _dailyStandardPrompt.asStateFlow()

    private val _startDate = MutableStateFlow("")
    val startDate: StateFlow<String> = _startDate.asStateFlow()

    private val _endDate = MutableStateFlow("")
    val endDate: StateFlow<String> = _endDate.asStateFlow()

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _noticeMessage = MutableStateFlow("")
    val noticeMessage: StateFlow<String> = _noticeMessage.asStateFlow()

    // 修改进行中的挑战时，是否正在等待 AI 判定难度变化。
    private val _aiJudging = MutableStateFlow(false)
    val aiJudging: StateFlow<Boolean> = _aiJudging.asStateFlow()

    // 失败惩罚捐款截图是否正在识别审核中（期间禁用上传按钮，防止重复提交）。
    private val _paymentVerifying = MutableStateFlow(false)
    val paymentVerifying: StateFlow<Boolean> = _paymentVerifying.asStateFlow()

    fun observeChallenge(projectId: Long): Flow<ChallengeEntity?> = challengeRepository.observeByProjectId(projectId)

    fun observeCheckIns(challengeId: Long): Flow<List<ChallengeCheckInEntity>> = challengeRepository.observeCheckIns(challengeId)

    fun currentCheckInDate(): LocalDate = challengeRepository.currentCheckInDate()

    // 进入页面时做缺卡自动检测（有缺卡则进入失败惩罚流程）。
    fun load(projectId: Long) {
        viewModelScope.launch {
            challengeRepository.checkForMissedDays(projectId)
        }
    }

    // 进入创建 / 开启新一轮的表单：有旧挑战时预填当前值。
    fun startEditing(challenge: ChallengeEntity?) {
        _goal.value = challenge?.goal.orEmpty()
        _dailyStandardPrompt.value = challenge?.dailyStandardPrompt.orEmpty()
        _startDate.value = challenge?.startDate.orEmpty()
        _endDate.value = challenge?.endDate.orEmpty()
        _errorMessage.value = ""
        _noticeMessage.value = ""
        _editing.value = true
    }

    fun cancelEditing() {
        _editing.value = false
        _errorMessage.value = ""
    }

    fun updateGoal(value: String) { _goal.value = value }
    fun updateDailyStandardPrompt(value: String) { _dailyStandardPrompt.value = value }
    fun updateStartDate(value: String) { _startDate.value = value }
    fun updateEndDate(value: String) { _endDate.value = value }

    // 保存：项目还没有挑战记录时创建；已有终态挑战记录时开启新一轮（重置并清空旧打卡）；
    // 已有 active 挑战记录时为修改：开始日期不可改，截止日期只能延后，其余字段变化需经 AI 判定难度是增强还是减弱。
    // 开始日期不能早于今天；结束日期必须晚于开始日期。
    fun save(projectId: Long) {
        val goalText = _goal.value.trim()
        if (goalText.isEmpty()) {
            _errorMessage.value = "请填写挑战目标"
            return
        }
        val standardText = _dailyStandardPrompt.value.trim()
        if (standardText.isEmpty()) {
            _errorMessage.value = "请填写每日打卡标准"
            return
        }
        val penaltyText = FIXED_FAIL_PENALTY
        val startDate = try {
            LocalDate.parse(_startDate.value.trim())
        } catch (e: Exception) {
            _errorMessage.value = "请选择开始日期"
            return
        }
        val endDate = try {
            LocalDate.parse(_endDate.value.trim())
        } catch (e: Exception) {
            _errorMessage.value = "请选择截止日期"
            return
        }
        if (!endDate.isAfter(startDate)) {
            _errorMessage.value = "截止日期必须晚于开始日期"
            return
        }

        _errorMessage.value = ""
        viewModelScope.launch {
            val oldChallenge = challengeRepository.getByProjectId(projectId)
            // 创建/开启新一轮时开始日期不能早于今天；修改进行中的挑战时开始日期锁定为旧值（可能在过去），不在此限制。
            if (oldChallenge == null ||
                oldChallenge.status == ChallengeRepository.STATUS_SUCCESS ||
                oldChallenge.status == ChallengeRepository.STATUS_SETTLED
            ) {
                if (startDate.isBefore(challengeRepository.currentCheckInDate())) {
                    _errorMessage.value = "开始日期不能早于今天"
                    return@launch
                }
            }
            when {
                // 项目还没有挑战记录：创建。
                oldChallenge == null -> {
                    challengeRepository.createChallenge(projectId, goalText, standardText, penaltyText, startDate.toString(), endDate.toString())
                    _editing.value = false
                    if (startDate.isAfter(challengeRepository.currentCheckInDate())) {
                        _noticeMessage.value = "挑战已设置，将于 $startDate 开始，开始前 AI 不会感知到该挑战。"
                    } else {
                        _noticeMessage.value = "挑战已开始，坚持到截止日期并每天完成打卡，挑战即成功。"
                    }
                }
                // 已结算的终态：开启新一轮（重置并清空旧打卡记录）。
                oldChallenge.status == ChallengeRepository.STATUS_SUCCESS ||
                    oldChallenge.status == ChallengeRepository.STATUS_SETTLED -> {
                    challengeRepository.startNewRound(projectId, goalText, standardText, penaltyText, startDate.toString(), endDate.toString())
                    _editing.value = false
                    if (startDate.isAfter(challengeRepository.currentCheckInDate())) {
                        _noticeMessage.value = "新一轮挑战已设置，将于 $startDate 开始，开始前 AI 不会感知到该挑战。"
                    } else {
                        _noticeMessage.value = "新一轮挑战已开始，坚持到截止日期并每天完成打卡，挑战即成功。"
                    }
                }
                // 进行中的挑战：修改。开始日期锁定为原值，截止日期只能延后。
                oldChallenge.status == ChallengeRepository.STATUS_ACTIVE -> {
                    val fixedStartDate = LocalDate.parse(oldChallenge.startDate)
                    val oldEndDate = LocalDate.parse(oldChallenge.endDate)
                    if (endDate.isBefore(oldEndDate)) {
                        _errorMessage.value = "截止日期只能延后，不能提前"
                        return@launch
                    }
                    // 开始日期未到（挑战未生效，AI 感知不到）：直接允许修改，不做难度判定。
                    if (challengeRepository.currentCheckInDate().isBefore(fixedStartDate)) {
                        challengeRepository.updateActiveChallenge(projectId, goalText, standardText, penaltyText, endDate.toString())
                        _editing.value = false
                        _noticeMessage.value = "挑战已更新，将于 $fixedStartDate 开始，开始前 AI 不会感知到该挑战。"
                        return@launch
                    }
                    // 非时间字段是否有变化。
                    // 失败惩罚已固定，不参与变化判断（旧挑战里的自定义惩罚文本会在保存时被统一覆盖）
                    val fieldsChanged = goalText != oldChallenge.goal ||
                        standardText != oldChallenge.dailyStandardPrompt
                    if (!fieldsChanged && endDate == oldEndDate) {
                        _errorMessage.value = "没有检测到任何修改"
                        return@launch
                    }
                    // 只延后截止日期、其他字段未变：总天数变多，难度必然增强，本地规则直接放行。
                    if (!fieldsChanged) {
                        challengeRepository.updateActiveChallenge(projectId, goalText, standardText, penaltyText, endDate.toString())
                        _editing.value = false
                        _noticeMessage.value = "截止日期已延后，挑战周期变为 $fixedStartDate ~ $endDate，已打卡记录保留。"
                        return@launch
                    }
                    // 其余字段有变化：向 AI 判定本次修改是增强还是减弱难度，增强才允许保存。
                    // 【临时】TEMP_ENABLE_AI_JUDGE_ON_MODIFY=false 时绕过 AI 判定直接保存；
                    // 恢复该限制只需把开关改回 true。
                    if (!TEMP_ENABLE_AI_JUDGE_ON_MODIFY) {
                        challengeRepository.updateActiveChallenge(projectId, goalText, standardText, penaltyText, endDate.toString())
                        _editing.value = false
                        _noticeMessage.value = "挑战已更新。已打卡记录保留，从今天起按新标准考核。"
                        return@launch
                    }
                    _aiJudging.value = true
                    try {
                        val api = aiApiRepository.getSelected()
                        if (api == null) {
                            _errorMessage.value = "修改挑战需先经 AI 判定难度变化，请先在 API 设置中选择一个 API"
                            return@launch
                        }
                        val judge = judgeDifficultyChange(api, oldChallenge, goalText, standardText, endDate.toString())
                        if (judge == null) {
                            _errorMessage.value = "AI 判定失败，无法确认本次修改是否增强难度，已拒绝修改，请稍后重试"
                            return@launch
                        }
                        if (!judge.allowed) {
                            _errorMessage.value = if (judge.reason.isBlank()) {
                                "AI 判定本次修改减弱了挑战难度，已拒绝修改。挑战将按原标准继续，到截止日期后自动结算。"
                            } else {
                                "AI 判定本次修改减弱了挑战难度，已拒绝修改：${judge.reason}。挑战将按原标准继续，到截止日期后自动结算。"
                            }
                            return@launch
                        }
                        challengeRepository.updateActiveChallenge(projectId, goalText, standardText, penaltyText, endDate.toString())
                        _editing.value = false
                        _noticeMessage.value = "挑战已更新，AI 判定本次修改增强了难度。已打卡记录保留，从今天起按新标准考核。"
                    } catch (e: Exception) {
                        _errorMessage.value = "AI 判定失败（${e.message ?: "未知错误"}），已拒绝修改，请稍后重试"
                    } finally {
                        _aiJudging.value = false
                    }
                }
                else -> {
                    _errorMessage.value = "当前挑战已失败，正在惩罚流程中，无法修改"
                }
            }
        }
    }

    // AI 判定本次挑战修改是增强还是减弱难度。
    // 返回 null 表示判定失败（请求失败或返回格式不符）；否则返回判定结果。
    private suspend fun judgeDifficultyChange(
        api: AiApiEntity,
        old: ChallengeEntity,
        goal: String,
        standard: String,
        endDate: String
    ): JudgeResult? {
        val totalDays = challengeRepository.totalDays(old)
        val checkInDays = challengeRepository.checkInDaysSoFar(old)
        val prompt = """
            你是这个自律项目的严格监督者。用户正在进行中的挑战被修改了，请你判断这次修改是把挑战难度增强了还是减弱了。只允许增强，不允许减弱。

            【原挑战】
            挑战目标：${old.goal}
            每日打卡标准：${old.dailyStandardPrompt}
            周期：${old.startDate} ~ ${old.endDate}
            已打卡 $checkInDays / $totalDays 天

            【修改后的挑战】
            挑战目标：$goal
            每日打卡标准：$standard
            周期：${old.startDate} ~ $endDate（开始日期不变）

            判定规则：
            1. 判断修改后，用户从今天到截止日期的剩余天数内，每天需要付出的自律成本（努力、意志力、时间、痛苦程度等）整体是否上升。上升 = 增强，下降 = 减弱。
            2. 必须综合所有字段做净效果判断：挑战目标、每日打卡标准、截止日期。截止日期延后意味着要持续更久 = 增强。
            3. 已打卡记录保留，从今天起按新标准考核。如果修改让标准变得几乎不可能完成、本质上是在变相逃避（例如把标准改到不可能达成来逼自己失败），即使字面上更严格，也判定为减弱。
            4. 只要净效果不明确或模棱两可，一律判定为减弱。
            5. 自然语言回复中不要解释，只在回复末尾输出判定工具块，不能放在代码块里。

            请在回复末尾严格按下面格式输出：
            <tool_call>{"name":"judge_challenge_modify","arguments":{"allowed":true,"reason":"增强的依据"}}</tool_call>
            allowed 为 true 表示难度增强、允许修改；false 表示难度减弱、拒绝修改；reason 用简短中文说明判断依据。
        """.trimIndent()
        val reply = aiApiService.chat(
            api = api,
            messages = listOf(
                AiChatMessage(role = "system", content = "你是严格的自律监督者，按用户提供的判定规则输出结果。"),
                AiChatMessage(role = "user", content = prompt)
            )
        )
        return parseJudgeResult(reply)
    }

    private data class JudgeResult(
        val allowed: Boolean,
        val reason: String
    )

    // 解析 AI 判定回复中的 <tool_call> 工具块；格式不符返回 null。
    private fun parseJudgeResult(reply: String): JudgeResult? {
        val startTag = "<tool_call>"
        val endTag = "</tool_call>"
        val startIndex = reply.indexOf(startTag)
        val endIndex = reply.indexOf(endTag)
        if (startIndex < 0 || endIndex <= startIndex) return null
        return try {
            val toolJson = JSONObject(reply.substring(startIndex + startTag.length, endIndex).trim())
            if (toolJson.optString("name") != "judge_challenge_modify") return null
            val arguments = toolJson.getJSONObject("arguments")
            JudgeResult(
                allowed = arguments.optBoolean("allowed"),
                reason = arguments.optString("reason", "").trim()
            )
        } catch (e: Exception) {
            null
        }
    }

    // 手动失败：用户主动放弃进行中的挑战，置为 failed 进入失败惩罚流程（与缺卡判定失败同一流程）。
    // 仅已开始（非 pending）且 active 的挑战允许；未开始的挑战直接修改设置即可，无需手动失败。
    fun failManually(projectId: Long) {
        viewModelScope.launch {
            val challenge = challengeRepository.getByProjectId(projectId) ?: return@launch
            if (challenge.status != ChallengeRepository.STATUS_ACTIVE) return@launch
            if (challengeRepository.currentCheckInDate().isBefore(LocalDate.parse(challenge.startDate))) {
                _errorMessage.value = "挑战尚未开始，不能手动失败；如不想开始可直接修改挑战。"
                return@launch
            }
            challengeRepository.markAsFailed(challenge.id)
            _errorMessage.value = ""
            _noticeMessage.value = "挑战已手动标记为失败，进入失败惩罚流程：捐款 3 元及以上并上传捐款截图，审核通过后结算。"
        }
    }

    // 手动结算（截止日当天或之后）：挑战期内每天都打卡 → 成功；有缺卡 → 判定失败进入惩罚流程。
    fun settle(projectId: Long) {
        viewModelScope.launch {
            val challenge = challengeRepository.getByProjectId(projectId) ?: return@launch
            if (challenge.status != ChallengeRepository.STATUS_ACTIVE) return@launch
            if (LocalDate.parse(challenge.endDate).isAfter(challengeRepository.currentCheckInDate())) {
                _errorMessage.value = "截止日期未到，暂不能结算"
                return@launch
            }

            val checkIns = challengeRepository.getCheckIns(challenge.id).map { it.date }.toSet()
            var day = LocalDate.parse(challenge.startDate)
            val end = LocalDate.parse(challenge.endDate)
            var allDone = true
            while (!day.isAfter(end)) {
                if (day.toString() !in checkIns) {
                    allDone = false
                    break
                }
                day = day.plusDays(1)
            }

            _errorMessage.value = ""
            if (allDone) {
                challengeRepository.settleSuccess(challenge.id)
                _noticeMessage.value = "挑战成功！你坚持到了截止日期并每天完成了打卡。"
            } else {
                challengeRepository.markAsFailed(challenge.id)
                _noticeMessage.value = "挑战失败：挑战期内有日期未打卡。请捐款 3 元及以上并上传捐款截图，审核通过后结算。"
            }
        }
    }

    // 提交挑战失败惩罚的捐款截图：保存图片 → LLM 识别 → 本地规则判定，通过后结算该挑战。
    // 审核不通过时不入库，并删除本次保存的截图文件。
    fun submitPenaltyPayment(challengeId: Long, uri: Uri) {
        if (_paymentVerifying.value) return
        _paymentVerifying.value = true
        _errorMessage.value = ""
        _noticeMessage.value = ""
        viewModelScope.launch {
            var imagePath: String? = null
            try {
                val context = getApplication<Application>()
                imagePath = PaymentVerifier.saveImage(context, uri)
                when (val result = PaymentVerifier.verifyChallengePenalty(context, challengeId, imagePath)) {
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
                _paymentVerifying.value = false
            }
        }
    }

    private fun deleteQuietly(path: String) {
        viewModelScope.launch(Dispatchers.IO) { File(path).delete() }
    }
}
