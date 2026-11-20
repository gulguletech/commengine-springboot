package com.commengine.dto;

import jakarta.validation.constraints.*;

public class SendGiftRequest {
    /** "audio" or "video" */
    @NotBlank
    private String roomKind;
    @NotBlank
    private String roomId;
    /** One receiver (old clients). New clients send receiverIds instead. */
    private String receiverId;
    /** Several receivers at once (multi-select / gift all). Each one gets the full gift. */
    @Size(max = 100)
    private java.util.List<String> receiverIds;
    @NotBlank
    private String giftId;
    @Min(1) @Max(99)
    private Integer count = 1;

    public String getRoomKind() { return roomKind; }
    public void setRoomKind(String roomKind) { this.roomKind = roomKind; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }
    public java.util.List<String> getReceiverIds() { return receiverIds; }
    public void setReceiverIds(java.util.List<String> receiverIds) { this.receiverIds = receiverIds; }
    public String getGiftId() { return giftId; }
    public void setGiftId(String giftId) { this.giftId = giftId; }
    public Integer getCount() { return count == null ? 1 : count; }
    public void setCount(Integer count) { this.count = count; }
}
