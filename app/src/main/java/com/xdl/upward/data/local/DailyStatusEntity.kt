package com.xdl.upward.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每日达标情况：每个 (项目, 打卡日, 组件) 一条记录。
 * component：morning / afternoon / evening（一天三次）、daily（一天一次）、challenge（挑战）。
 * type：three_times / once_per_day / challenge。
 * status：pending 待定 / success 成功 / failed 失败。
 * 写入规则见 DailyStatusDao.record：没有记录就新增；已有 pending 才允许变成 success/failed；
 * 已是 success/failed 的不再改动。
 */
@Entity(
    tableName = "daily_status",
    indices = [Index(value = ["project_id", "date", "component"], unique = true)]
)
data class DailyStatusEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    // 打卡日（24 点换日，即自然日），格式 yyyy-MM-dd
    val date: String,
    val component: String,
    val type: String,
    val status: String,
    // 记录写入的日期时间
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: String
) {
    companion object {
        const val PENDING = "pending"
        const val SUCCESS = "success"
        const val FAILED = "failed"
    }
}
