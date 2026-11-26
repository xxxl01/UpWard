package com.xdl.upward.data.repository

import com.xdl.upward.data.local.MessageDao
import com.xdl.upward.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import java.io.File
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

    suspend fun hasUserMessagesBetween(projectId: Long, startTime: String, endTime: String): Boolean {
        return messageDao.countUserMessagesBetween(projectId, startTime, endTime) > 0
    }

    suspend fun getMessagesBetween(projectId: Long, startTime: String, endTime: String): List<MessageEntity> {
        return messageDao.getMessagesBetween(projectId, startTime, endTime)
    }

    suspend fun getLastMessage(projectId: Long): MessageEntity? {
        return messageDao.getLastMessage(projectId)
    }

    suspend fun getLastUserMessage(projectId: Long): MessageEntity? {
        return messageDao.getLastUserMessage(projectId)
    }

    suspend fun addUserMessage(projectId: Long, content: String, imagePaths: List<String> = emptyList()) {
        val imagePathArray = JSONArray()
        imagePaths.forEach { path ->
            imagePathArray.put(path)
        }
        messageDao.insert(
            MessageEntity(
                projectId = projectId,
                role = "user",
                content = content,
                imagePaths = imagePathArray.toString(),
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

    suspend fun replaceMessagesWithSummary(projectId: Long, startTime: String, endTime: String, content: String, createdAt: String) {
        messageDao.replaceBetween(
            projectId,
            startTime,
            endTime,
            MessageEntity(
                projectId = projectId,
                role = "assistant",
                content = content,
                createdAt = createdAt
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
        val message = messageDao.getById(messageId)
        messageDao.deleteById(messageId)
        if (message != null && message.imagePaths.isNotBlank()) {
            try {
                val imagePathArray = JSONArray(message.imagePaths)
                for (index in 0 until imagePathArray.length()) {
                    val path = imagePathArray.optString(index).trim()
                    if (path.isNotEmpty()) {
                        File(path).delete()
                    }
                }
            } catch (e: Exception) {
            }
        }
    }
}
