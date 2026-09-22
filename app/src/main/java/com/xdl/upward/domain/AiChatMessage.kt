package com.xdl.upward.domain

data class AiChatMessage(
    val role: String,
    val content: String,
    val imagePaths: List<String> = emptyList()
)
