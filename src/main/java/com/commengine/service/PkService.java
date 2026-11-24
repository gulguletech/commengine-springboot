package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.DtoMapper;
import com.commengine.entity.*;
import com.commengine.repository.*;
import com.commengine.socket.AudioRoomLiveState;
import com.corundumstudio.socketio.SocketIOServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PK battles in audio rooms.
 *
 * - The host or a room admin picks 2 players (1V1) or 3 to 8 (MULTI) from the people in the room and a time.
 * - A player's score is the coin value of the gifts they RECEIVE in this room while the battle runs
 *   (gifts to yourself don't count).
 * - When the time is up (PkSettler checks every second) the battle ends: highest score wins; a tie at the top, or
 *   nobody getting a gift, is a draw. "Turn off" cancels it and nobody wins.
 * - Socket events to the room channel: pk:started, pk:update (scores), pk:ended (with the result).
 * - Also the PkIntegration for the invite feature: who is in a running PK, who is a room admin.
 */
@Service
public class PkService implements PkIntegration {

    private static final Logger log = LoggerFactory.getLogger(PkService.class);
    private static final Set<Integer> MINUTES = Set.of(3, 5, 10, 15, 20, 30, 60);

    private final PkBattleRepository battles;
    private final PkParticipantRepository participants;
    private final AudioRoomRepository rooms;
    private final AudioRoomAdminRepository admins;
    private final UserRepository users;
    private final AudioRoomLiveState state;
    private final SocketIOServer server;

    public PkService(PkBattleRepository battles, PkParticipantRepository participants, AudioRoomRepository rooms,
                     AudioRoomAdminRepository admins, UserRepository users, AudioRoomLiveState state, SocketIOServer server) {
        this.battles = battles;
        this.participants = participants;
        this.rooms = rooms;
        this.admins = admins;
        this.users = users;
        this.state = state;
        this.server = server;
    }

    // ------------------------------------------------------------------ PkIntegration (used by the invite feature)

    @Override
    @Transactional(readOnly = true)
    public boolean isInRunningPk(String userId) {
        return participants.inRunningBattle(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRoomAdmin(String roomId, String userId) {
        return admins.existsByRoomIdAndUserId(roomId, userId);
    }

    // ------------------------------------------------------------------ helpers

    private boolean isManager(AudioRoom room, String userId) {
        return (room.getHost() != null && room.getHost().getId().equals(userId)) || admins.existsByRoomIdAndUserId(room.getId(), userId);
    }

    private AudioRoom activeRoom(String roomId) {
        AudioRoom room = rooms.findById(roomId).orElseThrow(() -> ApiException.notFound("Room not found"));
        if (room.getStatus() != RoomStatus.ACTIVE) throw ApiException.notFound("Room not found or ended");
        return room;
    }

    private static String channel(String roomId) {
        return "audio-room:" + roomId;
    }

    /** The battle as the app reads it. Ranks: 1 = leading; equal scores share a rank. */
    private Map<String, Object> dto(PkBattle b) {
        List<PkParticipant> parts = participants.findByBattleIdOrderByPositionAsc(b.getId());
        Map<String, User> um = new HashMap<>();
        users.findAllById(parts.stream().map(PkParticipant::getUserId).toList()).forEach(u -> um.put(u.getId(), u));

        List<Map<String, Object>> list = new ArrayList<>();
        for (PkParticipant p : parts) {
            long higher = parts.stream().filter(o -> o.getScore() > p.getScore()).count();
            User u = um.get(p.getUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getUserId());
            m.put("name", u == null ? "Player" : u.getName());
            m.put("avatarUrl", u == null ? null : u.getAvatarUrl());
            m.put("frameUrl", u == null ? null : DtoMapper.userBrief(u).get("frameUrl"));
            m.put("score", p.getScore());
            m.put("rank", higher + 1);
            list.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", b.getId());
        out.put("roomId", b.getRoomId());
        out.put("mode", b.getMode());
        out.put("status", b.getStatus());
        out.put("durationSeconds", b.getDurationSeconds());
        out.put("endsAt", b.getEndsAt().toEpochMilli());
        out.put("startedAt", b.getStartedAt().toEpochMilli());
        out.put("serverNow", System.currentTimeMillis());
        out.put("participants", list);
        return out;
    }

    private Optional<PkBattle> running(String roomId) {
        return battles.findFirstByRoomIdAndStatus(roomId, PkBattle.RUNNING);
    }

    // ------------------------------------------------------------------ app calls

    /** The battle running in this room right now, or null. */
    @Transactional(readOnly = true)
    public Map<String, Object> current(String roomId) {
        return running(roomId).filter(b -> b.getEndsAt().isAfter(Instant.now())).map(this::dto).orElse(null);
    }

    @Transactional
    public Map<String, Object> start(User caller, String roomId, String modeRaw, List<String> userIds, int minutes) {
        AudioRoom room = activeRoom(roomId);
        if (!isManager(room, caller.getId())) throw ApiException.forbidden("Only the host or a room admin can start a PK");

        String mode = modeRaw == null ? "" : modeRaw.trim().toUpperCase();
        if (!mode.equals("1V1") && !mode.equals("MULTI")) throw ApiException.badRequest("Mode must be 1V1 or MULTI");
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(userIds == null ? List.<String>of() : userIds));
        if (mode.equals("1V1") && ids.size() != 2) throw ApiException.badRequest("Pick exactly 2 players for a 1V1 PK");
        if (mode.equals("MULTI") && (ids.size() < 3 || ids.size() > 8)) throw ApiException.badRequest("Pick 3 to 8 players for a multiplayer PK");
        if (!MINUTES.contains(minutes)) throw ApiException.badRequest("Choose 3, 5, 10, 15, 20, 30 or 60 minutes");

        // a battle that ran out of time but isn't settled yet should not block the next one
        Optional<PkBattle> existing = running(roomId);
        if (existing.isPresent()) {
            if (existing.get().getEndsAt().isAfter(Instant.now())) throw ApiException.conflict("A PK is already running in this room");
            finish(existing.get(), false);
        }

        Set<String> inside = state.peekMembers(roomId);
        String hostId = room.getHost().getId();
        Map<String, User> picked = new LinkedHashMap<>();
        for (String id : ids) {
            User u = users.findById(id).orElseThrow(() -> ApiException.notFound("Player not found"));
            if (!id.equals(hostId) && (inside == null || !inside.contains(id))) throw ApiException.badRequest(u.getName() + " is not in the room");
            if (participants.inRunningBattle(id)) throw ApiException.conflict(u.getName() + " is already in a PK battle");
            picked.put(id, u);
        }

        PkBattle b = new PkBattle();
        b.setRoomId(roomId);
        b.setMode(mode);
        b.setDurationSeconds(minutes * 60);
        b.setStartedAt(Instant.now());
        b.setEndsAt(b.getStartedAt().plusSeconds(b.getDurationSeconds()));
        b.setStartedBy(caller.getId());
        battles.save(b);
        int pos = 0;
        for (String id : picked.keySet()) {
            PkParticipant p = new PkParticipant();
            p.setBattleId(b.getId());
            p.setUserId(id);
            p.setPosition(pos++);
            participants.save(p);
        }

        Map<String, Object> out = dto(b);
        server.getRoomOperations(channel(roomId)).sendEvent("pk:started", out);
        return out;
    }

    /** "Turn off": cancels the running battle, nobody wins. */
    @Transactional
    public void stop(User caller, String roomId) {
        AudioRoom room = rooms.findById(roomId).orElseThrow(() -> ApiException.notFound("Room not found"));
        if (!isManager(room, caller.getId())) throw ApiException.forbidden("Only the host or a room admin can stop a PK");
        PkBattle b = running(roomId).orElseThrow(() -> ApiException.notFound("No PK is running"));
        finish(b, true);
    }

    // ------------------------------------------------------------------ scoring (called from GiftService)

    /**
     * A gift landed on `receiverId` in this audio room. If they are playing in a running battle here, their score goes up
     * and everyone gets pk:update. Never throws and never touches the gift's own transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void addScore(String roomId, String senderId, String receiverId, long coins) {
        try {
            if (coins <= 0 || receiverId.equals(senderId)) return; // gifts to yourself don't count
            PkBattle b = running(roomId).filter(x -> x.getEndsAt().isAfter(Instant.now())).orElse(null);
            if (b == null) return;
            if (participants.addScore(b.getId(), receiverId, coins) == 0) return;
            server.getRoomOperations(channel(roomId)).sendEvent("pk:update", dto(b));
        } catch (Exception e) {
            log.warn("PK score update failed in room {}: {}", roomId, e.toString());
        }
    }

    // ------------------------------------------------------------------ ending

    /** Ids of battles whose time is up (or whose room has closed). */
    @Transactional(readOnly = true)
    public List<String> dueIds() {
        Set<String> out = new LinkedHashSet<>();
        battles.findByStatusAndEndsAtLessThanEqual(PkBattle.RUNNING, Instant.now()).forEach(b -> out.add(b.getId()));
        for (PkBattle b : battles.findByStatus(PkBattle.RUNNING)) {
            AudioRoom room = rooms.findById(b.getRoomId()).orElse(null);
            if (room == null || room.getStatus() != RoomStatus.ACTIVE) out.add(b.getId());
        }
        return new ArrayList<>(out);
    }

    @Transactional
    public void settle(String battleId) {
        PkBattle b = battles.findById(battleId).orElse(null);
        if (b == null || !PkBattle.RUNNING.equals(b.getStatus())) return;
        AudioRoom room = rooms.findById(b.getRoomId()).orElse(null);
        boolean roomGone = room == null || room.getStatus() != RoomStatus.ACTIVE;
        boolean timeUp = !b.getEndsAt().isAfter(Instant.now());
        if (roomGone && !timeUp) finish(b, true); // the room closed mid-battle: cancel
        else finish(b, false);
    }

    private void finish(PkBattle b, boolean cancelled) {
        b.setStatus(cancelled ? PkBattle.CANCELLED : PkBattle.ENDED);
        b.setEndedAt(Instant.now());
        battles.save(b);

        Map<String, Object> ended = dto(b);
        @SuppressWarnings("unchecked") List<Map<String, Object>> parts = (List<Map<String, Object>>) ended.get("participants");

        List<String> winnerIds = new ArrayList<>();
        boolean draw = false;
        if (!cancelled && !parts.isEmpty()) {
            long top = parts.stream().mapToLong(p -> (Long) p.get("score")).max().orElse(0);
            winnerIds = parts.stream().filter(p -> (Long) p.get("score") == top).map(p -> (String) p.get("id")).collect(Collectors.toList());
            draw = winnerIds.size() > 1; // tie at the top, or nobody got a gift
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cancelled", cancelled);
        result.put("draw", draw);
        result.put("winnerIds", winnerIds);
        ended.put("result", result);
        server.getRoomOperations(channel(b.getRoomId())).sendEvent("pk:ended", ended);
    }
}
