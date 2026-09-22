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
        ChallengeCheckInEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun messageDao(): MessageDao
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun aiApiDao(): AiApiDao
    abstract fun configDao(): ConfigDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun challengeCheckInDao(): ChallengeCheckInDao

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

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "upward.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
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
                        }
                    })
                    .build()
                    .also { instance = it }
            }
        }
    }
}
