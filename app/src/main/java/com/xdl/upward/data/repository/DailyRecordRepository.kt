package com.xdl.upward.data.repository

import com.xdl.upward.data.local.DailyRecordDao
import com.xdl.upward.data.local.DailyRecordEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class DailyRecordRepository(
    private val dailyRecordDao: DailyRecordDao
) {
    fun observeDailyRecords(projectId: Long): Flow<List<DailyRecordEntity>> {
        return dailyRecordDao.observeDailyRecords(projectId)
    }

    suspend fun getAllByProjectId(projectId: Long): List<DailyRecordEntity> {
        return dailyRecordDao.getAllByProjectId(projectId)
    }

    suspend fun getById(id: Long): DailyRecordEntity? = dailyRecordDao.getById(id)

    suspend fun saveRecord(recordId: Long, projectId: Long, date: String, content: String) {
        val now = OffsetDateTime.now().toString()
        if (recordId == 0L) {
            dailyRecordDao.insert(
                DailyRecordEntity(
                    projectId = projectId,
                    date = date,
                    content = content,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            val oldRecord = dailyRecordDao.getById(recordId) ?: return
            dailyRecordDao.update(
                oldRecord.copy(
                    date = date,
                    content = content,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun saveTodayRecord(projectId: Long, content: String) {
        val today = todayText()
        val oldRecord = dailyRecordDao.getByDate(projectId, today)
        val now = OffsetDateTime.now().toString()
        if (oldRecord == null) {
            dailyRecordDao.insert(
                DailyRecordEntity(
                    projectId = projectId,
                    date = today,
                    content = content,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            dailyRecordDao.update(
                oldRecord.copy(
                    content = content,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun deleteRecord(recordId: Long) {
        dailyRecordDao.deleteById(recordId)
    }

    fun todayText(): String {
        val now = OffsetDateTime.now()
        return if (now.toLocalTime().isBefore(LocalTime.of(4, 0))) {
            now.toLocalDate().minusDays(1).toString()
        } else {
            now.toLocalDate().toString()
        }
    }

    fun todayDisplayText(): String {
        return LocalDate.parse(todayText()).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
    }
}
