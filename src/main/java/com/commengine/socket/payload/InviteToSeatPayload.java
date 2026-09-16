package com.commengine.socket.payload;

public class InviteToSeatPayload {
    private String roomId;
    private String userId;
    private Integer seatIndex;
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Integer getSeatIndex() { return seatIndex; }
    public void setSeatIndex(Integer seatIndex) { this.seatIndex = seatIndex; }
}
