package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.AgencyRequests;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.AgencyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** App: the Agency screen. Everything except /me, /invitations and /levels needs the caller to OWN an active agency. */
@RestController
@RequestMapping("/api/agency")
public class AgencyController {

    private final AgencyService service;

    public AgencyController(AgencyService service) {
        this.service = service;
    }

    /** { isOwner, agency?, today?, period?, ownerLadder?, memberOf?, pendingInvitations } */
    @GetMapping("/me")
    public Object me(@CurrentUser User user) {
        return ApiResponse.ok(service.me(user));
    }

    @GetMapping("/levels")
    public Object levels(@CurrentUser User user) {
        return ApiResponse.ok(Map.of("levels", service.levels()));
    }

    @GetMapping("/members")
    public Object members(@CurrentUser User user) {
        return ApiResponse.ok(Map.of("members", service.memberList(user)));
    }

    @GetMapping("/members/{userId}/daily")
    public Object memberDaily(@CurrentUser User user, @PathVariable String userId, @RequestParam(defaultValue = "15") int days) {
        return ApiResponse.ok(service.memberDaily(user, userId, days));
    }

    @DeleteMapping("/members/{userId}")
    public Object removeMember(@CurrentUser User user, @PathVariable String userId) {
        service.removeMember(user, userId);
        return ApiResponse.ok(Map.of("removed", true), "Host removed");
    }

    // ---- invitations the owner sent
    @PostMapping("/invites")
    public Object invite(@CurrentUser User user, @Valid @RequestBody AgencyRequests.Invite body) {
        return ApiResponse.ok(service.invite(user, body.getHostId()), "Invitation sent", HttpStatus.CREATED);
    }

    @GetMapping("/invites")
    public Object sent(@CurrentUser User user) {
        return ApiResponse.ok(Map.of("invites", service.sentInvites(user)));
    }

    @DeleteMapping("/invites/{id}")
    public Object cancel(@CurrentUser User user, @PathVariable String id) {
        service.cancelInvite(user, id);
        return ApiResponse.ok(Map.of("cancelled", true), "Invitation cancelled");
    }

    // ---- invitations the caller received
    @GetMapping("/invitations")
    public Object invitations(@CurrentUser User user) {
        return ApiResponse.ok(Map.of("invitations", service.myInvitations(user)));
    }

    @PostMapping("/invitations/{id}/respond")
    public Object respond(@CurrentUser User user, @PathVariable String id, @Valid @RequestBody AgencyRequests.Respond body) {
        Map<String, Object> res = service.respond(user, id, Boolean.TRUE.equals(body.getAccept()));
        return ApiResponse.ok(res, Boolean.TRUE.equals(res.get("joined")) ? "You joined the agency" : "Invitation declined");
    }
}
