package com.commengine.service;

import com.commengine.entity.RoomStatus;
import com.commengine.repository.AudioRoomRepository;
import com.commengine.socket.AudioRoomLiveState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Safety net for persistent audio rooms: a room that is marked online but has had nobody inside for two checks in a
 * row (host's app crashed, server restarted, socket never reconnected...) is switched offline so it stops showing up
 * on Home. The normal path is RoomPresenceService#leaveAudioRoom; this only catches what that path never sees.
 * Two checks (about a minute) give reconnecting sockets time to come back after a restart.
 */
@Component
public class AudioRoomOfflineSweeper {

    private static final Logger log = LoggerFactory.getLogger(AudioRoomOfflineSweeper.class);

    private final AudioRoomRepository rooms;
    private final AudioRoomLiveState state;
    private final RoomPresenceService presence;
    private final Set<String> emptyLastCheck = ConcurrentHashMap.newKeySet();

    public AudioRoomOfflineSweeper(AudioRoomRepository rooms, AudioRoomLiveState state, RoomPresenceService presence) {
        this.rooms = rooms;
        this.state = state;
        this.presence = presence;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void sweep() {
        try {
            List<String> online = rooms.findOnlineRoomIds(RoomStatus.ACTIVE);
            Set<String> emptyNow = new HashSet<>();
            for (String id : online) {
                Set<String> members = state.peekMembers(id);
                if (members != null && !members.isEmpty()) continue;
                emptyNow.add(id);
                if (emptyLastCheck.contains(id)) presence.setAudioOnline(id, false);
            }
            emptyLastCheck.clear();
            emptyLastCheck.addAll(emptyNow);
        } catch (Exception e) {
            log.warn("Audio room offline sweep failed: {}", e.getMessage());
        }
    }
}
