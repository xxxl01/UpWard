package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AiApiDao {
    @Query("SELECT * FROM ai_api ORDER BY id DESC")
    fun observeAiApis(): Flow<List<AiApiEntity>>

    @Query("SELECT * FROM ai_api WHERE selected = 1 LIMIT 1")
    suspend fun getSelected(): AiApiEntity?

    @Query("SELECT * FROM ai_api WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AiApiEntity?

    @Insert
    suspend fun insert(api: AiApiEntity): Long

    @Update
    suspend fun update(api: AiApiEntity)

    @Query("UPDATE ai_api SET selected = 0")
    suspend fun clearSelected()

    @Query("UPDATE ai_api SET selected = 1 WHERE id = :id")
    suspend fun selectById(id: Long)

    @Query("DELETE FROM ai_api WHERE id = :id")
    suspend fun deleteById(id: Long)
}
