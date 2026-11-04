package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentRecordDao {
    @Query("SELECT * FROM payment_record ORDER BY id DESC")
    fun observeAll(): Flow<List<PaymentRecordEntity>>

    @Query("SELECT * FROM payment_record WHERE purpose = :purpose ORDER BY id DESC")
    fun observeByPurpose(purpose: String): Flow<List<PaymentRecordEntity>>

    @Query("SELECT * FROM payment_record WHERE order_no = :orderNo LIMIT 1")
    suspend fun getByOrderNo(orderNo: String): PaymentRecordEntity?

    @Query("SELECT * FROM payment_record ORDER BY id ASC")
    suspend fun getAllForBackup(): List<PaymentRecordEntity>

    @Insert
    suspend fun insert(record: PaymentRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<PaymentRecordEntity>)

    @Query("DELETE FROM payment_record")
    suspend fun deleteAll()
}
