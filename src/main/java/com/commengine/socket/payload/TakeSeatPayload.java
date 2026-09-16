package com.commengine.socket.payload;

public class TakeSeatPayload {
    private String roomId;
    private Integer seatIndex;
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public Integer getSeatIndex() { return seatIndex; }
    public void setSeatIndex(Integer seatIndex) { this.seatIndex = seatIndex; }
}
