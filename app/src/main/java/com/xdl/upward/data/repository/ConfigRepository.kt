package com.xdl.upward.data.repository

import com.xdl.upward.data.local.ConfigDao
import com.xdl.upward.data.local.ConfigEntity
import kotlinx.coroutines.flow.Flow

class ConfigRepository(
    private val configDao: ConfigDao
) {
    suspend fun getMessageContextCount(): Int {
        val value = configDao.getValue("message_context_count")
        return value?.toIntOrNull() ?: 20
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
