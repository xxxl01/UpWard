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

                请结合长期记录和最近对话，持续监督用户、鼓励用户，并给出具体、可执行的下一步建议。
                """.trimIndent()
            )
        )
        recentMessages.forEach { message ->
            if (message.role == "user" || message.role == "assistant") {
                messages.add(AiChatMessage(message.role, message.content))
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
