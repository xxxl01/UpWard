package com.xdl.upward.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import com.xdl.upward.data.local.AiApiEntity
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.PaymentRecordEntity
import com.xdl.upward.data.remote.AiApiService
import com.xdl.upward.data.repository.AiApiRepository
import com.xdl.upward.data.repository.ChallengeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime

/**
 * 惩罚捐款截图审核：LLM 只负责从截图里识别支付信息（JSON），是否通过全部由本地规则判定。
 * 规则：支付成功 + 识别出交易单号 + 收款方为慈善机构 + 金额达标 + 单号未使用过。
 * 通过后在同一事务里写入 payment_record 并执行结算；不通过不入库，只返回提示。
 */
object PaymentVerifier {
    const val PURPOSE_CHALLENGE = "challenge_penalty"
    const val PURPOSE_VIOLATION = "violation_penalty"

    const val CHALLENGE_MIN_CENTS = 300L
    const val VIOLATION_MIN_CENTS = 400L

    private const val VIOLATION_DEDUCT = 10
    private const val VIOLATION_THRESHOLD = 10

    data class Recognition(
        val paymentSuccess: Boolean,
        val orderNo: String,
        val amountCents: Long,
        val payee: String,
        val paidAt: String,
        val isCharity: Boolean
    )

    sealed class Result {
        data class Accepted(val recognition: Recognition, val message: String) : Result()
        data class Rejected(val message: String, val recognition: Recognition? = null) : Result()
    }

    private val recognizePrompt = """
        你是支付截图识别器。请识别这张截图中的支付信息，只输出一个 JSON 对象，不要输出任何解释、不要使用代码块。格式如下：
        {"payment_success":true,"order_no":"交易单号","amount":3.00,"payee":"收款方","paid_at":"支付时间","is_charity":true}
        字段规则：
        - payment_success：截图是否显示一笔已成功完成的支付（支付成功/捐赠成功/交易成功）。不是支付截图或支付未成功时为 false。
        - order_no：交易单号/订单号/流水号/捐赠编号，逐字转写；截图中看不到完整单号时填空字符串，不要猜测或补全。
        - amount：实际支付金额，单位元，数字类型。
        - payee：收款方/捐赠项目/机构名称，逐字转写。
        - paid_at：截图上显示的支付时间，原样转写，看不到填空字符串。
        - is_charity：收款方是否为公益慈善机构或慈善捐赠项目（如慈善基金会、红十字会、公益平台上的捐赠项目）；普通商户、个人转账为 false。
    """.trimIndent()

