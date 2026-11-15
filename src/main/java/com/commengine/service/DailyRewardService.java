package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.entity.*;
import com.commengine.repository.DailyRewardClaimRepository;
import com.commengine.repository.DailyRewardDayRepository;
import com.commengine.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * The 7-day login reward. The admin sets what each of the 7 days gives (same for every user).
 * A user collects one day per calendar day (India time), in order. Miss a day and they start again at day 1;
 * after day 7 the cycle starts again at day 1.
 */
@Service
public class DailyRewardService {

    public static final int CYCLE = 7;
    /** The day changes at midnight India time. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final long MAX_COINS = 10_000_000L;
    private static final int MAX_ITEM_DAYS = 30;

    private final DailyRewardDayRepository days;
    private final DailyRewardClaimRepository claims;
    private final UserRepository users;
    private final WalletService wallets;
    private final StoreService store;

    public DailyRewardService(DailyRewardDayRepository days, DailyRewardClaimRepository claims, UserRepository users,
                              WalletService wallets, StoreService store) {
        this.days = days;
        this.claims = claims;
        this.users = users;
        this.wallets = wallets;
        this.store = store;
    }

    /** Where a user stands today. */
    private record State(int claimedCount, int nextDay, boolean canClaim, int streak) {}

    private static State compute(DailyRewardClaim c, LocalDate today) {
        if (c == null || c.getLastClaimDate().isBefore(today.minusDays(1))) {
            return new State(0, 1, true, 0); // never collected, or missed a day: back to day 1
        }
        int next = c.getStreakDay() % CYCLE + 1;
        if (c.getLastClaimDate().isEqual(today)) {
            return new State(c.getStreakDay(), next, false, c.getStreakDay()); // already collected today
        }
        // collected yesterday: today is the next day. After day 7 a new cycle starts with nothing ticked.
        return new State(c.getStreakDay() == CYCLE ? 0 : c.getStreakDay(), next, true, c.getStreakDay());
    }

    // ------------------------------------------------------------------ app side

    @Transactional(readOnly = true)
    public Map<String, Object> status(User user) {
        return buildStatus(user.getId());
    }

    private Map<String, Object> buildStatus(String userId) {
        LocalDate today = LocalDate.now(ZONE);
        State s = compute(claims.findById(userId).orElse(null), today);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("days", dayList());
        m.put("claimedCount", s.claimedCount());
        m.put("nextDay", s.nextDay());
        m.put("canClaim", s.canClaim());
        m.put("streak", s.streak());
        return m;
    }

    @Transactional
    public Map<String, Object> claim(User user) {
        users.lockById(user.getId()).orElseThrow(() -> ApiException.notFound("User not found"));
        LocalDate today = LocalDate.now(ZONE);
        DailyRewardClaim c = claims.findById(user.getId()).orElse(null);
        State s = compute(c, today);
        if (!s.canClaim()) throw ApiException.conflict("You already collected today's reward. Come back tomorrow!");

        int day = s.nextDay();
        Map<String, Object> given = new LinkedHashMap<>();
        given.put("day", day);
        given.put("type", DailyRewardType.NONE);

        DailyRewardDay cfg = days.findById(day).orElse(null);
        if (cfg != null && cfg.getType() == DailyRewardType.COINS && cfg.getCoins() > 0) {
            wallets.adjust(user.getId(), WalletService.COINS, cfg.getCoins(), "DAILY_REWARD", "daily:" + today,
                    "Daily reward - day " + day);
            given.put("type", DailyRewardType.COINS);
            given.put("coins", cfg.getCoins());
        } else if (cfg != null && cfg.getType() == DailyRewardType.ITEM && cfg.getItemId() != null) {
            Map<String, Object> item = store.grantReward(user.getId(), cfg.getItemId(), cfg.getItemDays());
            if (item != null) {
                given.put("type", DailyRewardType.ITEM);
                given.put("item", item);
                given.put("itemDays", cfg.getItemDays());
            }
        }

        if (c == null) {
            c = new DailyRewardClaim();
            c.setUserId(user.getId());
        }
        c.setLastClaimDate(today);
        c.setStreakDay(day);
        c.setTotalClaims(c.getTotalClaims() + 1);
        c.setUpdatedAt(Instant.now());
        claims.save(c);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reward", given);
        res.put("status", buildStatus(user.getId()));
        res.put("wallet", wallets.getBalance(user.getId()));
        return res;
    }

    /** Always 7 entries, day 1..7, so the app never has to fill gaps. */
    private List<Map<String, Object>> dayList() {
        Map<Integer, DailyRewardDay> byDay = new HashMap<>();
        for (DailyRewardDay d : days.findAll()) byDay.put(d.getDay(), d);
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 1; i <= CYCLE; i++) out.add(dayJson(i, byDay.get(i)));
        return out;
    }

    private Map<String, Object> dayJson(int day, DailyRewardDay d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("day", day);
        DailyRewardType type = d == null ? DailyRewardType.NONE : d.getType();
        Map<String, Object> item = null;
        if (type == DailyRewardType.ITEM) {
            item = store.describe(d.getItemId());
            if (item == null) type = DailyRewardType.NONE; // the item was removed from the store
        }
        m.put("type", type);
        m.put("coins", type == DailyRewardType.COINS ? d.getCoins() : 0);
        m.put("item", item);
        m.put("itemDays", type == DailyRewardType.ITEM ? d.getItemDays() : 0);
        return m;
    }

    // ------------------------------------------------------------------ admin side

    @Transactional(readOnly = true)
    public Map<String, Object> adminList() {
        return Map.of("days", dayList());
    }

    @Transactional
    public Map<String, Object> adminSet(int day, String typeRaw, Long coins, String itemId, Integer itemDays) {
        if (day < 1 || day > CYCLE) throw ApiException.badRequest("Day must be 1 to " + CYCLE);
        DailyRewardType type;
        try {
            type = DailyRewardType.valueOf(typeRaw == null ? "" : typeRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("type must be NONE, COINS or ITEM");
        }

        DailyRewardDay d = days.findById(day).orElseGet(() -> {
            DailyRewardDay n = new DailyRewardDay();
            n.setDay(day);
            return n;
        });
        d.setType(type);
        d.setCoins(0);
        d.setItemId(null);
        d.setItemDays(0);

        if (type == DailyRewardType.COINS) {
            if (coins == null || coins < 1 || coins > MAX_COINS) {
                throw ApiException.unprocessable("Coins must be between 1 and " + MAX_COINS);
            }
            d.setCoins(coins);
        } else if (type == DailyRewardType.ITEM) {
            if (itemId == null || itemId.isBlank()) throw ApiException.unprocessable("Choose a store item");
            Map<String, Object> item = store.describe(itemId.trim());
            if (item == null) throw ApiException.notFound("That store item no longer exists");
            if (Boolean.TRUE.equals(item.get("free"))) {
                throw ApiException.unprocessable("Free items are already available to everyone. Pick a paid item.");
            }
            if (itemDays == null || itemDays < 1 || itemDays > MAX_ITEM_DAYS) {
                throw ApiException.unprocessable("Item days must be between 1 and " + MAX_ITEM_DAYS);
            }
            d.setItemId(itemId.trim());
            d.setItemDays(itemDays);
        }
        d.setUpdatedAt(Instant.now());
        days.save(d);
        return dayJson(day, d);
    }
}
