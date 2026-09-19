package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.AudioRoomSettingsRequest;
import com.commengine.dto.CreateAudioRoomRequest;
import com.commengine.dto.DtoMapper;
import com.commengine.entity.*;
import com.commengine.repository.AudioJoinRequestRepository;
import com.commengine.repository.AudioRoomMemberRepository;
import com.commengine.repository.AudioRoomRepository;
import com.commengine.socket.AudioRoomLiveState;
import com.commengine.util.AgoraUidUtil;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
// Class-level readOnly transaction: with spring.jpa.open-in-view=false, the Hibernate
// session closes as soon as a repository call returns, so any method here that maps a
// LAZY relation (host, sender, user, etc.) via DtoMapper AFTER the repository call would
// otherwise throw LazyInitializationException. Write methods below carry their own
// non-read-only @Transactional, which correctly overrides this class-level default.
@Transactional(readOnly = true)
public class AudioRoomService {

    private final AudioRoomRepository roomRepository;
    private final AudioRoomMemberRepository memberRepository;
    private final AudioJoinRequestRepository joinRequestRepository;
    private final AgoraService agoraService;
    private final SocketIOServer socketIOServer;
    private final AudioRoomLiveState liveState;

    public AudioRoomService(AudioRoomRepository roomRepository, AudioRoomMemberRepository memberRepository,
                            AudioJoinRequestRepository joinRequestRepository, AgoraService agoraService,
                            SocketIOServer socketIOServer, AudioRoomLiveState liveState) {
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.agoraService = agoraService;
        this.socketIOServer = socketIOServer;
        this.liveState = liveState;
    }

    @Transactional
    public Map<String, Object> createRoom(User host, CreateAudioRoomRequest req) {
        AudioRoom room = new AudioRoom();
        room.setTitle(req.getTitle());
        room.setType(req.getType());
        room.setCoverUrl(req.getCoverUrl());
        room.setPasscode(req.getType() == RoomType.PRIVATE ? req.getPasscode() : null);
        room.setChannelName("audio-" + UUID.randomUUID());
        room.setHost(host);
        roomRepository.save(room);

        AudioRoomMember hostMember = new AudioRoomMember();
        hostMember.setRoom(room);
        hostMember.setUser(host);
        hostMember.setRole(MemberRole.HOST);
        hostMember.setMuted(false);
        memberRepository.save(hostMember);

        long uidNum = AgoraUidUtil.deriveAgoraUid(host.getId());
        Map<String, Object> rtc = agoraService.generateRtcToken(room.getChannelName(), uidNum, "publisher");

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> roomJson = DtoMapper.audioRoomBase(room);
        roomJson.put("members", List.of(bareMember(hostMember)));
        data.put("room", roomJson);
        data.put("rtc", rtc);
        return data;
    }

    public Map<String, Object> listRooms(User user) {
        List<AudioRoom> rooms = roomRepository.findVisibleRooms(RoomStatus.ACTIVE, user.getId());
        List<Map<String, Object>> shaped = rooms.stream()
                .map(r -> DtoMapper.audioRoomWithHostAndCount(r, memberRepository.findByRoomIdAndLeftAtIsNull(r.getId()).size()))
                .toList();
        return Map.of("rooms", shaped);
    }

    public Map<String, Object> getRoom(String id) {
        AudioRoom room = findRoomOrThrow(id);
        List<AudioRoomMember> members = memberRepository.findByRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(id);
        return Map.of("room", DtoMapper.audioRoomWithHostAndMembers(room, members));
    }

    public Map<String, Object> getMembers(String id) {
        findRoomOrThrow(id);
        List<AudioRoomMember> members = memberRepository.findByRoomIdAndLeftAtIsNullOrderByJoinedAtAsc(id);
        return Map.of("members", members.stream().map(DtoMapper::audioRoomMember).toList());
    }

