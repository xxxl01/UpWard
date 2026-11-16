package com.xdl.upward.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 违规记录：每次违规一条，违规次数 = deleted = 0 的记录数。
 * 捐款抵扣 / 月度清零不物理删除，而是逻辑删除（deleted = 1，并记录时间、原因、对应捐款）。
 * source：checkin 昨日缺卡 / deadline 错过指定回复时间 / legacy 旧版本迁移来的历史次数。
 * ref_key 唯一，用来保证同一次违规不会重复入账（如 checkin:2026-11-15）。
 */
@Entity(
    tableName = "violation_record",
    indices = [Index(value = ["ref_key"], unique = true)]
)
data class ViolationRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "ref_key")
    val refKey: String,
    val source: String,
    @ColumnInfo(name = "project_id")
    val projectId: Long = 0,
    // 违规对应的打卡日，旧数据为空
    val date: String = "",
    val detail: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    val deleted: Int = 0,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: String = "",
    // payment（捐款抵扣）/ monthly_reset（月度清零）
    @ColumnInfo(name = "delete_reason")
    val deleteReason: String = "",
    @ColumnInfo(name = "payment_record_id")
    val paymentRecordId: Long = 0
)
