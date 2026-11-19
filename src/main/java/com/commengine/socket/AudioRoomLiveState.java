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

    /**
     * Mic layouts a room may have, as the app draws them:
     * 4 (1x4), 8 (2x4), 10 (2x5), 12 (2 big seats + 5 + 5), 15 (3x5), 20 (4x5).
     * 4 and 8 are free; every other layout is for VIP members only (see {@link #FREE_SEAT_COUNTS}).
     */
    public static final int[] ALLOWED_SEAT_COUNTS = {4, 8, 10, 12, 15, 20};
    public static final int[] FREE_SEAT_COUNTS = {4, 8};
    public static final int DEFAULT_SEAT_COUNT = 8;

    public static boolean isFreeSeatCount(int n) {
        for (int a : FREE_SEAT_COUNTS) if (a == n) return true;
        return false;
    }

    public static boolean isAllowedSeatCount(int n) {
        for (int a : ALLOWED_SEAT_COUNTS) if (a == n) return true;
        return false;
    }

    private final ConcurrentHashMap<String, Integer> seatCountByRoom = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Seat[]> seatsByRoom = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> membersByRoom = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, boolean[]> blockedByRoom = new ConcurrentHashMap<>();

    /** How many seats this room has right now. */
    public int seatCount(String roomId) {
        return seatCountByRoom.getOrDefault(roomId, DEFAULT_SEAT_COUNT);
    }

    /**
     * Makes sure the live state knows this room's seat count (it is kept in the database, while seats live in memory
     * and start empty after a restart). Does nothing if the count is already known and the same.
     */
    public synchronized void ensureSeatCount(String roomId, int count) {
        Integer known = seatCountByRoom.get(roomId);
        if (known != null && known == count) return;
        resize(roomId, count);
    }

    /**
     * Changes the number of seats. Seats and locks on the remaining positions are kept.
     * @return the people who were sitting on seats that no longer exist (they are standing now)
     */
    public synchronized java.util.List<Seat> resize(String roomId, int newCount) {
        Seat[] oldSeats = seatsByRoom.get(roomId);
        boolean[] oldBlocked = blockedByRoom.get(roomId);

        Seat[] seats = new Seat[newCount];
        boolean[] blocked = new boolean[newCount];
        java.util.List<Seat> removed = new java.util.ArrayList<>();
        if (oldSeats != null) {
            for (int i = 0; i < oldSeats.length; i++) {
                if (oldSeats[i] == null) continue;
                if (i < newCount) seats[i] = oldSeats[i];
                else removed.add(oldSeats[i]);
            }
        }
        if (oldBlocked != null) System.arraycopy(oldBlocked, 0, blocked, 0, Math.min(oldBlocked.length, newCount));

        seatCountByRoom.put(roomId, newCount);
        seatsByRoom.put(roomId, seats);
        blockedByRoom.put(roomId, blocked);
        return removed;
    }

    /** true = the host has locked that seat. */
    public boolean[] getBlocked(String roomId) {
        return blockedByRoom.computeIfAbsent(roomId, k -> new boolean[seatCount(roomId)]);
    }

    /** Non-creating lookup (used by cleanup code so it never resurrects an ended room). */
    public Set<String> peekMembers(String roomId) {
        return membersByRoom.get(roomId);
    }

    public Seat[] getSeats(String roomId) {
        return seatsByRoom.computeIfAbsent(roomId, k -> new Seat[seatCount(roomId)]);
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

    /** Called when a room ends entirely — drops its seat/member maps so they don't
     *  accumulate in memory forever for rooms that will never be revisited. */
    public synchronized void removeRoom(String roomId) {
        seatsByRoom.remove(roomId);
        membersByRoom.remove(roomId);
        blockedByRoom.remove(roomId);
        seatCountByRoom.remove(roomId);
    }

    /** Iterates every room a user might be seated in (used on disconnect). */
    public java.util.Map<String, Seat[]> allSeats() {
        return seatsByRoom;
    }

    public java.util.Map<String, Set<String>> allMembers() {
        return membersByRoom;
    }
}