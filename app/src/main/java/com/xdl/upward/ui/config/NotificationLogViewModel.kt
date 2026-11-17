package com.xdl.upward.ui.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.NotificationRecordEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** 通知记录：按日期分页，一页一天，只在“有通知的日期”之间翻页（0 = 最新）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationLogViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).notificationRecordDao()

    val dates: StateFlow<List<String>> = dao.observeDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pageIndex = MutableStateFlow(0)
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    val records: StateFlow<List<NotificationRecordEntity>> = combine(dates, _pageIndex) { list, index ->
        list.getOrNull(index.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
    }.flatMapLatest { date ->
        if (date == null) flowOf(emptyList()) else dao.observeByDate(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // delta > 0 往更早的日期翻，< 0 往更新的日期翻
    fun shift(delta: Int) {
        val max = (dates.value.size - 1).coerceAtLeast(0)
        _pageIndex.value = (_pageIndex.value + delta).coerceIn(0, max)
    }
}
