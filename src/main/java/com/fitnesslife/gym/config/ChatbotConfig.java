package com.fitnesslife.gym.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChatbotProperties.class)
public class ChatbotConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    TokenTextSplitter tokenTextSplitter(ChatbotProperties properties) {
        ChatbotProperties.Ingestion ingestion = properties.ingestion();
        return TokenTextSplitter.builder()
                .withChunkSize(ingestion.chunkSizeTokens())
                .withMinChunkSizeChars(ingestion.minChunkSizeChars())
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .build();
    }
}