package com.commengine.service;

import com.commengine.entity.HostSession;
import com.commengine.repository.AgencyMemberRepository;
import com.commengine.repository.DirectHostRepository;
import com.commengine.repository.HostSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Records how long agency members spend inside their OWN audio room / live video ("working time").
 * Nothing here is allowed to break a room: every public method swallows its own errors.
 */
@Service
public class HostSessionService {

    private static final Logger log = LoggerFactory.getLogger(HostSessionService.class);

    private final HostSessionRepository sessions;
    private final AgencyMemberRepository members;
    private final DirectHostRepository directHosts;

    public HostSessionService(HostSessionRepository sessions, AgencyMemberRepository members, DirectHostRepository directHosts) {
        this.sessions = sessions;
        this.members = members;
        this.directHosts = directHosts;
    }

    /** The host is now inside their own room. Only agency members are tracked. Safe to call repeatedly. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void open(String userId, String kind, String roomId) {
        try {
            if (!members.existsByUserId(userId) && !directHosts.existsById(userId)) return; // agency hosts and direct hosts
            Instant now = Instant.now();
            boolean alreadyOpen = false;
            for (HostSession s : sessions.findByUserIdAndEndedAtIsNull(userId)) {
                if (s.getRoomId().equals(roomId)) {
                    alreadyOpen = true;
                    s.setLastActiveAt(now);
                } else {
                    s.setEndedAt(now); // a host is in one room at a time
                }
                sessions.save(s);
            }
            if (alreadyOpen) return;
            HostSession s = new HostSession();
            s.setUserId(userId);
            s.setKind(kind);
            s.setRoomId(roomId);
            s.setStartedAt(now);
            s.setLastActiveAt(now);
            sessions.save(s);
        } catch (Exception e) {
            log.warn("host session open failed for {}: {}", userId, e.toString());
        }
    }

    /** The user left that room. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void close(String userId, String roomId) {
        try {
            Instant now = Instant.now();
            for (HostSession s : sessions.findByUserIdAndEndedAtIsNull(userId)) {
                if (s.getRoomId().equals(roomId)) {
                    s.setEndedAt(now);
                    sessions.save(s);
                }
            }
        } catch (Exception e) {
            log.warn("host session close failed for {}: {}", userId, e.toString());
        }
    }

    /** The whole room is gone (live stream ended). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeRoom(String roomId) {
        try {
            Instant now = Instant.now();
            for (HostSession s : sessions.findByRoomIdAndEndedAtIsNull(roomId)) {
                s.setEndedAt(now);
                sessions.save(s);
            }
        } catch (Exception e) {
            log.warn("host session closeRoom failed for {}: {}", roomId, e.toString());
        }
    }

    /** Seconds the user spent hosting inside [from, to). Sessions still running count up to now. */
    @Transactional(readOnly = true)
    public long seconds(String userId, Instant from, Instant to) {
        if (!to.isAfter(from)) return 0;
        Instant now = Instant.now();
        List<HostSession> list = sessions.findOverlapping(userId, from, to);
        long total = 0;
        for (HostSession s : list) {
            Instant start = s.getStartedAt().isAfter(from) ? s.getStartedAt() : from;
            Instant endRaw = s.getEndedAt() != null ? s.getEndedAt() : now;
            Instant end = endRaw.isBefore(to) ? endRaw : to;
            if (end.isAfter(start)) total += end.getEpochSecond() - start.getEpochSecond();
        }
        return total;
    }
}
