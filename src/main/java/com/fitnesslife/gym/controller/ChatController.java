package com.fitnesslife.gym.controller;

import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.fitnesslife.gym.dto.ChatConversationResponse;
import com.fitnesslife.gym.dto.ChatMessageRequest;
import com.fitnesslife.gym.dto.ChatMessageResponse;
import com.fitnesslife.gym.dto.ChatReplyResponse;
import com.fitnesslife.gym.dto.PendingActionResponse;
import com.fitnesslife.gym.dto.PendingActionResultResponse;
import com.fitnesslife.gym.mapper.ChatMapper;
import com.fitnesslife.gym.model.PendingAction;
import com.fitnesslife.gym.service.ChatConversationService;
import com.fitnesslife.gym.service.ChatService;
import com.fitnesslife.gym.service.PendingActionService;
import com.fitnesslife.gym.service.PendingActionService.PendingActionResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_HISTORY_LIMIT = 100;

    private final ChatService chatService;
    private final ChatConversationService conversationService;
    private final PendingActionService pendingActionService;
    private final ChatMapper mapper;

    @PostMapping("/messages")
    public ChatReplyResponse sendMessage(@Valid @RequestBody ChatMessageRequest request, Principal principal) {
        ChatService.Reply reply = chatService.sendMessage(principal.getName(), request.conversationId(),
                request.message());
        return new ChatReplyResponse(reply.conversationId(), mapper.toResponse(reply.message()), reply.grounded(),
                reply.pendingActions().stream().map(this::toResponse).toList());
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatConversationResponse createConversation(Principal principal) {
        return mapper.toResponse(conversationService.create(principal.getName()));
    }

    @GetMapping("/conversations")
    public List<ChatConversationResponse> listConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Principal principal) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return conversationService.list(principal.getName(), safePage, safeSize).stream()
                .map(mapper::toResponse).toList();
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ChatMessageResponse> listMessages(
            @PathVariable String id,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_HISTORY_LIMIT);
        return conversationService.messages(principal.getName(), id, safeLimit).stream()
                .map(mapper::toResponse).toList();
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@PathVariable String id, Principal principal) {
        conversationService.delete(principal.getName(), id);
    }

    @GetMapping("/actions/pending")
    public List<PendingActionResponse> pendingActions(Principal principal) {
        return pendingActionService.findWaitingForEmail(principal.getName()).stream().map(this::toResponse).toList();
    }

    @PostMapping("/actions/{id}/confirm")
    public PendingActionResultResponse confirmAction(@PathVariable String id, Principal principal) {
        return toResponse(pendingActionService.confirm(id, principal.getName()));
    }

    @PostMapping("/actions/{id}/cancel")
    public PendingActionResultResponse cancelAction(@PathVariable String id, Principal principal) {
        return toResponse(pendingActionService.decline(id, principal.getName()));
    }

    private PendingActionResponse toResponse(PendingAction action) {
        return new PendingActionResponse(action.getId(), action.getConversationId(), action.getType(),
                action.getSummary(), action.getExpiresAt());
    }

    private PendingActionResultResponse toResponse(PendingActionResult result) {
        return new PendingActionResultResponse(result.id(), result.status(), result.message());
    }
}