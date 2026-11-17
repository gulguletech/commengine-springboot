package com.commengine.socket;

import com.commengine.dto.DtoMapper;
import com.commengine.entity.User;
import com.commengine.util.AgoraUidUtil;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * In-memory "mic" state of every live video room.
 *
 * The mic is a single guest slot: the host and ONE viewer share the screen (split in half).
 *  - holder   : the viewer currently on the mic (at most one per room)
 *  - requests : viewers who asked the host for the mic, oldest first
 *  - invites  : viewers the host invited and who have not answered yet
 *
 * It is plain memory on purpose (like AudioRoomLiveState): the mic only makes sense while the stream is running.
 */
@Component
public class VideoMicState {

    private final SocketIOServer server;

    private final Map<String, Map<String, Object>> holders = new HashMap<>();
    private final Map<String, LinkedHashMap<String, Map<String, Object>>> requests = new HashMap<>();
    private final Map<String, Set<String>> invites = new HashMap<>();

    public VideoMicState(SocketIOServer server) {
        this.server = server;
    }

    // ------------------------------------------------------------------ queries

    public synchronized Map<String, Object> holder(String roomId) {
        return holders.get(roomId);
    }

    public synchronized boolean isHolder(String roomId, String userId) {
        Map<String, Object> h = holders.get(roomId);
        return h != null && userId.equals(h.get("id"));
    }

    public synchronized List<Map<String, Object>> requestList(String roomId) {
        LinkedHashMap<String, Map<String, Object>> r = requests.get(roomId);
        return r == null ? new ArrayList<>() : new ArrayList<>(r.values());
    }

    public synchronized boolean hasRequest(String roomId, String userId) {
        LinkedHashMap<String, Map<String, Object>> r = requests.get(roomId);
        return r != null && r.containsKey(userId);
    }

    public synchronized boolean hasInvite(String roomId, String userId) {
        Set<String> s = invites.get(roomId);
        return s != null && s.contains(userId);
    }

    // ------------------------------------------------------------------ mutations (all return whether something changed)

    public synchronized void addRequest(String roomId, User user) {
        requests.computeIfAbsent(roomId, k -> new LinkedHashMap<>()).put(user.getId(), DtoMapper.userBrief(user));
    }

    public synchronized boolean removeRequest(String roomId, String userId) {
        LinkedHashMap<String, Map<String, Object>> r = requests.get(roomId);
        return r != null && r.remove(userId) != null;
    }

    public synchronized void addInvite(String roomId, String userId) {
        invites.computeIfAbsent(roomId, k -> new HashSet<>()).add(userId);
    }

    public synchronized boolean removeInvite(String roomId, String userId) {
        Set<String> s = invites.get(roomId);
        return s != null && s.remove(userId);
    }

    /**
     * Puts {@code user} on the mic if it is free (or already theirs). Returns false when someone else holds it.
     * Everybody else's pending requests / invites are dropped; the returned list is the users whose request was
     * dropped so the caller can tell them the mic is gone.
     */
    public synchronized List<String> grant(String roomId, User user, List<String> outDropped) {
        Map<String, Object> cur = holders.get(roomId);
        if (cur != null && !user.getId().equals(cur.get("id"))) return null;
        Map<String, Object> holder = DtoMapper.userBrief(user);
        holder.put("agoraUid", AgoraUidUtil.deriveAgoraUid(user.getId()));
        holders.put(roomId, holder);

        LinkedHashMap<String, Map<String, Object>> r = requests.remove(roomId);
        if (r != null) for (String id : r.keySet()) if (!id.equals(user.getId())) outDropped.add(id);
        invites.remove(roomId);
        return outDropped;
    }

    /** Frees the mic if {@code userId} holds it. */
    public synchronized boolean release(String roomId, String userId) {
        Map<String, Object> h = holders.get(roomId);
        if (h == null || !userId.equals(h.get("id"))) return false;
        holders.remove(roomId);
        return true;
    }

    public synchronized void clearRoom(String roomId) {
        holders.remove(roomId);
        requests.remove(roomId);
        invites.remove(roomId);
    }

    // ------------------------------------------------------------------ broadcasting

    public void broadcastMic(String roomId) {
        server.getRoomOperations("video-room:" + roomId).sendEvent("video:mic-update", micMessage(roomId));
    }

    public Map<String, Object> micMessage(String roomId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roomId", roomId);
        m.put("mic", holder(roomId));
        return m;
    }

    public void sendRequestsToHost(String roomId, String hostId) {
        server.getRoomOperations("user:" + hostId).sendEvent("video:mic-requests", requestsMessage(roomId));
    }

    public Map<String, Object> requestsMessage(String roomId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roomId", roomId);
        m.put("requests", requestList(roomId));
        return m;
    }

    /** A user left the stream (back button, app closed, network lost): free whatever they held or asked for. */
    public void userLeft(String roomId, String userId, String hostId) {
        boolean wasHolder = release(roomId, userId);
        boolean hadRequest = removeRequest(roomId, userId);
        removeInvite(roomId, userId);
        if (wasHolder) broadcastMic(roomId);
        if (hadRequest) sendRequestsToHost(roomId, hostId);
    }
}
