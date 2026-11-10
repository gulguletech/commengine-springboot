package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.entity.GameBet;
import com.commengine.entity.User;
import com.commengine.entity.Wallet;
import com.commengine.repository.GameBetRepository;
import com.commengine.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Galaxy Feast (ferris-wheel game): 8 foods on a wheel, shared 40s rounds
 * (30s betting -> 5s draw -> 5s result).
 *
 * Options: 0 crab, 1 fish, 2 shrimp, 3 steak, 4 tomato, 5 pumpkin, 6 mushroom, 7 cabbage,
 *          8 SALAD (wins when the winner is a vegetable 4..7), 9 PIZZA (wins when it is 0..3).
 *
 * Round + phase come from the server clock, the winner from a secret HMAC, bets are debited
 * immediately and winners are paid by the server (settler), never by the client.
 * Game history lives in game_bets and is shown ONLY inside the game (not in the wallet screen).
 */
@Service
public class GalaxyFeastService {

    public static final String GAME = "galaxy-feast";
    public static final long BETTING_MS = 30_000;
    public static final long DRAW_MS = 5_000;
    public static final long RESULT_MS = 5_000;
    public static final long CYCLE_MS = BETTING_MS + DRAW_MS + RESULT_MS;
    /** Round numbers count from here so they stay small (1, 2, 3 ...). */
    public static final long EPOCH_MS = Instant.parse("2026-11-01T00:00:00Z").toEpochMilli();

    private static final int ITEMS = 8;
    /** Multiplier x100: crab 45, fish 25, shrimp 15, steak 10, tomato/pumpkin/mushroom/cabbage 5, salad 1.25, pizza 4.35 */
    private static final int[] MULT_X100 = {4500, 2500, 1500, 1000, 500, 500, 500, 500, 125, 435};
    // weight = 450 / multiplier  ->  every single food returns ~97.2% in the long run
    private static final int[] WEIGHT = {10, 18, 30, 45, 90, 90, 90, 90};
    private static final int TOTAL_WEIGHT = Arrays.stream(WEIGHT).sum();

    public static final long[] CHIPS = {2_000, 10_000, 50_000, 200_000};
    private static final long MAX_PER_ROUND = 5_000_000;
    private static final int MAX_LINES = 40;
    private static final long CLOSE_GUARD_MS = 600;
    private static final int HISTORY = 8;
    private static final int RANK_SIZE = 20;

    private final GameBetRepository betRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;

    @Value("${GAME_SECRET:${JWT_SECRET:change_me}}")
    private String secret;

    /** Day boundary for "Today Win" and the daily revenue rank. */
    @Value("${GAME_TZ:Asia/Kolkata}")
    private String tz;

    public record BetLine(int option, long amount) {}

    private record Clock(long now, long round, String phase, long remaining) {}

    private record RankCache(long at, Instant from, List<Map<String, Object>> players) {}

    private volatile RankCache rankCache;

    public GalaxyFeastService(GameBetRepository betRepository, WalletService walletService, UserRepository userRepository) {
        this.betRepository = betRepository;
        this.walletService = walletService;
        this.userRepository = userRepository;
    }

    // ---- round maths -------------------------------------------------------------------------

