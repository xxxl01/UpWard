package com.xdl.upward.data.local

import androidx.room.Dao
import androidx.room.Insert
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

    @Insert
    suspend fun insert(config: ConfigEntity): Long

    @Update
    suspend fun update(config: ConfigEntity)
}
