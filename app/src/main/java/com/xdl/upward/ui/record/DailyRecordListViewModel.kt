package com.xdl.upward.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.DailyRecordEntity
import com.xdl.upward.data.repository.DailyRecordRepository
import kotlinx.coroutines.flow.Flow

class DailyRecordListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DailyRecordRepository(
        AppDatabase.getInstance(application).dailyRecordDao()
    )

    fun observeDailyRecords(projectId: Long): Flow<List<DailyRecordEntity>> {
        return repository.observeDailyRecords(projectId)
    }
}
