package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    @Query("SELECT * FROM config WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): ConfigEntity?

    @Query("SELECT value FROM config WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Query("SELECT value FROM config WHERE `key` = :key LIMIT 1")
    fun observeValue(key: String): Flow<String?>

    @Query("SELECT * FROM config ORDER BY id ASC")
    suspend fun getAllForBackup(): List<ConfigEntity>

    @Insert
    suspend fun insert(config: ConfigEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<ConfigEntity>)

    @Update
    suspend fun update(config: ConfigEntity)

    @Query("DELETE FROM config")
    suspend fun deleteAll()
}
