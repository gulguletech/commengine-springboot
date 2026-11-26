package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.dto.AgencyRequestRequests;
import com.commengine.entity.Admin;
import com.commengine.security.AdminAuthInterceptor;
import com.commengine.service.AgencyRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Admin panel: review "Apply for agency" requests. */
@RestController
@RequestMapping("/api/admin/agency-requests")
public class AdminAgencyRequestController {

    private final AgencyRequestService service;

    public AdminAgencyRequestController(AgencyRequestService service) {
        this.service = service;
    }

    @GetMapping
    public Object list(@RequestParam(defaultValue = "PENDING") String status, @RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(service.list(status, q, page, size));
    }

    @PostMapping("/{id}/approve")
    public Object approve(HttpServletRequest request, @PathVariable String id, @RequestBody(required = false) AgencyRequestRequests.Approve body) {
        Admin admin = AdminAuthInterceptor.current(request);
        return ApiResponse.ok(service.approveAsAdmin(admin.getUsername(), id, body == null ? null : body.getSuperAdminId()), "Agency approved");
    }

    @PostMapping("/{id}/reject")
    public Object reject(HttpServletRequest request, @PathVariable String id, @Valid @RequestBody(required = false) AgencyRequestRequests.Reject body) {
        Admin admin = AdminAuthInterceptor.current(request);
        return ApiResponse.ok(service.rejectAsAdmin(admin.getUsername(), id, body == null ? null : body.getReason()), "Request rejected");
    }
}
