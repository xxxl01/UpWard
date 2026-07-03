package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM message WHERE project_id = :projectId ORDER BY created_at ASC, id ASC")
    fun observeMessages(projectId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE project_id = :projectId ORDER BY created_at DESC, id DESC LIMIT :count")
    suspend fun getRecentMessages(projectId: Long, count: Int): List<MessageEntity>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("DELETE FROM message WHERE project_id = :projectId")
    suspend fun deleteByProjectId(projectId: Long)
}
