package com.xdl.upward.domain

import com.xdl.upward.data.local.DailyRecordEntity
import com.xdl.upward.data.local.MessageEntity
import com.xdl.upward.data.local.ProjectEntity

class AiMessageBuilder {
    fun buildChatMessages(
        project: ProjectEntity,
        dailyRecords: List<DailyRecordEntity>,
        recentMessages: List<MessageEntity>,
        includeReplyRules: Boolean = true,
        includeExperience: Boolean = true
    ): List<AiChatMessage> {
        // 回复长度/风格规则只用于聊天；生成日报等长文本任务不能受“100 字以内”限制。
        val replyRules = if (includeReplyRules) "\n\n" + CHAT_REPLY_RULES else ""
        // 经验记录（内容以“经验”开头的每日记录）默认不进聊天上下文，只在经验模式下拼接。
        val visibleRecords = if (includeExperience) dailyRecords else dailyRecords.filterNot { isExperienceRecord(it) }
        val experienceNote = if (includeExperience) {
            "\n每日记录里有普通记录，也有明确标注为“经验”的经验记录。普通记录要按当前情景判断重要性：如果当前回复需要了解当天发生了什么、当时状态、项目背景或上下文，就要认真参考；如果无关，就不要强行放大。\n明确标注为“经验”的内容要额外重视，优先当作用户已经沉淀过的有效依据，用来判断当前情况；需要给建议时优先从经验里找依据。"
        } else {
            "\n每日记录要按当前情景判断重要性：如果当前回复需要了解当天发生了什么、当时状态、项目背景或上下文，就要认真参考；如果无关，就不要强行放大。"
        }
        val dailyRecordText = if (visibleRecords.isEmpty()) {
            "暂无每日记录。"
        } else {
            visibleRecords.joinToString("\n\n") { record ->
                "${record.date}\n${record.content}"
            }
        }

        val messages = mutableListOf<AiChatMessage>()
        messages.add(
            AiChatMessage(
                role = "system",
                content = """
                你是这个自律项目的长期监督者和鼓励者。

                项目目标和规则：
                ${project.systemPrompt}

                以下是这个项目从开始到现在的每日记录，按日期升序排列：
                $dailyRecordText
                """.trimIndent() + "\n" + experienceNote + "\n如果每日记录和当前对话有冲突，以当前对话为准。" + replyRules
            )
        )
        recentMessages.forEach { message ->
            if (message.role == "user") {
                val content = if (message.createdAt.isBlank()) {
                    message.content
                } else {
                    "[消息发送时间: ${message.createdAt}]\n${message.content}"
                }
                messages.add(
                    AiChatMessage(
                        role = "user",
                        content = content
                    )
                )
            } else if (message.role == "assistant") {
                // 历史里可能残留旧版本写进正文的工具执行日志和未剥净的 <tool_call> 原文。
                // 这些内容一旦回灌，模型（尤其 DeepSeek）会照着模仿，直接用文本描述"工具已调用"而不再输出真正的工具块，
                // 所以回灌前统一截掉，只保留自然语言部分。
                val cleanContent = message.content
                    .substringBefore("<tool_call>")
                    .substringBefore("\n\n[工具已执行")
                    .substringBefore("\n\n[工具调用失败")
                    .trim()
                if (cleanContent.isNotEmpty()) {
                    messages.add(AiChatMessage(message.role, cleanContent))
                }
            }
        }
        return messages
    }

    fun buildDailyRecordMessages(
        project: ProjectEntity,
        dailyRecords: List<DailyRecordEntity>,
        recentMessages: List<MessageEntity>,
        prompt: String
    ): List<AiChatMessage> {
        val messages = buildChatMessages(project, dailyRecords, recentMessages, includeReplyRules = false).toMutableList()
        messages.add(AiChatMessage("user", prompt))
        return messages
    }

    companion object {
        // 经验记录：内容（去掉开头的 Markdown 标记）以“经验”开头，如“经验：…”“经验2：…”。
        private fun isExperienceRecord(record: DailyRecordEntity): Boolean =
            record.content.trimStart().trimStart('#', '*', ' ', '【', '[').startsWith("经验")

        // 聊天回复规则：核心是控制篇幅；监督、鼓励照常，但建议不必每次主动给。
        private val CHAT_REPLY_RULES = """
            回复规则（项目规则或用户明确另有要求时以它们为准）：
            1. 篇幅：默认 1~3 句话、100 字以内，像微信聊天一样说话。用户消息很短时回复也要短。不要用表格、标题、多级列表。
            2. 有人味，不要机械复述：不要把用户刚说的内容换个说法再念一遍（例如“午餐：鸡腿饭，符合要求 ✓”），也不要每次都用同一套开头和句式（如“收到”“记上”“判定通过”）。像一个了解用户近况的朋友那样回应：抓住这条消息里最值得接的一点（一个具体细节、一点变化、一种情绪）来说，可以有语气、有态度、偶尔调侃，结合最近几天的情况说说你的感受，而不是写结论报告。
            3. 挑战打卡：通过时自然地带一句通过和当前进度即可，不逐项列核验过程；不通过时只说缺了哪一项、怎么补。
            4. 建议：不必每次都给。只在确有必要（用户求助、明显卡住、出现新风险）时给，一次最多一条，一句话说完。
            5. 不重复：最近对话里已经提醒过的事情（截止日期、待办、作息等），不要每条回复都再提，除非快到期了或用户问起。
            6. 用户在对话里提出过的回复偏好（例如“简短回复就行”），之后一直遵守。
        """.trimIndent()
    }
}
