package com.xdl.upward.data.repository

import com.xdl.upward.data.local.MessageDao
import com.xdl.upward.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime
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

    suspend fun getTodayMessages(projectId: Long): List<MessageEntity> {
        val now = OffsetDateTime.now()
        val businessDate = if (now.toLocalTime().isBefore(LocalTime.of(4, 0))) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate()
        }
        val startTime = businessDate.atTime(4, 0).atOffset(now.offset).toString()
        val endTime = businessDate.plusDays(1).atTime(4, 0).atOffset(now.offset).toString()
        return messageDao.getMessagesBetween(projectId, startTime, endTime)
    }

    suspend fun hasMessagesBetween(projectId: Long, startTime: String, endTime: String): Boolean {
        return messageDao.countMessagesBetween(projectId, startTime, endTime) > 0
    }

    suspend fun getLastMessage(projectId: Long): MessageEntity? {
        return messageDao.getLastMessage(projectId)
    }

    suspend fun getLastUserMessage(projectId: Long): MessageEntity? {
        return messageDao.getLastUserMessage(projectId)
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

    suspend fun addAssistantMessage(projectId: Long, content: String): Long {
        return messageDao.insert(
            MessageEntity(
                projectId = projectId,
                role = "assistant",
                content = content,
                createdAt = OffsetDateTime.now().toString()
            )
        )
    }

    suspend fun importMessages(messages: List<MessageEntity>) {
        if (messages.isNotEmpty()) {
            messageDao.insertAll(messages)
        }
    }

    suspend fun updateMessageContent(messageId: Long, content: String) {
        messageDao.updateContent(messageId, content)
    }

    suspend fun deleteMessage(messageId: Long) {
        messageDao.deleteById(messageId)
    }
}
