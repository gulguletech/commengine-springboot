package com.commengine.socket;

/** Mirrors the seat shape broadcast by sockets/audioRoomSocket.js (null = empty seat). */
public class Seat {
    public String userId;
    public String name;
    public String avatarUrl;
    public boolean isMuted;

    public Seat() {}

    public Seat(String userId, String name, String avatarUrl, boolean isMuted) {
        this.userId = userId;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.isMuted = isMuted;
    }
}
