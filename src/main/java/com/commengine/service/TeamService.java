package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.DtoMapper;
import com.commengine.dto.TeamRequests;
import com.commengine.entity.*;
import com.commengine.repository.*;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The super admin chain:  Super admin -> BD -> Agency -> Host   (a super admin can also add agencies and hosts directly).
 *
 *  - Super admin invites: BD, Agency (new agency for an owner), Host (direct host).
 *  - BD invites: Agency only.  Agency owner invites: Host only (AgencyService).
 *  - Nobody is added until they ACCEPT the invitation from their inbox.
 *  - Everyone below a super admin counts towards that super admin's target.
 *  - One person can hold every role at once (super admin + BD + agency owner + host).
 *    The only limit: a person hosts for ONE agency (the one they own, if any) or is one direct host.
 */
@Service
public class TeamService {

    private static final int MAX_STARTING_HOSTS = 5;

    private final UserRepository users;
    private final AgencyRepository agencies;
    private final AgencyMemberRepository members;
    private final DirectHostRepository directHosts;
    private final BdProfileRepository bds;
    private final TeamInviteRepository teamInvites;
    private final AgencyInviteRepository agencyInvites;
    private final AgencyService agencyService;
    private final SocketIOServer server;

    public TeamService(UserRepository users, AgencyRepository agencies, AgencyMemberRepository members, DirectHostRepository directHosts,
                       BdProfileRepository bds, TeamInviteRepository teamInvites, AgencyInviteRepository agencyInvites,
                       AgencyService agencyService, SocketIOServer server) {
        this.users = users;
        this.agencies = agencies;
        this.members = members;
        this.directHosts = directHosts;
        this.bds = bds;
        this.teamInvites = teamInvites;
        this.agencyInvites = agencyInvites;
        this.agencyService = agencyService;
        this.server = server;
    }

    // ================================================================== who is under whom

    /** Everything under a super admin. */
    public record Downline(List<Agency> agencies, List<BdProfile> bds, List<String> agencyHostIds, List<String> directHostIds, List<String> allHostIds) {}

    @Transactional(readOnly = true)
    public Downline downline(String superAdminId) {
        List<Agency> ags = agencies.findBySuperAdminId(superAdminId);
        List<BdProfile> bdList = bds.findBySuperAdminId(superAdminId);
        List<String> agencyHosts = ags.isEmpty() ? List.of()
                : members.findByAgencyIdIn(ags.stream().map(Agency::getId).toList()).stream().map(AgencyMember::getUserId).distinct().toList();
        List<String> direct = directHosts.findBySuperAdminId(superAdminId).stream().map(DirectHost::getUserId).toList();
        LinkedHashSet<String> all = new LinkedHashSet<>(agencyHosts);
        all.addAll(direct);
        return new Downline(ags, bdList, agencyHosts, direct, new ArrayList<>(all));
    }

    @Transactional(readOnly = true)
    public boolean isBd(String userId) {
        return bds.existsById(userId);
    }

    @Transactional(readOnly = true)
    public Optional<BdProfile> bdProfile(String userId) {
        return bds.findById(userId);
    }

    // ================================================================== helpers

    private User requireSuperAdmin(User caller) {
        User u = users.findById(caller.getId()).orElseThrow(() -> ApiException.forbidden("Not allowed"));
        if (!u.isSuperAdmin() || u.isBanned()) throw ApiException.forbidden("Only super admins can do this");
        return u;
    }

    private BdProfile requireBd(User caller) {
        return bds.findById(caller.getId()).orElseThrow(() -> ApiException.forbidden("Only BDs can do this"));
    }

    private User resolve(String ref) {
        String r = ref == null ? "" : ref.trim();
        if (r.isEmpty()) throw ApiException.badRequest("Enter the user's ID");
        User u = users.findByPublicId(r).orElseThrow(() -> ApiException.notFound("No user with ID " + r));
        if (u.isBanned()) throw ApiException.badRequest(u.getName() + " can't be invited");
        return u;
    }

