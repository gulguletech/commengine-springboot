package com.commengine.socket.payload;

public class BlockSeatPayload {
    private String roomId;
    private Integer seatIndex;
    private Boolean blocked;
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public Integer getSeatIndex() { return seatIndex; }
    public void setSeatIndex(Integer seatIndex) { this.seatIndex = seatIndex; }
    public Boolean getBlocked() { return blocked; }
    public void setBlocked(Boolean blocked) { this.blocked = blocked; }
}
