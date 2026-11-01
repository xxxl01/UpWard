package com.xdl.upward.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 中国法定节假日/调休缓存：按年从公开接口拉取后落库，避免聊天时反复请求。
 * holiday 为 true 表示放假，false 表示补班（调休上班）。
 * date 存完整日期 yyyy-MM-dd，同一天全局唯一。
 */
@Entity(
    tableName = "holiday",
    indices = [Index(value = ["date"], unique = true)]
)
data class HolidayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val year: Int,
    val date: String,
    val holiday: Boolean,
    val name: String
)
