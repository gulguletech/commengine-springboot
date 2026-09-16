package com.commengine.socket;

import com.commengine.dto.DtoMapper;
import com.commengine.entity.RoomStatus;
import com.commengine.entity.User;
import com.commengine.entity.VideoRoom;
import com.commengine.entity.VideoRoomComment;
import com.commengine.repository.VideoRoomCommentRepository;
import com.commengine.repository.VideoRoomRepository;
import com.commengine.socket.payload.RoomIdPayload;
import com.commengine.socket.payload.TextPayload;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ported 1:1 from sockets/videoRoomSocket.js. */
@Component
public class VideoRoomSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VideoRoomSocketHandler.class);

    private final SocketUserResolver userResolver;
    private final VideoRoomRepository videoRoomRepository;
    private final VideoRoomCommentRepository commentRepository;
    private SocketIOServer server;

    public VideoRoomSocketHandler(SocketUserResolver userResolver, VideoRoomRepository videoRoomRepository,
                                   VideoRoomCommentRepository commentRepository) {
        this.userResolver = userResolver;
        this.videoRoomRepository = videoRoomRepository;
        this.commentRepository = commentRepository;
    }

    public void register(SocketIOServer server) {
        this.server = server;
        server.addEventListener("video:join-room-channel", RoomIdPayload.class, onJoinRoomChannel());
        server.addEventListener("video:leave-room-channel", RoomIdPayload.class, onLeaveRoomChannel());
        server.addEventListener("video:comment", TextPayload.class, onComment());
    }

    private DataListener<RoomIdPayload> onJoinRoomChannel() {
        return (client, payload, ack) -> {
            User user = userResolver.currentUser(client);
            if (user == null || payload.getRoomId() == null) return;
            client.joinRoom("video-room:" + payload.getRoomId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("roomId", payload.getRoomId());
            data.put("userId", user.getId());
            data.put("name", user.getName());
            server.getRoomOperations("video-room:" + payload.getRoomId()).sendEvent("video:viewer-joined", data);
        };
    }

    private DataListener<RoomIdPayload> onLeaveRoomChannel() {
        return (client, payload, ack) -> {
            User user = userResolver.currentUser(client);
            if (user == null || payload.getRoomId() == null) return;
            client.leaveRoom("video-room:" + payload.getRoomId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("roomId", payload.getRoomId());
            data.put("userId", user.getId());
            server.getRoomOperations("video-room:" + payload.getRoomId()).sendEvent("video:viewer-left", data);
        };
    }

    private DataListener<TextPayload> onComment() {
        return (client, payload, ackRequest) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ackRequest, "Not authenticated"); return; }
                if (payload.getText() == null || payload.getText().isBlank()) {
                    ackFail(ackRequest, "text is required");
                    return;
                }

                VideoRoom room = videoRoomRepository.findById(payload.getRoomId()).orElse(null);
                if (room == null || room.getStatus() != RoomStatus.ACTIVE) {
                    ackFail(ackRequest, "Live room not found or ended");
                    return;
                }

                String text = payload.getText().trim();
                if (text.length() > 300) text = text.substring(0, 300);

                VideoRoomComment comment = new VideoRoomComment();
                comment.setRoom(room);
                comment.setUser(user);
                comment.setText(text);
                commentRepository.save(comment);

                Map<String, Object> commentJson = DtoMapper.videoRoomComment(comment);
                server.getRoomOperations("video-room:" + payload.getRoomId()).sendEvent("video:new-comment", commentJson);

                if (ackRequest.isAckRequested()) {
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("success", true);
                    res.put("comment", commentJson);
                    ackRequest.sendAckData(res);
                }
            } catch (Exception e) {
                log.error("video:comment error", e);
                ackFail(ackRequest, "Failed to post comment");
            }
        };
    }

    private static void ackFail(AckRequest ackRequest, String message) {
        if (ackRequest.isAckRequested()) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("success", false);
            res.put("message", message);
            ackRequest.sendAckData(res);
        }
    }
}
