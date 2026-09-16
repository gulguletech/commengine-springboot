package com.commengine.socket.payload;

public class SpeakingPayload {
    private String roomId;
    private Boolean isSpeaking;
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public Boolean getIsSpeaking() { return isSpeaking; }
    public void setIsSpeaking(Boolean isSpeaking) { this.isSpeaking = isSpeaking; }
}
