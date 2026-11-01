package com.lisaan.app.core.network

import com.lisaan.app.data.remote.dto.AiCoachChatRequestDto
import com.lisaan.app.data.remote.dto.AiCoachChatResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface ClaudeAiApiService {
    @POST("ai-coach/chat")
    suspend fun sendMessageToClaudeCoach(
        @Body request: AiCoachChatRequestDto
    ): AiCoachChatResponseDto
}
