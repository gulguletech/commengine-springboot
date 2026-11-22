package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.AgencyRequests;
import com.commengine.dto.DtoMapper;
import com.commengine.entity.*;
import com.commengine.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Agencies: an admin creates an agency and picks its owner; the owner invites hosts; a user is in at most one agency.
 *
 * Diamonds = beans a host earned from gifts (WalletTransaction GIFT_RECEIVED) since they joined the agency.
 * Working time = time inside their own audio room / live (HostSession).
 * "This period" = the current calendar month in India time. Salaries are DISPLAY ONLY: the level a host's diamonds reach
 * in the admin-edited ladder decides the host salary and the agency commission. Nothing is paid out from here.
 */
@Service
@Transactional(readOnly = true)
public class AgencyService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final String CODE_PREFIX = "MMA";

    private final AgencyRepository agencies;
    private final AgencyMemberRepository members;
    private final AgencyInviteRepository invites;
    private final AgencyLevelRepository levels;
    private final UserRepository users;
    private final WalletTransactionRepository walletTx;
    private final HostSessionService sessions;

    public AgencyService(AgencyRepository agencies, AgencyMemberRepository members, AgencyInviteRepository invites,
                         AgencyLevelRepository levels, UserRepository users, WalletTransactionRepository walletTx,
                         HostSessionService sessions) {
        this.agencies = agencies;
        this.members = members;
        this.invites = invites;
        this.levels = levels;
        this.users = users;
        this.walletTx = walletTx;
        this.sessions = sessions;
    }

    // ------------------------------------------------------------------ time windows

    private record Window(Instant from, Instant to) {}

    private static Window today() {
        LocalDate d = LocalDate.now(ZONE);
        return new Window(d.atStartOfDay(ZONE).toInstant(), d.plusDays(1).atStartOfDay(ZONE).toInstant());
    }

    private static Window period() {
        LocalDate first = LocalDate.now(ZONE).withDayOfMonth(1);
        return new Window(first.atStartOfDay(ZONE).toInstant(), first.plusMonths(1).atStartOfDay(ZONE).toInstant());
    }

    private static String periodLabel() {
        LocalDate d = LocalDate.now(ZONE);
        return d.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + d.getYear();
    }

    // ------------------------------------------------------------------ levels + stats

    private static AgencyLevel levelFor(List<AgencyLevel> sorted, long diamonds) {
        AgencyLevel hit = null;
        for (AgencyLevel l : sorted) if (diamonds >= l.getDiamonds()) hit = l;
        return hit;
    }

    private static AgencyLevel nextLevel(List<AgencyLevel> sorted, long diamonds) {
        for (AgencyLevel l : sorted) if (diamonds < l.getDiamonds()) return l;
        return null;
    }

    private Map<String, Object> stat(AgencyMember m, Window w, List<AgencyLevel> ladder) {
        Instant from = m.getJoinedAt().isAfter(w.from()) ? m.getJoinedAt() : w.from();
        long diamonds = from.isBefore(w.to()) ? walletTx.sumGiftBeans(m.getUserId(), from, w.to()) : 0;
        long seconds = sessions.seconds(m.getUserId(), from, w.to());
        AgencyLevel lv = levelFor(ladder, diamonds);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("diamonds", diamonds);
        out.put("workingSeconds", seconds);
        out.put("level", lv == null ? 0 : lv.getLevel());
        out.put("hostSalary", lv == null ? BigDecimal.ZERO : lv.getHostSalary());
        out.put("agencySalary", lv == null ? BigDecimal.ZERO : lv.getAgencySalary());
        return out;
    }

    private static long l(Object o) { return ((Number) o).longValue(); }
    private static BigDecimal bd(Object o) { return (BigDecimal) o; }

    private Map<String, User> userMap(Collection<String> ids) {
        Map<String, User> map = new HashMap<>();
        users.findAllById(ids).forEach(u -> map.put(u.getId(), u));
        return map;
    }

    private static Map<String, Object> userSlim(User u) {
        if (u == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("publicId", u.getPublicId());
        m.put("name", u.getName());
        m.put("avatarUrl", u.getAvatarUrl());
        return m;
    }

    private static Map<String, Object> levelJson(AgencyLevel l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("level", l.getLevel());
        m.put("diamonds", l.getDiamonds());
        m.put("hostSalary", l.getHostSalary());
        m.put("agencySalary", l.getAgencySalary());
        m.put("total", l.getHostSalary().add(l.getAgencySalary()));
        return m;
    }

    private Map<String, Object> agencyJson(Agency a, User owner) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("code", a.getCode());
        m.put("name", a.getName());
        m.put("welcomeMessage", a.getWelcomeMessage());
        m.put("active", a.isActive());
        m.put("createdAt", a.getCreatedAt());
        m.put("owner", userSlim(owner));
        return m;
    }

    private String newCode() {
        for (int i = 0; i < 50; i++) {
            String code = CODE_PREFIX + (100000 + ThreadLocalRandom.current().nextInt(900000));
            if (!agencies.existsByCode(code)) return code;
        }
        throw ApiException.conflict("Could not generate an agency ID, try again");
    }

    // ================================================================== APP

    public List<Map<String, Object>> levels() {
        return levels.findAllByOrderByLevelAsc().stream().map(AgencyService::levelJson).toList();
    }

    /** What the Agency tile shows: owners get the dashboard, everybody else gets "apply" (+ their invitations). */
    public Map<String, Object> me(User user) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pendingInvitations", invites.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), AgencyInvite.PENDING).size());
        AgencyMember me = members.findByUserId(user.getId()).orElse(null);
        if (me == null) {
            out.put("isOwner", false);
            out.put("memberOf", null);
            return out;
        }
        Agency a = agencies.findById(me.getAgencyId()).orElse(null);
        if (a == null) {
            out.put("isOwner", false);
            out.put("memberOf", null);
            return out;
        }
        if (!AgencyMember.OWNER.equals(me.getRole())) {
            Map<String, Object> of = new LinkedHashMap<>();
            of.put("code", a.getCode());
            of.put("name", a.getName());
            out.put("isOwner", false);
            out.put("memberOf", of);
            return out;
        }

        out.put("isOwner", true);
        out.put("memberOf", null);
        out.put("agency", agencyJson(a, user));
        if (!a.isActive()) {
            out.put("disabled", true);
            return out;
        }

        List<AgencyLevel> ladder = levels.findAllByOrderByLevelAsc();
        Window day = today();
        Window per = period();
        List<AgencyMember> all = members.findByAgencyIdOrderByJoinedAtAsc(a.getId());

        long todayDiamonds = 0, todaySeconds = 0, perDiamonds = 0, perSeconds = 0;
        BigDecimal hostSalaries = BigDecimal.ZERO, commission = BigDecimal.ZERO;
        BigDecimal ownHostSalary = BigDecimal.ZERO;
        long ownDiamonds = 0;
        for (AgencyMember m : all) {
            Map<String, Object> t = stat(m, day, ladder);
            todayDiamonds += l(t.get("diamonds"));
            todaySeconds += l(t.get("workingSeconds"));
            Map<String, Object> p = stat(m, per, ladder);
            perDiamonds += l(p.get("diamonds"));
            perSeconds += l(p.get("workingSeconds"));
            hostSalaries = hostSalaries.add(bd(p.get("hostSalary")));
            commission = commission.add(bd(p.get("agencySalary")));
            if (m.getUserId().equals(user.getId())) {
                ownDiamonds = l(p.get("diamonds"));
                ownHostSalary = bd(p.get("hostSalary"));
            }
        }

        Map<String, Object> todayJson = new LinkedHashMap<>();
        todayJson.put("diamonds", todayDiamonds);
        todayJson.put("workingSeconds", todaySeconds);
        out.put("today", todayJson);

        Map<String, Object> per2 = new LinkedHashMap<>();
        per2.put("label", periodLabel());
        per2.put("diamonds", perDiamonds);
        per2.put("workingSeconds", perSeconds);
        per2.put("hostSalaries", hostSalaries);
        per2.put("agencyCommission", commission);
        per2.put("totalSalary", ownHostSalary.add(commission));
        out.put("period", per2);

        // the owner's own progress up the ladder (the bar in the dashboard)
        AgencyLevel cur = levelFor(ladder, ownDiamonds);
        AgencyLevel nxt = nextLevel(ladder, ownDiamonds);
        Map<String, Object> own = new LinkedHashMap<>();
        own.put("diamonds", ownDiamonds);
        own.put("level", cur == null ? 0 : cur.getLevel());
        own.put("hostSalary", ownHostSalary);
        own.put("nextLevel", nxt == null ? null : nxt.getLevel());
        own.put("nextTarget", nxt == null ? null : nxt.getDiamonds());
        own.put("nextHostSalary", nxt == null ? null : nxt.getHostSalary());
        own.put("remaining", nxt == null ? 0 : nxt.getDiamonds() - ownDiamonds);
        out.put("ownerLadder", own);

        out.put("memberCount", all.size());
        return out;
    }

    private Agency requireOwnedActive(User owner) {
        AgencyMember me = members.findByUserId(owner.getId()).orElseThrow(() -> ApiException.forbidden("You don't own an agency"));
        if (!AgencyMember.OWNER.equals(me.getRole())) throw ApiException.forbidden("Only the agency owner can do this");
        Agency a = agencies.findById(me.getAgencyId()).orElseThrow(() -> ApiException.notFound("Agency not found"));
        if (!a.isActive()) throw ApiException.forbidden("This agency is disabled");
        return a;
    }

    public List<Map<String, Object>> memberList(User owner) {
        Agency a = requireOwnedActive(owner);
        List<AgencyLevel> ladder = levels.findAllByOrderByLevelAsc();
        Window day = today();
        Window per = period();
        List<AgencyMember> all = members.findByAgencyIdOrderByJoinedAtAsc(a.getId());
        Map<String, User> um = userMap(all.stream().map(AgencyMember::getUserId).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgencyMember m : all) {
            Map<String, Object> t = stat(m, day, ladder);
            Map<String, Object> p = stat(m, per, ladder);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user", userSlim(um.get(m.getUserId())));
            row.put("role", m.getRole());
            row.put("joinedAt", m.getJoinedAt());
            row.put("todayDiamonds", t.get("diamonds"));
            row.put("diamonds", p.get("diamonds"));
            row.put("workingSeconds", p.get("workingSeconds"));
            row.put("level", p.get("level"));
            row.put("hostSalary", p.get("hostSalary"));
            row.put("agencyCommission", p.get("agencySalary"));
            out.add(row);
        }
        return out;
    }

    /** Day-by-day numbers for one member (like the "lucky" screen in the reference app). */
    public Map<String, Object> memberDaily(User owner, String memberUserId, int days) {
        Agency a = requireOwnedActive(owner);
        AgencyMember m = members.findByUserId(memberUserId).filter(x -> x.getAgencyId().equals(a.getId()))
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        List<AgencyLevel> ladder = levels.findAllByOrderByLevelAsc();
        int n = Math.max(1, Math.min(days, 31));
        LocalDate today = LocalDate.now(ZONE);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LocalDate d = today.minusDays(i);
            Window w = new Window(d.atStartOfDay(ZONE).toInstant(), d.plusDays(1).atStartOfDay(ZONE).toInstant());
            Map<String, Object> s = stat(m, w, ladder);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString());
            row.put("diamonds", s.get("diamonds"));
            row.put("workingSeconds", s.get("workingSeconds"));
            rows.add(row);
        }
        Map<String, Object> per = stat(m, period(), ladder);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", userSlim(users.findById(memberUserId).orElse(null)));
        out.put("period", Map.of("label", periodLabel(), "diamonds", per.get("diamonds"), "workingSeconds", per.get("workingSeconds"),
                "level", per.get("level"), "hostSalary", per.get("hostSalary"), "agencyCommission", per.get("agencySalary")));
        out.put("days", rows);
        return out;
    }

    @Transactional
    public Map<String, Object> invite(User owner, String hostPublicId) {
        Agency a = requireOwnedActive(owner);
        String pid = hostPublicId == null ? "" : hostPublicId.trim();
        if (pid.isEmpty()) throw ApiException.badRequest("Enter the host's ID");
        User host = users.findByPublicId(pid).orElseThrow(() -> ApiException.notFound("No user with that ID"));
        if (host.isBanned()) throw ApiException.badRequest("This user can't be invited");
        if (members.existsByUserId(host.getId())) {
            throw ApiException.conflict(host.getId().equals(owner.getId())
                    ? "You are already in your agency"
                    : "This user is already hosting in an agency or runs their own");
        }
        if (invites.findByAgencyIdAndUserIdAndStatus(a.getId(), host.getId(), AgencyInvite.PENDING).isPresent()) {
            throw ApiException.conflict("You already invited this user");
        }
        AgencyInvite inv = new AgencyInvite();
        inv.setAgencyId(a.getId());
        inv.setUserId(host.getId());
        invites.save(inv);
        return inviteJson(inv, a, owner, host);
    }

    public List<Map<String, Object>> sentInvites(User owner) {
        Agency a = requireOwnedActive(owner);
        List<AgencyInvite> list = invites.findByAgencyIdAndStatusOrderByCreatedAtDesc(a.getId(), AgencyInvite.PENDING);
        Map<String, User> um = userMap(list.stream().map(AgencyInvite::getUserId).toList());
        return list.stream().map(i -> inviteJson(i, a, owner, um.get(i.getUserId()))).toList();
    }

    @Transactional
    public void cancelInvite(User owner, String inviteId) {
        Agency a = requireOwnedActive(owner);
        AgencyInvite inv = invites.findById(inviteId).filter(i -> i.getAgencyId().equals(a.getId()))
                .orElseThrow(() -> ApiException.notFound("Invitation not found"));
        if (!AgencyInvite.PENDING.equals(inv.getStatus())) return;
        inv.setStatus(AgencyInvite.CANCELLED);
        inv.setRespondedAt(Instant.now());
        invites.save(inv);
    }

    @Transactional
    public void removeMember(User owner, String memberUserId) {
        Agency a = requireOwnedActive(owner);
        AgencyMember m = members.findByUserId(memberUserId).filter(x -> x.getAgencyId().equals(a.getId()))
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        if (AgencyMember.OWNER.equals(m.getRole())) throw ApiException.badRequest("The owner can't be removed");
        members.delete(m);
    }

    public List<Map<String, Object>> myInvitations(User user) {
        List<AgencyInvite> list = invites.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), AgencyInvite.PENDING);
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgencyInvite i : list) {
            Agency a = agencies.findById(i.getAgencyId()).orElse(null);
            if (a == null || !a.isActive()) continue;
            out.add(inviteJson(i, a, users.findById(a.getOwnerId()).orElse(null), user));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> respond(User user, String inviteId, boolean accept) {
        AgencyInvite inv = invites.findById(inviteId).filter(i -> i.getUserId().equals(user.getId()))
                .orElseThrow(() -> ApiException.notFound("Invitation not found"));
        if (!AgencyInvite.PENDING.equals(inv.getStatus())) throw ApiException.conflict("This invitation is no longer open");
        Map<String, Object> out = new LinkedHashMap<>();
        if (!accept) {
            inv.setStatus(AgencyInvite.DECLINED);
            inv.setRespondedAt(Instant.now());
            invites.save(inv);
            out.put("joined", false);
            return out;
        }
        Agency a = agencies.findById(inv.getAgencyId()).orElseThrow(() -> ApiException.notFound("Agency not found"));
        if (!a.isActive()) throw ApiException.badRequest("This agency is not active");
        if (members.existsByUserId(user.getId())) throw ApiException.conflict("You are already in an agency");
        AgencyMember m = new AgencyMember();
        m.setAgencyId(a.getId());
        m.setUserId(user.getId());
        m.setRole(AgencyMember.HOST);
        members.save(m);
        inv.setStatus(AgencyInvite.ACCEPTED);
        inv.setRespondedAt(Instant.now());
        invites.save(inv);
        invites.cancelPendingForUser(user.getId()); // the other open invitations are void now
        out.put("joined", true);
        return out;
    }

    private Map<String, Object> inviteJson(AgencyInvite i, Agency a, User owner, User invitee) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("status", i.getStatus());
        m.put("createdAt", i.getCreatedAt());
        Map<String, Object> ag = new LinkedHashMap<>();
        ag.put("id", a.getId());
        ag.put("code", a.getCode());
        ag.put("name", a.getName());
        ag.put("welcomeMessage", a.getWelcomeMessage());
        m.put("agency", ag);
        m.put("owner", owner == null ? null : DtoMapper.userBrief(owner));
        m.put("user", invitee == null ? null : userSlim(invitee));
        return m;
    }

    // ================================================================== ADMIN: agencies

    public Map<String, Object> adminList(String q, int page, int size) {
        int s = Math.max(1, Math.min(size, 100));
        Page<Agency> p = agencies.adminSearch(q == null ? "" : q.trim(), PageRequest.of(Math.max(0, page), s, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<String, User> um = userMap(p.getContent().stream().map(Agency::getOwnerId).toList());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Agency a : p.getContent()) {
            Map<String, Object> m = agencyJson(a, um.get(a.getOwnerId()));
            m.put("memberCount", members.countByAgencyId(a.getId()));
            items.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("total", p.getTotalElements());
        out.put("page", p.getNumber());
        out.put("size", s);
        out.put("pages", p.getTotalPages());
        return out;
    }

    public Map<String, Object> adminGet(String id) {
        Agency a = agencies.findById(id).orElseThrow(() -> ApiException.notFound("Agency not found"));
        List<AgencyLevel> ladder = levels.findAllByOrderByLevelAsc();
        Window per = period();
        List<AgencyMember> all = members.findByAgencyIdOrderByJoinedAtAsc(a.getId());
        Map<String, User> um = userMap(all.stream().map(AgencyMember::getUserId).toList());

        long diamonds = 0, seconds = 0;
        BigDecimal hostSalaries = BigDecimal.ZERO, commission = BigDecimal.ZERO;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AgencyMember m : all) {
            Map<String, Object> p = stat(m, per, ladder);
            diamonds += l(p.get("diamonds"));
            seconds += l(p.get("workingSeconds"));
            hostSalaries = hostSalaries.add(bd(p.get("hostSalary")));
            commission = commission.add(bd(p.get("agencySalary")));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user", userSlim(um.get(m.getUserId())));
            row.put("role", m.getRole());
            row.put("joinedAt", m.getJoinedAt());
            row.put("diamonds", p.get("diamonds"));
            row.put("workingSeconds", p.get("workingSeconds"));
            row.put("level", p.get("level"));
            row.put("hostSalary", p.get("hostSalary"));
            row.put("agencyCommission", p.get("agencySalary"));
            rows.add(row);
        }
        Map<String, Object> out = agencyJson(a, um.get(a.getOwnerId()) != null ? um.get(a.getOwnerId()) : users.findById(a.getOwnerId()).orElse(null));
        out.put("members", rows);
        out.put("pendingInvites", invites.countByAgencyIdAndStatus(a.getId(), AgencyInvite.PENDING));
        Map<String, Object> period = new LinkedHashMap<>();
        period.put("label", periodLabel());
        period.put("diamonds", diamonds);
        period.put("workingSeconds", seconds);
        period.put("hostSalaries", hostSalaries);
        period.put("agencyCommission", commission);
        out.put("period", period);
        return out;
    }

    private void requireFreeUser(User u, String agencyIdAllowed) {
        AgencyMember existing = members.findByUserId(u.getId()).orElse(null);
        if (existing != null && !existing.getAgencyId().equals(agencyIdAllowed)) {
            throw ApiException.conflict(u.getName() + " is already in another agency");
        }
    }

    @Transactional
    public Map<String, Object> adminCreate(AgencyRequests.AdminCreate r) {
        User owner = users.findById(r.getOwnerId()).orElseThrow(() -> ApiException.notFound("Owner user not found"));
        if (members.existsByUserId(owner.getId())) throw ApiException.conflict(owner.getName() + " is already in an agency");
        Agency a = new Agency();
        a.setCode(newCode());
        a.setName(r.getName().trim());
        a.setWelcomeMessage(r.getWelcomeMessage() == null || r.getWelcomeMessage().isBlank() ? null : r.getWelcomeMessage().trim());
        a.setOwnerId(owner.getId());
        agencies.save(a);
        AgencyMember m = new AgencyMember();
        m.setAgencyId(a.getId());
        m.setUserId(owner.getId());
        m.setRole(AgencyMember.OWNER);
        members.save(m);
        invites.cancelPendingForUser(owner.getId());
        return agencyJson(a, owner);
    }

    @Transactional
    public Map<String, Object> adminUpdate(String id, AgencyRequests.AdminUpdate r) {
        Agency a = agencies.findById(id).orElseThrow(() -> ApiException.notFound("Agency not found"));
        if (r.getName() != null) {
            if (r.getName().isBlank()) throw ApiException.badRequest("Name can't be empty");
            a.setName(r.getName().trim());
        }
        if (r.getWelcomeMessage() != null) a.setWelcomeMessage(r.getWelcomeMessage().isBlank() ? null : r.getWelcomeMessage().trim());
        if (r.getActive() != null) a.setActive(r.getActive());

        if (r.getOwnerId() != null && !r.getOwnerId().isBlank() && !r.getOwnerId().equals(a.getOwnerId())) {
            User nu = users.findById(r.getOwnerId()).orElseThrow(() -> ApiException.notFound("New owner not found"));
            requireFreeUser(nu, a.getId());
            AgencyMember nm = members.findByUserId(nu.getId()).orElse(null);
            if (nm == null) {
                nm = new AgencyMember();
                nm.setAgencyId(a.getId());
                nm.setUserId(nu.getId());
            }
            nm.setRole(AgencyMember.OWNER);
            members.save(nm);
            members.findByUserId(a.getOwnerId()).ifPresent(old -> {
                old.setRole(AgencyMember.HOST);
                members.save(old);
            });
            invites.cancelPendingForUser(nu.getId());
            a.setOwnerId(nu.getId());
        }
        agencies.save(a);
        return agencyJson(a, users.findById(a.getOwnerId()).orElse(null));
    }

    @Transactional
    public void adminDelete(String id) {
        if (!agencies.existsById(id)) throw ApiException.notFound("Agency not found");
        members.deleteByAgencyId(id);
        invites.deleteByAgencyId(id);
        agencies.deleteById(id);
    }

    @Transactional
    public void adminAddMember(String agencyId, String userId) {
        Agency a = agencies.findById(agencyId).orElseThrow(() -> ApiException.notFound("Agency not found"));
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (members.existsByUserId(u.getId())) throw ApiException.conflict(u.getName() + " is already in an agency");
        AgencyMember m = new AgencyMember();
        m.setAgencyId(a.getId());
        m.setUserId(u.getId());
        m.setRole(AgencyMember.HOST);
        members.save(m);
        invites.cancelPendingForUser(u.getId());
    }

    @Transactional
    public void adminRemoveMember(String agencyId, String userId) {
        AgencyMember m = members.findByUserId(userId).filter(x -> x.getAgencyId().equals(agencyId))
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        if (AgencyMember.OWNER.equals(m.getRole())) throw ApiException.badRequest("Change the owner first, the owner can't be removed");
        members.delete(m);
    }

    // ================================================================== ADMIN: levels

    public List<Map<String, Object>> adminLevels() {
        return levels();
    }

    @Transactional
    public Map<String, Object> adminCreateLevel(AgencyRequests.LevelCreate r) {
        if (levels.existsByLevel(r.getLevel())) throw ApiException.conflict("Level " + r.getLevel() + " already exists");
        AgencyLevel l = new AgencyLevel();
        l.setLevel(r.getLevel());
        l.setDiamonds(r.getDiamonds());
        l.setHostSalary(r.getHostSalary());
        l.setAgencySalary(r.getAgencySalary());
        return levelJson(levels.save(l));
    }

    @Transactional
    public Map<String, Object> adminUpdateLevel(String id, AgencyRequests.LevelUpdate r) {
        AgencyLevel l = levels.findById(id).orElseThrow(() -> ApiException.notFound("Level not found"));
        if (r.getLevel() != null && r.getLevel() != l.getLevel()) {
            if (levels.existsByLevel(r.getLevel())) throw ApiException.conflict("Level " + r.getLevel() + " already exists");
            l.setLevel(r.getLevel());
        }
        if (r.getDiamonds() != null) l.setDiamonds(r.getDiamonds());
        if (r.getHostSalary() != null) l.setHostSalary(r.getHostSalary());
        if (r.getAgencySalary() != null) l.setAgencySalary(r.getAgencySalary());
        return levelJson(levels.save(l));
    }

    @Transactional
    public void adminDeleteLevel(String id) {
        if (!levels.existsById(id)) throw ApiException.notFound("Level not found");
        levels.deleteById(id);
    }
}
