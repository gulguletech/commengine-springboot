package com.commengine.socket;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * In-memory, live-only audio room state (seats + presence), ported from the module-level
 * Maps in sockets/audioRoomSocket.js. Resets on restart — same behavior as the original.
 * For multi-instance deployments this would need to move to a shared store (e.g. Redis).
 */
@Component
public class AudioRoomLiveState {

    public static final int SEAT_COUNT = 12;

    private final ConcurrentHashMap<String, Seat[]> seatsByRoom = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> membersByRoom = new ConcurrentHashMap<>();

    public Seat[] getSeats(String roomId) {
        return seatsByRoom.computeIfAbsent(roomId, k -> new Seat[SEAT_COUNT]);
    }

    public Set<String> getMembers(String roomId) {
        return membersByRoom.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>());
    }

    public synchronized int findSeatIndexByUser(String roomId, String userId) {
        Seat[] seats = getSeats(roomId);
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] != null && userId.equals(seats[i].userId)) return i;
        }
        return -1;
    }

    /** @return the cleared seat index, or -1 if the user wasn't seated. */
    public synchronized int clearUserFromRoom(String roomId, String userId) {
        Seat[] seats = seatsByRoom.get(roomId);
        if (seats == null) return -1;
        int idx = findSeatIndexByUser(roomId, userId);
        if (idx != -1) seats[idx] = null;
        return idx;
    }

    /** Iterates every room a user might be seated in (used on disconnect). */
    public java.util.Map<String, Seat[]> allSeats() {
        return seatsByRoom;
    }

    public java.util.Map<String, Set<String>> allMembers() {
        return membersByRoom;
    }
}
