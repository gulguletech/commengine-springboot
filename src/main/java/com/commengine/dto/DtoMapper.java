package com.commengine.dto;

import com.commengine.entity.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds response payload shapes matching what the original Prisma-based Node controllers returned. */
public final class DtoMapper {

    private DtoMapper() {}

    // ---- User shapes -------------------------------------------------

    /** Full user record (matches Prisma's default `user.findUnique`/`update` — every scalar column). */
    public static Map<String, Object> userFull(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("phone", u.getPhone());
        m.put("age", u.getAge());
        m.put("gender", u.getGender());
        m.put("bio", u.getBio());
        m.put("avatarUrl", u.getAvatarUrl());
        m.put("isVerified", u.isVerified());
        m.put("isOnline", u.isOnline());
        m.put("lastSeen", u.getLastSeen());
        m.put("createdAt", u.getCreatedAt());
        m.put("updatedAt", u.getUpdatedAt());
        return m;
    }

    /** {id, name, avatarUrl} — the most common "select" used for hosts/senders across the Node app. */
    public static Map<String, Object> userBrief(User u) {
        if (u == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("avatarUrl", u.getAvatarUrl());
        return m;
    }

    /** {id, name, avatarUrl, isOnline} — used for friends list / search results. */
    public static Map<String, Object> userWithOnline(User u) {
        Map<String, Object> m = userBrief(u);
        m.put("isOnline", u.isOnline());
        return m;
    }

    /** Public profile shape used by GET /users/:id. */
    public static Map<String, Object> userProfile(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("avatarUrl", u.getAvatarUrl());
        m.put("bio", u.getBio());
        m.put("gender", u.getGender());
        m.put("isOnline", u.isOnline());
        m.put("lastSeen", u.getLastSeen());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }

    /** Room-member user select: {id, name, avatarUrl, bio, age, gender, isOnline}. */
    public static Map<String, Object> userMemberProfile(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("avatarUrl", u.getAvatarUrl());
        m.put("bio", u.getBio());
        m.put("age", u.getAge());
        m.put("gender", u.getGender());
        m.put("isOnline", u.isOnline());
        return m;
    }

    // ---- Audio room shapes --------------------------------------------

    /** Sanitized room (passcode stripped) with no relations expanded. */
    public static Map<String, Object> audioRoomBase(AudioRoom r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("title", r.getTitle());
        m.put("type", r.getType());
        m.put("status", r.getStatus());
        m.put("channelName", r.getChannelName());
        m.put("coverUrl", r.getCoverUrl());
        m.put("createdAt", r.getCreatedAt());
        m.put("endedAt", r.getEndedAt());
        m.put("hostId", r.getHost().getId());
        return m;
    }

    public static Map<String, Object> audioRoomWithHostAndCount(AudioRoom r, long memberCount) {
        Map<String, Object> m = audioRoomBase(r);
        m.put("host", userBrief(r.getHost()));
        m.put("_count", Map.of("members", memberCount));
        return m;
    }

    public static Map<String, Object> audioRoomWithHostAndMembers(AudioRoom r, List<AudioRoomMember> members) {
        Map<String, Object> m = audioRoomBase(r);
        m.put("host", userBrief(r.getHost()));
        m.put("members", members.stream().map(DtoMapper::audioRoomMember).collect(Collectors.toList()));
        return m;
    }

    public static Map<String, Object> audioRoomMember(AudioRoomMember mem) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", mem.getId());
        m.put("role", mem.getRole());
        m.put("isMuted", mem.isMuted());
        m.put("joinedAt", mem.getJoinedAt());
        m.put("leftAt", mem.getLeftAt());
        m.put("userId", mem.getUser().getId());
        m.put("user", userMemberProfile(mem.getUser()));
        return m;
    }

    public static Map<String, Object> audioJoinRequest(AudioJoinRequest jr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", jr.getId());
        m.put("status", jr.getStatus());
        m.put("createdAt", jr.getCreatedAt());
        m.put("resolvedAt", jr.getResolvedAt());
        m.put("roomId", jr.getRoom().getId());
        m.put("userId", jr.getUser().getId());
        m.put("user", userBrief(jr.getUser()));
        return m;
    }

    // ---- Video room shapes ---------------------------------------------

    public static Map<String, Object> videoRoom(VideoRoom r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("title", r.getTitle());
        m.put("status", r.getStatus());
        m.put("channelName", r.getChannelName());
        m.put("coverUrl", r.getCoverUrl());
        m.put("viewerCount", r.getViewerCount());
        m.put("createdAt", r.getCreatedAt());
        m.put("endedAt", r.getEndedAt());
        m.put("hostId", r.getHost().getId());
        m.put("host", userBrief(r.getHost()));
        return m;
    }

    public static Map<String, Object> videoRoomComment(VideoRoomComment c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("text", c.getText());
        m.put("createdAt", c.getCreatedAt());
        m.put("roomId", c.getRoom().getId());
        m.put("userId", c.getUser().getId());
        m.put("user", userBrief(c.getUser()));
        return m;
    }

    // ---- Chat shapes -----------------------------------------------------

    public static Map<String, Object> message(Message msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", msg.getId());
        m.put("type", msg.getType());
        m.put("content", msg.getContent());
        m.put("mediaUrl", msg.getMediaUrl());
        m.put("isRead", msg.isRead());
        m.put("createdAt", msg.getCreatedAt());
        m.put("conversationId", msg.getConversation().getId());
        m.put("senderId", msg.getSender().getId());
        m.put("sender", userBrief(msg.getSender()));
        return m;
    }

    public static Map<String, Object> conversationSummary(Conversation c, String currentUserId, Message lastMessage) {
        User other = c.getUserOne().getId().equals(currentUserId) ? c.getUserTwo() : c.getUserOne();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("otherUser", userWithOnline(other));
        m.put("lastMessage", lastMessage == null ? null : message(lastMessage));
        m.put("updatedAt", c.getUpdatedAt());
        return m;
    }

    public static Map<String, Object> conversation(Conversation c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("userOneId", c.getUserOne().getId());
        m.put("userTwoId", c.getUserTwo().getId());
        m.put("createdAt", c.getCreatedAt());
        m.put("updatedAt", c.getUpdatedAt());
        return m;
    }

    // ---- Friend shapes -----------------------------------------------------

    public static Map<String, Object> friendRequest(FriendRequest fr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", fr.getId());
        m.put("status", fr.getStatus());
        m.put("createdAt", fr.getCreatedAt());
        m.put("resolvedAt", fr.getResolvedAt());
        m.put("senderId", fr.getSender().getId());
        m.put("receiverId", fr.getReceiver().getId());
        return m;
    }

    public static Map<String, Object> incomingFriendRequest(FriendRequest fr) {
        Map<String, Object> m = friendRequest(fr);
        m.put("sender", userWithOnline(fr.getSender()));
        return m;
    }
}
