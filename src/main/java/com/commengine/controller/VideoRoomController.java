package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.CreateVideoRoomRequest;
import com.commengine.dto.PostCommentRequest;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.VideoRoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms/video")
public class VideoRoomController {

    private final VideoRoomService videoRoomService;

    public VideoRoomController(VideoRoomService videoRoomService) {
        this.videoRoomService = videoRoomService;
    }

    @PostMapping
    public Object goLive(@CurrentUser User user, @Valid @RequestBody CreateVideoRoomRequest body) {
        return ApiResponse.ok(videoRoomService.goLive(user, body), "You are live", HttpStatus.CREATED);
    }

    @GetMapping
    public Object listLive() {
        return ApiResponse.ok(videoRoomService.listLive());
    }

    @GetMapping("/{id}")
    public Object getRoom(@PathVariable String id) {
        return ApiResponse.ok(videoRoomService.getRoom(id));
    }

    @PostMapping("/{id}/join")
    public Object joinRoom(@CurrentUser User user, @PathVariable String id) {
        return ApiResponse.ok(videoRoomService.joinRoom(user, id));
    }

    @PostMapping("/{id}/leave")
    public Object leaveRoom(@CurrentUser User user, @PathVariable String id) {
        videoRoomService.leaveRoom(user, id);
        return ApiResponse.ok(Map.of(), "Left live room");
    }

    @DeleteMapping("/{id}")
    public Object endLive(@CurrentUser User user, @PathVariable String id) {
        videoRoomService.endLive(user, id);
        return ApiResponse.ok(Map.of(), "Live stream ended");
    }

    @GetMapping("/{id}/comments")
    public Object getComments(@PathVariable String id, @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(videoRoomService.getComments(id, limit));
    }

    @PostMapping("/{id}/comments")
    public Object postComment(@CurrentUser User user, @PathVariable String id, @Valid @RequestBody PostCommentRequest body) {
        return ApiResponse.ok(videoRoomService.postComment(user, id, body.getText()), "Comment posted", HttpStatus.CREATED);
    }
}
