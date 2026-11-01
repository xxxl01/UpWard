package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holiday WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): HolidayEntity?

    @Query("SELECT COUNT(*) FROM holiday WHERE year = :year")
    suspend fun countByYear(year: Int): Int

    @Query("SELECT DISTINCT year FROM holiday ORDER BY year ASC")
    fun observeCachedYears(): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HolidayEntity>)

    @Query("DELETE FROM holiday WHERE year = :year")
    suspend fun deleteByYear(year: Int)

    @Transaction
    suspend fun replaceYear(year: Int, items: List<HolidayEntity>) {
        deleteByYear(year)
        insertAll(items)
    }
}
