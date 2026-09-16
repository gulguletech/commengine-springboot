package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.DtoMapper;
import com.commengine.entity.Conversation;
import com.commengine.entity.Message;
import com.commengine.entity.MessageType;
import com.commengine.entity.User;
import com.commengine.repository.ConversationRepository;
import com.commengine.repository.MessageRepository;
import com.commengine.repository.UserRepository;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SocketIOServer socketIOServer;

    public ChatService(ConversationRepository conversationRepository, MessageRepository messageRepository,
                        UserRepository userRepository, SocketIOServer socketIOServer) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.socketIOServer = socketIOServer;
    }

    @Transactional
    public Conversation getOrCreateConversation(String userAId, String userBId) {
        String userOneId = userAId.compareTo(userBId) < 0 ? userAId : userBId;
        String userTwoId = userAId.compareTo(userBId) < 0 ? userBId : userAId;

        return conversationRepository.findByUserOneIdAndUserTwoId(userOneId, userTwoId)
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setUserOne(userRepository.getReferenceById(userOneId));
                    c.setUserTwo(userRepository.getReferenceById(userTwoId));
                    return conversationRepository.save(c);
                });
    }

    public Map<String, Object> listConversations(User user) {
        List<Conversation> conversations = conversationRepository.findAllForUser(user.getId());
        List<Map<String, Object>> shaped = conversations.stream().map(c -> {
            Message last = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(c.getId()).orElse(null);
            return DtoMapper.conversationSummary(c, user.getId(), last);
        }).collect(Collectors.toList());
        return Map.of("conversations", shaped);
    }

    @Transactional
    public Map<String, Object> openConversation(User user, String otherUserId) {
        if (otherUserId.equals(user.getId())) {
            throw ApiException.unprocessable("Cannot start a conversation with yourself");
        }
        User otherUser = userRepository.findById(otherUserId).orElseThrow(() -> ApiException.notFound("User not found"));
        Conversation conversation = getOrCreateConversation(user.getId(), otherUser.getId());
        return Map.of("conversation", DtoMapper.conversation(conversation));
    }

    @Transactional
    public Map<String, Object> getMessages(User user, String conversationId, Instant before, Integer limitParam) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("Conversation not found"));
        assertParticipant(conversation, user);

        int limit = Math.min(limitParam == null ? 50 : limitParam, 100);
        List<Message> messages = before != null
                ? messageRepository.findByConversationIdAndCreatedAtLessThanOrderByCreatedAtDesc(conversationId, before, PageRequest.of(0, limit))
                : messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, limit));

        messageRepository.markConversationRead(conversationId, user.getId());

        List<Map<String, Object>> shaped = messages.stream().map(DtoMapper::message).collect(Collectors.toList());
        Collections.reverse(shaped);
        return Map.of("messages", shaped);
    }

    @Transactional
    public Map<String, Object> sendMessage(User sender, String conversationId, String content, String mediaUrl, String type) {
        if ((content == null || content.isBlank()) && (mediaUrl == null || mediaUrl.isBlank())) {
            throw ApiException.unprocessable("content or mediaUrl is required");
        }
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("Conversation not found"));
        if (!conversation.getUserOne().getId().equals(sender.getId()) && !conversation.getUserTwo().getId().equals(sender.getId())) {
            throw ApiException.forbidden("Not authorized to message in this conversation");
        }

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content);
        message.setMediaUrl(mediaUrl);
        try {
            message.setType(MessageType.valueOf(type == null ? "TEXT" : type));
        } catch (IllegalArgumentException e) {
            message.setType(MessageType.TEXT);
        }
        messageRepository.save(message);

        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        String recipientId = conversation.getUserOne().getId().equals(sender.getId())
                ? conversation.getUserTwo().getId() : conversation.getUserOne().getId();

        Map<String, Object> messageJson = DtoMapper.message(message);
        socketIOServer.getRoomOperations("user:" + recipientId).sendEvent("chat:new-message", messageJson);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", messageJson);
        return data;
    }

    private void assertParticipant(Conversation conversation, User user) {
        if (!conversation.getUserOne().getId().equals(user.getId()) && !conversation.getUserTwo().getId().equals(user.getId())) {
            throw ApiException.forbidden("Not authorized to view this conversation");
        }
    }
}
