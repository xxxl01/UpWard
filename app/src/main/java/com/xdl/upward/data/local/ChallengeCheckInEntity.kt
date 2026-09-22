package com.xdl.upward.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 挑战每日打卡记录：某天有记录即代表当天达成目标。
 * 同一挑战同一天至多一条打卡。
 */
@Entity(
    tableName = "challenge_checkin",
    indices = [Index(value = ["challenge_id", "date"], unique = true)]
)
data class ChallengeCheckInEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "challenge_id")
    val challengeId: Long,
    val date: String,
    val summary: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: String
)