    private static Map<String, Object> slim(User u) {
        if (u == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("publicId", u.getPublicId());
        m.put("name", u.getName());
        m.put("avatarUrl", u.getAvatarUrl());
        return m;
    }

    private boolean isHostSomewhere(String userId) {
        return members.existsByUserId(userId) || directHosts.existsById(userId);
    }

    private void notifyInvite(TeamInvite inv, String text) {
        try {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("source", "TEAM");
            p.put("id", inv.getId());
            p.put("kind", inv.getKind());
            p.put("text", text);
            server.getRoomOperations("user:" + inv.getToUserId()).sendEvent("team:invite", p);
        } catch (Exception ignored) {
            // the invite is saved; the inbox shows it either way
        }
    }

    private String textFor(TeamInvite inv, String fromName) {
        return switch (inv.getKind()) {
            case TeamInvite.BD -> fromName + " invited you to join their team as a BD";
            case TeamInvite.HOST -> fromName + " invited you to join their team as a host";
            default -> fromName + " invited you to run the agency \"" + inv.getAgencyName() + "\"";
        };
    }

    private Map<String, Object> sentJson(TeamInvite inv, User to) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", inv.getId());
        m.put("kind", inv.getKind());
        m.put("status", inv.getStatus());
        m.put("createdAt", inv.getCreatedAt());
        m.put("agencyName", inv.getAgencyName());
        m.put("to", slim(to));
        return m;
    }

    // ================================================================== sending invitations

    @Transactional
    public Map<String, Object> inviteBd(User caller, String ref) {
        User sa = requireSuperAdmin(caller);
        User to = resolve(ref);
        if (bds.existsById(to.getId())) throw ApiException.conflict(to.getName() + " is already a BD");
        return createSimple(sa, to, TeamInvite.BD, null);
    }

    @Transactional
    public Map<String, Object> inviteDirectHost(User caller, String ref) {
        User sa = requireSuperAdmin(caller);
        User to = resolve(ref);
        if (isHostSomewhere(to.getId())) throw ApiException.conflict(to.getName() + " is already hosting in an agency or under a team");
        return createSimple(sa, to, TeamInvite.HOST, null);
    }

    private Map<String, Object> createSimple(User sa, User to, String kind, String bdUserId) {
        if (teamInvites.findByFromUserIdAndToUserIdAndKindAndStatus(sa.getId(), to.getId(), kind, TeamInvite.PENDING).isPresent()) {
            throw ApiException.conflict("You already invited " + to.getName());
        }
        TeamInvite inv = new TeamInvite();
        inv.setKind(kind);
        inv.setFromUserId(sa.getId());
        inv.setToUserId(to.getId());
        inv.setSuperAdminId(sa.getId());
        inv.setBdUserId(bdUserId);
        teamInvites.save(inv);
        notifyInvite(inv, textFor(inv, sa.getName()));
        return sentJson(inv, to);
    }

