package com.xdl.upward.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    val role: String,
    val content: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String
)
