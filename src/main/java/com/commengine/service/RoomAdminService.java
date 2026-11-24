package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.entity.AudioRoom;
import com.commengine.entity.AudioRoomAdmin;
import com.commengine.entity.User;
import com.commengine.repository.AudioRoomAdminRepository;
import com.commengine.repository.AudioRoomRepository;
import com.commengine.repository.UserRepository;
import com.commengine.socket.AudioRoomLiveState;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Room admins: people the host trusts with host powers (like starting a PK). Only the host adds or removes them. */
@Service
public class RoomAdminService {

    private final AudioRoomAdminRepository admins;
    private final AudioRoomRepository rooms;
    private final UserRepository users;
    private final AudioRoomLiveState state;
    private final SocketIOServer server;

    public RoomAdminService(AudioRoomAdminRepository admins, AudioRoomRepository rooms, UserRepository users,
                            AudioRoomLiveState state, SocketIOServer server) {
        this.admins = admins;
        this.rooms = rooms;
        this.users = users;
        this.state = state;
        this.server = server;
    }

    private AudioRoom room(String roomId) {
        return rooms.findById(roomId).orElseThrow(() -> ApiException.notFound("Room not found"));
    }

    private List<String> ids(String roomId) {
        return admins.findByRoomId(roomId).stream().map(AudioRoomAdmin::getUserId).toList();
    }

    private void requireHost(AudioRoom room, User caller) {
        if (room.getHost() == null || !room.getHost().getId().equals(caller.getId())) throw ApiException.forbidden("Only the host can do this");
    }

    private void broadcast(String roomId, User changed, boolean granted) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roomId", roomId);
        m.put("adminIds", ids(roomId));
        m.put("changedUserId", changed == null ? null : changed.getId());
        m.put("changedUserName", changed == null ? null : changed.getName());
        m.put("granted", granted);
        server.getRoomOperations("audio-room:" + roomId).sendEvent("audio:admins-updated", m);
    }

    @Transactional(readOnly = true)
    public List<String> list(String roomId) {
        room(roomId);
        return ids(roomId);
    }

    @Transactional
    public List<String> add(User host, String roomId, String userId) {
        AudioRoom room = room(roomId);
        requireHost(room, host);
        User target = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (target.getId().equals(host.getId())) throw ApiException.badRequest("You are already the host");
        Set<String> inside = state.peekMembers(roomId);
        if (inside == null || !inside.contains(target.getId())) throw ApiException.badRequest(target.getName() + " must be in the room");
        if (!admins.existsByRoomIdAndUserId(roomId, target.getId())) {
            AudioRoomAdmin a = new AudioRoomAdmin();
            a.setRoomId(roomId);
            a.setUserId(target.getId());
            admins.save(a);
            broadcast(roomId, target, true);
        }
        return ids(roomId);
    }

    @Transactional
    public List<String> remove(User host, String roomId, String userId) {
        AudioRoom room = room(roomId);
        requireHost(room, host);
        admins.findByRoomIdAndUserId(roomId, userId).ifPresent(a -> {
            admins.delete(a);
            broadcast(roomId, users.findById(userId).orElse(null), false);
        });
        return ids(roomId);
    }
}
