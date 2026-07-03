package com.xdl.upward.data.repository

import com.xdl.upward.data.local.AiApiDao
import com.xdl.upward.data.local.AiApiEntity
import kotlinx.coroutines.flow.Flow

class AiApiRepository(
    private val aiApiDao: AiApiDao
) {
    fun observeAiApis(): Flow<List<AiApiEntity>> = aiApiDao.observeAiApis()

    suspend fun getSelected(): AiApiEntity? = aiApiDao.getSelected()

    suspend fun getById(id: Long): AiApiEntity? = aiApiDao.getById(id)

    suspend fun saveApi(id: Long, baseUrl: String, apiKey: String, model: String, temperature: Double, selected: Boolean) {
        if (id == 0L) {
            val newId = aiApiDao.insert(
                AiApiEntity(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    temperature = temperature,
                    selected = false
                )
            )
            if (selected) {
                aiApiDao.clearSelected()
                aiApiDao.selectById(newId)
            }
        } else {
            val oldApi = aiApiDao.getById(id) ?: return
            aiApiDao.update(
                oldApi.copy(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    temperature = temperature,
                    selected = if (selected) false else oldApi.selected
                )
            )
            if (selected) {
                aiApiDao.clearSelected()
                aiApiDao.selectById(id)
            }
        }
    }

    suspend fun selectApi(id: Long) {
        aiApiDao.clearSelected()
        aiApiDao.selectById(id)
    }
}
