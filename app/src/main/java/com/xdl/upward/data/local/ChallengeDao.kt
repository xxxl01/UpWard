package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenge WHERE project_id = :projectId LIMIT 1")
    fun observeByProjectId(projectId: Long): Flow<ChallengeEntity?>

    @Query("SELECT * FROM challenge WHERE project_id = :projectId LIMIT 1")
    suspend fun getByProjectId(projectId: Long): ChallengeEntity?

    @Query("SELECT * FROM challenge WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ChallengeEntity?

    @Query("SELECT * FROM challenge ORDER BY id ASC")
    suspend fun getAllForBackup(): List<ChallengeEntity>

    @Query("SELECT * FROM challenge WHERE status = 'failed'")
    suspend fun getFailed(): List<ChallengeEntity>

    @Insert
    suspend fun insert(challenge: ChallengeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(challenges: List<ChallengeEntity>)

    @Update
    suspend fun update(challenge: ChallengeEntity)

    @Query("UPDATE challenge SET status = :status, finished_at = :finishedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, finishedAt: String)

    @Query("DELETE FROM challenge WHERE project_id = :projectId")
    suspend fun deleteByProjectId(projectId: Long)

    @Query("DELETE FROM challenge")
    suspend fun deleteAll()
}
