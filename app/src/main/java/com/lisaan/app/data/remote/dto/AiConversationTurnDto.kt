package com.lisaan.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AiConversationTurnDto(
    @SerialName("role") val role: String, // "user" or "assistant"
    @SerialName("content") val content: String
)

@Serializable
data class AiCoachChatRequestDto(
    @SerialName("scenarioId") val scenarioId: String,
    @SerialName("learnerLevel") val learnerLevel: String, // "A1", "A2", "B1", "B2"
    @SerialName("targetLanguage") val targetLanguage: String = "en",
    @SerialName("nativeLanguage") val nativeLanguage: String = "ar",
    @SerialName("userMessage") val userMessage: String,
    @SerialName("history") val history: List<AiConversationTurnDto> = emptyList()
)

@Serializable
data class AiGrammarCorrectionDto(
    @SerialName("hasMistake") val hasMistake: Boolean = false,
    @SerialName("originalText") val originalText: String = "",
    @SerialName("correctedText") val correctedText: String = "",
    @SerialName("explanationAr") val explanationAr: String = "",
    @SerialName("explanationEn") val explanationEn: String = ""
)

@Serializable
data class AiCoachChatResponseDto(
    @SerialName("replyEn") val replyEn: String,
    @SerialName("replyTranslationAr") val replyTranslationAr: String,
    @SerialName("correction") val correction: AiGrammarCorrectionDto,
    @SerialName("suggestedFollowUpEn") val suggestedFollowUpEn: String = ""
)
