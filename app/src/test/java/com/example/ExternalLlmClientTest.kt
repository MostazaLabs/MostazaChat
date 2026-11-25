package com.example

import com.example.data.model.FakeChat
import com.example.data.model.FakeMessage
import com.example.data.model.MessageType
import com.example.data.model.SenderRole
import com.example.llm.ChatAiConfig
import com.example.llm.ExternalLlmClient
import com.example.llm.LlmGlobalSettings
import com.example.llm.LlmProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLlmClientTest {

    @Test
    fun validateEndpointUrl_blocksUnsafeSchemesAndCleartextOnCloudProviders() {
        assertTrue(ExternalLlmClient.validateEndpointUrl("javascript:alert(1)", allowHttp = true).isFailure)
        assertTrue(ExternalLlmClient.validateEndpointUrl("file:///etc/passwd", allowHttp = true).isFailure)
        assertTrue(ExternalLlmClient.validateEndpointUrl("https://user:pass@api.openai.com/v1/chat/completions", allowHttp = false).isFailure)
        assertTrue(ExternalLlmClient.validateEndpointUrl("http://api.openai.com/v1/chat/completions", allowHttp = false).isFailure)

        assertTrue(ExternalLlmClient.validateEndpointUrl("https://openrouter.ai/api/v1/chat/completions", allowHttp = false).isSuccess)
        assertTrue(ExternalLlmClient.validateEndpointUrl("http://127.0.0.1:11434/v1/chat/completions", allowHttp = true).isSuccess)
    }

    @Test
    fun isConfiguredAndEnabled_requiresApiKeyOnlyForCloudProviders() {
        val disabled = LlmGlobalSettings(enabled = false, provider = LlmProvider.OPENROUTER, apiKey = "or-123")
        assertFalse(disabled.isConfiguredAndEnabled)

        val missingKey = LlmGlobalSettings(enabled = true, provider = LlmProvider.OPENAI, apiKey = "")
        assertFalse(missingKey.isConfiguredAndEnabled)

        val validCloud = LlmGlobalSettings(enabled = true, provider = LlmProvider.GROQ, apiKey = "gsk_valid")
        assertTrue(validCloud.isConfiguredAndEnabled)

        val validOllama = LlmGlobalSettings(enabled = true, provider = LlmProvider.OLLAMA_LOCAL, apiKey = "")
        assertTrue(validOllama.isConfiguredAndEnabled)
    }

    @Test
    fun buildChatTurnsAndJson_respectsPersonaHistoryAndSafetyFilter() {
        val chat = FakeChat(
            id = "c1",
            contactName = "Elena",
            groupChat = false,
            statusText = "en el estudio"
        )
        val cfg = ChatAiConfig(
            enabled = true,
            personaPrompt = "Arquitecta bromista que habla breve",
            autoReplyOnSend = true,
            temperature = 0.7f
        )
        val msgs = listOf(
            FakeMessage(id = "1", senderRole = SenderRole.OTHER.name, senderName = "Elena", text = "Hola, qué tal?", messageType = MessageType.TEXT.name),
            FakeMessage(id = "2", senderRole = SenderRole.ME.name, senderName = "Yo", text = "Todo bien, ¿y el plano?", messageType = MessageType.TEXT.name)
        )

        val turns = ExternalLlmClient.buildChatTurns(
            chat = chat,
            chatConfig = cfg,
            recentMessages = msgs,
            myDefaultName = "Yo",
            preferredGroupSpeaker = null,
            sensitiveFilterEnabled = true,
            contextLimit = 10
        )

        assertEquals("system", turns.first().role)
        assertTrue(turns.first().content.contains("Arquitecta bromista"))
        assertTrue(turns.first().content.contains("sin contenido sexual explícito"))
        assertEquals("assistant", turns[1].role)
        assertEquals("user", turns[2].role)

        val json = ExternalLlmClient.buildRequestPayloadJson(
            model = "gpt-4o-mini",
            turns = turns,
            temperature = cfg.temperature
        )
        assertTrue(json.contains("\"model\":\"gpt-4o-mini\""))
        assertTrue(json.contains("\"stream\":false"))
        assertTrue(json.contains("¿y el plano?"))
    }

    @Test
    fun cleanAssistantReply_stripsSpeakerPrefixAndOuterQuotes() {
        assertEquals(
            "Claro, nos vemos a las 6",
            ExternalLlmClient.cleanAssistantReply("Elena: \"Claro, nos vemos a las 6\"", "Elena")
        )
        assertEquals(
            "Perfecto entonces",
            ExternalLlmClient.cleanAssistantReply("«Perfecto entonces»", "Elena")
        )
    }
}
