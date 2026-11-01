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
    @ColumnInfo(name = "daily_record_prompt")
    val dailyRecordPrompt: String,
    @ColumnInfo(name = "check_in_mode")
    val checkInMode: String = "three_times",
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "next_required_message_at")
    val nextRequiredMessageAt: String = "",
    @ColumnInfo(name = "next_required_message_start_at")
    val nextRequiredMessageStartAt: String = "",
    @ColumnInfo(name = "next_required_message_end_at")
    val nextRequiredMessageEndAt: String = "",
    @ColumnInfo(name = "inner_voice")
    val innerVoice: String = "",
    @ColumnInfo(name = "inner_voice_enabled")
    val innerVoiceEnabled: Boolean = false
)
