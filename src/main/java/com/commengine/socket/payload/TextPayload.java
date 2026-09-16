package com.commengine.socket.payload;

/** Reused for audio:send-chat and video:comment: { roomId, text }. */
public class TextPayload {
    private String roomId;
    private String text;
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
