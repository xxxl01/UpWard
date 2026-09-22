package com.xdl.upward.domain

import com.xdl.upward.data.local.DailyRecordEntity
import com.xdl.upward.data.local.MessageEntity
import com.xdl.upward.data.local.ProjectEntity

class AiMessageBuilder {
    fun buildChatMessages(
        project: ProjectEntity,
        dailyRecords: List<DailyRecordEntity>,
        recentMessages: List<MessageEntity>
    ): List<AiChatMessage> {
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
                明确标注为“经验”的内容要额外重视，优先当作用户已经沉淀过的有效依据，用来判断当前情况和组织建议。
                如果经验记录里有能指导当前回复的做法、规律、失败教训、拖延诱因、环境限制或下一步提醒，你必须把它用到建议里，并尽量点明依据来自过往经验记录；不要只依赖最近几条对话。
                如果每日记录和当前对话有冲突，以当前对话为准，但要说明与过往记录的差异。
                """.trimIndent()
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
        val messages = buildChatMessages(project, dailyRecords, recentMessages).toMutableList()
        messages.add(AiChatMessage("user", prompt))
        return messages
    }
}
