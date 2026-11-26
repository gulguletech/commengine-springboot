package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.dto.SuperAdminRequests;
import com.commengine.entity.*;
import com.commengine.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.stream.Collectors;
import java.util.*;

/**
 * The super admin portal: dashboard, team (agencies + hosts), reports, and giving official wares / badges.
 * Only users flagged superAdmin (set in the admin panel) get in, and a super admin can never also be in an agency.
 * Windows are UTC, like the reference screens. "Team" = every agency and host in the app (no BD / admin levels yet).
 */
@Service
public class SuperAdminService {

    private static final int MAX_CUSTOM_DAYS = 62;
    private static final int LIST_LIMIT = 200;

    private final UserRepository users;
    private final SuperAdminSettingsRepository settingsRepo;
    private final AgencyRepository agencies;
    private final AgencyMemberRepository members;
    private final GiftLogRepository giftLogs;
    private final PaymentOrderRepository payments;
    private final HostSessionRepository hostSessionRepo;
    private final HostSessionService hostSessions;
    private final StoreService store;
    private final BadgeService badgeService;
    private final OfficialGrantRepository grants;
    private final TeamService team;
    private final AgencyRequestRepository agencyRequestRepo;

    public SuperAdminService(UserRepository users, SuperAdminSettingsRepository settingsRepo, AgencyRepository agencies,
                             AgencyMemberRepository members, GiftLogRepository giftLogs, PaymentOrderRepository payments,
                             HostSessionRepository hostSessionRepo, HostSessionService hostSessions, StoreService store,
                             BadgeService badgeService, OfficialGrantRepository grants, TeamService team,
                             AgencyRequestRepository agencyRequestRepo) {
        this.team = team;
        this.agencyRequestRepo = agencyRequestRepo;
        this.users = users;
        this.settingsRepo = settingsRepo;
        this.agencies = agencies;
        this.members = members;
        this.giftLogs = giftLogs;
        this.payments = payments;
        this.hostSessionRepo = hostSessionRepo;
        this.hostSessions = hostSessions;
        this.store = store;
        this.badgeService = badgeService;
        this.grants = grants;
    }

    // ================================================================== helpers

    /** Checks the caller against the database (not the cached login user) so a removed super admin loses access at once. */
    private User requireSuperAdmin(User caller) {
        User u = users.findById(caller.getId()).orElseThrow(() -> ApiException.forbidden("Not allowed"));
        if (!u.isSuperAdmin() || u.isBanned()) throw ApiException.forbidden("Only super admins can do this");
        return u;
    }

    private SuperAdminSettings settingsOf(String userId) {
        return settingsRepo.findById(userId).orElseGet(() -> {
            SuperAdminSettings s = new SuperAdminSettings();
            s.setUserId(userId);
            return s;
        });
    }

    private User resolveUser(String ref) {
        String r = ref == null ? "" : ref.trim();
        if (r.isEmpty()) throw ApiException.badRequest("Enter the user's ID");
        return users.findByPublicId(r).or(() -> users.findById(r)).orElseThrow(() -> ApiException.notFound("No user with that ID"));
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

    private static Instant toInstant(Object o) {
        if (o instanceof Instant i) return i;
        if (o instanceof java.sql.Timestamp t) return t.toInstant();
        if (o instanceof java.util.Date d) return d.toInstant();
        if (o instanceof LocalDateTime l) return l.toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("Unexpected time type " + (o == null ? "null" : o.getClass()));
    }

    private record Range(Instant from, Instant to, String label, LocalDate firstDay, LocalDate lastDay) {}

    private static Range range(String period, String fromStr, String toStr) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate first;
        LocalDate last;
        switch (period == null ? "month" : period.toLowerCase()) {
            case "day" -> { first = today; last = today; }
            case "week" -> { first = today.with(java.time.DayOfWeek.MONDAY); last = first.plusDays(6); }
            case "custom" -> {
                try {
                    first = LocalDate.parse(fromStr);
                    last = LocalDate.parse(toStr);
                } catch (Exception e) {
                    throw ApiException.badRequest("Pick a start and end date (YYYY-MM-DD)");
                }
                if (last.isBefore(first)) throw ApiException.badRequest("The end date is before the start date");
                if (first.until(last, java.time.temporal.ChronoUnit.DAYS) + 1 > MAX_CUSTOM_DAYS) {
                    throw ApiException.badRequest("Pick at most " + MAX_CUSTOM_DAYS + " days");
                }
            }
            default -> { first = today.withDayOfMonth(1); last = first.plusMonths(1).minusDays(1); }
        }
        String label = first.equals(last) ? first.toString() : first + " to " + last;
        return new Range(first.atStartOfDay(ZoneOffset.UTC).toInstant(), last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(), label, first, last);
    }