    /** A super admin (asBd = false) or a BD (asBd = true) registers a new agency. The owner gets it in their inbox. */
    @Transactional
    public Map<String, Object> inviteAgency(User caller, boolean asBd, TeamRequests.InviteAgency f) {
        String superAdminId;
        String bdUserId = null;
        User inviter;
        if (asBd) {
            BdProfile bd = requireBd(caller);
            if (bd.getSuperAdminId() == null) throw ApiException.conflict("Your BD account is not linked to a super admin yet");
            inviter = users.findById(caller.getId()).orElseThrow(() -> ApiException.forbidden("Not allowed"));
            superAdminId = bd.getSuperAdminId();
            bdUserId = bd.getUserId();
        } else {
            inviter = requireSuperAdmin(caller);
            superAdminId = inviter.getId();
        }

        User owner = resolve(f.getOwnerId());
        if (members.existsByUserId(owner.getId())) throw ApiException.conflict(owner.getName() + " is already hosting in an agency");
        if (agencies.findByOwnerId(owner.getId()).isPresent()) throw ApiException.conflict(owner.getName() + " already runs an agency");
        if (teamInvites.findByFromUserIdAndToUserIdAndKindAndStatus(inviter.getId(), owner.getId(), TeamInvite.AGENCY, TeamInvite.PENDING).isPresent()) {
            throw ApiException.conflict("You already invited " + owner.getName() + " to run an agency");
        }

        List<String> hostIds = new ArrayList<>();
        for (String ref : f.getHostIds() == null ? List.<String>of() : f.getHostIds()) {
            if (ref == null || ref.isBlank()) continue;
            User h = resolve(ref);
            if (h.getId().equals(owner.getId())) continue; // the owner hosts in their own agency already
            if (isHostSomewhere(h.getId())) throw ApiException.conflict(h.getName() + " is already hosting in an agency or under a team");
            if (!hostIds.contains(h.getId())) hostIds.add(h.getId());
        }
        if (hostIds.size() > MAX_STARTING_HOSTS) throw ApiException.badRequest("At most " + MAX_STARTING_HOSTS + " starting hosts");

        String whatsapp = f.getWhatsapp().trim();
        if (!whatsapp.matches("^[+0-9][0-9 \\-]{5,28}$")) throw ApiException.badRequest("Enter a valid WhatsApp number");

        TeamInvite inv = new TeamInvite();
        inv.setKind(TeamInvite.AGENCY);
        inv.setFromUserId(inviter.getId());
        inv.setToUserId(owner.getId());
        inv.setSuperAdminId(superAdminId);
        inv.setBdUserId(bdUserId);
        inv.setAgencyName(f.getName().trim());
        inv.setWhatsapp(whatsapp);
        inv.setCountry(f.getCountry().trim());
        inv.setLogoUrl(blankToNull(f.getLogoUrl()));
        inv.setIdProofUrl(blankToNull(f.getIdProofUrl()));
        inv.setHostIds(String.join(",", hostIds));
        teamInvites.save(inv);
        notifyInvite(inv, textFor(inv, inviter.getName()));
        return sentJson(inv, owner);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Invitations I sent that nobody answered yet. kind = BD / AGENCY / HOST or empty for all. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> sent(User caller, String kind) {
        List<TeamInvite> list = teamInvites.findByFromUserIdAndStatusOrderByCreatedAtDesc(caller.getId(), TeamInvite.PENDING);
        String k = kind == null ? "" : kind.trim().toUpperCase();
        list = list.stream().filter(i -> k.isEmpty() || i.getKind().equals(k)).toList();
        Map<String, User> um = new HashMap<>();
        users.findAllById(list.stream().map(TeamInvite::getToUserId).toList()).forEach(u -> um.put(u.getId(), u));
        return list.stream().map(i -> sentJson(i, um.get(i.getToUserId()))).toList();
    }

    @Transactional
    public void cancel(User caller, String id) {
        TeamInvite inv = teamInvites.findById(id).filter(i -> i.getFromUserId().equals(caller.getId()))
                .orElseThrow(() -> ApiException.notFound("Invitation not found"));
        if (!TeamInvite.PENDING.equals(inv.getStatus())) return;
        inv.setStatus(TeamInvite.CANCELLED);
        inv.setRespondedAt(Instant.now());
        teamInvites.save(inv);
    }

    // ================================================================== inbox

    /** Everything waiting for this person's answer: team invitations and agency host invitations. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> inbox(User user) {
        List<Map<String, Object>> out = new ArrayList<>();

        List<TeamInvite> team = teamInvites.findByToUserIdAndStatusOrderByCreatedAtDesc(user.getId(), TeamInvite.PENDING);
        List<AgencyInvite> host = agencyInvites.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), AgencyInvite.PENDING);

        Set<String> ids = new HashSet<>();
        team.forEach(i -> ids.add(i.getFromUserId()));
        Map<String, Agency> agencyById = new HashMap<>();
        for (AgencyInvite i : host) {
            agencies.findById(i.getAgencyId()).ifPresent(a -> {
                agencyById.put(a.getId(), a);
                ids.add(a.getOwnerId());
            });
        }
        Map<String, User> um = new HashMap<>();
        users.findAllById(ids).forEach(u -> um.put(u.getId(), u));

        for (TeamInvite i : team) {
            User from = um.get(i.getFromUserId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", "TEAM");
            m.put("id", i.getId());
            m.put("kind", i.getKind());
            m.put("text", textFor(i, from == null ? "Someone" : from.getName()));
            m.put("from", slim(from));
            m.put("agencyName", i.getAgencyName());
            m.put("createdAt", i.getCreatedAt());
            out.add(m);
        }
        for (AgencyInvite i : host) {
            Agency a = agencyById.get(i.getAgencyId());
            if (a == null || !a.isActive()) continue;
            User from = um.get(a.getOwnerId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", "AGENCY");
            m.put("id", i.getId());
            m.put("kind", "HOST_OF_AGENCY");
            m.put("text", (from == null ? "An agency owner" : from.getName()) + " invited you to host for the agency \"" + a.getName() + "\"");
            m.put("from", slim(from));
            m.put("agencyName", a.getName());
            m.put("createdAt", i.getCreatedAt());
            out.add(m);
        }
        out.sort((x, y) -> ((Instant) y.get("createdAt")).compareTo((Instant) x.get("createdAt")));
        return out;
    }

    /** For the Messages tab row: how many are waiting and the newest line. */
    @Transactional(readOnly = true)
    public Map<String, Object> inboxSummary(User user) {
        List<Map<String, Object>> list = inbox(user);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", list.size());
        m.put("latestText", list.isEmpty() ? null : list.get(0).get("text"));
        m.put("latestAt", list.isEmpty() ? null : list.get(0).get("createdAt"));
        return m;
    }

    @Transactional
    public Map<String, Object> respond(User user, String source, String id, boolean accept) {
        if ("AGENCY".equalsIgnoreCase(source)) return agencyService.respond(user, id, accept);
        if (!"TEAM".equalsIgnoreCase(source)) throw ApiException.badRequest("Unknown invitation type");

        TeamInvite inv = teamInvites.findById(id).filter(i -> i.getToUserId().equals(user.getId()))
                .orElseThrow(() -> ApiException.notFound("Invitation not found"));
        if (!TeamInvite.PENDING.equals(inv.getStatus())) throw ApiException.conflict("This invitation is no longer open");
        Map<String, Object> out = new LinkedHashMap<>();

        if (!accept) {
            inv.setStatus(TeamInvite.DECLINED);
            inv.setRespondedAt(Instant.now());
            teamInvites.save(inv);
            out.put("joined", false);
            return out;
        }

        // the sender must still have the right to add people
        boolean fromBd = inv.getBdUserId() != null;
        boolean senderOk = fromBd
                ? bds.existsById(inv.getBdUserId())
                : users.findById(inv.getFromUserId()).map(User::isSuperAdmin).orElse(false);
        if (!senderOk) throw ApiException.conflict("This invitation is no longer valid");

        switch (inv.getKind()) {
            case TeamInvite.BD -> {
                if (bds.existsById(user.getId())) throw ApiException.conflict("You are already a BD");
                BdProfile p = new BdProfile();
                p.setUserId(user.getId());
                p.setSuperAdminId(inv.getSuperAdminId());
                bds.save(p);
            }
            case TeamInvite.HOST -> {
                if (isHostSomewhere(user.getId())) throw ApiException.conflict("You are already hosting. Leave your current team or agency first.");
                DirectHost d = new DirectHost();
                d.setUserId(user.getId());
                d.setSuperAdminId(inv.getSuperAdminId());
                directHosts.save(d);
            }
            case TeamInvite.AGENCY -> {
                Agency a = agencyService.createFor(user, inv.getAgencyName(), null, inv.getWhatsapp(), inv.getCountry(),
                        inv.getLogoUrl(), inv.getIdProofUrl(), inv.getBdUserId(), inv.getSuperAdminId());
                // starting hosts get a normal host invitation from the new agency
                if (inv.getHostIds() != null && !inv.getHostIds().isBlank()) {
                    for (String hid : inv.getHostIds().split(",")) {
                        User h = users.findById(hid).orElse(null);
                        if (h == null || isHostSomewhere(h.getId())) continue;
                        AgencyInvite ai = agencyService.inviteHostInternal(a, h);
                        try {
                            Map<String, Object> p = new LinkedHashMap<>();
                            p.put("source", "AGENCY");
                            p.put("id", ai.getId());
                            p.put("kind", "HOST_OF_AGENCY");
                            p.put("text", user.getName() + " invited you to host for the agency \"" + a.getName() + "\"");
                            server.getRoomOperations("user:" + h.getId()).sendEvent("team:invite", p);
                        } catch (Exception ignored) {
                            // visible in the inbox anyway
                        }
                    }
                }
            }
            default -> throw ApiException.badRequest("Unknown invitation type");
        }
        inv.setStatus(TeamInvite.ACCEPTED);
        inv.setRespondedAt(Instant.now());
        teamInvites.save(inv);
        out.put("joined", true);
        return out;
    }

    /** Super admins and BDs may upload images for the Invite Agency form. */
    @Transactional(readOnly = true)
    public boolean canInvite(User user) {
        return bds.existsById(user.getId()) || users.findById(user.getId()).map(User::isSuperAdmin).orElse(false);
    }

    // ================================================================== roles (what the app shows for this person)

    @Transactional(readOnly = true)
    public Map<String, Object> roles(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean sa = users.findById(user.getId()).map(User::isSuperAdmin).orElse(false);
        m.put("isSuperAdmin", sa);
        m.put("isBd", bds.existsById(user.getId()));
        m.put("pendingInvitations", inbox(user).size());
        return m;
    }
}
