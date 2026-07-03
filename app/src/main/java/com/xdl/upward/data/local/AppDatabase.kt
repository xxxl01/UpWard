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
        ConfigEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun messageDao(): MessageDao
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun aiApiDao(): AiApiDao
    abstract fun configDao(): ConfigDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ai_api ADD COLUMN api_key TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "upward.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL(
                                """
                                INSERT INTO config (`key`, value)
                                SELECT 'daily_record_prompt', '请根据今天的对话，总结用户在本项目中的进展、问题、情绪状态和下一步建议。'
                                WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'daily_record_prompt')
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                INSERT INTO config (`key`, value)
                                SELECT 'message_context_count', '20'
                                WHERE NOT EXISTS (SELECT 1 FROM config WHERE `key` = 'message_context_count')
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
