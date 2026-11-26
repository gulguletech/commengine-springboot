package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.TeamRequests;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.BdService;
import com.commengine.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** App: the BD dashboard. A BD can only invite agencies. */
@RestController
@RequestMapping("/api/bd")
public class BdController {

    private final BdService bd;
    private final TeamService team;

    public BdController(BdService bd, TeamService team) {
        this.bd = bd;
        this.team = team;
    }

    @GetMapping("/dashboard")
    public Object dashboard(@CurrentUser User user) {
        return ApiResponse.ok(bd.dashboard(user));
    }

    @PostMapping("/invite-agency")
    public Object inviteAgency(@CurrentUser User user, @Valid @RequestBody TeamRequests.InviteAgency body) {
        return ApiResponse.ok(team.inviteAgency(user, true, body), "Invitation sent");
    }

    @GetMapping("/invites")
    public Object invites(@CurrentUser User user) {
        return ApiResponse.ok(Map.of("invites", team.sent(user, "AGENCY")));
    }

    @DeleteMapping("/invites/{id}")
    public Object cancel(@CurrentUser User user, @PathVariable String id) {
        team.cancel(user, id);
        return ApiResponse.ok(Map.of("cancelled", true), "Invitation cancelled");
    }
}
