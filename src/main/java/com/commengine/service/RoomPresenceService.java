package com.commengine.service;

import com.commengine.entity.MemberRole;
import com.commengine.entity.RoomStatus;
import com.commengine.entity.VideoRoom;
import com.commengine.entity.VideoRoomViewer;
import com.commengine.repository.AudioRoomMemberRepository;
import com.commengine.repository.VideoRoomCommentRepository;
import com.commengine.repository.VideoRoomRepository;
import com.commengine.repository.VideoRoomViewerRepository;
import com.commengine.socket.AudioRoomLiveState;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One place that decides "who is inside which room".
 *
 * Rules enforced here:
 *  - a user can be in ONE audio/video room at a time: joining another one removes them from the first
 *  - leaving (back button, X, app closed, socket dropped) fully removes them: seat, member list, viewer count
 *  - if the host of a live video stream leaves, the stream ends (nothing is being published any more)
 */
@Service
public class RoomPresenceService {

    private static final String AUDIO_KEY = "commengine_current_audio_room";

    private final AudioRoomLiveState state;
    private final AudioRoomMemberRepository audioMembers;
    private final VideoRoomRepository videoRooms;
    private final VideoRoomViewerRepository viewers;
    private final VideoRoomCommentRepository comments;
    private final SocketIOServer server;

    public RoomPresenceService(AudioRoomLiveState state, AudioRoomMemberRepository audioMembers,
                               VideoRoomRepository videoRooms, VideoRoomViewerRepository viewers,
                               VideoRoomCommentRepository comments, SocketIOServer server) {
        this.state = state;
        this.audioMembers = audioMembers;
        this.videoRooms = videoRooms;
        this.viewers = viewers;
        this.comments = comments;
        this.server = server;
    }

    // ---------------------------------------------------------------- audio

    @Transactional
    public void leaveAudioRoom(String userId, String roomId) {
        int seatIdx = state.clearUserFromRoom(roomId, userId);
        Set<String> members = state.peekMembers(roomId);
        if (members != null) members.remove(userId);

        audioMembers.findByRoomIdAndUserId(roomId, userId)
                .filter(m -> m.getLeftAt() == null)
                .ifPresent(m -> {
                    m.setLeftAt(Instant.now());
                    if (seatIdx != -1) {
                        m.setRole(MemberRole.LISTENER);
                        m.setMuted(true);
                    }
                    audioMembers.save(m);
                });

        for (SocketIOClient c : userClients(userId)) {
            c.leaveRoom("audio-room:" + roomId);
            c.leaveRoom("audio-room:" + roomId + ":host");
            if (roomId.equals(c.get(AUDIO_KEY))) c.del(AUDIO_KEY);
        }

        String channel = "audio-room:" + roomId;
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("roomId", roomId);
        left.put("userId", userId);
        server.getRoomOperations(channel).sendEvent("audio:member-left", left);

        Map<String, Object> count = new LinkedHashMap<>();
        count.put("roomId", roomId);
        count.put("count", members == null ? 0 : members.size());
        server.getRoomOperations(channel).sendEvent("audio:member-count", count);

        if (seatIdx != -1) {
            Map<String, Object> seatsMsg = new LinkedHashMap<>();
            seatsMsg.put("roomId", roomId);
            seatsMsg.put("seats", state.getSeats(roomId));
            seatsMsg.put("blockedSeats", state.getBlocked(roomId));
            server.getRoomOperations(channel).sendEvent("audio:seats-update", seatsMsg);
        }
    }

    // ---------------------------------------------------------------- video

    @Transactional
    public void leaveVideoRoom(String userId, String roomId) {
        VideoRoom room = videoRooms.findById(roomId).orElse(null);
        if (room == null) return;

        if (room.getHost().getId().equals(userId)) {
            endVideoRoom(room);
            return;
        }

        VideoRoomViewer viewer = viewers.findByRoomIdAndUserId(roomId, userId).orElse(null);
        if (viewer != null && viewer.getLeftAt() == null) {
            viewer.setLeftAt(Instant.now());
            viewers.save(viewer);
        }
        long count = viewers.countByRoomIdAndLeftAtIsNull(roomId);
        room.setViewerCount((int) count);
        videoRooms.save(room);

        for (SocketIOClient c : userClients(userId)) c.leaveRoom("video-room:" + roomId);
        broadcastViewerCount(roomId, count);
    }

    /** Ends a stream and deletes everything tied to it. */
    @Transactional
    public void endVideoRoom(VideoRoom room) {
        String id = room.getId();
        server.getRoomOperations("video-room:" + id).sendEvent("video:room-ended", Map.of("roomId", id));
        comments.deleteAllByRoomId(id);
        viewers.deleteAllByRoomId(id);
        videoRooms.delete(room);
    }

    public void broadcastViewerCount(String roomId, long count) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roomId", roomId);
        data.put("count", count);
        server.getRoomOperations("video-room:" + roomId).sendEvent("video:viewer-count", data);
    }

    // ---------------------------------------------------------------- cross-room rules

    /**
     * Removes the user from every audio/video room except the one they are joining now,
     * and tells their client(s) so the old room screen closes itself.
     */
    @Transactional
    public void leaveOthers(String userId, String keepKind, String keepRoomId) {
        leaveAllExcept(userId, keepKind, keepRoomId, true);
    }

    /** Admin ban: remove the user from every room and drop their live connections. */
    @Transactional
    public void kickUser(String userId) {
        leaveAllExcept(userId, "none", "", false);
        for (SocketIOClient c : new java.util.ArrayList<>(userClients(userId))) {
            c.disconnect();
        }
    }

    /** Called when the user's last socket disconnects (app closed / network lost). */
    @Transactional
    public void handleDisconnect(String userId, java.util.UUID disconnectedSession) {
        for (SocketIOClient c : userClients(userId)) {
            if (!c.getSessionId().equals(disconnectedSession)) return; // still connected on another device/connection
        }
        leaveAllExcept(userId, "none", "", false);
    }

    private void leaveAllExcept(String userId, String keepKind, String keepRoomId, boolean notify) {
        Set<String> audioIds = new LinkedHashSet<>();
        audioMembers.findByUserIdAndLeftAtIsNull(userId).forEach(m -> audioIds.add(m.getRoom().getId()));
        for (Map.Entry<String, Set<String>> e : state.allMembers().entrySet()) {
            if (e.getValue().contains(userId)) audioIds.add(e.getKey());
        }
        for (String roomId : audioIds) {
            if ("audio".equals(keepKind) && roomId.equals(keepRoomId)) continue;
            leaveAudioRoom(userId, roomId);
            if (notify) notifyReplaced(userId, "audio", roomId);
        }

        Set<String> videoIds = new LinkedHashSet<>();
        viewers.findByUserIdAndLeftAtIsNull(userId).forEach(v -> videoIds.add(v.getRoom().getId()));
        videoRooms.findByHostIdAndStatus(userId, RoomStatus.ACTIVE).forEach(r -> videoIds.add(r.getId()));
        for (String roomId : videoIds) {
            if ("video".equals(keepKind) && roomId.equals(keepRoomId)) continue;
            leaveVideoRoom(userId, roomId);
            if (notify) notifyReplaced(userId, "video", roomId);
        }
    }

    private void notifyReplaced(String userId, String kind, String roomId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", kind);
        data.put("roomId", roomId);
        server.getRoomOperations("user:" + userId).sendEvent("room:replaced", data);
    }

    private Collection<SocketIOClient> userClients(String userId) {
        return server.getRoomOperations("user:" + userId).getClients();
    }
}
