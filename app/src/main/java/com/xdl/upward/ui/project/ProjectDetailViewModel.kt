package com.xdl.upward.ui.project

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.MessageEntity
import com.xdl.upward.data.local.ProjectEntity
import com.xdl.upward.data.remote.AiApiService
import com.xdl.upward.data.repository.AiApiRepository
import com.xdl.upward.data.repository.ChallengeRepository
import com.xdl.upward.data.repository.ConfigRepository
import com.xdl.upward.data.repository.DailyRecordRepository
import com.xdl.upward.data.repository.MessageRepository
import com.xdl.upward.data.repository.ProjectRepository
import com.xdl.upward.domain.AiMessageBuilder
import com.xdl.upward.domain.ChatExecutor
import com.xdl.upward.reminder.RequiredMessageDeadlineHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ProjectDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = ProjectRepository(
        database.projectDao()
    )
    private val dailyRecordRepository = DailyRecordRepository(
        database.dailyRecordDao()
    )
    private val messageRepository = MessageRepository(
        database.messageDao()
    )
    private val aiApiRepository = AiApiRepository(
        database.aiApiDao()
    )
    private val configRepository = ConfigRepository(
        database.configDao()
    )
    private val challengeRepository = ChallengeRepository(
        database.challengeDao(),
        database.challengeCheckInDao()
    )
    private val aiApiService = AiApiService()
    private val aiMessageBuilder = AiMessageBuilder()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _violationCount = MutableStateFlow(0)
    val violationCount: StateFlow<Int> = _violationCount.asStateFlow()

    private val _selectedImagePaths = MutableStateFlow<List<String>>(emptyList())
    val selectedImagePaths: StateFlow<List<String>> = _selectedImagePaths.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _aiThinking = MutableStateFlow(false)
    val aiThinking: StateFlow<Boolean> = _aiThinking.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private var currentProjectId = 0L
    private var chatStateJob: Job? = null

    init {
        viewModelScope.launch {
            configRepository.observeViolationCount().collect { value ->
                _violationCount.value = value?.toIntOrNull() ?: 0
            }
        }
    }

    // 绑定当前项目：把聊天执行状态（loading/思考中/错误）从后台执行器透传到本页面的状态流。
    // 聊天请求由 ChatExecutor 在进程级作用域里执行，退出页面后仍在跑，回到页面时从这里恢复显示。
    fun setCurrentProject(projectId: Long) {
        if (currentProjectId == projectId) return
        currentProjectId = projectId
        chatStateJob?.cancel()
        chatStateJob = viewModelScope.launch {
            ChatExecutor.observeProjectChat(projectId).collect { state ->
                _loading.value = state.loading
                _aiThinking.value = state.aiThinking
                _errorMessage.value = state.errorMessage
            }
        }
    }

    fun observeProject(projectId: Long): Flow<ProjectEntity?> = repository.observeProject(projectId)

    fun observeMessages(projectId: Long): Flow<List<MessageEntity>> {
        return messageRepository.observeMessages(projectId)
    }

    fun updateInput(value: String) {
        _input.value = value
    }

    // 一次可选多张图片，按选择顺序压缩保存后追加到待发送列表；单张失败只跳过该张，不影响其余图片。
    fun addSelectedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return

        _errorMessage.value = ""
        viewModelScope.launch {
            val savedPaths = mutableListOf<String>()
            var failedCount = 0
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val imageDir = File(context.filesDir, "chat_images")
                if (!imageDir.exists()) {
                    imageDir.mkdirs()
                }
                uris.forEachIndexed { index, uri ->
                    try {
                        val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            BitmapFactory.decodeStream(inputStream)
                        } ?: throw IllegalStateException("图片读取失败")

                        val maxSide = 1600
                        val width = bitmap.width
                        val height = bitmap.height
                        val outputBitmap = if (width > maxSide || height > maxSide) {
                            val scale = maxSide.toFloat() / maxOf(width, height).toFloat()
                            Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
                        } else {
                            bitmap
                        }

                        // 多张图片可能在同一毫秒保存，文件名带上序号避免互相覆盖
                        val imageFile = File(imageDir, "chat_${System.currentTimeMillis()}_$index.webp")
                        FileOutputStream(imageFile).use { outputStream ->
                            outputBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, outputStream)
                        }
                        if (outputBitmap !== bitmap) {
                            outputBitmap.recycle()
                        }
                        bitmap.recycle()
                        savedPaths.add(imageFile.absolutePath)
                    } catch (e: Exception) {
                        failedCount++
                    }
                }
            }
            if (savedPaths.isNotEmpty()) {
                _selectedImagePaths.value = _selectedImagePaths.value + savedPaths
            }
            if (failedCount > 0) {
                _errorMessage.value = "有 $failedCount 张图片保存失败，已跳过。"
            }
        }
    }

    fun removeSelectedImage(path: String) {
        _selectedImagePaths.value = _selectedImagePaths.value.filter { it != path }
        viewModelScope.launch(Dispatchers.IO) {
            File(path).delete()
        }
    }

    fun checkRequiredMessageDeadline(projectId: Long) {
        viewModelScope.launch {
            // 挑战缺卡检测：挑战期内有日期没打卡则进入失败惩罚流程。
            challengeRepository.checkForMissedDays(projectId)
            val finalCount = RequiredMessageDeadlineHandler.checkAndRecord(getApplication(), projectId)
                ?: return@launch
            _errorMessage.value = if (finalCount >= 10) {
                "已错过指定回复时间，违规累计已达到惩罚线：需捐款 4 元并把截图发给违规告诫页的 AI，每笔捐款只能抵扣 10 次违规。"
            } else {
                "已错过指定回复时间，已记为违规 $finalCount/10。"
            }
        }
    }

    // 发送消息的完整请求流程（含流式写库、工具调用）由 ChatExecutor 在后台执行，
    // 这里只做输入校验和触发，退出页面后请求继续，回到页面时状态从执行器恢复。
    fun sendUserMessage(
        projectId: Long,
        criticalMode: Boolean = false,
        experienceMode: Boolean = false
    ) {
        val content = _input.value.trim()
        val imagePaths = _selectedImagePaths.value
        if ((content.isEmpty() && imagePaths.isEmpty()) || _loading.value) return

        // 违规累计 10 次进入惩罚状态：未完成捐款重置前，拦截所有项目的消息发送
        if (_violationCount.value >= 10) {
            _input.value = ""
            _selectedImagePaths.value = emptyList()
            _errorMessage.value = "你已累计违规 ${_violationCount.value} 次，处于惩罚状态，消息发送已被拦截。请到项目列表页的“违规告诫”入口捐款 4 元，把捐款截图发给 AI；每笔捐款只能抵扣 10 次违规。"
            return
        }

        _input.value = ""
        _selectedImagePaths.value = emptyList()
        _errorMessage.value = ""
        ChatExecutor.sendUserMessage(
            getApplication(),
            projectId,
            content,
            imagePaths,
            criticalMode,
            experienceMode
        )
    }

    fun saveInnerVoice(projectId: Long, content: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.saveInnerVoice(projectId, content.trim(), enabled)
        }
    }

    fun setInnerVoiceEnabled(projectId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setInnerVoiceEnabled(projectId, enabled)
        }
    }

    fun stopUserMessage(projectId: Long) {
        ChatExecutor.stop(projectId)
    }

    fun resendLastMessage(projectId: Long, messageId: Long) {
        if (_loading.value) return

        // 惩罚状态同样拦截重发
        if (_violationCount.value >= 10) {
            _errorMessage.value = "你已累计违规 ${_violationCount.value} 次，处于惩罚状态，消息发送已被拦截。请到“违规告诫”页捐款 4 元并截图给 AI；每笔捐款只能抵扣 10 次违规。"
            return
        }

        _errorMessage.value = ""
        ChatExecutor.resendLastMessage(getApplication(), projectId, messageId)
    }

    fun generateTodayRecord(projectId: Long) {
        if (_loading.value) return

        _errorMessage.value = ""
        viewModelScope.launch {
            try {
                _loading.value = true
                _aiThinking.value = false
                val project = repository.getProject(projectId)
                if (project == null) {
                    _errorMessage.value = "项目不存在"
                    return@launch
                }

                val prompt = project.dailyRecordPrompt
                val dailyRecords = dailyRecordRepository.getAllByProjectId(projectId)
                val todayMessages = messageRepository.getTodayMessages(projectId)
                if (todayMessages.isEmpty()) {
                    dailyRecordRepository.saveTodayRecord(
                        projectId = projectId,
                        content = "${dailyRecordRepository.todayDisplayText()} 没有数据"
                    )
                    return@launch
                }

                val api = aiApiRepository.getSelected()
                if (api == null) {
                    _errorMessage.value = "请先在 API 设置中选择一个 API"
                    return@launch
                }

                val requestMessages = aiMessageBuilder.buildDailyRecordMessages(
                    project = project,
                    dailyRecords = dailyRecords,
                    recentMessages = todayMessages,
                    prompt = prompt
                )
                val recordContent = aiApiService.chat(api, requestMessages)
                if (recordContent.isNotEmpty()) {
                    dailyRecordRepository.saveTodayRecord(projectId, recordContent)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "生成今日记录失败"
            } finally {
                _loading.value = false
                _aiThinking.value = false
            }
        }
    }

    fun updateMessage(messageId: Long, content: String) {
        val newContent = content.trim()
        if (newContent.isEmpty()) return

        viewModelScope.launch {
            messageRepository.updateMessageContent(messageId, newContent)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            messageRepository.deleteMessage(messageId)
        }
    }

    fun importMessages(projectId: Long, text: String) {
        _errorMessage.value = ""
        viewModelScope.launch {
            try {
                val trimmedText = text.trim()
                if (trimmedText.isEmpty()) {
                    _errorMessage.value = "导入失败：文件内容为空"
                    return@launch
                }

                val messageArray = if (trimmedText.startsWith("[")) {
                    JSONArray(trimmedText)
                } else {
                    val root = JSONObject(trimmedText)
                    root.getJSONArray("messages")
                }

                val importedMessages = mutableListOf<MessageEntity>()
                for (index in 0 until messageArray.length()) {
                    val item = messageArray.getJSONObject(index)
                    val role = item.optString("role", "").trim()
                    val content = item.optString("content", "").trim()
                    val createdAt = if (item.has("createdAt") && !item.isNull("createdAt")) {
                        item.getString("createdAt").trim()
                    } else {
                        ""
                    }

                    if (role != "user" && role != "assistant") {
                        _errorMessage.value = "导入失败：第 ${index + 1} 条消息 role 只能是 user 或 assistant"
                        return@launch
                    }
                    if (content.isEmpty()) {
                        _errorMessage.value = "导入失败：第 ${index + 1} 条消息内容为空"
                        return@launch
                    }

                    importedMessages.add(
                        MessageEntity(
                            projectId = projectId,
                            role = role,
                            content = content,
                            createdAt = createdAt
                        )
                    )
                }

                if (importedMessages.isEmpty()) {
                    _errorMessage.value = "导入失败：没有可导入的消息"
                    return@launch
                }

                messageRepository.importMessages(importedMessages)
            } catch (e: Exception) {
                _errorMessage.value = "导入失败：${e.message ?: "文件格式不正确"}"
            }
        }
    }
}