    // 把用户选择的截图压缩保存到 chat_images（随备份一起导出），返回保存路径。
    suspend fun saveImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val imageDir = File(context.filesDir, "chat_images")
        if (!imageDir.exists()) imageDir.mkdirs()
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("图片读取失败")
        // 单号是细小文字，分辨率比聊天图片放宽，避免压缩后识别不出
        val maxSide = 2400
        val outputBitmap = if (bitmap.width > maxSide || bitmap.height > maxSide) {
            val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        val imageFile = File(imageDir, "payment_${System.currentTimeMillis()}.webp")
        FileOutputStream(imageFile).use { outputBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, it) }
        if (outputBitmap !== bitmap) outputBitmap.recycle()
        bitmap.recycle()
        imageFile.absolutePath
    }

    // 挑战失败惩罚：识别通过后把该挑战 failed → settled。
    suspend fun verifyChallengePenalty(context: Context, challengeId: Long, imagePath: String): Result {
        val database = AppDatabase.getInstance(context)
        val challengeRepository = ChallengeRepository(database.challengeDao(), database.challengeCheckInDao())
        val challenge = challengeRepository.getById(challengeId)
        if (challenge == null || challenge.status != ChallengeRepository.STATUS_FAILED) {
            return Result.Rejected("当前挑战不在失败惩罚中，无需提交捐款")
        }
        val recognition = when (val r = recognizeAndCheck(context, imagePath, CHALLENGE_MIN_CENTS)) {
            is Result.Rejected -> return r
            is Result.Accepted -> r.recognition
        }
        return database.withTransaction {
            val current = challengeRepository.getById(challengeId)
            if (current == null || current.status != ChallengeRepository.STATUS_FAILED) {
                return@withTransaction Result.Rejected("当前挑战不在失败惩罚中，无需提交捐款", recognition)
            }
            if (database.paymentRecordDao().getByOrderNo(recognition.orderNo) != null) {
                return@withTransaction Result.Rejected("该笔捐款（单号 ${recognition.orderNo}）已经使用过，请提交新的捐款", recognition)
            }
            database.paymentRecordDao().insert(recognition.toRecord(PURPOSE_CHALLENGE, challengeId, imagePath))
            challengeRepository.settleFailed(challengeId)
            Result.Accepted(recognition, "审核通过：捐款 ${formatYuan(recognition.amountCents)} 元，挑战失败惩罚已结算，可以开启新一轮挑战。")
        }
    }

    // 违规惩罚：识别通过后违规次数抵扣 10 次，并一并结算所有失败惩罚中的挑战。
    suspend fun verifyViolationPenalty(context: Context, imagePath: String): Result {
        val database = AppDatabase.getInstance(context)
        val configDao = database.configDao()
        val count = configDao.getByKey("violation_count")?.value?.toIntOrNull() ?: 0
        if (count < VIOLATION_THRESHOLD) {
            return Result.Rejected("当前违规 $count 次，未达到惩罚线，无需提交捐款")
        }
        val recognition = when (val r = recognizeAndCheck(context, imagePath, VIOLATION_MIN_CENTS)) {
            is Result.Rejected -> return r
            is Result.Accepted -> r.recognition
        }
        val challengeRepository = ChallengeRepository(database.challengeDao(), database.challengeCheckInDao())
        return database.withTransaction {
            val config = configDao.getByKey("violation_count")
            val currentCount = config?.value?.toIntOrNull() ?: 0
            if (config == null || currentCount < VIOLATION_THRESHOLD) {
                return@withTransaction Result.Rejected("当前违规 $currentCount 次，未达到惩罚线，无需提交捐款", recognition)
            }
            if (database.paymentRecordDao().getByOrderNo(recognition.orderNo) != null) {
                return@withTransaction Result.Rejected("该笔捐款（单号 ${recognition.orderNo}）已经使用过，请提交新的捐款", recognition)
            }
            database.paymentRecordDao().insert(recognition.toRecord(PURPOSE_VIOLATION, 0, imagePath))
            val remaining = currentCount - VIOLATION_DEDUCT
            configDao.update(config.copy(value = remaining.toString()))
            val settled = challengeRepository.settleAllFailed()
            val challengeNote = if (settled > 0) "，并已同步结算 $settled 个挑战失败惩罚" else ""
            val stateNote = if (remaining >= VIOLATION_THRESHOLD) {
                "项目消息仍处于拦截状态，需要再提交一笔捐款"
            } else {
                "惩罚已解除，可以在项目里正常发送消息了"
            }
            Result.Accepted(
                recognition,
                "审核通过：捐款 ${formatYuan(recognition.amountCents)} 元，违规次数抵扣 $VIOLATION_DEDUCT 次$challengeNote，剩余 $remaining 次，$stateNote。"
            )
        }
    }

    fun formatYuan(cents: Long): String =
        BigDecimal(cents).divide(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP).toPlainString()

    // 调 LLM 识别截图并按本地规则校验（不含单号查重，查重放在结算事务里做）。
    private suspend fun recognizeAndCheck(context: Context, imagePath: String, minCents: Long): Result {
        val database = AppDatabase.getInstance(context)
        val api = resolveVisionApi(AiApiRepository(database.aiApiDao()))
            ?: return Result.Rejected("没有可识别图片的 API：请选择支持图片的 API，或为当前 API 配置视觉辅助模型")
        val reply = try {
            AiApiService().chat(
                api.copy(temperature = 0.0),
                listOf(AiChatMessage("user", recognizePrompt, listOf(imagePath)))
            )
        } catch (e: Exception) {
            return Result.Rejected("识别请求失败：${e.message ?: "未知错误"}，请重试")
        }
        val recognition = parseRecognition(reply)
            ?: return Result.Rejected("识别失败，未能读取支付信息，请重新上传清晰的截图")
        return when {
            !recognition.paymentSuccess ->
                Result.Rejected("截图不是一笔成功的支付，请上传捐款成功的截图", recognition)
            recognition.orderNo.isBlank() ->
                Result.Rejected("未识别到交易单号，请上传包含完整交易单号的截图（如支付详情/账单详情页）", recognition)
            !recognition.isCharity ->
                Result.Rejected("收款方「${recognition.payee}」不是慈善机构，请向公益慈善机构或捐赠项目捐款", recognition)
            recognition.amountCents < minCents ->
                Result.Rejected("捐款金额 ${formatYuan(recognition.amountCents)} 元不足 ${formatYuan(minCents)} 元", recognition)
            else -> Result.Accepted(recognition, "")
        }
    }

    // 优先用当前选中的 API（需支持图片），否则用它配置的视觉辅助模型。
    private suspend fun resolveVisionApi(aiApiRepository: AiApiRepository): AiApiEntity? {
        val selected = aiApiRepository.getSelected() ?: return null
        if (selected.supportImage) return selected
        if (selected.visionApiId > 0L) return aiApiRepository.getById(selected.visionApiId)
        return null
    }

    private fun parseRecognition(reply: String): Recognition? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            val json = JSONObject(reply.substring(start, end + 1))
            val amountText = json.opt("amount")?.toString()?.replace(Regex("[^0-9.]"), "").orEmpty()
            val amountCents = amountText.toBigDecimalOrNull()
                ?.multiply(BigDecimal(100))
                ?.setScale(0, RoundingMode.HALF_UP)
                ?.toLong()
                ?: 0L
            Recognition(
                paymentSuccess = json.optBoolean("payment_success", false),
                // 单号统一去掉空白，避免同一单号因空格不同而绕过查重
                orderNo = json.optString("order_no", "").replace(Regex("\\s"), ""),
                amountCents = amountCents,
                payee = json.optString("payee", "").trim(),
                paidAt = json.optString("paid_at", "").trim(),
                isCharity = json.optBoolean("is_charity", false)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun Recognition.toRecord(purpose: String, challengeId: Long, imagePath: String) = PaymentRecordEntity(
        purpose = purpose,
        challengeId = challengeId,
        orderNo = orderNo,
        amountCents = amountCents,
        payee = payee,
        paidAt = paidAt,
        imagePath = imagePath,
        createdAt = OffsetDateTime.now().toString()
    )
}
