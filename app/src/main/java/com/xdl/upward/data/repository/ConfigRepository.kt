package com.xdl.upward.data.repository

import com.xdl.upward.data.local.ConfigDao

class ConfigRepository(
    private val configDao: ConfigDao
) {
    suspend fun getMessageContextCount(): Int {
        val value = configDao.getValue("message_context_count")
        return value?.toIntOrNull() ?: 20
    }

    suspend fun getDailyRecordPrompt(): String {
        return configDao.getValue("daily_record_prompt")
            ?: "请根据今天的对话，总结用户在本项目中的进展、问题、情绪状态和下一步建议。"
    }
}
