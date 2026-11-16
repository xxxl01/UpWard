package com.xdl.upward.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 在调用 set_next_resume_time 时附带的快捷回复：状态好 / 一般 / 差 各 3 句。
 * 按项目存在 config 表（键 quick_replies_<projectId>），每次工具调用覆盖上一份。
 */
data class QuickReplies(
    val good: List<String>,
    val medium: List<String>,
    val bad: List<String>
) {
    fun total() = good.size + medium.size + bad.size

    fun isEmpty() = total() == 0

    fun toJson(): String = JSONObject()
        .put("good", JSONArray(good))
        .put("medium", JSONArray(medium))
        .put("bad", JSONArray(bad))
        .toString()

    companion object {
        fun configKey(projectId: Long) = "quick_replies_$projectId"

        fun fromToolArguments(json: JSONObject?): QuickReplies? {
            if (json == null) return null
            val result = QuickReplies(read(json, "good"), read(json, "medium"), read(json, "bad"))
            return result.takeUnless { it.isEmpty() }
        }

        fun fromJson(text: String?): QuickReplies? {
            if (text.isNullOrBlank()) return null
            return runCatching { fromToolArguments(JSONObject(text)) }.getOrNull()
        }

        private fun read(json: JSONObject, key: String): List<String> {
            val array = json.optJSONArray(key) ?: return emptyList()
            return (0 until array.length())
                .map { array.optString(it).trim() }
                .filter { it.isNotEmpty() }
                .take(3)
        }
    }
}
