package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ViolationRecordDao {
    @Query("SELECT COUNT(*) FROM violation_record WHERE deleted = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM violation_record WHERE deleted = 0")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM violation_record ORDER BY id ASC")
    suspend fun getAllForBackup(): List<ViolationRecordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: ViolationRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ViolationRecordEntity>)

    // 逻辑删除最早的 n 条有效记录（捐款抵扣）
    @Query(
        """
        UPDATE violation_record
        SET deleted = 1, deleted_at = :at, delete_reason = :reason, payment_record_id = :paymentId
        WHERE id IN (SELECT id FROM violation_record WHERE deleted = 0 ORDER BY id ASC LIMIT :n)
        """
    )
    suspend fun logicalDeleteOldest(n: Int, at: String, reason: String, paymentId: Long)

    @Query("UPDATE violation_record SET deleted = 1, deleted_at = :at, delete_reason = :reason WHERE deleted = 0")
    suspend fun logicalDeleteAllActive(at: String, reason: String)

    @Query("DELETE FROM violation_record")
    suspend fun deleteAll()
}
