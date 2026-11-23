package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.DtoMapper;
import com.commengine.entity.AudioRoom;
import com.commengine.entity.User;
import com.commengine.repository.AudioRoomRepository;
import com.commengine.repository.UserRepository;
import com.commengine.socket.AudioRoomLiveState;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * "Invite from the app" for PK: the host / room admin picks anyone who is online in the app and they get an
 * `audio:battle-invite` event on their own socket channel (user:{id}). Accepting just takes them into the room;
 * once they are in the room the host can pick them for the battle like anyone else.
 *
 * No cooldown. The only people who can't be invited: someone already in this room, someone in a running PK,
 * a banned user, and yourself.
 */
@Service
public class BattleInviteService {

    private static final int FETCH = 100;
    private static final int SHOW = 40;

    private final AudioRoomRepository rooms;
    private final UserRepository users;
    private final AudioRoomLiveState state;
    private final SocketIOServer server;
    private final ObjectProvider<PkIntegration> pk;

    public BattleInviteService(AudioRoomRepository rooms, UserRepository users, AudioRoomLiveState state,
                               SocketIOServer server, ObjectProvider<PkIntegration> pk) {
        this.rooms = rooms;
        this.users = users;
        this.state = state;
        this.server = server;
        this.pk = pk;
    }

    private boolean inPk(String userId) {
        PkIntegration p = pk.getIfAvailable();
        return p != null && p.isInRunningPk(userId);
    }

    private boolean isManager(AudioRoom room, User caller) {
        if (room.getHost() != null && room.getHost().getId().equals(caller.getId())) return true;
        PkIntegration p = pk.getIfAvailable();
        return p != null && p.isRoomAdmin(room.getId(), caller.getId());
    }

    private AudioRoom requireManagedRoom(String roomId, User caller) {
        AudioRoom room = rooms.findById(roomId).orElseThrow(() -> ApiException.notFound("Room not found"));
        if (!isManager(room, caller)) throw ApiException.forbidden("Only the host or a room admin can invite people");
        return room;
    }

    private boolean inRoom(String roomId, String userId) {
        Set<String> members = state.peekMembers(roomId);
        return members != null && members.contains(userId);
    }

    /** Online users who can be invited right now. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> candidates(String roomId, User caller, String q) {
        AudioRoom room = requireManagedRoom(roomId, caller);
        String query = q == null ? "" : q.trim();
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : users.findOnlineForInvite(query, PageRequest.of(0, FETCH))) {
            if (u.getId().equals(caller.getId())) continue;
            if (room.getHost() != null && u.getId().equals(room.getHost().getId())) continue;
            if (inRoom(roomId, u.getId())) continue;
            if (inPk(u.getId())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("publicId", u.getPublicId());
            m.put("name", u.getName());
            m.put("avatarUrl", u.getAvatarUrl());
            out.add(m);
            if (out.size() >= SHOW) break;
        }
        return out;
    }

    /** Sends the invite. Throws a clear message if the person can't be invited. */
    @Transactional(readOnly = true)
    public void invite(String roomId, User caller, String userId) {
        AudioRoom room = requireManagedRoom(roomId, caller);
        if (userId.equals(caller.getId())) throw ApiException.badRequest("You can't invite yourself");
        User target = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (target.isBanned()) throw ApiException.badRequest("This user can't be invited");
        if (inRoom(roomId, target.getId())) throw ApiException.conflict(target.getName() + " is already in this room");
        if (inPk(target.getId())) throw ApiException.conflict(target.getName() + " is in a PK battle right now");

        var channel = server.getRoomOperations("user:" + target.getId());
        if (channel.getClients().isEmpty()) throw ApiException.conflict(target.getName() + " just went offline");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", room.getId());
        payload.put("roomTitle", room.getTitle());
        payload.put("host", DtoMapper.userBrief(caller));
        payload.put("sentAt", System.currentTimeMillis());
        channel.sendEvent("audio:battle-invite", payload);
    }
}
