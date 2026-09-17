package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.CreateVideoRoomRequest;
import com.commengine.dto.DtoMapper;
import com.commengine.entity.RoomStatus;
import com.commengine.entity.User;
import com.commengine.entity.VideoRoom;
import com.commengine.entity.VideoRoomComment;
import com.commengine.entity.VideoRoomViewer;
import com.commengine.repository.VideoRoomCommentRepository;
import com.commengine.repository.VideoRoomRepository;
import com.commengine.repository.VideoRoomViewerRepository;
import com.commengine.util.AgoraUidUtil;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
// See AudioRoomService for why this class-level readOnly transaction is required
// (open-in-view=false + lazy relations mapped by DtoMapper after the repository call).
@Transactional(readOnly = true)
public class VideoRoomService {

    private final VideoRoomRepository roomRepository;
    private final VideoRoomViewerRepository viewerRepository;
    private final VideoRoomCommentRepository commentRepository;
    private final AgoraService agoraService;
    private final SocketIOServer socketIOServer;

    public VideoRoomService(VideoRoomRepository roomRepository, VideoRoomViewerRepository viewerRepository,
                            VideoRoomCommentRepository commentRepository, AgoraService agoraService,
                            SocketIOServer socketIOServer) {
        this.roomRepository = roomRepository;
        this.viewerRepository = viewerRepository;
        this.commentRepository = commentRepository;
        this.agoraService = agoraService;
        this.socketIOServer = socketIOServer;
    }

    @Transactional
    public Map<String, Object> goLive(User host, CreateVideoRoomRequest req) {
        VideoRoom room = new VideoRoom();
        room.setTitle(req.getTitle());
        room.setCoverUrl(req.getCoverUrl());
        room.setChannelName("video-" + UUID.randomUUID());
        room.setHost(host);
        roomRepository.save(room);

        long uidNum = AgoraUidUtil.deriveAgoraUid(host.getId());
        Map<String, Object> rtc = agoraService.generateRtcToken(room.getChannelName(), uidNum, "publisher");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("room", DtoMapper.videoRoom(room));
        data.put("rtc", rtc);
        return data;
    }

    public Map<String, Object> listLive() {
        List<VideoRoom> rooms = roomRepository.findByStatusOrderByCreatedAtDesc(RoomStatus.ACTIVE);
        return Map.of("rooms", rooms.stream().map(DtoMapper::videoRoom).collect(Collectors.toList()));
    }

    public Map<String, Object> getRoom(String id) {
        VideoRoom room = findRoomOrThrow(id);
        return Map.of("room", DtoMapper.videoRoom(room));
    }

    @Transactional
    public Map<String, Object> joinRoom(User user, String id) {
        VideoRoom room = roomRepository.findById(id).orElse(null);
        if (room == null || room.getStatus() != RoomStatus.ACTIVE) {
            throw ApiException.notFound("Live room not found or ended");
        }

        boolean isHost = room.getHost().getId().equals(user.getId());
        String role = isHost ? "publisher" : "subscriber";
        long uidNum = AgoraUidUtil.deriveAgoraUid(user.getId());
        Map<String, Object> rtc = agoraService.generateRtcToken(room.getChannelName(), uidNum, role);

        if (!isHost) {
            VideoRoomViewer viewer = viewerRepository.findByRoomIdAndUserId(id, user.getId()).orElse(null);
            if (viewer == null) {
                room.setViewerCount(room.getViewerCount() + 1);
                roomRepository.save(room);
                viewer = new VideoRoomViewer();
                viewer.setRoom(room);
                viewer.setUser(user);
                viewerRepository.save(viewer);
            } else if (viewer.getLeftAt() != null) {
                viewer.setLeftAt(null);
                viewerRepository.save(viewer);
            }

            Map<String, Object> notify = new LinkedHashMap<>();
            notify.put("userId", user.getId());
            notify.put("name", user.getName());
            socketIOServer.getRoomOperations("video-room:" + room.getId()).sendEvent("video:viewer-joined", notify);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("room", DtoMapper.videoRoom(room));
        data.put("rtc", rtc);
        return data;
    }

    @Transactional
    public void leaveRoom(User user, String id) {
        VideoRoomViewer viewer = viewerRepository.findByRoomIdAndUserId(id, user.getId()).orElse(null);
        if (viewer != null && viewer.getLeftAt() == null) {
            viewer.setLeftAt(Instant.now());
            viewerRepository.save(viewer);
            roomRepository.findById(id).ifPresent(room -> {
                room.setViewerCount(Math.max(0, room.getViewerCount() - 1));
                roomRepository.save(room);
            });
        }
        socketIOServer.getRoomOperations("video-room:" + id).sendEvent("video:viewer-left", Map.of("userId", user.getId()));
    }

    @Transactional
    public void endLive(User host, String id) {
        VideoRoom room = findRoomOrThrow(id);
        if (!room.getHost().getId().equals(host.getId())) {
            throw ApiException.forbidden("Only the host can end the stream");
        }
        room.setStatus(RoomStatus.ENDED);
        room.setEndedAt(Instant.now());
        roomRepository.save(room);

        socketIOServer.getRoomOperations("video-room:" + room.getId()).sendEvent("video:room-ended", Map.of("roomId", room.getId()));
    }

    public Map<String, Object> getComments(String id, Integer limitParam) {
        int limit = Math.min(limitParam == null ? 50 : limitParam, 200);
        List<VideoRoomComment> comments = commentRepository.findByRoomIdOrderByCreatedAtDesc(id, PageRequest.of(0, limit));
        List<Map<String, Object>> shaped = comments.stream().map(DtoMapper::videoRoomComment).collect(Collectors.toList());
        Collections.reverse(shaped);
        return Map.of("comments", shaped);
    }

    @Transactional
    public Map<String, Object> postComment(User user, String id, String text) {
        VideoRoom room = roomRepository.findById(id).orElse(null);
        if (room == null || room.getStatus() != RoomStatus.ACTIVE) {
            throw ApiException.notFound("Live room not found or ended");
        }
        VideoRoomComment comment = new VideoRoomComment();
        comment.setRoom(room);
        comment.setUser(user);
        comment.setText(text);
        commentRepository.save(comment);

        Map<String, Object> commentJson = DtoMapper.videoRoomComment(comment);
        socketIOServer.getRoomOperations("video-room:" + room.getId()).sendEvent("video:new-comment", commentJson);

        return Map.of("comment", commentJson);
    }

    private VideoRoom findRoomOrThrow(String id) {
        return roomRepository.findById(id).orElseThrow(() -> ApiException.notFound("Room not found"));
    }
}