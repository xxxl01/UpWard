package com.xdl.upward.data.repository

import com.xdl.upward.data.local.MessageDao
import com.xdl.upward.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow
import java.time.OffsetDateTime

class MessageRepository(
    private val messageDao: MessageDao
) {
    fun observeMessages(projectId: Long): Flow<List<MessageEntity>> {
        return messageDao.observeMessages(projectId)
    }

    suspend fun getRecentMessages(projectId: Long, count: Int): List<MessageEntity> {
        return messageDao.getRecentMessages(projectId, count).asReversed()
    }

    suspend fun addUserMessage(projectId: Long, content: String) {
        messageDao.insert(
            MessageEntity(
                projectId = projectId,
                role = "user",
                content = content,
                createdAt = OffsetDateTime.now().toString()
            )
        )
    }

    suspend fun addAssistantMessage(projectId: Long, content: String) {
        messageDao.insert(
            MessageEntity(
                projectId = projectId,
                role = "assistant",
                content = content,
                createdAt = OffsetDateTime.now().toString()
            )
        )
    }
}
