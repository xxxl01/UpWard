package com.xdl.upward.data.repository

import com.xdl.upward.data.local.HolidayDao
import com.xdl.upward.data.local.HolidayEntity
import com.xdl.upward.data.remote.HolidayApiService
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class HolidayRepository(
    private val holidayDao: HolidayDao,
    private val apiService: HolidayApiService = HolidayApiService()
) {
    fun observeCachedYears(): Flow<List<Int>> = holidayDao.observeCachedYears()

    suspend fun getCachedCount(year: Int): Int = holidayDao.countByYear(year)

    // 更新指定年份：拉取后整年覆盖写入，保证放假/调休调整能同步。
    suspend fun refreshYear(year: Int) {
        holidayDao.replaceYear(year, apiService.fetchYear(year))
    }

    // 取某天的节假日信息：本地有就直接用；整年没缓存过才联网拉一次。
    suspend fun getHolidayOrFetch(date: LocalDate): HolidayEntity? {
        holidayDao.getByDate(date.toString())?.let { return it }
        if (holidayDao.countByYear(date.year) == 0) {
            refreshYear(date.year)
        }
        return holidayDao.getByDate(date.toString())
    }
}
