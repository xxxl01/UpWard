package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM project ORDER BY created_at DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM project ORDER BY created_at DESC")
    suspend fun getProjects(): List<ProjectEntity>

    @Query("SELECT * FROM project ORDER BY id ASC")
    suspend fun getAllForBackup(): List<ProjectEntity>

    @Query("SELECT * FROM project WHERE id = :id LIMIT 1")
    suspend fun getProject(id: Long): ProjectEntity?

    @Query("SELECT * FROM project WHERE id = :id LIMIT 1")
    fun observeProject(id: Long): Flow<ProjectEntity?>

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(projects: List<ProjectEntity>)

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("UPDATE project SET next_required_message_start_at = :startTime, next_required_message_end_at = :endTime, next_required_message_at = '' WHERE id = :id")
    suspend fun updateNextRequiredMessagePeriod(id: Long, startTime: String, endTime: String)

    @Query("DELETE FROM project WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM project")
    suspend fun deleteAll()
}
