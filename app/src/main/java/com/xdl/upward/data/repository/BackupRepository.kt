package com.xdl.upward.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.xdl.upward.data.local.AiApiEntity
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.ChallengeCheckInEntity
import com.xdl.upward.data.local.ChallengeEntity
import com.xdl.upward.data.local.ConfigEntity
import com.xdl.upward.data.local.DailyRecordEntity
import com.xdl.upward.data.local.MessageEntity
import com.xdl.upward.data.local.PaymentRecordEntity
import com.xdl.upward.data.local.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val imageEntries = mutableListOf<Pair<String, File>>()
        var imageIndex = 1
        val messages = JSONArray()
        database.messageDao().getAllForBackup().forEach { message ->
            val imagePaths = JSONArray()
            if (message.imagePaths.isNotBlank()) {
                val oldImagePaths = JSONArray(message.imagePaths)
                for (index in 0 until oldImagePaths.length()) {
                    val path = oldImagePaths.optString(index).trim()
                    val file = File(path)
                    if (path.isNotEmpty() && file.exists() && file.isFile) {
                        val entryName = "chat_images/${imageIndex}_${file.name}"
                        imageIndex++
                        imageEntries.add(entryName to file)
                        imagePaths.put(entryName)
                    } else if (path.isNotEmpty()) {
                        imagePaths.put(path)
                    }
                }
            }
            messages.put(
                JSONObject()
                    .put("id", message.id)
                    .put("project_id", message.projectId)
                    .put("role", message.role)
                    .put("content", message.content)
                    .put("created_at", message.createdAt)
                    .put("image_paths", imagePaths)
            )
        }

        // 捐款记录的截图与聊天图片一样打包进 chat_images/
        val paymentRecords = JSONArray()
        database.paymentRecordDao().getAllForBackup().forEach { record ->
            val file = File(record.imagePath)
            val imagePath = if (record.imagePath.isNotBlank() && file.exists() && file.isFile) {
                val entryName = "chat_images/${imageIndex}_${file.name}"
                imageIndex++
                imageEntries.add(entryName to file)
                entryName
            } else {
                record.imagePath
            }
            paymentRecords.put(
                JSONObject()
                    .put("id", record.id)
                    .put("purpose", record.purpose)
                    .put("challenge_id", record.challengeId)
                    .put("order_no", record.orderNo)
                    .put("amount_cents", record.amountCents)
                    .put("payee", record.payee)
                    .put("paid_at", record.paidAt)
                    .put("image_path", imagePath)
                    .put("created_at", record.createdAt)
            )
        }

        val json = JSONObject()
            .put("format", "upward-full-backup")
            .put("version", 1)
            .put("projects", projectsToJson(database.projectDao().getAllForBackup()))
            .put("messages", messages)
            .put("daily_records", dailyRecordsToJson(database.dailyRecordDao().getAllForBackup()))
            .put("ai_apis", aiApisToJson(database.aiApiDao().getAllForBackup()))
            .put("configs", configsToJson(database.configDao().getAllForBackup()))
            .put("challenges", challengesToJson(database.challengeDao().getAllForBackup()))
            .put("challenge_checkins", challengeCheckInsToJson(database.challengeCheckInDao().getAllForBackup()))
            .put("payment_records", paymentRecords)

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(json.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                imageEntries.forEach { (entryName, file) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { input ->
                        input.copyTo(zip)
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("无法打开导出文件")
    }

    suspend fun restoreFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val imageDir = File(context.filesDir, "chat_images")
        if (!imageDir.exists()) {
            imageDir.mkdirs()
        }

        var backupJson: JSONObject? = null
        val restoredImagePaths = mutableMapOf<String, String>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "backup.json") {
                        backupJson = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    } else if (!entry.isDirectory && entry.name.startsWith("chat_images/")) {
                        val fileName = entry.name.substringAfterLast('/').ifBlank { "image_${System.nanoTime()}" }
                        val targetFile = File(imageDir, fileName)
                        targetFile.outputStream().use { output ->
                            zip.copyTo(output)
                        }
                        restoredImagePaths[entry.name] = targetFile.absolutePath
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("无法打开恢复文件")

        val json = backupJson ?: error("备份文件缺少 backup.json")
        if (json.optString("format") != "upward-full-backup") {
            error("不是 UpWard 全量备份文件")
        }

        val messages = mutableListOf<MessageEntity>()
        val messageArray = json.getJSONArray("messages")
        for (index in 0 until messageArray.length()) {
            val item = messageArray.getJSONObject(index)
            val imagePaths = JSONArray()
            val oldImagePaths = item.optJSONArray("image_paths") ?: JSONArray()
            for (imageIndex in 0 until oldImagePaths.length()) {
                val path = oldImagePaths.optString(imageIndex).trim()
                if (restoredImagePaths.containsKey(path)) {
                    imagePaths.put(restoredImagePaths[path])
                } else if (path.isNotEmpty()) {
                    imagePaths.put(path)
                }
            }
            messages.add(
                MessageEntity(
                    id = item.getLong("id"),
                    projectId = item.getLong("project_id"),
                    role = item.getString("role"),
                    content = item.getString("content"),
                    createdAt = item.getString("created_at"),
                    imagePaths = imagePaths.toString()
                )
            )
        }

        database.withTransaction {
            database.messageDao().deleteAll()
            database.dailyRecordDao().deleteAll()
            database.projectDao().deleteAll()
            database.aiApiDao().deleteAll()
            database.configDao().deleteAll()
            database.challengeDao().deleteAll()
            database.challengeCheckInDao().deleteAll()
            database.paymentRecordDao().deleteAll()

            val projects = readProjects(json.getJSONArray("projects"))
            if (projects.isNotEmpty()) database.projectDao().insertAll(projects)
            if (messages.isNotEmpty()) database.messageDao().insertAll(messages)
            val records = readDailyRecords(json.getJSONArray("daily_records"))
            if (records.isNotEmpty()) database.dailyRecordDao().insertAll(records)
            val apis = readAiApis(json.getJSONArray("ai_apis"))
            if (apis.isNotEmpty()) database.aiApiDao().insertAll(apis)
            val configs = readConfigs(json.getJSONArray("configs"))
            if (configs.isNotEmpty()) database.configDao().insertAll(configs)
            val challenges = readChallenges(json.optJSONArray("challenges"))
            if (challenges.isNotEmpty()) database.challengeDao().insertAll(challenges)
            val checkIns = readChallengeCheckIns(json.optJSONArray("challenge_checkins"))
            if (checkIns.isNotEmpty()) database.challengeCheckInDao().insertAll(checkIns)
            // 旧备份没有 payment_records，optJSONArray 兜底为空
            val paymentRecords = readPaymentRecords(json.optJSONArray("payment_records"), restoredImagePaths)
            if (paymentRecords.isNotEmpty()) database.paymentRecordDao().insertAll(paymentRecords)
        }
    }

    private fun projectsToJson(projects: List<ProjectEntity>): JSONArray {
        val array = JSONArray()
        projects.forEach { project ->
            array.put(
                JSONObject()
                    .put("id", project.id)
                    .put("name", project.name)
                    .put("system_prompt", project.systemPrompt)
                    .put("daily_record_prompt", project.dailyRecordPrompt)
                    .put("check_in_mode", project.checkInMode)
                    .put("created_at", project.createdAt)
                    .put("next_required_message_at", project.nextRequiredMessageAt)
                    .put("next_required_message_start_at", project.nextRequiredMessageStartAt)
                    .put("next_required_message_end_at", project.nextRequiredMessageEndAt)
                    .put("inner_voice", project.innerVoice)
                    .put("inner_voice_enabled", project.innerVoiceEnabled)
            )
        }
        return array
    }

    private fun dailyRecordsToJson(records: List<DailyRecordEntity>): JSONArray {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("project_id", record.projectId)
                    .put("date", record.date)
                    .put("content", record.content)
                    .put("created_at", record.createdAt)
                    .put("updated_at", record.updatedAt)
            )
        }
        return array
    }

    private fun aiApisToJson(apis: List<AiApiEntity>): JSONArray {
        val array = JSONArray()
        apis.forEach { api ->
            array.put(
                JSONObject()
                    .put("id", api.id)
                    .put("base_url", api.baseUrl)
                    .put("api_key", api.apiKey)
                    .put("model", api.model)
                    .put("temperature", api.temperature)
                    .put("support_image", api.supportImage)
                    .put("vision_api_id", api.visionApiId)
                    .put("selected", api.selected)
            )
        }
        return array
    }

    private fun configsToJson(configs: List<ConfigEntity>): JSONArray {
        val array = JSONArray()
        configs.forEach { config ->
            array.put(
                JSONObject()
                    .put("id", config.id)
                    .put("key", config.key)
                    .put("value", config.value)
            )
        }
        return array
    }

    private fun challengesToJson(challenges: List<ChallengeEntity>): JSONArray {
        val array = JSONArray()
        challenges.forEach { challenge ->
            array.put(
                JSONObject()
                    .put("id", challenge.id)
                    .put("project_id", challenge.projectId)
                    .put("goal", challenge.goal)
                    .put("daily_standard_prompt", challenge.dailyStandardPrompt)
                    .put("fail_penalty_prompt", challenge.failPenaltyPrompt)
                    .put("start_date", challenge.startDate)
                    .put("end_date", challenge.endDate)
                    .put("transition_end_date", challenge.transitionEndDate)
                    .put("status", challenge.status)
                    .put("created_at", challenge.createdAt)
                    .put("finished_at", challenge.finishedAt)
            )
        }
        return array
    }

    private fun challengeCheckInsToJson(checkIns: List<ChallengeCheckInEntity>): JSONArray {
        val array = JSONArray()
        checkIns.forEach { checkIn ->
            array.put(
                JSONObject()
                    .put("id", checkIn.id)
                    .put("challenge_id", checkIn.challengeId)
                    .put("date", checkIn.date)
                    .put("summary", checkIn.summary)
                    .put("created_at", checkIn.createdAt)
            )
        }
        return array
    }

    private fun readProjects(array: JSONArray): List<ProjectEntity> {
        val projects = mutableListOf<ProjectEntity>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            projects.add(
                ProjectEntity(
                    id = item.getLong("id"),
                    name = item.getString("name"),
                    systemPrompt = item.getString("system_prompt"),
                    dailyRecordPrompt = item.getString("daily_record_prompt"),
                    checkInMode = item.optString("check_in_mode", "three_times"),
                    createdAt = item.getString("created_at"),
                    nextRequiredMessageAt = item.optString("next_required_message_at"),
                    nextRequiredMessageStartAt = item.optString("next_required_message_start_at"),
                    nextRequiredMessageEndAt = item.optString("next_required_message_end_at"),
                    innerVoice = item.optString("inner_voice"),
                    innerVoiceEnabled = item.optBoolean("inner_voice_enabled", false)
                )
            )
        }
        return projects
    }

    private fun readDailyRecords(array: JSONArray): List<DailyRecordEntity> {
        val records = mutableListOf<DailyRecordEntity>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            records.add(
                DailyRecordEntity(
                    id = item.getLong("id"),
                    projectId = item.getLong("project_id"),
                    date = item.getString("date"),
                    content = item.getString("content"),
                    createdAt = item.getString("created_at"),
                    updatedAt = item.getString("updated_at")
                )
            )
        }
        return records
    }

    private fun readAiApis(array: JSONArray): List<AiApiEntity> {
        val apis = mutableListOf<AiApiEntity>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            apis.add(
                AiApiEntity(
                    id = item.getLong("id"),
                    baseUrl = item.getString("base_url"),
                    apiKey = item.getString("api_key"),
                    model = item.getString("model"),
                    temperature = item.getDouble("temperature"),
                    supportImage = item.optBoolean("support_image", false),
                    visionApiId = item.optLong("vision_api_id", 0),
                    selected = item.optBoolean("selected", false)
                )
            )
        }
        return apis
    }

    private fun readConfigs(array: JSONArray): List<ConfigEntity> {
        val configs = mutableListOf<ConfigEntity>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            configs.add(
                ConfigEntity(
                    id = item.getLong("id"),
                    key = item.getString("key"),
                    value = item.getString("value")
                )
            )
        }
        return configs
    }

    private fun readChallenges(array: JSONArray?): List<ChallengeEntity> {
        if (array == null) return emptyList()
        val challenges = mutableListOf<ChallengeEntity>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            challenges.add(
                ChallengeEntity(
                    id = item.getLong("id"),
                    projectId = item.getLong("project_id"),
                    goal = item.getString("goal"),
                    dailyStandardPrompt = item.getString("daily_standard_prompt"),
                    failPenaltyPrompt = item.getString("fail_penalty_prompt"),
                    startDate = item.getString("start_date"),
                    endDate = item.getString("end_date"),
                    // 旧备份没有过渡期字段，兜底为空（无过渡期）
                    transitionEndDate = item.optString("transition_end_date", ""),
                    status = item.getString("status"),
                    createdAt = item.getString("created_at"),
                    finishedAt = item.optString("finished_at")
                )
            )
        }
        return challenges
    }

    private fun readChallengeCheckIns(array: JSONArray?): List<ChallengeCheckInEntity> {
        if (array == null) return emptyList()
        val checkIns = mutableListOf<ChallengeCheckInEntity>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            checkIns.add(
                ChallengeCheckInEntity(
                    id = item.getLong("id"),
                    challengeId = item.getLong("challenge_id"),
                    date = item.getString("date"),
                    summary = item.optString("summary"),
                    createdAt = item.getString("created_at")
                )
            )
        }
        return checkIns
    }

    private fun readPaymentRecords(array: JSONArray?, restoredImagePaths: Map<String, String>): List<PaymentRecordEntity> {
        if (array == null) return emptyList()
        val records = mutableListOf<PaymentRecordEntity>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val path = item.optString("image_path")
            records.add(
                PaymentRecordEntity(
                    id = item.getLong("id"),
                    purpose = item.getString("purpose"),
                    challengeId = item.optLong("challenge_id", 0),
                    orderNo = item.getString("order_no"),
                    amountCents = item.getLong("amount_cents"),
                    payee = item.optString("payee"),
                    paidAt = item.optString("paid_at"),
                    imagePath = restoredImagePaths[path] ?: path,
                    createdAt = item.getString("created_at")
                )
            )
        }
        return records
    }
}
