package com.fitnesslife.gym.mapper;

import org.mapstruct.Mapper;
import com.fitnesslife.gym.dto.ChatConversationResponse;
import com.fitnesslife.gym.dto.ChatMessageResponse;
import com.fitnesslife.gym.dto.ChatSourceResponse;
import com.fitnesslife.gym.dto.KnowledgeDocumentResponse;
import com.fitnesslife.gym.model.ChatConversation;
import com.fitnesslife.gym.model.ChatMessage;
import com.fitnesslife.gym.model.ChatSource;
import com.fitnesslife.gym.model.KnowledgeDocument;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    ChatConversationResponse toResponse(ChatConversation conversation);

    ChatMessageResponse toResponse(ChatMessage message);

    ChatSourceResponse toResponse(ChatSource source);

    KnowledgeDocumentResponse toResponse(KnowledgeDocument document);
}