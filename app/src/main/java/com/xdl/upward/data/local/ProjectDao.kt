package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM project ORDER BY created_at DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM project ORDER BY created_at DESC")
    suspend fun getProjects(): List<ProjectEntity>

    @Query("SELECT * FROM project WHERE id = :id LIMIT 1")
    suspend fun getProject(id: Long): ProjectEntity?

    @Query("SELECT * FROM project WHERE id = :id LIMIT 1")
    fun observeProject(id: Long): Flow<ProjectEntity?>

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM project WHERE id = :id")
    suspend fun deleteById(id: Long)
}