    public int winnerOf(long round) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal((GAME + ":" + round).getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 8; i++) v = (v << 8) | (h[i] & 0xffL);
            long r = Math.floorMod(v, (long) TOTAL_WEIGHT);
            long acc = 0;
            for (int i = 0; i < WEIGHT.length; i++) {
                acc += WEIGHT[i];
                if (r < acc) return i;
            }
            return WEIGHT.length - 1;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute result", e);
        }
    }

    static boolean hits(int option, int winner) {
        if (option < ITEMS) return option == winner;
        if (option == 8) return winner >= 4;   // salad = vegetables
        return winner < 4;                     // pizza = crab, fish, shrimp, steak
    }

    static long payoutFor(int option, long amount, int winner) {
        return hits(option, winner) ? amount * MULT_X100[option] / 100 : 0;
    }

    private Clock clock() {
        long now = System.currentTimeMillis();
        long since = now - EPOCH_MS;
        long round = Math.floorDiv(since, CYCLE_MS);
        long offset = Math.floorMod(since, CYCLE_MS);
        String phase;
        long remaining;
        if (offset < BETTING_MS) {
            phase = "BETTING";
            remaining = BETTING_MS - offset;
        } else if (offset < BETTING_MS + DRAW_MS) {
            phase = "DRAW";
            remaining = BETTING_MS + DRAW_MS - offset;
        } else {
            phase = "RESULT";
            remaining = CYCLE_MS - offset;
        }
        return new Clock(now, round, phase, remaining);
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    private Instant dayStart() {
        ZoneId z = zone();
        return LocalDate.now(z).atStartOfDay(z).toInstant();
    }

    private long msUntilReset() {
        ZoneId z = zone();
        ZonedDateTime now = ZonedDateTime.now(z);
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(z)).toMillis();
    }

    // ---- API ---------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> state(User user) {
        Clock c = clock();
        if ("RESULT".equals(c.phase())) settleDue(); // wallet + rankings already include this round's payout

        Wallet wallet = walletService.ensure(user.getId());
        List<GameBet> bets = betRepository.findByUserIdAndGameAndRound(user.getId(), GAME, c.round());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("round", c.round());
        data.put("epochMs", EPOCH_MS);
        data.put("phase", c.phase());
        data.put("phaseRemainingMs", c.remaining());
        data.put("serverTime", c.now());
        data.put("bettingMs", BETTING_MS);
        data.put("drawMs", DRAW_MS);
        data.put("resultMs", RESULT_MS);
        data.put("cycleMs", CYCLE_MS);
        data.put("multipliers", Arrays.stream(MULT_X100).mapToDouble(m -> m / 100.0).boxed().toList());
        data.put("chips", Arrays.stream(CHIPS).boxed().toList());
        data.put("coins", wallet.getCoins());
        data.put("beans", wallet.getBeans());
        data.put("myBets", aggregate(bets));
        data.put("todayWin", betRepository.sumPayoutSince(user.getId(), GAME, dayStart()));

        List<Map<String, Object>> rank = todayRank();
        data.put("topPlayers", rank.subList(0, Math.min(3, rank.size())));
        data.put("rankResetsInMs", msUntilReset());

        boolean showWinner = !"BETTING".equals(c.phase()); // hidden while bets are open
        data.put("winner", showWinner ? winnerOf(c.round()) : null);

        if ("RESULT".equals(c.phase())) {
            int w = winnerOf(c.round());
            long total = bets.stream().mapToLong(GameBet::getAmount).sum();
            long win = bets.stream().mapToLong(b -> payoutFor(b.getOptionIndex(), b.getAmount(), w)).sum();
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("totalBet", total);
            res.put("payout", win);
            data.put("result", res);
            data.put("roundRanking", withProfiles(betRepository.roundRevenue(GAME, c.round(), PageRequest.of(0, 5))));
        }

        // Last 8 winners, newest first (the current round counts once its result is showing).
        int start = "RESULT".equals(c.phase()) ? 0 : 1;
        List<Integer> history = new ArrayList<>();
        for (int i = start; i < start + HISTORY; i++) {
            if (c.round() - i >= 0) history.add(winnerOf(c.round() - i));
        }
        data.put("history", history);
        return data;
    }

    @Transactional
    public Map<String, Object> placeBets(User user, List<BetLine> lines) {
        if (lines == null || lines.isEmpty()) throw ApiException.badRequest("No bets");
        if (lines.size() > MAX_LINES) throw ApiException.badRequest("Too many bets at once");

        long total = 0;
        Map<Integer, Long> perOption = new TreeMap<>();
        for (BetLine l : lines) {
            if (l.option() < 0 || l.option() >= MULT_X100.length) throw ApiException.badRequest("Invalid option");
            final long amt = l.amount();
            if (Arrays.stream(CHIPS).noneMatch(x -> x == amt)) throw ApiException.badRequest("Invalid bet amount");
            total += amt;
            perOption.merge(l.option(), amt, Long::sum);
        }

        Clock c = clock();
        if (!"BETTING".equals(c.phase()) || c.remaining() < CLOSE_GUARD_MS) {
            throw ApiException.unprocessable("Betting is closed");
        }
        long already = betRepository.sumAmount(user.getId(), GAME, c.round());
        if (already + total > MAX_PER_ROUND) throw ApiException.unprocessable("Round bet limit reached");

        // Debit first (throws "Insufficient coins" and rolls everything back if the balance is too low)
        Wallet w = walletService.adjust(user.getId(), WalletService.COINS, -total, "GAME_BET", null,
                "Galaxy Feast bet · round " + c.round());

        for (Map.Entry<Integer, Long> e : perOption.entrySet()) {
            GameBet b = new GameBet();
            b.setUserId(user.getId());
            b.setGame(GAME);
            b.setRound(c.round());
            b.setOptionIndex(e.getKey());
            b.setAmount(e.getValue());
            betRepository.save(b);
        }

        List<GameBet> bets = betRepository.findByUserIdAndGameAndRound(user.getId(), GAME, c.round());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("round", c.round());
        data.put("coins", w.getCoins());
        data.put("beans", w.getBeans());
        data.put("myBets", aggregate(bets));
        return data;
    }

    /** The player's own finished rounds, newest first. Shown only inside the game. */
    @Transactional(readOnly = true)
    public Map<String, Object> history(User user, int limit) {
        int rounds = Math.max(1, Math.min(limit, 50));
        List<GameBet> bets = betRepository.findByUserIdAndGameAndSettledTrueOrderByRoundDescCreatedAtAsc(
                user.getId(), GAME, PageRequest.of(0, 600));

        Map<Long, List<GameBet>> byRound = new LinkedHashMap<>();
        for (GameBet b : bets) byRound.computeIfAbsent(b.getRound(), k -> new ArrayList<>()).add(b);

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<Long, List<GameBet>> e : byRound.entrySet()) {
            if (out.size() >= rounds) break;
            long round = e.getKey();
            int winner = winnerOf(round);
            long revenue = 0;
            Instant first = null;
            for (GameBet b : e.getValue()) {
                revenue += b.getPayout();
                if (first == null || b.getCreatedAt().isBefore(first)) first = b.getCreatedAt();
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("round", round);
            m.put("time", first == null ? null : first.toString());
            m.put("winner", winner);
            m.put("revenue", revenue);
            m.put("bets", aggregate(e.getValue()));
            out.add(m);
        }
        return Map.of("rounds", out);
    }

    /** Today's top players by revenue. */
    @Transactional(readOnly = true)
    public Map<String, Object> rank() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("players", todayRank());
        data.put("rankResetsInMs", msUntilReset());
        return data;
    }

    /** Pays out every finished round. Safe to call from many places at once. */
    @Transactional
    public void settleDue() {
        Clock c = clock();
        long maxRound = "RESULT".equals(c.phase()) ? c.round() : c.round() - 1;
        List<GameBet> due = betRepository.findUnsettled(GAME, maxRound, PageRequest.of(0, 500));
        boolean any = false;
        for (GameBet b : due) {
            String id = b.getId();
            String userId = b.getUserId();
            long round = b.getRound();
            int win = winnerOf(round);
            long payout = payoutFor(b.getOptionIndex(), b.getAmount(), win);
            // Only one caller can flip settled false -> true, so a bet is paid at most once.
            if (betRepository.markSettled(id, payout) == 1) {
                any = true;
                if (payout > 0) {
                    walletService.adjust(userId, WalletService.COINS, payout, "GAME_WIN", GAME + ":" + id,
                            "Galaxy Feast win · round " + round);
                }
            }
        }
        if (any) rankCache = null;
    }

    // ---- helpers -----------------------------------------------------------------------------

    private List<Map<String, Object>> todayRank() {
        long now = System.currentTimeMillis();
        Instant from = dayStart();
        RankCache c = rankCache;
        if (c != null && now - c.at() < 2000 && c.from().equals(from)) return c.players();
        List<Map<String, Object>> list = withProfiles(betRepository.topRevenue(GAME, from, PageRequest.of(0, RANK_SIZE)));
        rankCache = new RankCache(now, from, list);
        return list;
    }

    private List<Map<String, Object>> withProfiles(List<Object[]> rows) {
        List<String> ids = new ArrayList<>();
        for (Object[] r : rows) ids.add((String) r[0]);
        Map<String, User> users = new HashMap<>();
        if (!ids.isEmpty()) userRepository.findAllById(ids).forEach(u -> users.put(u.getId(), u));

        List<Map<String, Object>> out = new ArrayList<>();
        int rank = 1;
        for (Object[] r : rows) {
            User u = users.get((String) r[0]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", rank++);
            m.put("userId", r[0]);
            m.put("name", u == null ? "Player" : u.getName());
            m.put("avatarUrl", u == null ? null : u.getAvatarUrl());
            m.put("revenue", ((Number) r[1]).longValue());
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> aggregate(List<GameBet> bets) {
        Map<Integer, Long> sums = new TreeMap<>();
        for (GameBet b : bets) sums.merge(b.getOptionIndex(), b.getAmount(), Long::sum);
        List<Map<String, Object>> out = new ArrayList<>();
        sums.forEach((k, v) -> out.add(Map.of("option", k, "amount", v)));
        return out;
    }
}
