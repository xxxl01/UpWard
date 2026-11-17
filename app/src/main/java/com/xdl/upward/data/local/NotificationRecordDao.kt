package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationRecordDao {
    // 有通知的日期，新的在前（按日期分页）
    @Query("SELECT DISTINCT date FROM notification_record ORDER BY date DESC")
    fun observeDates(): Flow<List<String>>

    @Query("SELECT * FROM notification_record WHERE date = :date ORDER BY id DESC")
    fun observeByDate(date: String): Flow<List<NotificationRecordEntity>>

    @Query("SELECT * FROM notification_record ORDER BY id ASC")
    suspend fun getAllForBackup(): List<NotificationRecordEntity>

    @Insert
    suspend fun insert(record: NotificationRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<NotificationRecordEntity>)

    @Query("DELETE FROM notification_record")
    suspend fun deleteAll()
}
