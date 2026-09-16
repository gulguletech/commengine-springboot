package com.commengine.socket.payload;

public class TypingPayload {
    private String conversationId;
    private Boolean isTyping;
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Boolean getIsTyping() { return isTyping; }
    public void setIsTyping(Boolean isTyping) { this.isTyping = isTyping; }
}
