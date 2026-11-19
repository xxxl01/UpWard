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

    @Query("SELECT * FROM violation_record ORDER BY id DESC")
    fun observeAll(): Flow<List<ViolationRecordEntity>>

    // 有违规记录的月份（按记录时间，yyyy-MM），新的在前，用于按月分页
    @Query("SELECT DISTINCT substr(created_at, 1, 7) FROM violation_record ORDER BY 1 DESC")
    fun observeMonths(): Flow<List<String>>

    @Query("SELECT * FROM violation_record WHERE substr(created_at, 1, 7) = :month ORDER BY id DESC")
    fun observeByMonth(month: String): Flow<List<ViolationRecordEntity>>

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

    @Query("SELECT created_at FROM violation_record WHERE deleted = 0 ORDER BY id ASC LIMIT 1 OFFSET 9")
    suspend fun tenthActiveCreatedAt(): String?

    @Query("SELECT COUNT(*) FROM violation_record WHERE date >= :fromDate AND date <= :toDate")
    suspend fun countByDateRange(fromDate: String, toDate: String): Int

    @Query("DELETE FROM violation_record WHERE ref_key = :refKey")
    suspend fun deleteByRefKey(refKey: String)

    @Query("DELETE FROM violation_record")
    suspend fun deleteAll()
}
