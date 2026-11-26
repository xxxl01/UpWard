package com.xdl.upward.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProjectEntity::class,
        MessageEntity::class,
        DailyRecordEntity::class,
        AiApiEntity::class,
        ConfigEntity::class,
        ChallengeEntity::class,
        ChallengeCheckInEntity::class,
        HolidayEntity::class,
        PaymentRecordEntity::class,
        DailyStatusEntity::class,
        ViolationRecordEntity::class,
        NotificationRecordEntity::class
    ],
    version = 23,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun messageDao(): MessageDao
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun aiApiDao(): AiApiDao
    abstract fun configDao(): ConfigDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun paymentRecordDao(): PaymentRecordDao
    abstract fun challengeCheckInDao(): ChallengeCheckInDao
    abstract fun holidayDao(): HolidayDao
    abstract fun dailyStatusDao(): DailyStatusDao
    abstract fun violationRecordDao(): ViolationRecordDao
    abstract fun notificationRecordDao(): NotificationRecordDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_api ADD COLUMN api_key TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE project ADD COLUMN daily_record_prompt TEXT NOT NULL DEFAULT '请根据今天的对话，总结用户在本项目中的进展、问题、情绪状态和下一步建议。'
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE project ADD COLUMN next_required_message_at TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE project ADD COLUMN next_required_message_start_at TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE project ADD COLUMN next_required_message_end_at TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE message ADD COLUMN image_paths TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ai_api ADD COLUMN support_image INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE project ADD COLUMN check_in_mode TEXT NOT NULL DEFAULT 'three_times'")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE project ADD COLUMN morning_reminder_time TEXT NOT NULL DEFAULT '07:00'")
                db.execSQL("ALTER TABLE project ADD COLUMN afternoon_reminder_time TEXT NOT NULL DEFAULT '12:00'")
                db.execSQL("ALTER TABLE project ADD COLUMN evening_reminder_time TEXT NOT NULL DEFAULT '19:00'")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE project DROP COLUMN morning_reminder_time")
                db.execSQL("ALTER TABLE project DROP COLUMN afternoon_reminder_time")
                db.execSQL("ALTER TABLE project DROP COLUMN evening_reminder_time")
                db.execSQL(
                    """
                    INSERT INTO config (`key`, value)
                    SELECT 'global_reminder_morning_time', '07:00'
                    WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'global_reminder_morning_time')
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO config (`key`, value)
                    SELECT 'global_reminder_afternoon_time', '13:00'
                    WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'global_reminder_afternoon_time')
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO config (`key`, value)
                    SELECT 'global_reminder_evening_time', '19:30'
                    WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'global_reminder_evening_time')
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 打卡时段分界调整：中午 12:00→13:00、晚上开始 19:00→19:30，提醒默认时间同步更新
                db.execSQL(
                    "UPDATE config SET value = '13:00' WHERE `key` = 'global_reminder_afternoon_time' AND value = '12:00'"
                )
                db.execSQL(
                    "UPDATE config SET value = '19:30' WHERE `key` = 'global_reminder_evening_time' AND value = '19:00'"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 无视觉 API 的视觉辅助模型引用：指向一条支持图片的 AI API 配置
                db.execSQL("ALTER TABLE ai_api ADD COLUMN vision_api_id INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 项目挑战：每项目一条挑战记录 + 每日打卡记录
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS challenge (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        project_id INTEGER NOT NULL,
                        goal TEXT NOT NULL,
                        daily_standard_prompt TEXT NOT NULL,
                        fail_penalty_prompt TEXT NOT NULL,
                        start_date TEXT NOT NULL,
                        end_date TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        finished_at TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_challenge_project_id ON challenge(project_id)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS challenge_checkin (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        challenge_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        summary TEXT NOT NULL DEFAULT '',
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_challenge_checkin_challenge_date ON challenge_checkin(challenge_id, date)"
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 中国法定节假日/调休缓存表：按年从公开接口拉取后落库
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS holiday (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        year INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        holiday INTEGER NOT NULL,
                        name TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_holiday_date ON holiday(date)")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 全局提醒分段开关：默认全部开启，兼容旧库
                db.execSQL(
                    """
                    INSERT INTO config (`key`, value)
                    SELECT 'global_reminder_morning_enabled', '1'
                    WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'global_reminder_morning_enabled')
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO config (`key`, value)
                    SELECT 'global_reminder_afternoon_enabled', '1'
                    WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'global_reminder_afternoon_enabled')
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO config (`key`, value)
                    SELECT 'global_reminder_evening_enabled', '1'
                    WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'global_reminder_evening_enabled')
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 项目级“心里话”：内容与开关，一对一存在 project 表
                db.execSQL("ALTER TABLE project ADD COLUMN inner_voice TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE project ADD COLUMN inner_voice_enabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 惩罚捐款记录：截图识别通过后入库，交易单号唯一防止重复使用
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payment_record (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        purpose TEXT NOT NULL,
                        challenge_id INTEGER NOT NULL,
                        order_no TEXT NOT NULL,
                        amount_cents INTEGER NOT NULL,
                        payee TEXT NOT NULL,
                        paid_at TEXT NOT NULL,
                        image_path TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payment_record_order_no ON payment_record (order_no)")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 挑战过渡期截止日期（含当天），空字符串表示没有过渡期
                db.execSQL("ALTER TABLE challenge ADD COLUMN transition_end_date TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 每日达标情况持久化表
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_status (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        project_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        component TEXT NOT NULL,
                        type TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_daily_status_project_id_date_component ON daily_status (project_id, date, component)")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS violation_record (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ref_key TEXT NOT NULL,
                        source TEXT NOT NULL,
                        project_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        detail TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        deleted INTEGER NOT NULL,
                        deleted_at TEXT NOT NULL,
                        delete_reason TEXT NOT NULL,
                        payment_record_id INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_violation_record_ref_key ON violation_record (ref_key)")
                // 把旧的 config.violation_count 计数转成等量的历史记录，然后删除该计数键
                var count = 0
                db.query("SELECT value FROM config WHERE `key` = 'violation_count'").use { c ->
                    if (c.moveToFirst()) count = c.getString(0)?.toIntOrNull() ?: 0
                }
                val now = java.time.OffsetDateTime.now().toString()
                for (i in 1..count) {
                    db.execSQL(
                        "INSERT INTO violation_record (ref_key, source, project_id, date, detail, created_at, deleted, deleted_at, delete_reason, payment_record_id) " +
                            "VALUES ('legacy:$i', 'legacy', 0, '', '升级前累计的违规次数', ?, 0, '', '', 0)",
                        arrayOf(now)
                    )
                }
                db.execSQL("DELETE FROM config WHERE `key` = 'violation_count'")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notification_record (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        kind TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        project_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_record_date ON notification_record (date)")
            }
        }

        // 挑战改为每轮一行：去掉 project_id 唯一约束（索引同名，改为非唯一）
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_challenge_project_id")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_challenge_project_id ON challenge (project_id)")
            }
        }

        // 2.9 一次性清零：挑战重构 + 缺卡误判修复后，升级前累积的违规与挑战惩罚大量是误记的，全部清除，从头开始。
        // 违规记录只做逻辑删除（保留历史），失败/缺卡中的挑战直接结算，连续全勤计数与追踪起点重置。
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) = resetPenalties(db)
        }

        // 2.10 再清零一次（换日改回凌晨 4 点 + 早起奖励上线），逻辑同 21→22
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) = resetPenalties(db)
        }

        private fun resetPenalties(db: SupportSQLiteDatabase) {
            run {
                val now = java.time.OffsetDateTime.now().toString()
                val nowTime = java.time.OffsetDateTime.now()
                // 打卡日：凌晨 4 点换日
                val today = if (nowTime.toLocalTime().isBefore(java.time.LocalTime.of(4, 0))) nowTime.toLocalDate().minusDays(1) else nowTime.toLocalDate()
                db.execSQL(
                    "UPDATE violation_record SET deleted = 1, deleted_at = ?, delete_reason = 'version_reset' WHERE deleted = 0",
                    arrayOf(now)
                )
                // failed（惩罚中）→ settled；active 但已有缺卡的挑战同样直接结算，避免打开页面又被判失败
                val toSettle = mutableListOf<Long>()
                db.query("SELECT id, start_date, end_date, status FROM challenge WHERE status IN ('failed', 'active')").use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        if (c.getString(3) == "failed") { toSettle.add(id); continue }
                        val start = runCatching { java.time.LocalDate.parse(c.getString(1)) }.getOrNull() ?: continue
                        val end = runCatching { java.time.LocalDate.parse(c.getString(2)) }.getOrNull() ?: continue
                        val last = if (end.isBefore(today.minusDays(1))) end else today.minusDays(1)
                        if (last.isBefore(start)) continue
                        val done = mutableSetOf<String>()
                        db.query("SELECT date FROM challenge_checkin WHERE challenge_id = ?", arrayOf<Any>(id)).use { k ->
                            while (k.moveToNext()) done.add(k.getString(0))
                        }
                        var d = start
                        while (!d.isAfter(last)) {
                            if (d.toString() !in done) { toSettle.add(id); break }
                            d = d.plusDays(1)
                        }
                    }
                }
                toSettle.forEach {
                    db.execSQL("UPDATE challenge SET status = 'settled', finished_at = ? WHERE id = ?", arrayOf<Any>(now, it))
                }
                // 追踪起点重置为现在：此前的时段不再回溯；全勤计数清零
                fun setConfig(key: String, value: String) {
                    db.execSQL("DELETE FROM config WHERE `key` = ?", arrayOf<Any>(key))
                    db.execSQL("INSERT INTO config (`key`, value) VALUES (?, ?)", arrayOf<Any>(key, value))
                }
                setConfig("tracking_start_at", now)
                setConfig("clean_streak_days", "0")
                setConfig("early_bird_halves", "0")
                setConfig("early_bird_last_date", today.minusDays(1).toString())
                setConfig("clean_streak_last_date", today.minusDays(1).toString())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "upward.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("DELETE FROM config WHERE `key` = 'daily_record_prompt'")
                            db.execSQL(
                                """
                                INSERT INTO config (`key`, value)
                                SELECT 'message_context_count', '20'
                                WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'message_context_count')
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                INSERT INTO config (`key`, value)
                                SELECT 'context_summary_prompt', '请把下面时间段内的用户和 AI 对话整理成一段连续、清楚、可替代原聊天记录的上下文总结。保留关键事实、决定、目标、约束、情绪状态、承诺事项和后续待办，不要写成列表，不要添加原文没有的信息。'
                                WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'context_summary_prompt')
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                INSERT INTO config (`key`, value)
                                SELECT 'global_reminder_morning_time', '07:00'
                                WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'global_reminder_morning_time')
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                INSERT INTO config (`key`, value)
                                SELECT 'global_reminder_afternoon_time', '13:00'
                                WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'global_reminder_afternoon_time')
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                INSERT INTO config (`key`, value)
                                SELECT 'global_reminder_evening_time', '19:30'
                                WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'global_reminder_evening_time')
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                INSERT INTO config (`key`, value)
                                SELECT 'global_reminder_morning_enabled', '1'
                                WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'global_reminder_morning_enabled')
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                INSERT INTO config (`key`, value)
                                SELECT 'global_reminder_afternoon_enabled', '1'
                                WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'global_reminder_afternoon_enabled')
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                INSERT INTO config (`key`, value)
                                SELECT 'global_reminder_evening_enabled', '1'
                                WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'global_reminder_evening_enabled')
                                """.trimIndent()
                            )
                        }
                    })
                    .build()
                    .also { instance = it }
            }
        }
    }
}
