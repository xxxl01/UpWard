package com.xdl.upward.data.remote

import com.xdl.upward.data.local.HolidayEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * 中国法定节假日公开接口：https://timor.tech/api/holiday/year/{year}
 * 返回 { code: 0, holiday: { "MM-DD": { holiday, name, date, ... } } }，code 为 0 表示成功。
 * holiday 为 true 表示放假，false 表示补班（调休上班）。
 */
class HolidayApiService {
    suspend fun fetchYear(year: Int): List<HolidayEntity> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://timor.tech/api/holiday/year/$year")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) UpWard/1.0")
            .header("Accept", "application/json")
            .get()
            .build()

        AiHttpClient.client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("节假日接口请求失败：${response.code}")
            }
            val bodyJson = JSONObject(bodyText)
            if (bodyJson.optInt("code") != 0) {
                throw IllegalStateException("节假日接口返回错误：${bodyJson.optString("msg").ifBlank { "未知错误" }}")
            }
            val holidayObject = bodyJson.optJSONObject("holiday") ?: return@withContext emptyList()
            val items = mutableListOf<HolidayEntity>()
            val keys = holidayObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = holidayObject.optJSONObject(key) ?: continue
                items.add(
                    HolidayEntity(
                        year = year,
                        date = item.optString("date").ifBlank { "$year-$key" },
                        holiday = item.optBoolean("holiday"),
                        name = item.optString("name")
                    )
                )
            }
            items
        }
    }
}
