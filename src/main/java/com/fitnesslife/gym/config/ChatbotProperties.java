package com.fitnesslife.gym.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "chatbot")
public record ChatbotProperties(
        @Valid @DefaultValue Llm llm,
        @Valid @DefaultValue Rag rag,
        @Valid @DefaultValue Limits limits,
        @Valid @DefaultValue Conversation conversation,
        @Valid @DefaultValue Ingestion ingestion,
        @Valid @DefaultValue Assistant assistant,
        @Valid @DefaultValue Tools tools) {

    public record Llm(
            @DefaultValue("0.2") double temperature,
            @DefaultValue("1500") @Min(100) int maxCompletionTokens,
            @DefaultValue("low") String reasoningEffort) {
    }

    public record Rag(
            @DefaultValue("5") @Min(1) @Max(10) int topK,
            @DefaultValue("0.70") double minScore,
            @DefaultValue("6000") @Min(500) int maxContextChars,
            @DefaultValue("1024") @Min(1) int dimensions) {
    }

    public record Limits(
            @DefaultValue("1000") @Min(1) @Max(4000) int maxMessageLength,
            @DefaultValue("16384") @Min(1024) int maxRequestBytes,
            @DefaultValue("8") @Min(1) int messagesPerMinute,
            @DefaultValue("120") @Min(1) int messagesPerDay) {
    }

    public record Conversation(
            @DefaultValue("6") @Min(0) int historyMessages,
            @DefaultValue("4000") @Min(0) int historyMaxChars,
            @DefaultValue("200") @Min(2) int maxMessagesPerConversation,
            @DefaultValue("30") @Min(1) int maxConversationsPerUser,
            @DefaultValue("90") @Min(1) int retentionDays) {
    }

    public record Ingestion(
            @DefaultValue("knowledge") String directory,
            @DefaultValue("300") @Min(50) int chunkSizeTokens,
            @DefaultValue("200") @Min(0) int minChunkSizeChars,
            @DefaultValue("5242880") @Min(1024) @Max(15728640) long maxFileBytes,
            @DefaultValue("500") int maxChunksPerDocument,
            @DefaultValue("200") int maxDocumentsPerRun,
            @DefaultValue("16") @Min(1) int embeddingBatchSize,
            @DefaultValue("0s") Duration minIntervalBetweenRequests,
            @DefaultValue("30s") Duration rateLimitRetryWait,
            @DefaultValue("3") @Min(0) @Max(10) int rateLimitRetries) {
    }

    public record Assistant(
            @DefaultValue("info@fitnesslife.com · +57 323 4580755") String contact,
            @DefaultValue("No tengo información suficiente para responder tu consulta. Puedes comunicarte con nuestro equipo: {contact}") String noAnswerMessage,
            @DefaultValue("¡Hola! Soy el asistente virtual de Fitness Life. ¿En qué puedo ayudarte?") String greetingMessage,
            @DefaultValue("¡Con mucho gusto! Estoy aquí para ayudarte.") String thanksMessage) {
    }

    public record Tools(
            @DefaultValue("America/Bogota") String timezone,
            @DefaultValue("3") @Min(1) @Max(6) int maxCallsPerMessage,
            @DefaultValue("8") @Min(1) @Max(20) int maxClassesPerCall,
            @DefaultValue("14") @Min(0) @Max(60) int maxDaysAhead,
            @DefaultValue("10") @Min(1) @Max(60) int pendingActionTtlMinutes,
            @DefaultValue("3") @Min(1) @Max(10) int maxPendingActions) {
    }
}