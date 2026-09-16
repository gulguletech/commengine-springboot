package com.commengine.socket;

import com.commengine.dto.DtoMapper;
import com.commengine.entity.Conversation;
import com.commengine.entity.Message;
import com.commengine.entity.MessageType;
import com.commengine.entity.User;
import com.commengine.repository.ConversationRepository;
import com.commengine.repository.MessageRepository;
import com.commengine.socket.payload.ChatSendMessagePayload;
import com.commengine.socket.payload.TypingPayload;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ported from sockets/chatSocket.js. */
@Component
public class ChatSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatSocketHandler.class);

    private final SocketUserResolver userResolver;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ChatSocketHandler(SocketUserResolver userResolver, ConversationRepository conversationRepository,
                              MessageRepository messageRepository) {
        this.userResolver = userResolver;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public void register(SocketIOServer server) {
        server.addEventListener("chat:typing", TypingPayload.class, onTyping());
        server.addEventListener("chat:send-message", ChatSendMessagePayload.class, onSendMessage());
    }

    private DataListener<TypingPayload> onTyping() {
        return (client, payload, ackRequest) -> {
            try {
                User me = userResolver.currentUser(client);
                if (me == null || payload.getConversationId() == null) return;
                Conversation conversation = conversationRepository.findById(payload.getConversationId()).orElse(null);
                if (conversation == null) return;

                String recipientId = conversation.getUserOne().getId().equals(me.getId())
                        ? conversation.getUserTwo().getId() : conversation.getUserOne().getId();

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("conversationId", payload.getConversationId());
                data.put("userId", me.getId());
                data.put("isTyping", Boolean.TRUE.equals(payload.getIsTyping()));

                client.getNamespace().getRoomOperations("user:" + recipientId).sendEvent("chat:typing", data);
            } catch (Exception e) {
                log.error("chat:typing error", e);
            }
        };
    }

    private DataListener<ChatSendMessagePayload> onSendMessage() {
        return (client, payload, ackRequest) -> {
            try {
                User me = userResolver.currentUser(client);
                if (me == null) { ack(ackRequest, false, "Not authenticated"); return; }

                boolean hasContent = payload.getContent() != null && !payload.getContent().isBlank();
                boolean hasMedia = payload.getMediaUrl() != null && !payload.getMediaUrl().isBlank();
                if (payload.getConversationId() == null || (!hasContent && !hasMedia)) {
                    ack(ackRequest, false, "conversationId and content/mediaUrl required");
                    return;
                }

                Conversation conversation = conversationRepository.findById(payload.getConversationId()).orElse(null);
                if (conversation == null ||
                        (!conversation.getUserOne().getId().equals(me.getId()) && !conversation.getUserTwo().getId().equals(me.getId()))) {
                    ack(ackRequest, false, "Not authorized for this conversation");
                    return;
                }

                Message message = new Message();
                message.setConversation(conversation);
                message.setSender(me);
                message.setContent(payload.getContent());
                message.setMediaUrl(payload.getMediaUrl());
                try {
                    message.setType(MessageType.valueOf(payload.getType()));
                } catch (Exception ignored) {
                    message.setType(MessageType.TEXT);
                }
                messageRepository.save(message);

                conversation.setUpdatedAt(Instant.now());
                conversationRepository.save(conversation);

                String recipientId = conversation.getUserOne().getId().equals(me.getId())
                        ? conversation.getUserTwo().getId() : conversation.getUserOne().getId();

                Map<String, Object> messageJson = DtoMapper.message(message);
                client.getNamespace().getRoomOperations("user:" + recipientId).sendEvent("chat:new-message", messageJson);
                client.getNamespace().getRoomOperations("user:" + me.getId()).sendEvent("chat:new-message", messageJson);

                if (ackRequest.isAckRequested()) {
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("success", true);
                    res.put("message", messageJson);
                    ackRequest.sendAckData(res);
                }
            } catch (Exception e) {
                log.error("chat:send-message error", e);
                ack(ackRequest, false, "Failed to send message");
            }
        };
    }

    private static void ack(AckRequest ackRequest, boolean success, String message) {
        if (ackRequest.isAckRequested()) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("success", success);
            res.put("message", message);
            ackRequest.sendAckData(res);
        }
    }
}
