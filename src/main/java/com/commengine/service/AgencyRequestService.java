package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.AgencyRequestRequests;
import com.commengine.entity.*;
import com.commengine.repository.*;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * "Apply for agency" inside the app. The applicant fills a form (names, experience, expected hosts, Aadhaar front and
 * back, selfie). The main admin (admin panel) or any super admin (app) approves it: that creates the agency with the
 * applicant as owner. A super admin who approves also becomes the agency's super admin (its hosts work towards their
 * target); the admin panel can pick one or leave it unassigned.
 */
@Service
public class AgencyRequestService {

    private final AgencyRequestRepository requests;
    private final UserRepository users;
    private final AgencyRepository agencies;
    private final AgencyMemberRepository members;
    private final AgencyService agencyService;
    private final SocketIOServer server;

    public AgencyRequestService(AgencyRequestRepository requests, UserRepository users, AgencyRepository agencies,
                                AgencyMemberRepository members, AgencyService agencyService, SocketIOServer server) {
        this.requests = requests;
        this.users = users;
        this.agencies = agencies;
        this.members = members;
        this.agencyService = agencyService;
        this.server = server;
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> slim(User u) {
        if (u == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("publicId", u.getPublicId());
        m.put("name", u.getName());
        m.put("avatarUrl", u.getAvatarUrl());
        m.put("phone", u.getPhone());
        return m;
    }

    private Map<String, Object> json(AgencyRequest r, User applicant) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("status", r.getStatus());
        m.put("fullName", r.getFullName());
        m.put("agencyName", r.getAgencyName());
        m.put("experience", r.getExperience());
        m.put("expectedHosts", r.getExpectedHosts());
        m.put("aadhaarFrontUrl", r.getAadhaarFrontUrl());
        m.put("aadhaarBackUrl", r.getAadhaarBackUrl());
        m.put("selfieUrl", r.getSelfieUrl());
        m.put("createdAt", r.getCreatedAt());
        m.put("decidedAt", r.getDecidedAt());
        m.put("decidedBy", r.getDecidedBy());
        m.put("rejectReason", r.getRejectReason());
        m.put("agencyId", r.getAgencyId());
        m.put("applicant", slim(applicant));
        return m;
    }

    private void tell(String userId, String event, Map<String, Object> payload) {
        try {
            server.getRoomOperations("user:" + userId).sendEvent(event, payload);
        } catch (Exception ignored) {
            // best effort: the request is saved either way
        }
    }

    private User requireSuperAdmin(User caller) {
        User u = users.findById(caller.getId()).orElseThrow(() -> ApiException.forbidden("Not allowed"));
        if (!u.isSuperAdmin() || u.isBanned()) throw ApiException.forbidden("Only super admins can do this");
        return u;
    }

    private static String clean(String s, int min, int max, String what) {
        String v = s == null ? "" : s.trim();
        if (v.length() < min || v.length() > max) throw ApiException.unprocessable(what + " must be " + min + " to " + max + " characters");
        return v;
    }

    // ------------------------------------------------------------------ applicant

    @Transactional
    public Map<String, Object> submit(User caller, AgencyRequestRequests.Submit f) {
        User u = users.findById(caller.getId()).orElseThrow(() -> ApiException.forbidden("Not allowed"));
        if (u.isBanned()) throw ApiException.forbidden("Your account can't apply");
        if (agencies.findByOwnerId(u.getId()).isPresent()) throw ApiException.conflict("You already run an agency");
        if (members.existsByUserId(u.getId())) throw ApiException.conflict("You are already hosting in an agency. Leave it before applying for your own.");
        if (requests.existsByUserIdAndStatus(u.getId(), AgencyRequest.PENDING)) throw ApiException.conflict("Your request is already under review");

        AgencyRequest r = new AgencyRequest();
        r.setUserId(u.getId());
        r.setFullName(clean(f.getFullName(), 2, 80, "Name"));
        r.setAgencyName(clean(f.getAgencyName(), 2, 80, "Agency name"));
        r.setExperience(clean(f.getExperience(), 3, 1000, "Experience"));
        r.setExpectedHosts(f.getExpectedHosts());
        r.setAadhaarFrontUrl(f.getAadhaarFrontUrl().trim());
        r.setAadhaarBackUrl(f.getAadhaarBackUrl().trim());
        r.setSelfieUrl(f.getSelfieUrl().trim());
        requests.save(r);

        // tell every super admin who is online; the request also shows in their Requests screen
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", r.getId());
        p.put("text", r.getFullName() + " applied to run the agency \"" + r.getAgencyName() + "\"");
        for (User sa : users.findBySuperAdminTrueOrderByNameAsc()) tell(sa.getId(), "agency:request", p);
        return json(r, u);
    }

    /** The applicant's latest request (null if never applied). */
    @Transactional(readOnly = true)
    public Map<String, Object> mine(User caller) {
        return requests.findFirstByUserIdOrderByCreatedAtDesc(caller.getId()).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("status", r.getStatus());
            m.put("agencyName", r.getAgencyName());
            m.put("createdAt", r.getCreatedAt());
            m.put("decidedAt", r.getDecidedAt());
            m.put("rejectReason", r.getRejectReason());
            return m;
        }).orElse(null);
    }

    // ------------------------------------------------------------------ review (admin panel + super admins)

    @Transactional(readOnly = true)
    public Map<String, Object> list(String status, String q, int page, int size) {
        String st = status == null ? "" : status.trim().toUpperCase();
        if (!st.isEmpty() && !Set.of(AgencyRequest.PENDING, AgencyRequest.APPROVED, AgencyRequest.REJECTED).contains(st)) st = "";
        int s = Math.max(1, Math.min(size, 50));
        Page<AgencyRequest> p = requests.search(st, q == null ? "" : q.trim(), PageRequest.of(Math.max(0, page), s, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<String, User> um = new HashMap<>();
        users.findAllById(p.getContent().stream().map(AgencyRequest::getUserId).toList()).forEach(u -> um.put(u.getId(), u));
        List<Map<String, Object>> items = p.getContent().stream().map(r -> json(r, um.get(r.getUserId()))).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("total", p.getTotalElements());
        out.put("page", p.getNumber());
        out.put("size", s);
        out.put("pages", p.getTotalPages());
        out.put("pending", requests.countByStatus(AgencyRequest.PENDING));
        return out;
    }

    /** Super admins see the same list. */
    @Transactional(readOnly = true)
    public Map<String, Object> listForSuperAdmin(User caller, String status, String q, int page, int size) {
        requireSuperAdmin(caller);
        return list(status, q, page, size);
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return requests.countByStatus(AgencyRequest.PENDING);
    }

    /** Approved by a super admin: the agency works under them. */
    @Transactional
    public Map<String, Object> approveAsSuperAdmin(User caller, String id) {
        User sa = requireSuperAdmin(caller);
        return approve(id, sa.getName() + " (super admin)", sa.getId());
    }

    /** Approved from the admin panel: optionally put the agency under a super admin. */
    @Transactional
    public Map<String, Object> approveAsAdmin(String adminName, String id, String superAdminId) {
        String sa = superAdminId == null || superAdminId.isBlank() ? null : superAdminId.trim();
        if (sa != null && !users.findById(sa).map(User::isSuperAdmin).orElse(false)) throw ApiException.badRequest("That user is not a super admin");
        return approve(id, "Admin: " + adminName, sa);
    }

    private Map<String, Object> approve(String id, String by, String superAdminId) {
        AgencyRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("Request not found"));
        if (!AgencyRequest.PENDING.equals(r.getStatus())) throw ApiException.conflict("This request was already " + r.getStatus().toLowerCase());
        User owner = users.findById(r.getUserId()).orElseThrow(() -> ApiException.notFound("Applicant not found"));
        Agency a = agencyService.createFor(owner, r.getAgencyName(), null, owner.getPhone(), null, null, r.getAadhaarFrontUrl(), null, superAdminId);
        r.setStatus(AgencyRequest.APPROVED);
        r.setDecidedAt(Instant.now());
        r.setDecidedBy(by);
        r.setAgencyId(a.getId());
        requests.save(r);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", r.getId());
        p.put("approved", true);
        p.put("text", "Your agency \"" + a.getName() + "\" was approved. Agency ID " + a.getCode());
        tell(owner.getId(), "agency:request-decided", p);
        return json(r, owner);
    }

    @Transactional
    public Map<String, Object> rejectAsSuperAdmin(User caller, String id, String reason) {
        User sa = requireSuperAdmin(caller);
        return reject(id, sa.getName() + " (super admin)", reason);
    }

    @Transactional
    public Map<String, Object> rejectAsAdmin(String adminName, String id, String reason) {
        return reject(id, "Admin: " + adminName, reason);
    }

    private Map<String, Object> reject(String id, String by, String reason) {
        AgencyRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("Request not found"));
        if (!AgencyRequest.PENDING.equals(r.getStatus())) throw ApiException.conflict("This request was already " + r.getStatus().toLowerCase());
        r.setStatus(AgencyRequest.REJECTED);
        r.setDecidedAt(Instant.now());
        r.setDecidedBy(by);
        r.setRejectReason(reason == null || reason.isBlank() ? null : reason.trim());
        requests.save(r);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", r.getId());
        p.put("approved", false);
        p.put("text", "Your agency request was not approved" + (r.getRejectReason() == null ? "." : ": " + r.getRejectReason()));
        tell(r.getUserId(), "agency:request-decided", p);
        return json(r, users.findById(r.getUserId()).orElse(null));
    }
}
