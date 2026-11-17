package com.xdl.upward.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 推送通知记录：每次真正提交给系统的通知一条（通知权限未开启、没发出去的不记）。
 * kind：global_reminder 固定提醒 / required_message 指定回复提醒。
 * date 是通知当天的日历日期（yyyy-MM-dd），用来按日期分页。
 */
@Entity(
    tableName = "notification_record",
    indices = [Index(value = ["date"])]
)
data class NotificationRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val kind: String,
    val title: String,
    val content: String,
    @ColumnInfo(name = "project_id")
    val projectId: Long = 0,
    val date: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String
)
