package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import java.time.OffsetDateTime

@Dao
abstract class DailyStatusDao {
    @Query("SELECT * FROM daily_status WHERE date = :date")
    abstract suspend fun getByDate(date: String): List<DailyStatusEntity>

    @Query("SELECT * FROM daily_status WHERE project_id = :projectId AND date = :date AND component = :component LIMIT 1")
    abstract suspend fun find(projectId: Long, date: String, component: String): DailyStatusEntity?

    @Query("SELECT * FROM daily_status WHERE status = 'failed' AND date >= :fromDate ORDER BY date ASC, id ASC")
    abstract suspend fun getFailedFrom(fromDate: String): List<DailyStatusEntity>

    @Query("SELECT * FROM daily_status ORDER BY id ASC")
    abstract suspend fun getAllForBackup(): List<DailyStatusEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(entity: DailyStatusEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<DailyStatusEntity>)

    @Query("UPDATE daily_status SET status = :status, updated_at = :updatedAt WHERE id = :id")
    abstract suspend fun updateStatus(id: Long, status: String, updatedAt: String)

    @Query("DELETE FROM daily_status WHERE project_id = :projectId")
    abstract suspend fun deleteByProjectId(projectId: Long)

    @Query("DELETE FROM daily_status")
    abstract suspend fun deleteAll()

    /** 没有记录就新增；已有 pending 且新状态不是 pending 才更新；其余情况不动。 */
    @Transaction
    open suspend fun record(projectId: Long, date: String, component: String, type: String, status: String) {
        val now = OffsetDateTime.now().toString()
        val existing = find(projectId, date, component)
        if (existing == null) {
            insert(DailyStatusEntity(0, projectId, date, component, type, status, now, now))
        } else if (existing.status == DailyStatusEntity.PENDING && status != DailyStatusEntity.PENDING) {
            updateStatus(existing.id, status, now)
        }
    }
}
