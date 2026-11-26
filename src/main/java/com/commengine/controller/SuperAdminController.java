package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.SuperAdminRequests;
import com.commengine.dto.TeamRequests;
import com.commengine.service.TeamService;
import com.commengine.service.AgencyRequestService;
import com.commengine.dto.AgencyRequestRequests;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.SuperAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** App: the super admin portal. Every method re-checks that the caller is a super admin. */
@RestController
@RequestMapping("/api/super-admin")
public class SuperAdminController {

    private final SuperAdminService service;
    private final TeamService team;
    private final AgencyRequestService agencyRequests;

    public SuperAdminController(SuperAdminService service, TeamService team, AgencyRequestService agencyRequests) {
        this.service = service;
        this.team = team;
        this.agencyRequests = agencyRequests;
    }

    @GetMapping("/dashboard")
    public Object dashboard(@CurrentUser User user) {
        return ApiResponse.ok(service.dashboard(user));
    }

    @GetMapping("/team/agencies")
    public Object agencies(@CurrentUser User user, @RequestParam(defaultValue = "month") String period,
                           @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ApiResponse.ok(service.teamAgencies(user, period, from, to));
    }

    @GetMapping("/team/hosts")
    public Object hosts(@CurrentUser User user, @RequestParam(defaultValue = "month") String period,
                        @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ApiResponse.ok(service.teamHosts(user, period, from, to));
    }

    @GetMapping("/team/bds")
    public Object bds(@CurrentUser User user, @RequestParam(defaultValue = "month") String period,
                      @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ApiResponse.ok(service.teamBds(user, period, from, to));
    }

    // ---- add people under this super admin (they accept from their inbox)
    @PostMapping("/team/invite-bd")
    public Object inviteBd(@CurrentUser User user, @Valid @RequestBody TeamRequests.InviteTarget body) {
        return ApiResponse.ok(team.inviteBd(user, body.getUserRef()), "Invitation sent");
    }

    @PostMapping("/team/invite-host")
    public Object inviteHost(@CurrentUser User user, @Valid @RequestBody TeamRequests.InviteTarget body) {
        return ApiResponse.ok(team.inviteDirectHost(user, body.getUserRef()), "Invitation sent");
    }

    @PostMapping("/team/invite-agency")
    public Object inviteAgency(@CurrentUser User user, @Valid @RequestBody TeamRequests.InviteAgency body) {
        return ApiResponse.ok(team.inviteAgency(user, false, body), "Invitation sent");
    }

    @GetMapping("/team/invites")
    public Object invites(@CurrentUser User user, @RequestParam(defaultValue = "") String kind) {
        return ApiResponse.ok(Map.of("invites", team.sent(user, kind)));
    }

    @DeleteMapping("/team/invites/{id}")
    public Object cancelInvite(@CurrentUser User user, @PathVariable String id) {
        team.cancel(user, id);
        return ApiResponse.ok(Map.of("cancelled", true), "Invitation cancelled");
    }

    // ---- "Apply for agency" requests: a super admin can approve or reject them
    @GetMapping("/agency-requests")
    public Object agencyRequests(@CurrentUser User user, @RequestParam(defaultValue = "PENDING") String status, @RequestParam(defaultValue = "") String q,
                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(agencyRequests.listForSuperAdmin(user, status, q, page, size));
    }

    @PostMapping("/agency-requests/{id}/approve")
    public Object approveRequest(@CurrentUser User user, @PathVariable String id) {
        return ApiResponse.ok(agencyRequests.approveAsSuperAdmin(user, id), "Agency approved");
    }

    @PostMapping("/agency-requests/{id}/reject")
    public Object rejectRequest(@CurrentUser User user, @PathVariable String id, @Valid @RequestBody(required = false) AgencyRequestRequests.Reject body) {
        return ApiResponse.ok(agencyRequests.rejectAsSuperAdmin(user, id, body == null ? null : body.getReason()), "Request rejected");
    }

    @GetMapping("/reports")
    public Object reports(@CurrentUser User user, @RequestParam(defaultValue = "day") String period,
                          @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ApiResponse.ok(service.reports(user, period, from, to));
    }

    @GetMapping("/wares")
    public Object wares(@CurrentUser User user, @RequestParam(defaultValue = "") String q) {
        return ApiResponse.ok(Map.of("items", service.wares(user, q)));
    }

    @PostMapping("/wares/give")
    public Object giveWare(@CurrentUser User user, @Valid @RequestBody SuperAdminRequests.GiveWare body) {
        return ApiResponse.ok(service.giveWare(user, body), "Item given");
    }

    @GetMapping("/badges")
    public Object badges(@CurrentUser User user) {
        return ApiResponse.ok(Map.of("items", service.badges(user)));
    }

    @PostMapping("/badges/give")
    public Object giveBadge(@CurrentUser User user, @Valid @RequestBody SuperAdminRequests.GiveBadge body) {
        return ApiResponse.ok(service.giveBadge(user, body), "Badge given");
    }

    @GetMapping("/dedicated")
    public Object dedicated(@CurrentUser User user, @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "ALL") String kind) {
        return ApiResponse.ok(Map.of("items", service.dedicated(user, q, kind)));
    }

    @DeleteMapping("/dedicated/{id}")
    public Object revoke(@CurrentUser User user, @PathVariable String id) {
        service.revoke(user, id);
        return ApiResponse.ok(Map.of("revoked", true), "Taken back");
    }
}
