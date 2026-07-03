package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyRecordDao {
    @Query("SELECT * FROM daily_record WHERE project_id = :projectId ORDER BY date ASC, id ASC")
    fun observeDailyRecords(projectId: Long): Flow<List<DailyRecordEntity>>

    @Query("SELECT * FROM daily_record WHERE project_id = :projectId ORDER BY date ASC, id ASC")
    suspend fun getAllByProjectId(projectId: Long): List<DailyRecordEntity>

    @Query("SELECT * FROM daily_record WHERE project_id = :projectId AND date = :date LIMIT 1")
    suspend fun getByDate(projectId: Long, date: String): DailyRecordEntity?

    @Query("SELECT * FROM daily_record WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DailyRecordEntity?

    @Insert
    suspend fun insert(record: DailyRecordEntity): Long

    @Update
    suspend fun update(record: DailyRecordEntity)

    @Query("DELETE FROM daily_record WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM daily_record WHERE project_id = :projectId")
    suspend fun deleteByProjectId(projectId: Long)
}
