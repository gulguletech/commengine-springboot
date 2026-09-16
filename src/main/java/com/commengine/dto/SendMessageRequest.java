package com.commengine.dto;

import com.commengine.entity.MessageType;
import jakarta.validation.constraints.Size;

public class SendMessageRequest {
    @Size(min = 1, max = 2000)
    private String content;

    private String mediaUrl;

    private MessageType type = MessageType.TEXT;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public MessageType getType() { return type == null ? MessageType.TEXT : type; }
    public void setType(MessageType type) { this.type = type; }
}
