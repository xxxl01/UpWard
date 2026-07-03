package com.xdl.upward.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_api")
data class AiApiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "base_url")
    val baseUrl: String,
    @ColumnInfo(name = "api_key")
    val apiKey: String,
    val model: String,
    val temperature: Double,
    val selected: Boolean = false
)
