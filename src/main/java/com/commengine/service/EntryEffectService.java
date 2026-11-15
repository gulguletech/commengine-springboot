package com.commengine.service;

import com.commengine.dto.DtoMapper;
import com.commengine.entity.User;
import com.commengine.repository.UserRepository;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry effects: when someone with an active entry effect walks into a room, everyone in that room (including them)
 * gets a `room:entry-effect` event and plays the video.
 */
@Service
public class EntryEffectService {

    /** A socket that reconnects re-sends "join" - don't replay the clip for the same person within this window. */
    private static final long REPLAY_GUARD_MS = 30_000;

    private final UserRepository userRepository;
    private final SocketIOServer server;
    private final ConcurrentHashMap<String, Long> lastPlayed = new ConcurrentHashMap<>();

    public EntryEffectService(UserRepository userRepository, SocketIOServer server) {
        this.userRepository = userRepository;
        this.server = server;
    }

    /**
     * @param channel  the socket.io room everyone is in, e.g. "audio-room:123"
     * @param roomKind "audio" or "video"
     */
    public void announce(String channel, String roomKind, String roomId, String userId) {
        try {
            // always read the user fresh: the copy cached on the socket may predate the purchase
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || !user.hasActiveEntryEffect()) return;

            long now = System.currentTimeMillis();
            String key = roomKind + ":" + roomId + ":" + userId;
            Long prev = lastPlayed.put(key, now);
            if (prev != null && now - prev < REPLAY_GUARD_MS) return;
            if (lastPlayed.size() > 5000) lastPlayed.values().removeIf(t -> now - t > REPLAY_GUARD_MS);

            Map<String, Object> effect = new LinkedHashMap<>();
            effect.put("id", user.getEntryItemId());
            effect.put("name", user.getEntryName());
            effect.put("videoUrl", user.getEntryVideoUrl());

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("id", java.util.UUID.randomUUID().toString());
            event.put("roomKind", roomKind);
            event.put("roomId", roomId);
            event.put("user", DtoMapper.userBrief(user));
            event.put("effect", effect);
            server.getRoomOperations(channel).sendEvent("room:entry-effect", event);
        } catch (Exception ignored) {
            // an entry effect is decoration: never let it break joining a room
        }
    }
}
