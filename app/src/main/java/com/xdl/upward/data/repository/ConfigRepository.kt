package com.xdl.upward.data.repository

import com.xdl.upward.data.local.ConfigDao
import com.xdl.upward.data.local.ConfigEntity
import kotlinx.coroutines.flow.Flow

class ConfigRepository(
    private val configDao: ConfigDao
) {
    companion object {
        const val DEFAULT_CONTEXT_SUMMARY_PROMPT = "请把下面时间段内的用户和 AI 对话整理成一段连续、清楚、可替代原聊天记录的上下文总结。保留关键事实、决定、目标、约束、情绪状态、承诺事项和后续待办，不要写成列表，不要添加原文没有的信息。"
    }

    suspend fun getMessageContextCount(): Int {
        val value = configDao.getValue("message_context_count")
        return value?.toIntOrNull() ?: 20
    }

    suspend fun getContextSummaryPrompt(): String {
        return configDao.getValue("context_summary_prompt") ?: DEFAULT_CONTEXT_SUMMARY_PROMPT
    }

    fun observeViolationCount(): Flow<String?> {
        return configDao.observeValue("violation_count")
    }

    suspend fun getValue(key: String): String? {
        return configDao.getValue(key)
    }

    suspend fun setValue(key: String, value: String) {
        val oldConfig = configDao.getByKey(key)
        if (oldConfig == null) {
            configDao.insert(ConfigEntity(key = key, value = value))
        } else {
            configDao.update(oldConfig.copy(value = value))
        }
    }
}
