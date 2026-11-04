package com.xdl.upward.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 惩罚捐款记录：只保存审核通过的捐款。
 * order_no（交易单号）唯一，用来防止同一笔捐款重复抵扣惩罚。
 * purpose：challenge_penalty 挑战失败惩罚 / violation_penalty 违规惩罚。
 */
@Entity(
    tableName = "payment_record",
    indices = [Index(value = ["order_no"], unique = true)]
)
data class PaymentRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val purpose: String,
    @ColumnInfo(name = "challenge_id")
    val challengeId: Long = 0,
    @ColumnInfo(name = "order_no")
    val orderNo: String,
    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,
    val payee: String,
    @ColumnInfo(name = "paid_at")
    val paidAt: String,
    @ColumnInfo(name = "image_path")
    val imagePath: String,
    @ColumnInfo(name = "created_at")
    val createdAt: String
)
