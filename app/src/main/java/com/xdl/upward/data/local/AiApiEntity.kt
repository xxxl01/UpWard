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
    @ColumnInfo(name = "support_image")
    val supportImage: Boolean = false,
    @ColumnInfo(name = "vision_api_id")
    val visionApiId: Long = 0,
    val selected: Boolean = false
)
