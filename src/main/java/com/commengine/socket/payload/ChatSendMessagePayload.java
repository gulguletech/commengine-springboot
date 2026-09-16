package com.commengine.socket.payload;

public class ChatSendMessagePayload {
    private String conversationId;
    private String content;
    private String mediaUrl;
    private String type = "TEXT";
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public String getType() { return type == null ? "TEXT" : type; }
    public void setType(String type) { this.type = type; }
}
