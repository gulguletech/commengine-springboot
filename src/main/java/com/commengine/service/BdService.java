package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.entity.*;
import com.commengine.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * The BD (Business Developer) dashboard. A BD registers agencies and earns a commission from each one for 90 days after
 * it registers: when the agency's host salaries in a month reach a tier (admin panel: BD tiers), the BD earns that tier's
 * commission. Months are UTC calendar months. Numbers are for display only; nothing is paid out here.
 */
@Service
public class BdService {

    private static final int COMMISSION_DAYS = 90;
    private static final int MAX_MONTHS_BACK = 5;

    private final UserRepository users;
    private final BdProfileRepository bds;
    private final AgencyRepository agencies;
    private final AgencyMemberRepository members;
    private final BdTierRepository tiers;
    private final AgencyService agencyService;
    private final TeamService team;

    public BdService(UserRepository users, BdProfileRepository bds, AgencyRepository agencies, AgencyMemberRepository members,
                     BdTierRepository tiers, AgencyService agencyService, TeamService team) {
        this.users = users;
        this.bds = bds;
        this.agencies = agencies;
        this.members = members;
        this.tiers = tiers;
        this.agencyService = agencyService;
        this.team = team;
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

    private static BigDecimal commissionFor(List<BdTier> sorted, BigDecimal hostSalaries) {
        BigDecimal hit = BigDecimal.ZERO;
        for (BdTier t : sorted) if (hostSalaries.compareTo(t.getMinSalary()) >= 0) hit = t.getCommission();
        return hit;
    }

    /** Does this month fall inside the agency's 90-day commission window? */
    private static boolean inWindow(Agency a, Instant monthStart, Instant monthEnd) {
        Instant ends = a.getCreatedAt().plus(Duration.ofDays(COMMISSION_DAYS));
        return monthStart.isBefore(ends) && monthEnd.isAfter(a.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(User caller) {
        BdProfile p = bds.findById(caller.getId()).orElseThrow(() -> ApiException.forbidden("Only BDs can open this"));
        User me = users.findById(caller.getId()).orElseThrow(() -> ApiException.forbidden("Not allowed"));
        User sa = p.getSuperAdminId() == null ? null : users.findById(p.getSuperAdminId()).orElse(null);
        List<BdTier> ladder = tiers.findAllByOrderByMinSalaryAsc();

        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        Instant monthStart = now.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthEnd = now.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Agency> mine = agencies.findByBdUserId(me.getId());
        Map<String, User> owners = new HashMap<>();
        users.findAllById(mine.stream().map(Agency::getOwnerId).toList()).forEach(u -> owners.put(u.getId(), u));

        BigDecimal periodCommission = BigDecimal.ZERO, accumulated = BigDecimal.ZERO;
        BigDecimal hostSalaries = BigDecimal.ZERO, agencyCommission = BigDecimal.ZERO;
        int effective = 0;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (Agency a : mine) {
            Map<String, Object> cur = agencyService.salaryFor(a.getId(), monthStart, monthEnd);
            BigDecimal hs = (BigDecimal) cur.get("hostSalaries");
            BigDecimal ac = (BigDecimal) cur.get("agencyCommission");
            hostSalaries = hostSalaries.add(hs);
            agencyCommission = agencyCommission.add(ac);

            BigDecimal commissionNow = inWindow(a, monthStart, monthEnd) ? commissionFor(ladder, hs) : BigDecimal.ZERO;
            periodCommission = periodCommission.add(commissionNow);
            if (commissionNow.signum() > 0) effective++;

            // accumulated: every month of the 90-day window up to now
            BigDecimal total = commissionNow;
            YearMonth first = YearMonth.from(a.getCreatedAt().atZone(ZoneOffset.UTC));
            YearMonth m = now.minusMonths(1);
            for (int i = 0; i < MAX_MONTHS_BACK && !m.isBefore(first); i++, m = m.minusMonths(1)) {
                Instant ms = m.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant me2 = m.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                if (!inWindow(a, ms, me2)) continue;
                BigDecimal prev = (BigDecimal) agencyService.salaryFor(a.getId(), ms, me2).get("hostSalaries");
                total = total.add(commissionFor(ladder, prev));
            }
            accumulated = accumulated.add(total);

            long daysLeft = Math.max(0, COMMISSION_DAYS - Duration.between(a.getCreatedAt(), Instant.now()).toDays());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("code", a.getCode());
            row.put("name", a.getName());
            row.put("owner", slim(owners.get(a.getOwnerId())));
            row.put("members", members.countByAgencyId(a.getId()));
            row.put("hostSalaries", hs);
            row.put("commission", commissionNow);
            row.put("registeredAt", a.getCreatedAt());
            row.put("commissionDaysLeft", daysLeft);
            rows.add(row);
        }

        List<Map<String, Object>> tierRows = new ArrayList<>();
        for (BdTier t : ladder) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("minSalary", t.getMinSalary());
            m.put("commission", t.getCommission());
            tierRows.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", slim(me));
        out.put("superAdmin", slim(sa));
        out.put("teamId", sa == null ? null : sa.getPublicId());
        out.put("agents", mine.size());
        out.put("effectiveAgents", effective);
        out.put("accumulatedCommission", accumulated);
        out.put("periodCommission", periodCommission);
        out.put("hostSalaries", hostSalaries);
        out.put("agencyCommission", agencyCommission);
        out.put("periodLabel", now.atDay(1) + " to " + now.atEndOfMonth());
        out.put("agencies", rows);
        out.put("pendingInvites", team.sent(me, "AGENCY"));
        out.put("tiers", tierRows);
        out.put("commissionDays", COMMISSION_DAYS);
        return out;
    }

    // ---------------------------------------------------------------- admin panel: BD tiers

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminTiers() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BdTier t : tiers.findAllByOrderByMinSalaryAsc()) out.add(tierJson(t));
        return out;
    }

    private static Map<String, Object> tierJson(BdTier t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("minSalary", t.getMinSalary());
        m.put("commission", t.getCommission());
        return m;
    }

    @Transactional
    public Map<String, Object> adminCreateTier(BigDecimal min, BigDecimal commission) {
        if (tiers.existsByMinSalary(min)) throw ApiException.conflict("A tier at this salary already exists");
        BdTier t = new BdTier();
        t.setMinSalary(min);
        t.setCommission(commission);
        return tierJson(tiers.save(t));
    }

    @Transactional
    public Map<String, Object> adminUpdateTier(String id, BigDecimal min, BigDecimal commission) {
        BdTier t = tiers.findById(id).orElseThrow(() -> ApiException.notFound("Tier not found"));
        if (min != null && min.compareTo(t.getMinSalary()) != 0) {
            if (tiers.existsByMinSalary(min)) throw ApiException.conflict("A tier at this salary already exists");
            t.setMinSalary(min);
        }
        if (commission != null) t.setCommission(commission);
        return tierJson(tiers.save(t));
    }

    @Transactional
    public void adminDeleteTier(String id) {
        if (!tiers.existsById(id)) throw ApiException.notFound("Tier not found");
        tiers.deleteById(id);
    }
}
