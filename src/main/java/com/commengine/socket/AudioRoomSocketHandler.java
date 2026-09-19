package com.commengine.socket;

import com.commengine.dto.DtoMapper;
import com.commengine.entity.*;
import com.commengine.repository.AudioRoomMemberRepository;
import com.commengine.repository.AudioRoomRepository;
import com.commengine.socket.payload.*;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ported 1:1 from sockets/audioRoomSocket.js. */
@Component
public class AudioRoomSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AudioRoomSocketHandler.class);
    private static final String CURRENT_ROOM_KEY = "commengine_current_audio_room";

    private final SocketUserResolver userResolver;
    private final AudioRoomRepository audioRoomRepository;
    private final AudioRoomMemberRepository memberRepository;
    private final AudioRoomLiveState state;
    private SocketIOServer server;

    public AudioRoomSocketHandler(SocketUserResolver userResolver, AudioRoomRepository audioRoomRepository,
                                  AudioRoomMemberRepository memberRepository, AudioRoomLiveState state) {
        this.userResolver = userResolver;
        this.audioRoomRepository = audioRoomRepository;
        this.memberRepository = memberRepository;
        this.state = state;
    }

    public void register(SocketIOServer server) {
        this.server = server;
        server.addEventListener("audio:join-room-channel", RoomIdPayload.class, onJoinRoomChannel());
        server.addEventListener("audio:leave-room-channel", RoomIdPayload.class, onLeaveRoomChannel());
        server.addEventListener("audio:get-seats", RoomIdPayload.class, onGetSeats());
        server.addEventListener("audio:take-seat", TakeSeatPayload.class, onTakeSeat());
        server.addEventListener("audio:invite-to-seat", InviteToSeatPayload.class, onInviteToSeat());
        server.addEventListener("audio:leave-seat", RoomIdPayload.class, onLeaveSeat());
        server.addEventListener("audio:remove-from-seat", RemoveFromSeatPayload.class, onRemoveFromSeat());
        server.addEventListener("audio:kick-member", RemoveFromSeatPayload.class, onKickMember());
        server.addEventListener("audio:toggle-mute", ToggleMutePayload.class, onToggleMute());
        server.addEventListener("audio:speaking", SpeakingPayload.class, onSpeaking());
        server.addEventListener("audio:send-chat", TextPayload.class, onSendChat());

        server.addDisconnectListener(disconnectCleanup());
    }

    private void broadcastMemberCount(SocketIOServer server, String roomId) {
        int count = state.getMembers(roomId).size();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("roomId", roomId);
        data.put("count", count);
        server.getRoomOperations("audio-room:" + roomId).sendEvent("audio:member-count", data);
    }

    private DataListener<RoomIdPayload> onJoinRoomChannel() {
        return (client, payload, ack) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null || payload.getRoomId() == null) return;
                AudioRoom room = audioRoomRepository.findById(payload.getRoomId()).orElse(null);
                if (room == null) return;

                client.joinRoom("audio-room:" + payload.getRoomId());
                if (room.getHost().getId().equals(user.getId())) {
                    client.joinRoom("audio-room:" + payload.getRoomId() + ":host");
                }
                client.set(CURRENT_ROOM_KEY, payload.getRoomId());
                state.getMembers(payload.getRoomId()).add(user.getId());

                Map<String, Object> joined = new LinkedHashMap<>();
                joined.put("roomId", payload.getRoomId());
                joined.put("user", DtoMapper.userBrief(user));
                server.getRoomOperations("audio-room:" + payload.getRoomId()).sendEvent("audio:member-joined", joined);
                broadcastMemberCount(server, payload.getRoomId());

                Map<String, Object> seatsMsg = new LinkedHashMap<>();
                seatsMsg.put("roomId", payload.getRoomId());
                seatsMsg.put("seats", state.getSeats(payload.getRoomId()));
                client.sendEvent("audio:seats-update", seatsMsg);
            } catch (Exception e) {
                log.error("audio:join-room-channel error", e);
            }
        };
    }

    private DataListener<RoomIdPayload> onLeaveRoomChannel() {
        return (client, payload, ack) -> {
            User user = userResolver.currentUser(client);
            if (user == null || payload.getRoomId() == null) return;
            String roomId = payload.getRoomId();
            client.leaveRoom("audio-room:" + roomId);
            client.leaveRoom("audio-room:" + roomId + ":host");
            if (roomId.equals(client.get(CURRENT_ROOM_KEY))) {
                // NOTE: netty-socketio's client store is backed by ConcurrentHashMap, which
                // throws NullPointerException on a null value — set(key, null) is NOT a
                // valid way to clear a key there (unlike a plain HashMap). Use del() instead.
                client.del(CURRENT_ROOM_KEY);
            }
            int clearedIdx = state.clearUserFromRoom(roomId, user.getId());
            state.getMembers(roomId).remove(user.getId());

            Map<String, Object> left = new LinkedHashMap<>();
            left.put("roomId", roomId);
            left.put("userId", user.getId());
            server.getRoomOperations("audio-room:" + roomId).sendEvent("audio:member-left", left);
            broadcastMemberCount(server, roomId);

            if (clearedIdx != -1) {
                Map<String, Object> seatsMsg = new LinkedHashMap<>();
                seatsMsg.put("roomId", roomId);
                seatsMsg.put("seats", state.getSeats(roomId));
                server.getRoomOperations("audio-room:" + roomId).sendEvent("audio:seats-update", seatsMsg);
            }
        };
    }

    private DataListener<RoomIdPayload> onGetSeats() {
        return (client, payload, ack) -> {
            if (payload.getRoomId() == null) return;
            Map<String, Object> seatsMsg = new LinkedHashMap<>();
            seatsMsg.put("roomId", payload.getRoomId());
            seatsMsg.put("seats", state.getSeats(payload.getRoomId()));
            client.sendEvent("audio:seats-update", seatsMsg);
        };
    }

    private DataListener<TakeSeatPayload> onTakeSeat() {
        return (client, payload, ackRequest) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ackRequest, "Not authenticated"); return; }
                Integer seatIndex = payload.getSeatIndex();
                if (seatIndex == null || seatIndex < 0 || seatIndex >= AudioRoomLiveState.SEAT_COUNT) {
                    ackFail(ackRequest, "Invalid seat index");
                    return;
                }
                AudioRoom room = audioRoomRepository.findById(payload.getRoomId()).orElse(null);
                if (room == null || room.getStatus() != RoomStatus.ACTIVE) {
                    ackFail(ackRequest, "Room not found or ended");
                    return;
                }

                Seat[] seats = state.getSeats(payload.getRoomId());
                synchronized (state) {
                    Seat occupant = seats[seatIndex];
                    if (occupant != null && !occupant.userId.equals(user.getId())) {
                        ackFail(ackRequest, "Seat is already taken");
                        return;
                    }
                    // occupant == null -> free seat; occupant.userId == user.getId() -> the
                    // caller already holds this seat (e.g. a retried/duplicate emit after a
                    // reconnect) — treat both as success instead of failing on your own seat.
                    state.clearUserFromRoom(payload.getRoomId(), user.getId());
                    seats[seatIndex] = new Seat(user.getId(), user.getName(), user.getAvatarUrl(), true);
                }

                upsertMember(room, user, room.getHost().getId().equals(user.getId()) ? MemberRole.HOST : MemberRole.SPEAKER, true);

                Map<String, Object> seatsMsg = new LinkedHashMap<>();
                seatsMsg.put("roomId", payload.getRoomId());
                seatsMsg.put("seats", seats);
                server.getRoomOperations("audio-room:" + payload.getRoomId()).sendEvent("audio:seats-update", seatsMsg);

                if (ackRequest.isAckRequested()) {
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("success", true);
                    res.put("seats", seats);
                    ackRequest.sendAckData(res);
                }
            } catch (Exception e) {
                log.error("audio:take-seat error", e);
                ackFail(ackRequest, "Failed to take seat");
            }
        };
    }

    private DataListener<InviteToSeatPayload> onInviteToSeat() {
        return (client, payload, ackRequest) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ackRequest, "Not authenticated"); return; }
                AudioRoom room = audioRoomRepository.findById(payload.getRoomId()).orElse(null);
                if (room == null) { ackFail(ackRequest, "Room not found"); return; }
                if (!room.getHost().getId().equals(user.getId())) {
                    ackFail(ackRequest, "Only the host can invite someone to a seat");
                    return;
                }
                Integer seatIndex = payload.getSeatIndex();
                if (seatIndex == null || seatIndex < 0 || seatIndex >= AudioRoomLiveState.SEAT_COUNT) {
                    ackFail(ackRequest, "Invalid seat index");
                    return;
                }
                Seat[] seats = state.getSeats(payload.getRoomId());
                if (seats[seatIndex] != null) {
                    ackFail(ackRequest, "That seat is already taken");
                    return;
                }

                Map<String, Object> invite = new LinkedHashMap<>();
                invite.put("roomId", payload.getRoomId());
                invite.put("seatIndex", seatIndex);
                invite.put("host", DtoMapper.userBrief(user));
                server.getRoomOperations("user:" + payload.getUserId()).sendEvent("audio:seat-invite", invite);

                ackOk(ackRequest);
            } catch (Exception e) {
                log.error("audio:invite-to-seat error", e);
                ackFail(ackRequest, "Failed to send invite");
            }
        };
    }

    private DataListener<RoomIdPayload> onLeaveSeat() {
        return (client, payload, ackRequest) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ackRequest, "Not authenticated"); return; }
                int idx = state.clearUserFromRoom(payload.getRoomId(), user.getId());
                if (idx == -1) {
                    if (ackRequest.isAckRequested()) {
                        Map<String, Object> res = new LinkedHashMap<>();
                        res.put("success", true);
                        res.put("seats", state.getSeats(payload.getRoomId()));
                        ackRequest.sendAckData(res);
                    }
                    return;
                }
                memberRepository.findByRoomIdAndUserId(payload.getRoomId(), user.getId())
                        .filter(m -> m.getLeftAt() == null)
                        .ifPresent(m -> { m.setRole(MemberRole.LISTENER); memberRepository.save(m); });

                Seat[] seats = state.getSeats(payload.getRoomId());
                Map<String, Object> seatsMsg = new LinkedHashMap<>();
                seatsMsg.put("roomId", payload.getRoomId());
                seatsMsg.put("seats", seats);
                server.getRoomOperations("audio-room:" + payload.getRoomId()).sendEvent("audio:seats-update", seatsMsg);

                if (ackRequest.isAckRequested()) {
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("success", true);
                    res.put("seats", seats);
                    ackRequest.sendAckData(res);
                }
            } catch (Exception e) {
                log.error("audio:leave-seat error", e);
                ackFail(ackRequest, "Failed to leave seat");
            }
        };
    }

    private DataListener<RemoveFromSeatPayload> onRemoveFromSeat() {
        return (client, payload, ackRequest) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ackRequest, "Not authenticated"); return; }
                AudioRoom room = audioRoomRepository.findById(payload.getRoomId()).orElse(null);
                if (room == null) { ackFail(ackRequest, "Room not found"); return; }
                if (!room.getHost().getId().equals(user.getId())) {
                    ackFail(ackRequest, "Only the host can remove someone from a seat");
                    return;
                }

                int idx = state.clearUserFromRoom(payload.getRoomId(), payload.getUserId());
                if (idx == -1) {
                    if (ackRequest.isAckRequested()) {
                        Map<String, Object> res = new LinkedHashMap<>();
                        res.put("success", true);
                        res.put("seats", state.getSeats(payload.getRoomId()));
                        ackRequest.sendAckData(res);
                    }
                    return;
                }
                memberRepository.findByRoomIdAndUserId(payload.getRoomId(), payload.getUserId())
                        .filter(m -> m.getLeftAt() == null)
                        .ifPresent(m -> { m.setRole(MemberRole.LISTENER); m.setMuted(true); memberRepository.save(m); });

                Seat[] seats = state.getSeats(payload.getRoomId());
                Map<String, Object> seatsMsg = new LinkedHashMap<>();
                seatsMsg.put("roomId", payload.getRoomId());
                seatsMsg.put("seats", seats);
                server.getRoomOperations("audio-room:" + payload.getRoomId()).sendEvent("audio:seats-update", seatsMsg);

                Map<String, Object> removed = new LinkedHashMap<>();
                removed.put("roomId", payload.getRoomId());
                server.getRoomOperations("user:" + payload.getUserId()).sendEvent("audio:removed-from-seat", removed);

                if (ackRequest.isAckRequested()) {
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("success", true);
                    res.put("seats", seats);
                    ackRequest.sendAckData(res);
                }
            } catch (Exception e) {
                log.error("audio:remove-from-seat error", e);
                ackFail(ackRequest, "Failed to remove from seat");
            }
        };
    }

    /**
     * Host-only: fully removes someone from the room (not just their seat) — they stop
     * being an active member entirely, unlike audio:remove-from-seat which only demotes
     * them back to LISTENER. They CAN rejoin afterwards (this isn't a ban), matching the
     * simple "kick" behavior described for this app rather than a permanent block.
     */
    private DataListener<RemoveFromSeatPayload> onKickMember() {
        return (client, payload, ackRequest) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) { ackFail(ackRequest, "Not authenticated"); return; }
                AudioRoom room = audioRoomRepository.findById(payload.getRoomId()).orElse(null);
                if (room == null) { ackFail(ackRequest, "Room not found"); return; }
                if (!room.getHost().getId().equals(user.getId())) {
                    ackFail(ackRequest, "Only the host can remove someone from the room");
                    return;
                }
                if (room.getHost().getId().equals(payload.getUserId())) {
                    ackFail(ackRequest, "The host can't be removed");
                    return;
                }

                int seatIdx = state.clearUserFromRoom(payload.getRoomId(), payload.getUserId());
                state.getMembers(payload.getRoomId()).remove(payload.getUserId());

                memberRepository.findByRoomIdAndUserId(payload.getRoomId(), payload.getUserId())
                        .filter(m -> m.getLeftAt() == null)
                        .ifPresent(m -> { m.setLeftAt(Instant.now()); memberRepository.save(m); });

                if (seatIdx != -1) {
                    Map<String, Object> seatsMsg = new LinkedHashMap<>();
                    seatsMsg.put("roomId", payload.getRoomId());
                    seatsMsg.put("seats", state.getSeats(payload.getRoomId()));
                    server.getRoomOperations("audio-room:" + payload.getRoomId()).sendEvent("audio:seats-update", seatsMsg);
                }

                Map<String, Object> leftMsg = new LinkedHashMap<>();
                leftMsg.put("roomId", payload.getRoomId());
                leftMsg.put("userId", payload.getUserId());
                server.getRoomOperations("audio-room:" + payload.getRoomId()).sendEvent("audio:member-left", leftMsg);
                broadcastMemberCount(server, payload.getRoomId());

                // Personal notification so the kicked user's own client can show an
                // alert and navigate out of the room screen (they stay connected to
                // the socket server overall — only removed from this specific room).
                Map<String, Object> kicked = new LinkedHashMap<>();
                kicked.put("roomId", payload.getRoomId());
                server.getRoomOperations("user:" + payload.getUserId()).sendEvent("audio:kicked", kicked);

                ackOk(ackRequest);
            } catch (Exception e) {
                log.error("audio:kick-member error", e);
                ackFail(ackRequest, "Failed to remove from room");
            }
        };
    }

    private DataListener<ToggleMutePayload> onToggleMute() {
        return (client, payload, ack) -> {
            try {
                User user = userResolver.currentUser(client);
                if (user == null) return;
                boolean isMuted = Boolean.TRUE.equals(payload.getIsMuted());

                memberRepository.findByRoomIdAndUserId(payload.getRoomId(), user.getId())
                        .filter(m -> m.getLeftAt() == null)
                        .ifPresent(m -> { m.setMuted(isMuted); memberRepository.save(m); });

                Seat[] seats = state.getSeats(payload.getRoomId());
                int idx = state.findSeatIndexByUser(payload.getRoomId(), user.getId());
                if (idx != -1) seats[idx].isMuted = isMuted;

                Map<String, Object> changed = new LinkedHashMap<>();
                changed.put("roomId", payload.getRoomId());
                changed.put("userId", user.getId());
                changed.put("isMuted", isMuted);
                server.getRoomOperations("audio-room:" + payload.getRoomId()).sendEvent("audio:member-mute-changed", changed);

                if (idx != -1) {
                    Map<String, Object> seatsMsg = new LinkedHashMap<>();
                    seatsMsg.put("roomId", payload.getRoomId());
                    seatsMsg.put("seats", seats);
                    server.getRoomOperations("audio-room:" + payload.getRoomId()).sendEvent("audio:seats-update", seatsMsg);
                }
            } catch (Exception e) {
                log.error("audio:toggle-mute error", e);
            }
        };
    }

    private DataListener<SpeakingPayload> onSpeaking() {
        return (client, payload, ack) -> {
            User user = userResolver.currentUser(client);
            if (user == null) return;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("roomId", payload.getRoomId());
            data.put("userId", user.getId());
            data.put("isSpeaking", Boolean.TRUE.equals(payload.getIsSpeaking()));
            server.getRoomOperations("audio-room:" + payload.getRoomId()).sendEvent("audio:speaking", client, data);
        };
    }

    private DataListener<TextPayload> onSendChat() {
        return (client, payload, ackRequest) -> {
            User user = userResolver.currentUser(client);
            if (user == null) { ackFail(ackRequest, "Not authenticated"); return; }
            if (payload.getText() == null || payload.getText().isBlank()) {
                ackFail(ackRequest, "text is required");
                return;
            }
            String text = payload.getText().trim();
            if (text.length() > 300) text = text.substring(0, 300);

            Map<String, Object> chatMessage = new LinkedHashMap<>();
            chatMessage.put("id", System.currentTimeMillis() + "-" + user.getId());
            chatMessage.put("roomId", payload.getRoomId());
            chatMessage.put("text", text);
            chatMessage.put("user", DtoMapper.userBrief(user));
            chatMessage.put("createdAt", Instant.now().toString());

            server.getRoomOperations("audio-room:" + payload.getRoomId()).sendEvent("audio:new-chat", chatMessage);

            if (ackRequest.isAckRequested()) {
                Map<String, Object> res = new LinkedHashMap<>();
                res.put("success", true);
                res.put("chatMessage", chatMessage);
                ackRequest.sendAckData(res);
            }
        };
    }

    private DisconnectListener disconnectCleanup() {
        return client -> {
            User user = userResolver.currentUser(client);
            if (user == null) return;
            for (Map.Entry<String, Seat[]> entry : state.allSeats().entrySet()) {
                String roomId = entry.getKey();
                Seat[] seats = entry.getValue();
                int idx = state.findSeatIndexByUser(roomId, user.getId());
                if (idx != -1) {
                    seats[idx] = null;
                    Map<String, Object> seatsMsg = new LinkedHashMap<>();
                    seatsMsg.put("roomId", roomId);
                    seatsMsg.put("seats", seats);
                    server.getRoomOperations("audio-room:" + roomId).sendEvent("audio:seats-update", seatsMsg);
                }
            }
            for (Map.Entry<String, java.util.Set<String>> entry : state.allMembers().entrySet()) {
                String roomId = entry.getKey();
                if (entry.getValue().remove(user.getId())) {
                    Map<String, Object> left = new LinkedHashMap<>();
                    left.put("roomId", roomId);
                    left.put("userId", user.getId());
                    server.getRoomOperations("audio-room:" + roomId).sendEvent("audio:member-left", left);
                    broadcastMemberCount(server, roomId);
                }
            }
        };
    }

    @Transactional
    void upsertMember(AudioRoom room, User user, MemberRole role, boolean muted) {
        AudioRoomMember member = memberRepository.findByRoomIdAndUserId(room.getId(), user.getId()).orElse(null);
        if (member == null) {
            member = new AudioRoomMember();
            member.setRoom(room);
            member.setUser(user);
            member.setMuted(muted);
        }
        member.setLeftAt(null);
        member.setRole(role);
        memberRepository.save(member);
    }

    private static void ackFail(AckRequest ackRequest, String message) {
        if (ackRequest.isAckRequested()) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("success", false);
            res.put("message", message);
            ackRequest.sendAckData(res);
        }
    }

    private static void ackOk(AckRequest ackRequest) {
        if (ackRequest.isAckRequested()) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("success", true);
            ackRequest.sendAckData(res);
        }
    }
}