    private record Recharge(long txns, long coins, long inr) {}

    private Recharge recharge(Collection<String> ids, Range r) {
        if (ids.isEmpty()) return new Recharge(0, 0, 0);
        long coins = 0, inr = 0, n = 0;
        for (Object[] row : payments.paidRows(ids, r.from(), r.to())) {
            n++;
            coins += ((Number) row[1]).longValue();
            inr += ((Number) row[2]).longValue();
        }
        return new Recharge(n, coins, inr);
    }

    private static double pct(long part, long whole) {
        if (whole <= 0) return 0;
        return BigDecimal.valueOf(part * 100.0 / whole).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // ================================================================== admin panel: who is a super admin

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminList() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : users.findBySuperAdminTrueOrderByNameAsc()) {
            Map<String, Object> m = slim(u);
            SuperAdminSettings s = settingsOf(u.getId());
            m.put("phone", u.getPhone());
            m.put("monthlySalary", s.getMonthlySalary());
            m.put("commissionRate", s.getCommissionRate());
            m.put("targetCoins", s.getTargetCoins());
            m.put("agencyTargetCoins", s.getAgencyTargetCoins());
            out.add(m);
        }
        return out;
    }

    private void apply(SuperAdminSettings s, BigDecimal salary, BigDecimal rate, Long target, Long agencyTarget) {
        if (salary != null) s.setMonthlySalary(salary);
        if (rate != null) s.setCommissionRate(rate);
        if (target != null) s.setTargetCoins(target);
        if (agencyTarget != null) s.setAgencyTargetCoins(agencyTarget);
    }

    @Transactional
    public Map<String, Object> adminAdd(SuperAdminRequests.Create r) {
        User u = users.findById(r.getUserId()).orElseThrow(() -> ApiException.notFound("User not found"));
        if (u.isBanned()) throw ApiException.badRequest("This user is banned");
        u.setSuperAdmin(true);
        users.save(u);
        SuperAdminSettings s = settingsOf(u.getId());
        apply(s, r.getMonthlySalary(), r.getCommissionRate(), r.getTargetCoins(), r.getAgencyTargetCoins());
        settingsRepo.save(s);
        return slim(u);
    }

    @Transactional
    public void adminUpdate(String userId, SuperAdminRequests.Update r) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!u.isSuperAdmin()) throw ApiException.badRequest("This user is not a super admin");
        SuperAdminSettings s = settingsOf(userId);
        apply(s, r.getMonthlySalary(), r.getCommissionRate(), r.getTargetCoins(), r.getAgencyTargetCoins());
        settingsRepo.save(s);
    }

    @Transactional
    public void adminRemove(String userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        u.setSuperAdmin(false);
        users.save(u);
        settingsRepo.deleteById(userId);
    }

    // ================================================================== app: dashboard

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(User caller) {
        User me = requireSuperAdmin(caller);
        SuperAdminSettings s = settingsOf(me.getId());
        Range month = range("month", null, null);
        TeamService.Downline d = team.downline(me.getId()); // everyone under this super admin
        List<String> ids = d.allHostIds();
        long agencyCount = d.agencies().size();

        long gift = ids.isEmpty() ? 0 : giftLogs.sumReceivedByIds(ids, month.from(), month.to());
        Recharge rc = recharge(ids, month);
        long income = BigDecimal.valueOf(gift).multiply(s.getCommissionRate()).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).longValue();
        long perAgency = s.getAgencyTargetCoins();
        long milestoneTarget = perAgency * agencyCount;

        Set<String> idSet = new HashSet<>(ids);
        long live = hostSessionRepo.findByEndedAtIsNull().stream().filter(x -> idSet.contains(x.getUserId())).map(HostSession::getUserId).distinct().count();

        Map<String, Object> profile = slim(me);
        profile.put("phone", me.getPhone());
        profile.put("commissionRate", s.getCommissionRate());
        profile.put("monthlySalary", s.getMonthlySalary());
        profile.put("periodLabel", month.firstDay() + " to " + month.lastDay());

        Map<String, Object> network = new LinkedHashMap<>();
        network.put("bds", d.bds().size());
        network.put("agencies", agencyCount);
        network.put("hosts", ids.size());
        network.put("liveStreamers", live);
        network.put("rechargeInr", rc.inr());
        network.put("rechargeTxns", rc.txns());

        Map<String, Object> milestone = new LinkedHashMap<>();
        milestone.put("current", gift);
        milestone.put("target", milestoneTarget);
        milestone.put("perAgencyTarget", perAgency);
        milestone.put("agencies", agencyCount);
        milestone.put("percent", pct(gift, milestoneTarget));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profile", profile);
        out.put("targetCoins", s.getTargetCoins());
        out.put("targetPercent", pct(gift, s.getTargetCoins()));
        out.put("teamGiftCoins", gift);
        out.put("income", income);
        out.put("pendingRequests", agencyRequestRepo.countByStatus(AgencyRequest.PENDING));
        out.put("monthlySalary", s.getMonthlySalary());
        out.put("network", network);
        out.put("milestone", milestone);
        return out;
    }

    // ================================================================== app: my team

    @Transactional(readOnly = true)
    public Map<String, Object> teamAgencies(User caller, String period, String from, String to) {
        User me = requireSuperAdmin(caller);
        Range r = range(period, from, to);
        long target = settingsOf(me.getId()).getAgencyTargetCoins();
        TeamService.Downline d = team.downline(me.getId());

        List<AgencyMember> all = d.agencies().isEmpty() ? List.of()
                : members.findByAgencyIdIn(d.agencies().stream().map(Agency::getId).toList());
        Map<String, Long> giftByUser = giftByUser(all.stream().map(AgencyMember::getUserId).distinct().toList(), r);
        Map<String, List<AgencyMember>> byAgency = new HashMap<>();
        for (AgencyMember m : all) byAgency.computeIfAbsent(m.getAgencyId(), k -> new ArrayList<>()).add(m);

        Map<String, User> people = new HashMap<>();
        Set<String> need = new HashSet<>();
        d.agencies().forEach(a -> { need.add(a.getOwnerId()); if (a.getBdUserId() != null) need.add(a.getBdUserId()); });
        users.findAllById(need).forEach(u -> people.put(u.getId(), u));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Agency a : d.agencies()) {
            List<AgencyMember> ms = byAgency.getOrDefault(a.getId(), List.of());
            long gift = 0;
            for (AgencyMember m : ms) gift += giftByUser.getOrDefault(m.getUserId(), 0L);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("code", a.getCode());
            row.put("name", a.getName());
            row.put("active", a.isActive());
            row.put("owner", slim(people.get(a.getOwnerId())));
            row.put("bd", a.getBdUserId() == null ? null : slim(people.get(a.getBdUserId())));
            row.put("members", ms.size());
            row.put("giftCoins", gift);
            row.put("target", target);
            row.put("achievedPercent", pct(gift, target));
            rows.add(row);
        }
        rows.sort((x, y) -> Long.compare((Long) y.get("giftCoins"), (Long) x.get("giftCoins")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("label", r.label());
        out.put("agencies", rows.size() > LIST_LIMIT ? rows.subList(0, LIST_LIMIT) : rows);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> teamHosts(User caller, String period, String from, String to) {
        User me = requireSuperAdmin(caller);
        Range r = range(period, from, to);
        TeamService.Downline d = team.downline(me.getId());

        List<AgencyMember> agencyMembers = d.agencies().isEmpty() ? List.of()
                : members.findByAgencyIdIn(d.agencies().stream().map(Agency::getId).toList());
        Map<String, Long> giftByUser = giftByUser(d.allHostIds(), r);
        Map<String, User> um = new HashMap<>();
        users.findAllById(d.allHostIds()).forEach(u -> um.put(u.getId(), u));
        Map<String, String> agencyName = new HashMap<>();
        d.agencies().forEach(a -> agencyName.put(a.getId(), a.getName()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (AgencyMember m : agencyMembers) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user", slim(um.get(m.getUserId())));
            row.put("agency", agencyName.get(m.getAgencyId()));
            row.put("direct", false);
            row.put("role", m.getRole());
            row.put("giftCoins", giftByUser.getOrDefault(m.getUserId(), 0L));
            rows.add(row);
        }
        for (String uid : d.directHostIds()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user", slim(um.get(uid)));
            row.put("agency", null);
            row.put("direct", true);
            row.put("role", "HOST");
            row.put("giftCoins", giftByUser.getOrDefault(uid, 0L));
            rows.add(row);
        }
        rows.sort((x, y) -> Long.compare((Long) y.get("giftCoins"), (Long) x.get("giftCoins")));
        List<Map<String, Object>> top = rows.size() > LIST_LIMIT ? new ArrayList<>(rows.subList(0, LIST_LIMIT)) : rows;
        for (Map<String, Object> row : top) { // live time only for the rows we send
            @SuppressWarnings("unchecked") Map<String, Object> u = (Map<String, Object>) row.get("user");
            row.put("liveSeconds", u == null ? 0L : hostSessions.seconds((String) u.get("id"), r.from(), r.to()));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("label", r.label());
        out.put("hosts", top);
        return out;
    }

    /** BDs under this super admin, with their agencies and the gift coins those agencies earned. */
    @Transactional(readOnly = true)
    public Map<String, Object> teamBds(User caller, String period, String from, String to) {
        User me = requireSuperAdmin(caller);
        Range r = range(period, from, to);
        TeamService.Downline d = team.downline(me.getId());

        List<AgencyMember> all = d.agencies().isEmpty() ? List.of()
                : members.findByAgencyIdIn(d.agencies().stream().map(Agency::getId).toList());
        Map<String, Long> giftByUser = giftByUser(all.stream().map(AgencyMember::getUserId).distinct().toList(), r);
        Map<String, String> bdOfAgency = new HashMap<>();
        d.agencies().forEach(a -> { if (a.getBdUserId() != null) bdOfAgency.put(a.getId(), a.getBdUserId()); });

        Map<String, Long> giftByBd = new HashMap<>();
        for (AgencyMember m : all) {
            String bd = bdOfAgency.get(m.getAgencyId());
            if (bd != null) giftByBd.merge(bd, giftByUser.getOrDefault(m.getUserId(), 0L), Long::sum);
        }
        Map<String, Long> agenciesOfBd = d.agencies().stream().filter(a -> a.getBdUserId() != null)
                .collect(Collectors.groupingBy(Agency::getBdUserId, Collectors.counting()));

        Map<String, User> um = new HashMap<>();
        users.findAllById(d.bds().stream().map(BdProfile::getUserId).toList()).forEach(u -> um.put(u.getId(), u));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BdProfile b : d.bds()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user", slim(um.get(b.getUserId())));
            row.put("agencies", agenciesOfBd.getOrDefault(b.getUserId(), 0L));
            row.put("giftCoins", giftByBd.getOrDefault(b.getUserId(), 0L));
            row.put("since", b.getCreatedAt());
            rows.add(row);
        }
        rows.sort((x, y) -> Long.compare((Long) y.get("giftCoins"), (Long) x.get("giftCoins")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("label", r.label());
        out.put("bds", rows);
        return out;
    }

    private Map<String, Long> giftByUser(List<String> ids, Range r) {
        Map<String, Long> m = new HashMap<>();
        if (ids.isEmpty()) return m;
        for (Object[] row : giftLogs.sumByReceiver(ids, r.from(), r.to())) m.put((String) row[0], ((Number) row[1]).longValue());
        return m;
    }

    // ================================================================== app: reports

    @Transactional(readOnly = true)
    public Map<String, Object> reports(User caller, String period, String from, String to) {
        User me = requireSuperAdmin(caller);
        Range r = range(period, from, to);
        List<String> ids = team.downline(me.getId()).allHostIds();

        Map<LocalDate, long[]> days = new TreeMap<>(Comparator.reverseOrder()); // [rechargeInr, rechargeTxns, giftCoins, gifts]
        for (LocalDate d = r.firstDay(); !d.isAfter(r.lastDay()); d = d.plusDays(1)) days.put(d, new long[4]);
        long rInr = 0, rTxns = 0, rCoins = 0, gCoins = 0, gCount = 0;
        if (!ids.isEmpty()) {
            for (Object[] row : payments.paidRows(ids, r.from(), r.to())) {
                long[] d = days.get(toInstant(row[0]).atZone(ZoneOffset.UTC).toLocalDate());
                long inr = ((Number) row[2]).longValue();
                rInr += inr; rTxns++; rCoins += ((Number) row[1]).longValue();
                if (d != null) { d[0] += inr; d[1]++; }
            }
            for (Object[] row : giftLogs.rowsReceivedByIds(ids, r.from(), r.to())) {
                long[] d = days.get(toInstant(row[0]).atZone(ZoneOffset.UTC).toLocalDate());
                long coins = ((Number) row[1]).longValue();
                gCoins += coins; gCount++;
                if (d != null) { d[2] += coins; d[3]++; }
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        days.forEach((date, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date.toString());
            m.put("weekday", date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH));
            m.put("rechargeInr", v[0]);
            m.put("rechargeTxns", v[1]);
            m.put("giftCoins", v[2]);
            m.put("gifts", v[3]);
            rows.add(m);
        });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("managerId", me.getPublicId());
        out.put("label", r.label());
        out.put("rechargeInr", rInr);
        out.put("rechargeTxns", rTxns);
        out.put("rechargeCoins", rCoins);
        out.put("giftCoins", gCoins);
        out.put("gifts", gCount);
        out.put("days", rows);
        return out;
    }

    // ================================================================== app: give wares / badges

    @Transactional(readOnly = true)
    public List<Map<String, Object>> wares(User caller, String q) {
        requireSuperAdmin(caller);
        return store.officialCatalog(q);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> badges(User caller) {
        requireSuperAdmin(caller);
        return badgeService.giveable();
    }

    @Transactional
    public Map<String, Object> giveWare(User caller, SuperAdminRequests.GiveWare r) {
        User giver = requireSuperAdmin(caller);
        User receiver = resolveUser(r.getUserRef());
        if (receiver.isBanned()) throw ApiException.badRequest("This user is banned");
        int days = r.getDays() == null ? 7 : r.getDays();
        Map<String, Object> given = store.giveOfficial(giver, receiver, r.getItemId(), days);

        OfficialGrant g = new OfficialGrant();
        g.setKind(OfficialGrant.WARE);
        g.setUserId(receiver.getId());
        g.setTargetId(r.getItemId());
        g.setTargetName((String) given.get("name"));
        g.setCategory((String) given.get("category"));
        g.setImageUrl((String) given.get("imageUrl"));
        g.setGivenBy(giver.getId());
        g.setExpiresAt((Instant) given.get("expiresAt"));
        grants.save(g);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("to", slim(receiver));
        out.put("name", given.get("name"));
        out.put("days", days);
        return out;
    }

    @Transactional
    public Map<String, Object> giveBadge(User caller, SuperAdminRequests.GiveBadge r) {
        User giver = requireSuperAdmin(caller);
        User receiver = resolveUser(r.getUserRef());
        if (receiver.isBanned()) throw ApiException.badRequest("This user is banned");
        Badge badge = badgeService.require(r.getBadgeId());
        if (!badge.isActive()) throw ApiException.badRequest("This badge is turned off");
        Instant ends = badgeService.give(receiver.getId(), badge, r.getDays());

        OfficialGrant g = new OfficialGrant();
        g.setKind(OfficialGrant.BADGE);
        g.setUserId(receiver.getId());
        g.setTargetId(badge.getId());
        g.setTargetName(badge.getName());
        g.setCategory("BADGE");
        g.setImageUrl(badge.getImageUrl());
        g.setGivenBy(giver.getId());
        g.setExpiresAt(ends);
        grants.save(g);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("to", slim(receiver));
        out.put("name", badge.getName());
        out.put("permanent", ends == null);
        return out;
    }

    // ================================================================== app: dedicated items

    @Transactional(readOnly = true)
    public List<Map<String, Object>> dedicated(User caller, String q, String kind) {
        requireSuperAdmin(caller);
        Instant now = Instant.now();
        String needle = q == null ? "" : q.trim().toLowerCase();
        String k = kind == null ? "" : kind.trim().toUpperCase();

        List<OfficialGrant> live = grants.findTop500ByRevokedAtIsNullOrderByCreatedAtDesc().stream()
                .filter(g -> g.getExpiresAt() == null || g.getExpiresAt().isAfter(now))
                .filter(g -> k.isEmpty() || k.equals("ALL") || g.getKind().equals(k))
                .toList();
        Set<String> userIds = new HashSet<>();
        live.forEach(g -> { userIds.add(g.getUserId()); userIds.add(g.getGivenBy()); });
        Map<String, User> um = new HashMap<>();
        users.findAllById(userIds).forEach(u -> um.put(u.getId(), u));

        List<Map<String, Object>> out = new ArrayList<>();
        for (OfficialGrant g : live) {
            User receiver = um.get(g.getUserId());
            if (!needle.isEmpty()) {
                boolean hit = g.getTargetName().toLowerCase().contains(needle)
                        || (receiver != null && (receiver.getName().toLowerCase().contains(needle)
                        || needle.equals(receiver.getPublicId()) || needle.equals(receiver.getId().toLowerCase())));
                if (!hit) continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("kind", g.getKind());
            m.put("name", g.getTargetName());
            m.put("category", g.getCategory());
            m.put("imageUrl", g.getImageUrl());
            m.put("user", slim(receiver));
            User by = um.get(g.getGivenBy());
            m.put("givenBy", by == null ? null : by.getName());
            m.put("givenAt", g.getCreatedAt());
            m.put("expiresAt", g.getExpiresAt());
            m.put("permanent", g.getExpiresAt() == null);
            out.add(m);
            if (out.size() >= 100) break;
        }
        return out;
    }

    @Transactional
    public void revoke(User caller, String grantId) {
        requireSuperAdmin(caller);
        OfficialGrant g = grants.findById(grantId).orElseThrow(() -> ApiException.notFound("Not found"));
        if (g.getRevokedAt() != null) return;
        if (OfficialGrant.WARE.equals(g.getKind())) store.revokeOfficial(g.getUserId(), g.getTargetId());
        else badgeService.take(g.getUserId(), g.getTargetId());
        g.setRevokedAt(Instant.now());
        grants.save(g);
    }
}
