package com.commengine.service;

import com.commengine.entity.HostSession;
import com.commengine.entity.RoomStatus;
import com.commengine.repository.HostSessionRepository;
import com.commengine.repository.VideoRoomRepository;
import com.commengine.socket.AudioRoomLiveState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * Keeps working-time sessions honest:
 *  - every minute: touches sessions whose host is really still in the room, and closes ones that are not
 *    (app crashed, a leave path was missed);
 *  - at startup: anything left open by the previous run is closed at the last time we saw the host there.
 */
@Component
public class HostSessionSweeper {

    private static final Logger log = LoggerFactory.getLogger(HostSessionSweeper.class);
    /** The HTTP join happens a moment before the socket join, so don't judge a brand-new session. */
    private static final long GRACE_SECONDS = 120;

    private final HostSessionRepository sessions;
    private final AudioRoomLiveState liveState;
    private final VideoRoomRepository videoRooms;

    public HostSessionSweeper(HostSessionRepository sessions, AudioRoomLiveState liveState, VideoRoomRepository videoRooms) {
        this.sessions = sessions;
        this.liveState = liveState;
        this.videoRooms = videoRooms;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void closeLeftovers() {
        try {
            for (HostSession s : sessions.findByEndedAtIsNull()) {
                s.setEndedAt(s.getLastActiveAt());
                sessions.save(s);
            }
        } catch (Exception e) {
            log.warn("closing leftover host sessions failed: {}", e.toString());
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void tick() {
        try {
            Instant now = Instant.now();
            for (HostSession s : sessions.findByEndedAtIsNull()) {
                boolean old = now.getEpochSecond() - s.getStartedAt().getEpochSecond() > GRACE_SECONDS;
                boolean present;
                if ("VIDEO".equals(s.getKind())) {
                    present = videoRooms.findById(s.getRoomId()).map(r -> r.getStatus() == RoomStatus.ACTIVE).orElse(false);
                } else {
                    Set<String> inside = liveState.peekMembers(s.getRoomId());
                    present = inside != null && inside.contains(s.getUserId());
                }
                if (present || !old) {
                    s.setLastActiveAt(now);
                } else {
                    s.setEndedAt(s.getLastActiveAt());
                }
                sessions.save(s);
            }
        } catch (Exception e) {
            log.warn("host session sweep failed: {}", e.toString());
        }
    }
}
