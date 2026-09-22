package com.xdl.upward.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 项目挑战：每个项目同时只有一条挑战记录（project_id 唯一）。
 * 新一轮挑战是对同一条记录的修改（重置状态、改提示词与截止日期），旧打卡记录在开新一轮时清空。
 * status：active 进行中 / failed 缺卡失败进入惩罚（中间态） / success 成功结算 / settled 失败惩罚完成后结算。
 */
@Entity(
    tableName = "challenge",
    indices = [Index(value = ["project_id"], unique = true)]
)
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    val goal: String,
    @ColumnInfo(name = "daily_standard_prompt")
    val dailyStandardPrompt: String,
    @ColumnInfo(name = "fail_penalty_prompt")
    val failPenaltyPrompt: String,
    @ColumnInfo(name = "start_date")
    val startDate: String,
    @ColumnInfo(name = "end_date")
    val endDate: String,
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "finished_at")
    val finishedAt: String = ""
)
