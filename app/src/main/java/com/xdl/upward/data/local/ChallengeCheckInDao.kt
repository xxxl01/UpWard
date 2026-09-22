package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeCheckInDao {
    @Query("SELECT * FROM challenge_checkin WHERE challenge_id = :challengeId ORDER BY date ASC, id ASC")
    fun observeByChallengeId(challengeId: Long): Flow<List<ChallengeCheckInEntity>>

    @Query("SELECT * FROM challenge_checkin WHERE challenge_id = :challengeId ORDER BY date ASC, id ASC")
    suspend fun getByChallengeId(challengeId: Long): List<ChallengeCheckInEntity>

    @Query("SELECT * FROM challenge_checkin WHERE challenge_id = :challengeId AND date = :date LIMIT 1")
    suspend fun getByChallengeIdAndDate(challengeId: Long, date: String): ChallengeCheckInEntity?

    @Query("SELECT * FROM challenge_checkin WHERE challenge_id = :challengeId AND date >= :startDate AND date <= :endDate ORDER BY date ASC, id ASC")
    suspend fun getBetween(challengeId: Long, startDate: String, endDate: String): List<ChallengeCheckInEntity>

    @Query("SELECT COUNT(*) FROM challenge_checkin WHERE challenge_id = :challengeId AND date >= :startDate AND date <= :endDate")
    suspend fun countBetween(challengeId: Long, startDate: String, endDate: String): Int

    @Query("SELECT * FROM challenge_checkin ORDER BY id ASC")
    suspend fun getAllForBackup(): List<ChallengeCheckInEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(checkIn: ChallengeCheckInEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(checkIns: List<ChallengeCheckInEntity>)

    @Query("DELETE FROM challenge_checkin WHERE challenge_id = :challengeId")
    suspend fun deleteByChallengeId(challengeId: Long)

    @Query("DELETE FROM challenge_checkin")
    suspend fun deleteAll()
}
