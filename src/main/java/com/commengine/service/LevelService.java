package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.entity.*;
import com.commengine.repository.GiftLogRepository;
import com.commengine.repository.LevelConfigRepository;
import com.commengine.repository.LevelRewardClaimRepository;
import com.commengine.repository.UserLevelRepository;
import com.commengine.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Levels 1..10 (everyone starts at level 0).
 * A user's level goes up as their CONTRIBUTION grows: the total value of gifts they SEND (gifts to themselves don't count).
 * Charisma (gifts received) no longer affects the level. The admin sets, per level, how much gift value
 * is needed and what reward (coins or days of a store item) the user can collect when they get there.
 * Rewards are collected by the user from the Level screen, once per level.
 */
@Service
public class LevelService {

    public static final int MAX_LEVEL = 10;
    private static final long MAX_REQUIRED = 1_000_000_000L;
    private static final long MAX_COINS = 10_000_000L;
    private static final int MAX_ITEM_DAYS = 30;

    /** Used for any level the admin has not edited yet. */
    private static final long[] DEFAULT_REQUIRED = {
            1_000, 5_000, 15_000, 40_000, 100_000, 250_000, 500_000, 1_000_000, 2_500_000, 5_000_000
    };

    private final LevelConfigRepository configs;
    private final UserLevelRepository userLevels;
    private final GiftLogRepository giftLogs;
    private final LevelRewardClaimRepository claims;
    private final UserRepository users;
    private final WalletService wallets;
    private final StoreService store;

    public LevelService(LevelConfigRepository configs, UserLevelRepository userLevels, LevelRewardClaimRepository claims,
                        GiftLogRepository giftLogs, UserRepository users, WalletService wallets, StoreService store) {
        this.configs = configs;
        this.userLevels = userLevels;
        this.claims = claims;
        this.giftLogs = giftLogs;
        this.users = users;
        this.wallets = wallets;
        this.store = store;
    }

    // ------------------------------------------------------------------ called when a gift is received

    /** Adds the value of gifts the user just received. Call inside the gift transaction. */
    @Transactional
    public void addReceived(String userId, long coins) {
        if (coins > 0) userLevels.addReceived(userId, coins);
    }

    // ------------------------------------------------------------------ helpers

    /** Contribution = total coin value of gifts this user has sent (all time; self-gifts are never logged). */
    private long contributionOf(String userId) {
        return giftLogs.sentSince(userId, Instant.EPOCH);
    }

    /** Always MAX_LEVEL entries, level 1..10, so nothing downstream fills gaps. */
    private List<LevelConfig> configList() {
        Map<Integer, LevelConfig> by = new HashMap<>();
        for (LevelConfig c : configs.findAll()) by.put(c.getLevel(), c);
        List<LevelConfig> out = new ArrayList<>();
        for (int i = 1; i <= MAX_LEVEL; i++) {
            LevelConfig c = by.get(i);
            if (c == null) {
                c = new LevelConfig();
                c.setLevel(i);
                c.setRequiredCoins(DEFAULT_REQUIRED[i - 1]);
            }
            out.add(c);
        }
        return out;
    }

    /** Highest level whose threshold is reached, going in order (a level is only reached after the one before it). */
    private static int levelFor(List<LevelConfig> list, long received) {
        int lvl = 0;
        for (LevelConfig c : list) {
            if (received >= c.getRequiredCoins()) lvl = c.getLevel();
            else break;
        }
        return lvl;
    }

