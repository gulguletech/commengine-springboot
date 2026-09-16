package com.commengine.socket.payload;

public class RemoveFromSeatPayload {
    private String roomId;
    private String userId;
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
