package com.commengine.controller;

import com.commengine.common.ApiException;
import com.commengine.common.ApiResponse;
import com.commengine.dto.TeamRequests;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.CloudinaryService;
import com.commengine.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** App: the invitation inbox (everyone) + roles + image upload for the Invite Agency form (super admins and BDs). */
@RestController
@RequestMapping("/api/team")
public class TeamController {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final TeamService team;
    private final CloudinaryService cloudinary;

    public TeamController(TeamService team, CloudinaryService cloudinary) {
        this.team = team;
        this.cloudinary = cloudinary;
    }

    /** { isSuperAdmin, isBd, pendingInvitations }: what this person can open. */
    @GetMapping("/roles")
    public Object roles(@CurrentUser User user) {
        return ApiResponse.ok(team.roles(user));
    }

    @GetMapping("/inbox")
    public Object inbox(@CurrentUser User user) {
        return ApiResponse.ok(Map.of("invitations", team.inbox(user)));
    }

    /** { pending, latestText, latestAt } for the Messages tab row. */
    @GetMapping("/inbox/summary")
    public Object summary(@CurrentUser User user) {
        return ApiResponse.ok(team.inboxSummary(user));
    }

    @PostMapping("/inbox/{source}/{id}/respond")
    public Object respond(@CurrentUser User user, @PathVariable String source, @PathVariable String id, @RequestBody TeamRequests.Respond body) {
        Map<String, Object> res = team.respond(user, source, id, body.isAccept());
        return ApiResponse.ok(res, Boolean.TRUE.equals(res.get("joined")) ? "Accepted" : "Declined");
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object upload(@CurrentUser User user, @RequestParam("file") MultipartFile file) {
        if (!team.canInvite(user)) throw ApiException.forbidden("Not allowed");
        String type = file.getContentType() == null ? "" : file.getContentType();
        if (!(type.equals("image/png") || type.equals("image/jpeg") || type.equals("image/webp"))) throw ApiException.unprocessable("Use a PNG, JPG or WebP image");
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw ApiException.unprocessable("The image must be under 5 MB");
        CloudinaryService.MediaResult up = cloudinary.uploadMedia(file, "commengine/team", "image");
        return ApiResponse.ok(Map.of("url", up.url()));
    }
}
