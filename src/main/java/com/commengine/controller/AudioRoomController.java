package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.AudioRoomSettingsRequest;
import com.commengine.dto.CreateAudioRoomRequest;
import com.commengine.dto.JoinAudioRoomRequest;
import com.commengine.dto.RespondRequest;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.AudioRoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms/audio")
public class AudioRoomController {

    private final AudioRoomService audioRoomService;

    public AudioRoomController(AudioRoomService audioRoomService) {
        this.audioRoomService = audioRoomService;
    }

    @PostMapping
    public Object createRoom(@CurrentUser User user, @Valid @RequestBody CreateAudioRoomRequest body) {
        return ApiResponse.ok(audioRoomService.createRoom(user, body), "Audio room created", HttpStatus.CREATED);
    }

    @GetMapping
    public Object listRooms() {
        return ApiResponse.ok(audioRoomService.listRooms());
    }

    /** The caller's own persistent room (online or offline); {"room": null} if they never created one. */
    @GetMapping("/mine")
    public Object myRoom(@CurrentUser User user) {
        return ApiResponse.ok(audioRoomService.getMyRoom(user));
    }

    @GetMapping("/{id}")
    public Object getRoom(@PathVariable String id) {
        return ApiResponse.ok(audioRoomService.getRoom(id));
    }

    @GetMapping("/{id}/members")
    public Object getMembers(@PathVariable String id) {
        return ApiResponse.ok(audioRoomService.getMembers(id));
    }

    @PostMapping("/{id}/join")
    public Object joinRoom(@CurrentUser User user, @PathVariable String id,
                           @RequestBody(required = false) JoinAudioRoomRequest body) {
        String passcode = body == null ? null : body.getPasscode();
        Map<String, Object> data = audioRoomService.joinRoom(user, id, passcode);
        String message = "PENDING".equals(data.get("status")) ? "Join request sent, waiting for host approval" : "Success";
        return ApiResponse.ok(data, message);
    }

    @PostMapping("/{id}/leave")
    public Object leaveRoom(@CurrentUser User user, @PathVariable String id) {
        audioRoomService.leaveRoom(user, id);
        return ApiResponse.ok(Map.of(), "Left room");
    }

    @PatchMapping("/{id}/settings")
    public Object updateSettings(@CurrentUser User user, @PathVariable String id,
                                 @Valid @RequestBody AudioRoomSettingsRequest body) {
        return ApiResponse.ok(audioRoomService.updateSettings(user, id, body), "Room settings updated");
    }

    /** POST /api/rooms/audio/{id}/cover  (multipart, field: cover). Host only; the old picture is deleted. */
    @PostMapping("/{id}/cover")
    public Object updateCover(@CurrentUser User user, @PathVariable String id,
                              @RequestParam("cover") org.springframework.web.multipart.MultipartFile cover) {
        return ApiResponse.ok(audioRoomService.updateCover(user, id, cover), "Room picture updated");
    }

    /** Kick everybody out and take the room offline, keeping it for next time. */
    @PostMapping("/{id}/close")
    public Object closeRoom(@CurrentUser User user, @PathVariable String id) {
        audioRoomService.closeRoom(user, id);
        return ApiResponse.ok(Map.of(), "Room is now offline");
    }

    @DeleteMapping("/{id}")
    public Object endRoom(@CurrentUser User user, @PathVariable String id) {
        audioRoomService.endRoom(user, id);
        return ApiResponse.ok(Map.of(), "Room ended");
    }

    @GetMapping("/{id}/join-requests")
    public Object listJoinRequests(@CurrentUser User user, @PathVariable String id) {
        return ApiResponse.ok(audioRoomService.listJoinRequests(user, id));
    }

    @PostMapping("/{id}/join-requests/{requestId}/respond")
    public Object respondJoinRequest(@CurrentUser User user, @PathVariable String id, @PathVariable String requestId,
                                     @Valid @RequestBody RespondRequest body) {
        Map<String, Object> data = audioRoomService.respondJoinRequest(user, id, requestId, Boolean.TRUE.equals(body.getAccept()));
        String status = String.valueOf(data.get("status"));
        return ApiResponse.ok(data, "Request " + status.toLowerCase());
    }
}