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
    private final RoomPresenceService presence;
    private final StoreService storeService;
    private final CloudinaryService cloudinary;

    public AudioRoomService(AudioRoomRepository roomRepository, AudioRoomMemberRepository memberRepository,
                            AudioJoinRequestRepository joinRequestRepository, AgoraService agoraService,
                            SocketIOServer socketIOServer, AudioRoomLiveState liveState,
                            RoomPresenceService presence, StoreService storeService, CloudinaryService cloudinary) {
        this.storeService = storeService;
        this.cloudinary = cloudinary;
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.agoraService = agoraService;
        this.socketIOServer = socketIOServer;
        this.liveState = liveState;
        this.presence = presence;
    }

    @Transactional
    public Map<String, Object> createRoom(User host, CreateAudioRoomRequest req) {
        // A host owns ONE persistent room: after the first time they just open it again (it goes online when they join).
        AudioRoom existing = roomRepository.findFirstByHostIdAndStatusOrderByCreatedAtDesc(host.getId(), RoomStatus.ACTIVE).orElse(null);
        if (existing != null) {
            Map<String, Object> errors = new LinkedHashMap<>();
            errors.put("code", "ROOM_EXISTS");
            errors.put("roomId", existing.getId());
            throw new ApiException("You already have a room. Open it from My Rooms.", HttpStatus.CONFLICT, errors);
        }
        AudioRoom room = new AudioRoom();
        room.setOnline(true);
        room.setTitle(req.getTitle());
        room.setType(req.getType());
        room.setCoverUrl(req.getCoverUrl());
        room.setCategory(com.commengine.util.Catalog.category(req.getCategory()));
        room.setLanguage(host.getLanguage());
        room.setLanguages(com.commengine.util.Catalog.joinLanguages(host.languageList()));
        storeService.applyRoomBackground(room, host, req.getBackgroundItemId()); // free or owned, else 403
        room.setPasscode(req.getType() == RoomType.PRIVATE ? req.getPasscode() : null);
        int startSeats = validSeatCount(req.getSeatCount(), AudioRoomLiveState.DEFAULT_SEAT_COUNT);
        requireVipForSeats(host, startSeats);
        room.setSeatCount(startSeats);
        room.setChannelName("audio-" + UUID.randomUUID());
        room.setHost(host);
        roomRepository.save(room);
        liveState.ensureSeatCount(room.getId(), room.getSeatCount());

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
        // one room at a time: starting a room exits any other room the host was in
        presence.leaveOthers(host.getId(), "audio", room.getId());
        return data;
    }

    /** Mic layouts: 4 and 8 seats are free, every other layout is for VIP members only. */
    private static void requireVipForSeats(User user, int seats) {
        if (AudioRoomLiveState.isFreeSeatCount(seats) || user.isVip()) return;
        throw new ApiException("The " + seats + "-seat mic layout is for VIP members", HttpStatus.FORBIDDEN, Map.of("code", "VIP_REQUIRED"));
    }

    /** 4, 8, 10, 12, 15 or 20. `fallback` is used when nothing was sent. */
    private static int validSeatCount(Integer asked, int fallback) {
        if (asked == null) return fallback;
        if (!AudioRoomLiveState.isAllowedSeatCount(asked)) {
            throw ApiException.badRequest("A room can have 4, 8, 10, 12, 15 or 20 seats");
        }
        return asked;
    }

    public Map<String, Object> listRooms() {
        List<AudioRoom> rooms = roomRepository.findVisibleRooms(RoomStatus.ACTIVE);
        List<Map<String, Object>> shaped = rooms.stream()
                .map(r -> DtoMapper.audioRoomWithHostAndCount(r, memberRepository.findByRoomIdAndLeftAtIsNull(r.getId()).size()))
                .toList();
        return Map.of("rooms", shaped);
    }

    /** The caller's own persistent room (online or offline), or {"room": null} when they have never created one. */
    public Map<String, Object> getMyRoom(User host) {
        AudioRoom room = roomRepository.findFirstByHostIdAndStatusOrderByCreatedAtDesc(host.getId(), RoomStatus.ACTIVE).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("room", room == null ? null
                : DtoMapper.audioRoomWithHostAndCount(room, memberRepository.findByRoomIdAndLeftAtIsNull(room.getId()).size()));
        return data;
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
        Map<String, Object> result = joinRoomInternal(user, id, passcode);
        // one room at a time: once the join really succeeded (not just a pending request),
        // drop the user from any other audio/video room they were in.
        if (result.containsKey("rtc")) {
            presence.leaveOthers(user.getId(), "audio", id);
        }
        return result;
    }

    private Map<String, Object> joinRoomInternal(User user, String id, String passcode) {
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
            // the host coming back brings an offline room back online
            if (!room.isOnline()) {
                room.setOnline(true);
                roomRepository.save(room);
            }
            return joinedResponse(room, user);
        }

        // an offline room (host gone, nobody inside) can't be joined by anyone else
        if (!room.isOnline()) {
            throw ApiException.notFound("This room is offline right now");
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
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            room.setCategory(com.commengine.util.Catalog.category(req.getCategory()));
        }
        if (req.isBackgroundProvided()) {
            storeService.applyRoomBackground(room, host, req.getBackgroundItemId());
        }

        // seat count: resize the live seats; anyone sitting on a seat that disappears becomes a listener
        boolean seatsChanged = false;
        List<com.commengine.socket.Seat> displaced = List.of();
        if (req.getSeatCount() != null) {
            int next = validSeatCount(req.getSeatCount(), room.getSeatCount());
            liveState.ensureSeatCount(room.getId(), room.getSeatCount()); // after a restart the live state may not know yet
            if (next != room.getSeatCount()) {
                requireVipForSeats(host, next); // keeping the current layout is always fine, e.g. after VIP ran out
                displaced = liveState.resize(room.getId(), next);
                room.setSeatCount(next);
                seatsChanged = true;
            }
        }
        roomRepository.save(room);

        if (seatsChanged) {
            for (com.commengine.socket.Seat seat : displaced) {
                memberRepository.findByRoomIdAndUserId(room.getId(), seat.userId)
                        .filter(m -> m.getLeftAt() == null)
                        .ifPresent(m -> { m.setRole(MemberRole.LISTENER); m.setMuted(true); memberRepository.save(m); });
                socketIOServer.getRoomOperations("user:" + seat.userId)
                        .sendEvent("audio:removed-from-seat", Map.of("roomId", room.getId()));
            }
            Map<String, Object> seatsMsg = new LinkedHashMap<>();
            seatsMsg.put("roomId", room.getId());
            seatsMsg.put("seats", liveState.getSeats(room.getId()));
            seatsMsg.put("blockedSeats", liveState.getBlocked(room.getId()));
            socketIOServer.getRoomOperations("audio-room:" + room.getId()).sendEvent("audio:seats-update", seatsMsg);
        }

        if (req.isBackgroundProvided()) {
            // everyone already in the room switches to the new background immediately
            Map<String, Object> updated = new LinkedHashMap<>();
            updated.put("roomId", room.getId());
            updated.put("background", DtoMapper.roomBackground(room));
            socketIOServer.getRoomOperations("audio-room:" + room.getId()).sendEvent("audio:room-updated", updated);
        }

        Map<String, Object> notify = new LinkedHashMap<>();
        notify.put("roomId", room.getId());
        notify.put("type", room.getType());
        notify.put("category", room.getCategory());
        notify.put("hasPasscode", room.getPasscode() != null);
        notify.put("seatCount", room.getSeatCount());
        socketIOServer.getRoomOperations("audio-room:" + room.getId()).sendEvent("audio:settings-updated", notify);

        return Map.of("room", DtoMapper.audioRoomBase(room));
    }

    private static final String COVER_FOLDER = "room-covers";
    private static final long MAX_COVER_BYTES = 5L * 1024 * 1024;

    /**
     * The host changes the room's picture. The new image is uploaded and saved first; only then is the previous
     * one deleted from Cloudinary, so a failed upload never leaves the room without a picture, and replaced pictures
     * don't pile up and use storage. Only pictures this method uploaded (folder room-covers) are ever deleted.
     */
    @Transactional
    public Map<String, Object> updateCover(User host, String id, org.springframework.web.multipart.MultipartFile file) {
        AudioRoom room = findRoomOrThrow(id);
        if (!room.getHost().getId().equals(host.getId())) {
            throw ApiException.forbidden("Only the host can change the room picture");
        }
        if (file == null || file.isEmpty()) throw ApiException.unprocessable("No picture uploaded");
        if (file.getSize() > MAX_COVER_BYTES) throw ApiException.unprocessable("Picture must be under 5 MB");

        String oldPublicId = cloudinary.publicIdInFolder(room.getCoverUrl(), COVER_FOLDER);
        CloudinaryService.MediaResult uploaded = cloudinary.uploadMedia(file, COVER_FOLDER, "image");
        room.setCoverUrl(uploaded.url());
        roomRepository.save(room);
        if (oldPublicId != null && !oldPublicId.equals(uploaded.publicId())) cloudinary.delete(oldPublicId, "image");

        // everyone in the room sees the new picture straight away
        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put("roomId", room.getId());
        updated.put("coverUrl", room.getCoverUrl());
        socketIOServer.getRoomOperations("audio-room:" + room.getId()).sendEvent("audio:room-updated", updated);

        return Map.of("room", DtoMapper.audioRoomBase(room));
    }

    @Transactional
    public void leaveRoom(User user, String id) {
        presence.leaveAudioRoom(user.getId(), id);
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

    /**
     * "End room for everyone", but the room is kept: everybody inside is removed and the room goes offline
     * until its host opens it again. (endRoom above deletes it for good.)
     */
    @Transactional
    public void closeRoom(User host, String id) {
        AudioRoom room = findRoomOrThrow(id);
        if (!room.getHost().getId().equals(host.getId())) {
            throw ApiException.forbidden("Only the host can close the room");
        }
        socketIOServer.getRoomOperations("audio-room:" + id).sendEvent("audio:room-ended", Map.of("roomId", id));
        java.util.Set<String> inside = liveState.peekMembers(id);
        if (inside != null) {
            for (String uid : new java.util.ArrayList<>(inside)) presence.leaveAudioRoom(uid, id);
        }
        presence.leaveAudioRoom(host.getId(), id);
        presence.setAudioOnline(id, false);
    }

    /** Admin panel: end any room regardless of who hosts it (same clean-up as the host ending it). */
    @Transactional
    public void adminEndRoom(String id) {
        AudioRoom room = findRoomOrThrow(id);
        socketIOServer.getRoomOperations("audio-room:" + room.getId()).sendEvent("audio:room-ended", Map.of("roomId", room.getId()));
        joinRequestRepository.deleteAllByRoomId(id);
        memberRepository.deleteAllByRoomId(id);
        roomRepository.delete(room);
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