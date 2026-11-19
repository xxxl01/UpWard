package com.xdl.upward.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 项目挑战：每一轮挑战一行，历史轮次与其打卡记录永久保留（日期模式判定依赖它们）。
 * 项目当前的挑战 = id 最大的一行；同一项目同时只有最新一轮可能是 active。
 * status：active 进行中 / failed 缺卡失败进入惩罚（中间态） / success 成功结算 / settled 失败惩罚完成后结算。
 */
@Entity(
    tableName = "challenge",
    indices = [Index(value = ["project_id"])]
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
    // 过渡期截止日期（含当天），为空表示没有过渡期；过渡期为 startDate ~ transitionEndDate。
    @ColumnInfo(name = "transition_end_date")
    val transitionEndDate: String = "",
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "finished_at")
    val finishedAt: String = ""
)
