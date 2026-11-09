package com.xdl.upward.domain

import com.xdl.upward.data.local.DailyRecordEntity
import com.xdl.upward.data.local.MessageEntity
import com.xdl.upward.data.local.ProjectEntity

class AiMessageBuilder {
    fun buildChatMessages(
        project: ProjectEntity,
        dailyRecords: List<DailyRecordEntity>,
        recentMessages: List<MessageEntity>,
        includeReplyRules: Boolean = true
    ): List<AiChatMessage> {
        // 回复长度/风格规则只用于聊天；生成日报等长文本任务不能受“100 字以内”限制。
        val replyRules = if (includeReplyRules) "\n\n" + CHAT_REPLY_RULES else ""
        val dailyRecordText = if (dailyRecords.isEmpty()) {
            "暂无每日记录。"
        } else {
            dailyRecords.joinToString("\n\n") { record ->
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

                每日记录里有普通记录，也有明确标注为“经验”的经验记录。普通记录要按当前情景判断重要性：如果当前回复需要了解当天发生了什么、当时状态、项目背景或上下文，就要认真参考；如果无关，就不要强行放大。
                明确标注为“经验”的内容要额外重视，优先当作用户已经沉淀过的有效依据，用来判断当前情况；需要给建议时优先从经验里找依据。
                如果每日记录和当前对话有冲突，以当前对话为准。
                """.trimIndent() + replyRules
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
        // 聊天回复规则：核心是控制篇幅；监督、鼓励照常，但建议不必每次主动给。
        private val CHAT_REPLY_RULES = """
            回复规则（项目规则或用户明确另有要求时以它们为准）：
            1. 篇幅：默认 1~3 句话、100 字以内，像微信聊天一样说话。用户消息很短时回复也要短。不要用表格、标题、多级列表，不要逐项复述用户发来的内容。
            2. 挑战打卡：通过时一两句话说明通过和当前进度即可，不逐项列核验过程；不通过时只说缺了哪一项、怎么补。
            3. 建议：不必每次都给。只在确有必要（用户求助、明显卡住、出现新风险）时给，一次最多一条，一句话说完。
            4. 不重复：最近对话里已经提醒过的事情（截止日期、待办、作息等），不要每条回复都再提，除非快到期了或用户问起。
            5. 用户在对话里提出过的回复偏好（例如“简短回复就行”），之后一直遵守。
        """.trimIndent()
    }
}
