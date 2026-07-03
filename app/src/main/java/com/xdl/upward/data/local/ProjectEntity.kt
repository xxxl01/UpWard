package com.xdl.upward.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String
)
