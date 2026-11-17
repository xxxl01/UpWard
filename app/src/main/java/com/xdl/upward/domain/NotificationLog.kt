package com.xdl.upward.domain

import android.content.Context
import android.util.Log
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.NotificationRecordEntity
import java.time.OffsetDateTime

/** 通知入库：每次成功提交给系统的通知记一条，供“通知记录”页查询。 */
object NotificationLog {
    const val KIND_GLOBAL_REMINDER = "global_reminder"
    const val KIND_REQUIRED_MESSAGE = "required_message"

    suspend fun record(context: Context, kind: String, title: String, content: String, projectId: Long = 0L) {
        try {
            val now = OffsetDateTime.now()
            AppDatabase.getInstance(context).notificationRecordDao().insert(
                NotificationRecordEntity(
                    kind = kind,
                    title = title,
                    content = content,
                    projectId = projectId,
                    date = now.toLocalDate().toString(),
                    createdAt = now.toString()
                )
            )
        } catch (e: Exception) {
            // 记录失败不能影响通知本身
            Log.e("NotificationLog", "通知入库失败", e)
        }
    }
}
