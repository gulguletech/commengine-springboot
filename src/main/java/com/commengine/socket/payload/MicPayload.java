package com.commengine.socket.payload;

/** Payload for the live-room "mic" events (video:mic-*). Every field except roomId is optional per event. */
public class MicPayload {
    private String roomId;
    private String userId;
    private Boolean accept;
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Boolean getAccept() { return accept; }
    public void setAccept(Boolean accept) { this.accept = accept; }
}
