package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.BattleInviteRequest;
import com.commengine.dto.PkRequests;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.PkService;
import com.commengine.service.RoomAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** PK battles and room admins for one audio room. */
@RestController
@RequestMapping("/api/rooms/audio/{roomId}")
public class AudioRoomPkController {

    private final PkService pk;
    private final RoomAdminService roomAdmins;

    public AudioRoomPkController(PkService pk, RoomAdminService roomAdmins) {
        this.pk = pk;
        this.roomAdmins = roomAdmins;
    }

    // ---------------------------------------------------------------- PK
    @GetMapping("/pk")
    public Object current(@CurrentUser User user, @PathVariable String roomId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pk", pk.current(roomId)); // null when nothing is running
        return ApiResponse.ok(data);
    }

    @PostMapping("/pk")
    public Object start(@CurrentUser User user, @PathVariable String roomId, @Valid @RequestBody PkRequests.Start body) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pk", pk.start(user, roomId, body.getMode(), body.getUserIds(), body.getMinutes()));
        return ApiResponse.ok(data, "PK started");
    }

    @DeleteMapping("/pk")
    public Object stop(@CurrentUser User user, @PathVariable String roomId) {
        pk.stop(user, roomId);
        return ApiResponse.ok(Map.of("stopped", true), "PK turned off");
    }

    // ---------------------------------------------------------------- room admins
    @GetMapping("/admins")
    public Object admins(@CurrentUser User user, @PathVariable String roomId) {
        return ApiResponse.ok(Map.of("adminIds", roomAdmins.list(roomId)));
    }

    @PostMapping("/admins")
    public Object addAdmin(@CurrentUser User user, @PathVariable String roomId, @Valid @RequestBody BattleInviteRequest body) {
        return ApiResponse.ok(Map.of("adminIds", roomAdmins.add(user, roomId, body.getUserId())));
    }

    @DeleteMapping("/admins/{userId}")
    public Object removeAdmin(@CurrentUser User user, @PathVariable String roomId, @PathVariable String userId) {
        return ApiResponse.ok(Map.of("adminIds", roomAdmins.remove(user, roomId, userId)));
    }
}
