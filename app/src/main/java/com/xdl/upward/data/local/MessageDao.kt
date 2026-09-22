package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM message WHERE project_id = :projectId ORDER BY created_at ASC, id ASC")
    fun observeMessages(projectId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE project_id = :projectId ORDER BY created_at DESC, id DESC LIMIT :count")
    suspend fun getRecentMessages(projectId: Long, count: Int): List<MessageEntity>

    @Query("SELECT * FROM message WHERE project_id = :projectId ORDER BY id ASC")
    suspend fun getAllByProjectId(projectId: Long): List<MessageEntity>

    @Query("SELECT * FROM message ORDER BY id ASC")
    suspend fun getAllForBackup(): List<MessageEntity>

    @Query("SELECT * FROM message WHERE project_id = :projectId AND created_at >= :startTime AND created_at < :endTime ORDER BY created_at ASC, id ASC")
    suspend fun getMessagesBetween(projectId: Long, startTime: String, endTime: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM message WHERE project_id = :projectId AND created_at >= :startTime AND created_at < :endTime")
    suspend fun countMessagesBetween(projectId: Long, startTime: String, endTime: String): Int

    @Query("SELECT COUNT(*) FROM message WHERE project_id = :projectId AND role = 'user' AND created_at >= :startTime AND created_at < :endTime")
    suspend fun countUserMessagesBetween(projectId: Long, startTime: String, endTime: String): Int

    @Query("SELECT * FROM message WHERE project_id = :projectId ORDER BY created_at DESC, id DESC LIMIT 1")
    suspend fun getLastMessage(projectId: Long): MessageEntity?

    @Query("SELECT * FROM message WHERE project_id = :projectId AND role = 'user' ORDER BY created_at DESC, id DESC LIMIT 1")
    suspend fun getLastUserMessage(projectId: Long): MessageEntity?

    @Query("SELECT * FROM message WHERE id = :messageId LIMIT 1")
    suspend fun getById(messageId: Long): MessageEntity?

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("UPDATE message SET content = :content WHERE id = :messageId")
    suspend fun updateContent(messageId: Long, content: String)

    @Query("DELETE FROM message WHERE id = :messageId")
    suspend fun deleteById(messageId: Long)

    @Query("DELETE FROM message WHERE project_id = :projectId AND created_at >= :startTime AND created_at < :endTime")
    suspend fun deleteBetween(projectId: Long, startTime: String, endTime: String)

    @Transaction
    suspend fun replaceBetween(projectId: Long, startTime: String, endTime: String, message: MessageEntity) {
        deleteBetween(projectId, startTime, endTime)
        insert(message)
    }

    @Query("DELETE FROM message WHERE project_id = :projectId")
    suspend fun deleteByProjectId(projectId: Long)

    @Query("DELETE FROM message")
    suspend fun deleteAll()
}
