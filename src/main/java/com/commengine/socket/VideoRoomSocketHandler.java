package com.commengine.socket;

import com.commengine.dto.DtoMapper;
import com.commengine.entity.RoomStatus;
import com.commengine.entity.User;
import com.commengine.entity.VideoRoom;
import com.commengine.entity.VideoRoomComment;
import com.commengine.repository.VideoRoomCommentRepository;
import com.commengine.repository.VideoRoomRepository;
import com.commengine.repository.UserRepository;
import com.commengine.repository.VideoRoomViewerRepository;
import com.commengine.service.AgoraService;
import com.commengine.socket.payload.MicPayload;
import com.commengine.socket.payload.RoomIdPayload;
import com.commengine.socket.payload.TextPayload;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ported 1:1 from sockets/videoRoomSocket.js. */
@Component
public class VideoRoomSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VideoRoomSocketHandler.class);

    private final SocketUserResolver userResolver;
    private final VideoRoomRepository videoRoomRepository;
    private final VideoRoomCommentRepository commentRepository;
    private final com.commengine.service.RoomPresenceService presence;
    private final com.commengine.service.EntryEffectService entryEffects;
    private final VideoRoomViewerRepository viewerRepository;
    private final UserRepository userRepository;
    private final AgoraService agoraService;
    private final VideoMicState micState;
    private SocketIOServer server;

    public VideoRoomSocketHandler(SocketUserResolver userResolver, VideoRoomRepository videoRoomRepository,
                                   VideoRoomCommentRepository commentRepository,
                                   com.commengine.service.RoomPresenceService presence,
                                   com.commengine.service.EntryEffectService entryEffects,
                                   VideoRoomViewerRepository viewerRepository, UserRepository userRepository,
                                   AgoraService agoraService, VideoMicState micState) {
        this.viewerRepository = viewerRepository;
        this.userRepository = userRepository;
        this.agoraService = agoraService;
        this.micState = micState;
        this.userResolver = userResolver;
        this.videoRoomRepository = videoRoomRepository;
        this.commentRepository = commentRepository;
        this.presence = presence;
        this.entryEffects = entryEffects;
    }

    public void register(SocketIOServer server) {
        this.server = server;
        server.addEventListener("video:join-room-channel", RoomIdPayload.class, onJoinRoomChannel());
        server.addEventListener("video:leave-room-channel", RoomIdPayload.class, onLeaveRoomChannel());
        server.addEventListener("video:comment", TextPayload.class, onComment());

        // ---- the "mic": host + one guest share the screen ----
        server.addEventListener("video:mic-get", RoomIdPayload.class, onMicGet());
        server.addEventListener("video:mic-request", MicPayload.class, onMicRequest());
        server.addEventListener("video:mic-cancel-request", MicPayload.class, onMicCancelRequest());
        server.addEventListener("video:mic-respond", MicPayload.class, onMicRespond());
        server.addEventListener("video:mic-invite", MicPayload.class, onMicInvite());
        server.addEventListener("video:mic-accept-invite", MicPayload.class, onMicAcceptInvite());
        server.addEventListener("video:mic-decline-invite", MicPayload.class, onMicDeclineInvite());
        server.addEventListener("video:mic-leave", MicPayload.class, onMicLeave());
        server.addEventListener("video:mic-remove", MicPayload.class, onMicRemove());
    }

    private DataListener<RoomIdPayload> onJoinRoomChannel() {
        return (client, payload, ack) -> {
            User user = userResolver.currentUser(client);
            if (user == null || payload.getRoomId() == null) return;
            // only subscribes this socket to the room's events; the viewer count is
            // broadcast by the REST join/leave (RoomPresenceService) so it is never double counted
            client.joinRoom("video-room:" + payload.getRoomId());
            // late joiners must see the split screen straight away
            client.sendEvent("video:mic-update", micState.micMessage(payload.getRoomId()));
            VideoRoom joined = videoRoomRepository.findById(payload.getRoomId()).orElse(null);
            if (joined != null && joined.getHost().getId().equals(user.getId())) {
                client.sendEvent("video:mic-requests", micState.requestsMessage(payload.getRoomId()));
            }
            // everyone in the stream (including the joiner) sees the newcomer's entry effect, if they own one
            entryEffects.announce("video-room:" + payload.getRoomId(), "video", payload.getRoomId(), user.getId());
        };
    }

    private DataListener<RoomIdPayload> onLeaveRoomChannel() {
        return (client, payload, ack) -> {
            User user = userResolver.currentUser(client);
            if (user == null || payload.getRoomId() == null) return;
            presence.leaveVideoRoom(user.getId(), payload.getRoomId());
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

    // =====================================================================================================
    // MIC
    // =====================================================================================================

    /** Loads an ACTIVE room or acks a failure and returns null. */
    private VideoRoom activeRoom(String roomId, AckRequest ack) {
        VideoRoom room = roomId == null ? null : videoRoomRepository.findById(roomId).orElse(null);
        if (room == null || room.getStatus() != RoomStatus.ACTIVE) {
            ackFail(ack, "Live room not found or ended");
            return null;
        }
        return room;
    }

    private boolean isInRoom(String roomId, String userId) {
        return viewerRepository.findByRoomIdAndUserId(roomId, userId).filter(v -> v.getLeftAt() == null).isPresent();
    }

    private DataListener<RoomIdPayload> onMicGet() {
        return (client, payload, ack) -> {
            User user = userResolver.currentUser(client);
            if (user == null || payload.getRoomId() == null) return;
            client.sendEvent("video:mic-update", micState.micMessage(payload.getRoomId()));
            VideoRoom room = videoRoomRepository.findById(payload.getRoomId()).orElse(null);
            if (room != null && room.getHost().getId().equals(user.getId())) {
                client.sendEvent("video:mic-requests", micState.requestsMessage(payload.getRoomId()));
            }
        };
    }

    /** Viewer asks the host for the mic. */
    private DataListener<MicPayload> onMicRequest() {
        return (client, payload, ack) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ack, "Not authenticated"); return; }
                VideoRoom room = activeRoom(payload.getRoomId(), ack);
                if (room == null) return;
                String hostId = room.getHost().getId();
                if (hostId.equals(user.getId())) { ackFail(ack, "You are the host"); return; }
                if (!isInRoom(room.getId(), user.getId())) { ackFail(ack, "Join the live first"); return; }
                if (micState.holder(room.getId()) != null) { ackFail(ack, "Someone is already on the mic"); return; }

                User fresh = userRepository.findById(user.getId()).orElse(user);
                micState.addRequest(room.getId(), fresh);
                micState.sendRequestsToHost(room.getId(), hostId);
                ackOk(ack);
            } catch (Exception e) {
                log.error("video:mic-request error", e);
                ackFail(ack, "Failed to send request");
            }
        };
    }

    private DataListener<MicPayload> onMicCancelRequest() {
        return (client, payload, ack) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ack, "Not authenticated"); return; }
                VideoRoom room = videoRoomRepository.findById(payload.getRoomId()).orElse(null);
                if (room == null) { ackOk(ack); return; }
                if (micState.removeRequest(room.getId(), user.getId())) {
                    micState.sendRequestsToHost(room.getId(), room.getHost().getId());
                }
                ackOk(ack);
            } catch (Exception e) {
                log.error("video:mic-cancel-request error", e);
                ackFail(ack, "Failed to cancel");
            }
        };
    }

    /** Host accepts / rejects a viewer's request. */
    private DataListener<MicPayload> onMicRespond() {
        return (client, payload, ack) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ack, "Not authenticated"); return; }
                VideoRoom room = activeRoom(payload.getRoomId(), ack);
                if (room == null) return;
                if (!room.getHost().getId().equals(user.getId())) { ackFail(ack, "Only the host can answer requests"); return; }
                String targetId = payload.getUserId();
                if (targetId == null || !micState.hasRequest(room.getId(), targetId)) {
                    ackFail(ack, "That request is no longer there");
                    micState.sendRequestsToHost(room.getId(), user.getId());
                    return;
                }

                if (!Boolean.TRUE.equals(payload.getAccept())) {
                    micState.removeRequest(room.getId(), targetId);
                    micState.sendRequestsToHost(room.getId(), user.getId());
                    sendRejected(targetId, room.getId(), "declined");
                    ackOk(ack);
                    return;
                }

                String err = grantMic(room, targetId);
                if (err != null) { ackFail(ack, err); return; }
                micState.sendRequestsToHost(room.getId(), user.getId());
                ackOk(ack);
            } catch (Exception e) {
                log.error("video:mic-respond error", e);
                ackFail(ack, "Failed to answer the request");
            }
        };
    }

    /** Host invites a viewer to take the mic. */
    private DataListener<MicPayload> onMicInvite() {
        return (client, payload, ack) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ack, "Not authenticated"); return; }
                VideoRoom room = activeRoom(payload.getRoomId(), ack);
                if (room == null) return;
                if (!room.getHost().getId().equals(user.getId())) { ackFail(ack, "Only the host can invite to the mic"); return; }
                String targetId = payload.getUserId();
                if (targetId == null || targetId.equals(user.getId())) { ackFail(ack, "Pick a viewer to invite"); return; }
                if (micState.holder(room.getId()) != null) { ackFail(ack, "Someone is already on the mic"); return; }
                if (!isInRoom(room.getId(), targetId)) { ackFail(ack, "That person is no longer watching"); return; }

                micState.addInvite(room.getId(), targetId);
                Map<String, Object> invite = new LinkedHashMap<>();
                invite.put("roomId", room.getId());
                invite.put("host", DtoMapper.userBrief(user));
                invite.put("roomTitle", room.getTitle());
                server.getRoomOperations("user:" + targetId).sendEvent("video:mic-invite", invite);
                ackOk(ack);
            } catch (Exception e) {
                log.error("video:mic-invite error", e);
                ackFail(ack, "Failed to send invite");
            }
        };
    }

    private DataListener<MicPayload> onMicAcceptInvite() {
        return (client, payload, ack) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ack, "Not authenticated"); return; }
                VideoRoom room = activeRoom(payload.getRoomId(), ack);
                if (room == null) return;
                if (!micState.hasInvite(room.getId(), user.getId())) { ackFail(ack, "That invite has expired"); return; }
                if (!isInRoom(room.getId(), user.getId())) { ackFail(ack, "Join the live first"); return; }
                String err = grantMic(room, user.getId());
                if (err != null) { ackFail(ack, err); return; }
                micState.sendRequestsToHost(room.getId(), room.getHost().getId());
                ackOk(ack);
            } catch (Exception e) {
                log.error("video:mic-accept-invite error", e);
                ackFail(ack, "Failed to take the mic");
            }
        };
    }

    private DataListener<MicPayload> onMicDeclineInvite() {
        return (client, payload, ack) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ack, "Not authenticated"); return; }
                VideoRoom room = videoRoomRepository.findById(payload.getRoomId()).orElse(null);
                if (room == null) { ackOk(ack); return; }
                if (micState.removeInvite(room.getId(), user.getId())) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("roomId", room.getId());
                    m.put("user", DtoMapper.userBrief(user));
                    server.getRoomOperations("user:" + room.getHost().getId()).sendEvent("video:mic-invite-declined", m);
                }
                ackOk(ack);
            } catch (Exception e) {
                log.error("video:mic-decline-invite error", e);
                ackFail(ack, "Failed");
            }
        };
    }

    /** The person on the mic hands it back. */
    private DataListener<MicPayload> onMicLeave() {
        return (client, payload, ack) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ack, "Not authenticated"); return; }
                if (payload.getRoomId() != null && micState.release(payload.getRoomId(), user.getId())) {
                    micState.broadcastMic(payload.getRoomId());
                }
                ackOk(ack);
            } catch (Exception e) {
                log.error("video:mic-leave error", e);
                ackFail(ack, "Failed to leave the mic");
            }
        };
    }

    /** Host takes the mic away from the guest. */
    private DataListener<MicPayload> onMicRemove() {
        return (client, payload, ack) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ack, "Not authenticated"); return; }
                VideoRoom room = activeRoom(payload.getRoomId(), ack);
                if (room == null) return;
                if (!room.getHost().getId().equals(user.getId())) { ackFail(ack, "Only the host can remove the guest"); return; }
                Map<String, Object> cur = micState.holder(room.getId());
                if (cur != null) {
                    String guestId = (String) cur.get("id");
                    micState.release(room.getId(), guestId);
                    micState.broadcastMic(room.getId());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("roomId", room.getId());
                    server.getRoomOperations("user:" + guestId).sendEvent("video:mic-removed", m);
                }
                ackOk(ack);
            } catch (Exception e) {
                log.error("video:mic-remove error", e);
                ackFail(ack, "Failed to remove the guest");
            }
        };
    }

    /**
     * Gives the mic to {@code targetId}: takes the slot, mints a PUBLISHER token for them (viewers only join with a
     * subscriber token), and tells everyone. Returns an error message, or null on success.
     */
    private String grantMic(VideoRoom room, String targetId) {
        User target = userRepository.findById(targetId).orElse(null);
        if (target == null) return "User not found";
        if (!isInRoom(room.getId(), targetId)) return "That person is no longer watching";

        List<String> dropped = new ArrayList<>();
        if (micState.grant(room.getId(), target, dropped) == null) return "Someone is already on the mic";

        Map<String, Object> rtc = agoraService.generateRtcToken(
                room.getChannelName(), com.commengine.util.AgoraUidUtil.deriveAgoraUid(targetId), "publisher");
        Map<String, Object> granted = new LinkedHashMap<>();
        granted.put("roomId", room.getId());
        granted.put("rtc", rtc);
        server.getRoomOperations("user:" + targetId).sendEvent("video:mic-granted", granted);

        for (String id : dropped) sendRejected(id, room.getId(), "taken");
        micState.broadcastMic(room.getId());
        return null;
    }

    private void sendRejected(String userId, String roomId, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roomId", roomId);
        m.put("reason", reason);
        server.getRoomOperations("user:" + userId).sendEvent("video:mic-rejected", m);
    }

    private static void ackOk(AckRequest ackRequest) {
        if (ackRequest.isAckRequested()) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("success", true);
            ackRequest.sendAckData(res);
        }
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