    @Transactional
    public Map<String, Object> joinRoom(User user, String id, String passcode) {
        // Every audio-room participant is issued a PUBLISHER-capable Agora token, not just
        // the host. Unlike a live video room, any listener here can be promoted to a
        // speaker later via audio:take-seat. That handler does not reissue a new token —
        // there is no mid-session token-renewal flow implemented. Actual permission to
        // speak is fully enforced server-side by the seat/mute state broadcast over
        // sockets, so granting broad publishing privilege in the token itself is safe.
        // It's also what makes unmuting after taking a seat actually work: a
        // subscriber-only token has no publishing-privilege bits at all, so Agora
        // silently rejects the publish attempt regardless of the app's local UI/role state.
        AudioRoom room = roomRepository.findById(id).orElse(null);
        if (room == null || room.getStatus() != RoomStatus.ACTIVE) {
            throw ApiException.notFound("Room not found or ended");
        }

        if (room.getHost().getId().equals(user.getId())) {
            return joinedResponse(room, user);
        }

        if (room.getType() == RoomType.PUBLIC) {
            upsertListenerMember(room, user);
            return joinedResponse(room, user);
        }

        AudioRoomMember existing = memberRepository.findByRoomIdAndUserId(id, user.getId()).orElse(null);
        if (existing != null && existing.getLeftAt() == null) {
            return joinedResponse(room, user);
        }

        if (room.getPasscode() != null) {
            if (passcode == null || passcode.isBlank()) {
                throw new ApiException("This room needs a passcode to join", HttpStatus.UNAUTHORIZED, Map.of("code", "PASSCODE_REQUIRED"));
            }
            if (!passcode.equals(room.getPasscode())) {
                throw new ApiException("Incorrect passcode", HttpStatus.UNAUTHORIZED, Map.of("code", "PASSCODE_INCORRECT"));
            }
            upsertListenerMember(room, user);
            return joinedResponse(room, user);
        }

        AudioJoinRequest joinRequest = joinRequestRepository.findByRoomIdAndUserId(id, user.getId()).orElse(null);
        if (joinRequest == null) {
            joinRequest = new AudioJoinRequest();
            joinRequest.setRoom(room);
            joinRequest.setUser(user);
        }
        joinRequest.setStatus(JoinRequestStatus.PENDING);
        joinRequest.setResolvedAt(null);
        joinRequestRepository.save(joinRequest);

        Map<String, Object> notify = new LinkedHashMap<>();
        notify.put("roomId", room.getId());
        notify.put("requestId", joinRequest.getId());
        notify.put("user", DtoMapper.userBrief(user));
        socketIOServer.getRoomOperations("audio-room:" + room.getId() + ":host").sendEvent("audio:join-request", notify);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "PENDING");
        data.put("joinRequest", DtoMapper.audioJoinRequest(joinRequest));
        return data;
    }

    public Map<String, Object> listJoinRequests(User host, String id) {
        AudioRoom room = findRoomOrThrow(id);
        if (!room.getHost().getId().equals(host.getId())) {
            throw ApiException.forbidden("Only the host can view join requests");
        }
        List<AudioJoinRequest> requests = joinRequestRepository.findByRoomIdAndStatus(id, JoinRequestStatus.PENDING);
        return Map.of("requests", requests.stream().map(DtoMapper::audioJoinRequest).toList());
    }

    @Transactional
    public Map<String, Object> respondJoinRequest(User host, String id, String requestId, boolean accept) {
        AudioRoom room = findRoomOrThrow(id);
        if (!room.getHost().getId().equals(host.getId())) {
            throw ApiException.forbidden("Only the host can respond to requests");
        }
        AudioJoinRequest request = joinRequestRepository.findById(requestId)
                .filter(r -> r.getRoom().getId().equals(room.getId()))
                .orElseThrow(() -> ApiException.notFound("Join request not found"));

        JoinRequestStatus status = accept ? JoinRequestStatus.ACCEPTED : JoinRequestStatus.REJECTED;
        request.setStatus(status);
        request.setResolvedAt(Instant.now());
        joinRequestRepository.save(request);

        Map<String, Object> rtc = null;
        if (accept) {
            upsertListenerMember(room, request.getUser());
            long uidNum = AgoraUidUtil.deriveAgoraUid(request.getUser().getId());
            rtc = agoraService.generateRtcToken(room.getChannelName(), uidNum, "publisher");
        }

        Map<String, Object> notify = new LinkedHashMap<>();
        notify.put("roomId", room.getId());
        notify.put("accepted", accept);
        notify.put("rtc", rtc);
        socketIOServer.getRoomOperations("user:" + request.getUser().getId()).sendEvent("audio:join-response", notify);

        return Map.of("status", status.name());
    }

    @Transactional
    public Map<String, Object> updateSettings(User host, String id, AudioRoomSettingsRequest req) {
        AudioRoom room = findRoomOrThrow(id);
        if (!room.getHost().getId().equals(host.getId())) {
            throw ApiException.forbidden("Only the host can edit room settings");
        }

        String nextPasscode;
        if (req.getType() == RoomType.PRIVATE) {
            nextPasscode = req.isPasscodeProvided() ? req.getPasscode() : room.getPasscode();
        } else {
            nextPasscode = null;
        }
        room.setType(req.getType());
        room.setPasscode(nextPasscode);
        roomRepository.save(room);

        Map<String, Object> notify = new LinkedHashMap<>();
        notify.put("roomId", room.getId());
        notify.put("type", room.getType());
        notify.put("hasPasscode", room.getPasscode() != null);
        socketIOServer.getRoomOperations("audio-room:" + room.getId()).sendEvent("audio:settings-updated", notify);

        return Map.of("room", DtoMapper.audioRoomBase(room));
    }

    @Transactional
    public void leaveRoom(User user, String id) {
        memberRepository.findByRoomIdAndUserId(id, user.getId())
                .filter(m -> m.getLeftAt() == null)
                .ifPresent(m -> { m.setLeftAt(Instant.now()); memberRepository.save(m); });

        Map<String, Object> notify = Map.of("userId", user.getId());
        socketIOServer.getRoomOperations("audio-room:" + id).sendEvent("audio:member-left", notify);
    }

    @Transactional
    public void endRoom(User host, String id) {
        AudioRoom room = findRoomOrThrow(id);
        if (!room.getHost().getId().equals(host.getId())) {
            throw ApiException.forbidden("Only the host can end the room");
        }

        // Notify everyone still connected BEFORE the data disappears underneath them.
        socketIOServer.getRoomOperations("audio-room:" + room.getId()).sendEvent("audio:room-ended", Map.of("roomId", room.getId()));

        // A room that has ended has no further use — hard-delete it and everything tied
        // to it (members, join requests) instead of soft-marking it ENDED and leaving
        // the rows around forever with nothing in the app ever reading them again.
        // Children are deleted first to satisfy the foreign key constraints.
        joinRequestRepository.deleteAllByRoomId(id);
        memberRepository.deleteAllByRoomId(id);
        roomRepository.delete(room);

        // Drop the in-memory seat/presence state too, or it would otherwise sit around
        // in the JVM's memory for the lifetime of the process for a room that no longer exists.
        liveState.removeRoom(id);
    }

    // ---- helpers ----

    private AudioRoom findRoomOrThrow(String id) {
        return roomRepository.findById(id).orElseThrow(() -> ApiException.notFound("Room not found"));
    }

    private void upsertListenerMember(AudioRoom room, User user) {
        AudioRoomMember member = memberRepository.findByRoomIdAndUserId(room.getId(), user.getId()).orElse(null);
        if (member == null) {
            member = new AudioRoomMember();
            member.setRoom(room);
            member.setUser(user);
            member.setRole(MemberRole.LISTENER);
            member.setMuted(true);
        } else {
            member.setLeftAt(null);
        }
        memberRepository.save(member);
    }

    // Always issues a publisher-capable token — see the comment on joinRoom() for why
    // every audio-room participant needs one, not just the host.
    private Map<String, Object> joinedResponse(AudioRoom room, User user) {
        long uidNum = AgoraUidUtil.deriveAgoraUid(user.getId());
        Map<String, Object> rtc = agoraService.generateRtcToken(room.getChannelName(), uidNum, "publisher");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "JOINED");
        data.put("room", DtoMapper.audioRoomBase(room));
        data.put("rtc", rtc);
        return data;
    }

    private Map<String, Object> bareMember(AudioRoomMember m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("role", m.getRole());
        map.put("isMuted", m.isMuted());
        map.put("joinedAt", m.getJoinedAt());
        map.put("leftAt", m.getLeftAt());
        map.put("roomId", m.getRoom().getId());
        map.put("userId", m.getUser().getId());
        return map;
    }
}