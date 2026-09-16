package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.SendMessageRequest;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/conversations")
    public Object listConversations(@CurrentUser User user) {
        return ApiResponse.ok(chatService.listConversations(user));
    }

    @PostMapping("/conversations/{userId}")
    public Object openConversation(@CurrentUser User user, @PathVariable String userId) {
        return ApiResponse.ok(chatService.openConversation(user, userId));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public Object getMessages(@CurrentUser User user, @PathVariable String conversationId,
                               @RequestParam(required = false) String before,
                               @RequestParam(required = false) Integer limit) {
        Instant beforeInstant = (before != null && !before.isBlank()) ? Instant.parse(before) : null;
        return ApiResponse.ok(chatService.getMessages(user, conversationId, beforeInstant, limit));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public Object sendMessage(@CurrentUser User user, @PathVariable String conversationId,
                               @Valid @RequestBody SendMessageRequest body) {
        return ApiResponse.ok(
                chatService.sendMessage(user, conversationId, body.getContent(), body.getMediaUrl(), body.getType().name()),
                "Message sent", HttpStatus.CREATED);
    }
}