    private Map<String, Object> rewardJson(LevelConfig c) {
        DailyRewardType type = c.getType();
        Map<String, Object> item = null;
        if (type == DailyRewardType.ITEM) {
            item = c.getItemId() == null ? null : store.describe(c.getItemId());
            if (item == null) type = DailyRewardType.NONE; // the item was removed from the store
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("coins", type == DailyRewardType.COINS ? c.getCoins() : 0);
        m.put("item", item);
        m.put("itemDays", type == DailyRewardType.ITEM ? c.getItemDays() : 0);
        return m;
    }

    /** A user's level right now (0 until they reach level 1). For showing on profiles. */
    @Transactional(readOnly = true)
    public int levelOf(String userId) {
        long received = contributionOf(userId);
        return levelFor(configList(), received);
    }

    // ------------------------------------------------------------------ app side

    @Transactional(readOnly = true)
    public Map<String, Object> status(User user) {
        return buildStatus(user.getId());
    }

    private Map<String, Object> buildStatus(String userId) {
        List<LevelConfig> list = configList();
        long received = contributionOf(userId);
        int level = levelFor(list, received);
        Set<Integer> claimed = new HashSet<>();
        for (LevelRewardClaim c : claims.findByUserId(userId)) claimed.add(c.getLevel());

        List<Map<String, Object>> levels = new ArrayList<>();
        for (LevelConfig c : list) {
            boolean unlocked = c.getLevel() <= level;
            boolean isClaimed = claimed.contains(c.getLevel());
            Map<String, Object> reward = rewardJson(c);
            boolean hasReward = reward.get("type") != DailyRewardType.NONE;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("level", c.getLevel());
            m.put("requiredCoins", c.getRequiredCoins());
            m.put("reward", reward);
            m.put("unlocked", unlocked);
            m.put("claimed", isClaimed);
            m.put("claimable", unlocked && !isClaimed && hasReward);
            levels.add(m);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("level", level);
        res.put("maxLevel", MAX_LEVEL);
        res.put("contributionCoins", received);
        res.put("currentRequired", level == 0 ? 0 : list.get(level - 1).getRequiredCoins());
        res.put("nextLevel", level >= MAX_LEVEL ? null : level + 1);
        res.put("nextRequired", level >= MAX_LEVEL ? null : list.get(level).getRequiredCoins());
        res.put("levels", levels);
        return res;
    }

    @Transactional
    public Map<String, Object> claim(User user, int level) {
        if (level < 1 || level > MAX_LEVEL) throw ApiException.badRequest("Level must be 1 to " + MAX_LEVEL);
        users.lockById(user.getId()).orElseThrow(() -> ApiException.notFound("User not found"));

        List<LevelConfig> list = configList();
        long received = contributionOf(user.getId());
        if (level > levelFor(list, received)) throw ApiException.forbidden("You have not reached level " + level + " yet");
        if (claims.existsByUserIdAndLevel(user.getId(), level)) {
            throw ApiException.conflict("You already collected the level " + level + " reward");
        }

        LevelConfig cfg = list.get(level - 1);
        Map<String, Object> given = new LinkedHashMap<>();
        given.put("level", level);
        given.put("type", DailyRewardType.NONE);

        if (cfg.getType() == DailyRewardType.COINS && cfg.getCoins() > 0) {
            wallets.adjust(user.getId(), WalletService.COINS, cfg.getCoins(), "LEVEL_REWARD", "level:" + level,
                    "Level " + level + " reward");
            given.put("type", DailyRewardType.COINS);
            given.put("coins", cfg.getCoins());
        } else if (cfg.getType() == DailyRewardType.ITEM && cfg.getItemId() != null) {
            Map<String, Object> item = store.grantReward(user.getId(), cfg.getItemId(), cfg.getItemDays());
            if (item != null) {
                given.put("type", DailyRewardType.ITEM);
                given.put("item", item);
                given.put("itemDays", cfg.getItemDays());
            }
        }
        if (given.get("type") == DailyRewardType.NONE) {
            throw ApiException.conflict("There is no reward set for this level");
        }

        LevelRewardClaim c = new LevelRewardClaim();
        c.setUserId(user.getId());
        c.setLevel(level);
        c.setClaimedAt(Instant.now());
        claims.save(c);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reward", given);
        res.put("status", buildStatus(user.getId()));
        res.put("wallet", wallets.getBalance(user.getId()));
        return res;
    }

    // ------------------------------------------------------------------ admin side

    @Transactional(readOnly = true)
    public Map<String, Object> adminList() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LevelConfig c : configList()) out.add(adminJson(c));
        return Map.of("levels", out);
    }

    private Map<String, Object> adminJson(LevelConfig c) {
        Map<String, Object> m = rewardJson(c);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("level", c.getLevel());
        out.put("requiredCoins", c.getRequiredCoins());
        out.putAll(m);
        return out;
    }

    @Transactional
    public Map<String, Object> adminSet(int level, Long requiredCoins, String typeRaw, Long coins, String itemId, Integer itemDays) {
        if (level < 1 || level > MAX_LEVEL) throw ApiException.badRequest("Level must be 1 to " + MAX_LEVEL);

        if (requiredCoins == null || requiredCoins < 1 || requiredCoins > MAX_REQUIRED) {
            throw ApiException.unprocessable("Contribution amount must be between 1 and " + MAX_REQUIRED + " coins");
        }
        // Thresholds must climb: each level needs more than the one before and less than the one after.
        List<LevelConfig> list = configList();
        if (level > 1 && requiredCoins <= list.get(level - 2).getRequiredCoins()) {
            throw ApiException.unprocessable("Level " + level + " must need more than level " + (level - 1)
                    + " (" + list.get(level - 2).getRequiredCoins() + " coins)");
        }
        if (level < MAX_LEVEL && requiredCoins >= list.get(level).getRequiredCoins()) {
            throw ApiException.unprocessable("Level " + level + " must need less than level " + (level + 1)
                    + " (" + list.get(level).getRequiredCoins() + " coins)");
        }

        DailyRewardType type;
        try {
            type = DailyRewardType.valueOf(typeRaw == null ? "" : typeRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("type must be NONE, COINS or ITEM");
        }

        LevelConfig c = configs.findById(level).orElseGet(() -> {
            LevelConfig n = new LevelConfig();
            n.setLevel(level);
            return n;
        });
        c.setRequiredCoins(requiredCoins);
        c.setType(type);
        c.setCoins(0);
        c.setItemId(null);
        c.setItemDays(0);

        if (type == DailyRewardType.COINS) {
            if (coins == null || coins < 1 || coins > MAX_COINS) {
                throw ApiException.unprocessable("Coins must be between 1 and " + MAX_COINS);
            }
            c.setCoins(coins);
        } else if (type == DailyRewardType.ITEM) {
            if (itemId == null || itemId.isBlank()) throw ApiException.unprocessable("Choose a store item");
            Map<String, Object> item = store.describe(itemId.trim());
            if (item == null) throw ApiException.notFound("That store item no longer exists");
            if (Boolean.TRUE.equals(item.get("vipOnly"))) {
                throw ApiException.unprocessable("That item is for VIP members only, so it can't be a reward. Pick another item.");
            }
            if (Boolean.TRUE.equals(item.get("free"))) {
                throw ApiException.unprocessable("Free items are already available to everyone. Pick a paid item.");
            }
            if (itemDays == null || itemDays < 1 || itemDays > MAX_ITEM_DAYS) {
                throw ApiException.unprocessable("Item days must be between 1 and " + MAX_ITEM_DAYS);
            }
            c.setItemId(itemId.trim());
            c.setItemDays(itemDays);
        }
        c.setUpdatedAt(Instant.now());
        configs.save(c);
        return adminJson(c);
    }
}
