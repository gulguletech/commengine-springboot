package com.commengine.socket;

/** Mirrors the seat shape broadcast by sockets/audioRoomSocket.js (null = empty seat). */
public class Seat {
    public String userId;
    public String name;
    public String avatarUrl;
    public boolean isMuted;
    /** Equipped avatar frame (store), or null. */
    public String frameUrl;

    public Seat() {}

    public Seat(String userId, String name, String avatarUrl, boolean isMuted) {
        this(userId, name, avatarUrl, isMuted, null);
    }

    public Seat(String userId, String name, String avatarUrl, boolean isMuted, String frameUrl) {
        this.userId = userId;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.isMuted = isMuted;
        this.frameUrl = frameUrl;
    }
}
