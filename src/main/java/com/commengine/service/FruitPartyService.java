package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.entity.GameBet;
import com.commengine.entity.GameJackpot;
import com.commengine.entity.User;
import com.commengine.entity.Wallet;
import com.commengine.repository.GameBetRepository;
import com.commengine.repository.GameJackpotRepository;
import com.commengine.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
import java.util.*;

/**
 * Fruit Party (a.k.a. "Jackpot" fruit game).
 *
 * Eight fruits around a countdown tile. Players pick a chip (1K / 10K / 50K / 100K) and tap the fruits they
 * expect to win. Shared 40s rounds: 20s betting -> 14s draw (+ mini-slot) -> 6s result.
 *
 * Options (clockwise from the top-left tile): 0 lemon x5, 1 raspberry x5, 2 kiwi x5, 3 plum x5,
 * 4 strawberry x10, 5 pineapple x10, 6 watermelon x20, 7 cherry x40.
 * Each fruit is drawn with probability proportional to 1 / odds, so every fruit has the same ~93% return.
 *
 * After every draw a mini-slot may add a bonus (see BONUS_WEIGHTS): Fruit +1/+2/+3 (extra winning fruits),
 * Bonus x2 / x3 (all winning payouts multiplied) or JACKPOT (winners share part of the jackpot pool).
 * Every bet adds JACKPOT_RAKE_PERCENT % to the jackpot pool.
 *
 * Expected return per coin bet ~= 0.93 * (1 + P(x2) + 2*P(x3) + E[extra fruits]) + jackpot rake
 *                               ~= 0.93 * 1.04 + 0.01 ~= 97.7%  -> house edge about 2.3% with the weights below.
 * Raise/lower BONUS_WEIGHTS to tune it.
 *
 * Round + phase come from the server clock, the result from a secret HMAC; bets are debited at once and
 * winners are paid by the server (settler), never by the client.
 */
@Service
public class FruitPartyService {

    public static final String GAME = "fruit-party";
    public static final long BETTING_MS = 20_000;
    public static final long DRAW_MS = 14_000;
    public static final long RESULT_MS = 6_000;
    public static final long CYCLE_MS = BETTING_MS + DRAW_MS + RESULT_MS;
    public static final long EPOCH_MS = Instant.parse("2026-11-01T00:00:00Z").toEpochMilli();

    /** payout multiplier per fruit (stake included) */
    private static final int[] ODDS = {5, 5, 5, 5, 10, 10, 20, 40};
    /** draw weight per fruit = 1 / odds (scaled to integers) */
    private static final int[] WEIGHT = {8, 8, 8, 8, 4, 4, 2, 1};
    private static final int WEIGHT_SUM = Arrays.stream(WEIGHT).sum();

    /** bonus codes */
    public static final int B_NONE = 0, B_PLUS1 = 1, B_PLUS2 = 2, B_PLUS3 = 3, B_X2 = 4, B_X3 = 5, B_JACKPOT = 6;
    /** chance per 1000 rounds of each bonus code (index = code); must sum to 1000 */
    private static final int[] BONUS_WEIGHTS = {960, 8, 4, 2, 10, 4, 12};

    public static final long[] CHIPS = {1_000, 10_000, 50_000, 100_000};
    private static final long MAX_PER_ROUND = 10_000_000;
    private static final long CLOSE_GUARD_MS = 600;
    private static final int HISTORY = 8;

    private static final long JACKPOT_SEED = 1_000_000;
    private static final long JACKPOT_FLOOR = 100_000;
    private static final int JACKPOT_RAKE_PERCENT = 1;
    private static final long JACKPOT_MIN_COST = 1_000;

    private final GameBetRepository betRepository;
    private final GameJackpotRepository jackpotRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;

    @Value("${GAME_SECRET:${JWT_SECRET:change_me}}")
    private String secret;

    @Value("${GAME_TZ:Asia/Kolkata}")
    private String tz;

    public record BetLine(int option, long amount) {}

    private record Clock(long now, long round, String phase, long remaining) {}

    /** primary = winning fruit, bonus = bonus code, extras = additional winning fruits (Fruit +N) */
    public record Outcome(int primary, int bonus, List<Integer> extras) {}

    public FruitPartyService(GameBetRepository betRepository, GameJackpotRepository jackpotRepository,
                             WalletService walletService, UserRepository userRepository) {
        this.betRepository = betRepository;
        this.jackpotRepository = jackpotRepository;
        this.walletService = walletService;
        this.userRepository = userRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        ensureJackpot();
    }

    private void ensureJackpot() {
        if (!jackpotRepository.existsById(GAME)) {
            jackpotRepository.saveAndFlush(new GameJackpot(GAME, JACKPOT_SEED));
        }
    }

    // ---- round maths -------------------------------------------------------------------------

    public Outcome outcomeOf(long round) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal((GAME + ":" + round).getBytes(StandardCharsets.UTF_8));

            int primary = pickWeighted(u64(h, 0), WEIGHT, new boolean[WEIGHT.length]);

            int roll = (int) Math.floorMod(u64(h, 8), 1000L);
            int bonus = 0, acc = 0;
            for (int i = 0; i < BONUS_WEIGHTS.length; i++) {
                acc += BONUS_WEIGHTS[i];
                if (roll < acc) {
                    bonus = i;
                    break;
                }
            }

            List<Integer> extras = new ArrayList<>();
            if (bonus >= B_PLUS1 && bonus <= B_PLUS3) {
                boolean[] taken = new boolean[WEIGHT.length];
                taken[primary] = true;
                for (int n = 0; n < bonus; n++) {
                    int pick = pickWeighted((u64(h, 16) ^ u64(h, 24)) + 0x9E3779B97F4A7C15L * (n + 1), WEIGHT, taken);
                    taken[pick] = true;
                    extras.add(pick);
                }
            }
            return new Outcome(primary, bonus, List.copyOf(extras));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute result", e);
        }
    }

    private static long u64(byte[] h, int from) {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | (h[from + i] & 0xffL);
        return v;
    }

    private static int pickWeighted(long rnd, int[] weights, boolean[] taken) {
        int total = 0;
        for (int i = 0; i < weights.length; i++) if (!taken[i]) total += weights[i];
        int r = (int) Math.floorMod(rnd, (long) total);
        for (int i = 0; i < weights.length; i++) {
            if (taken[i]) continue;
            if (r < weights[i]) return i;
            r -= weights[i];
        }
        return 0;
    }

    static Set<Integer> winners(Outcome o) {
        Set<Integer> w = new LinkedHashSet<>();
        w.add(o.primary());
        w.addAll(o.extras());
        return w;
    }

    static int multiplier(Outcome o) {
        return o.bonus() == B_X2 ? 2 : o.bonus() == B_X3 ? 3 : 1;
    }

    static long payoutFor(int option, long amount, Outcome o) {
        return winners(o).contains(option) ? amount * ODDS[option] * multiplier(o) : 0;
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

    // ---- API ---------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> state(User user) {
        Clock c = clock();
        if ("RESULT".equals(c.phase())) settleDue();

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
        data.put("odds", Arrays.stream(ODDS).boxed().toList());
        data.put("chips", Arrays.stream(CHIPS).boxed().toList());
        data.put("coins", wallet.getCoins());
        data.put("beans", wallet.getBeans());
        data.put("myBets", aggregate(bets));
        data.put("todayWin", betRepository.sumPayoutSince(user.getId(), GAME, dayStart()));
        Map<String, Object> pool = pool();
        data.put("pool", pool.get("pool"));
        data.put("jackpot", pool.get("jackpot"));
        data.put("me", user.getAvatarUrl() == null
                ? Map.of("name", String.valueOf(user.getName()))
                : Map.of("name", String.valueOf(user.getName()), "avatarUrl", user.getAvatarUrl()));

        boolean show = !"BETTING".equals(c.phase()); // result stays hidden while bets are open
        Outcome o = outcomeOf(c.round());
        data.put("winner", show ? o.primary() : null);
        data.put("bonus", show ? o.bonus() : null);
        data.put("extras", show ? o.extras() : null);

        if ("RESULT".equals(c.phase())) {
            long total = bets.stream().mapToLong(GameBet::getAmount).sum();
            long win = bets.stream().mapToLong(GameBet::getPayout).sum();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("totalBet", total);
            r.put("payout", win);
            data.put("result", r);
            data.put("roundTop", withProfiles(betRepository.roundTopWithBet(GAME, c.round(), PageRequest.of(0, 3))));
        }

        // last results, newest first (the current round counts once its result shows)
        int start = "RESULT".equals(c.phase()) ? 0 : 1;
        List<Integer> history = new ArrayList<>();
        for (int i = start; i < start + HISTORY; i++) {
            if (c.round() - i >= 0) history.add(outcomeOf(c.round() - i).primary());
        }
        data.put("history", history);
        data.put("prevBonus", c.round() > 0 ? outcomeOf(c.round() - ("RESULT".equals(c.phase()) ? 0 : 1)).bonus() : 0);
        return data;
    }

    /** Jackpot pool and everyone's total bet on each fruit this round. */
    @Transactional(readOnly = true)
    public Map<String, Object> pool() {
        Clock c = clock();
        long[] pool = new long[ODDS.length];
        for (Object[] r : betRepository.poolByOption(GAME, c.round())) {
            int opt = ((Number) r[0]).intValue();
            if (opt >= 0 && opt < pool.length) pool[opt] = ((Number) r[1]).longValue();
        }
        long jackpot = jackpotRepository.findById(GAME).map(GameJackpot::getPool).orElse(JACKPOT_SEED);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("round", c.round());
        m.put("pool", Arrays.stream(pool).boxed().toList());
        m.put("jackpot", jackpot);
        return m;
    }

    @Transactional
    public Map<String, Object> placeBets(User user, List<BetLine> lines) {
        if (lines == null || lines.isEmpty()) throw ApiException.badRequest("No bets");
        if (lines.size() > 200) throw ApiException.badRequest("Too many bets");

        long total = 0;
        for (BetLine l : lines) {
            if (l.option() < 0 || l.option() >= ODDS.length) throw ApiException.badRequest("Invalid option");
            final long amt = l.amount();
            if (Arrays.stream(CHIPS).noneMatch(x -> x == amt)) throw ApiException.badRequest("Invalid bet amount");
            total += amt;
        }

        Clock c = clock();
        if (!"BETTING".equals(c.phase()) || c.remaining() < CLOSE_GUARD_MS) {
            throw ApiException.unprocessable("Betting is closed");
        }
        long already = betRepository.sumAmount(user.getId(), GAME, c.round());
        if (already + total > MAX_PER_ROUND) throw ApiException.unprocessable("Round bet limit reached");

        // Debit first: throws "Insufficient coins" and rolls everything back if the balance is too low.
        Wallet w = walletService.adjust(user.getId(), WalletService.COINS, -total, "GAME_BET", null,
                "Fruit Party bet · round " + c.round());

        for (BetLine l : lines) {
            GameBet b = new GameBet();
            b.setUserId(user.getId());
            b.setGame(GAME);
            b.setRound(c.round());
            b.setOptionIndex(l.option());
            b.setAmount(l.amount());
            betRepository.save(b);
        }

        ensureJackpot();
        jackpotRepository.addPool(GAME, Math.max(1, total * JACKPOT_RAKE_PERCENT / 100));

        List<GameBet> bets = betRepository.findByUserIdAndGameAndRound(user.getId(), GAME, c.round());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("round", c.round());
        data.put("coins", w.getCoins());
        data.put("beans", w.getBeans());
        data.put("myBets", aggregate(bets));
        return data;
    }

    /** "Mine": the player's own finished rounds, newest first. */
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
            Outcome o = outcomeOf(round);
            long revenue = 0;
            Instant first = null;
            for (GameBet b : e.getValue()) {
                revenue += b.getPayout();
                if (first == null || b.getCreatedAt().isBefore(first)) first = b.getCreatedAt();
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("round", round);
            m.put("time", first == null ? null : first.toString());
            m.put("winner", o.primary());
            m.put("bonus", o.bonus());
            m.put("extras", o.extras());
            m.put("revenue", revenue);
            m.put("bets", aggregate(e.getValue()));
            out.add(m);
        }
        return Map.of("rounds", out);
    }

    /** Trophy: today's biggest winners. */
    @Transactional(readOnly = true)
    public Map<String, Object> rank() {
        List<Object[]> rows = betRepository.topRevenue(GAME, dayStart(), PageRequest.of(0, 10));
        List<Object[]> shaped = new ArrayList<>();
        for (Object[] r : rows) shaped.add(new Object[]{r[0], 0L, r[1]});
        return Map.of("rank", withProfiles(shaped));
    }

    /** Pays out every finished round (and the jackpot). Safe to call from many places at once. */
    @Transactional
    public void settleDue() {
        Clock c = clock();
        long maxRound = "RESULT".equals(c.phase()) ? c.round() : c.round() - 1;
        if (betRepository.countUnsettled(GAME, maxRound) == 0) return;

        // One settler at a time (row lock on the jackpot). Bets are loaded only after we hold the lock.
        ensureJackpot();
        GameJackpot jp = jackpotRepository.lockByGame(GAME).orElseThrow();
        List<GameBet> due = betRepository.findUnsettled(GAME, maxRound, PageRequest.of(0, 1000));

        Map<Long, List<GameBet>> byRound = new TreeMap<>();
        for (GameBet b : due) byRound.computeIfAbsent(b.getRound(), k -> new ArrayList<>()).add(b);
        for (Map.Entry<Long, List<GameBet>> e : byRound.entrySet()) settleRound(jp, e.getKey(), e.getValue());
    }

    private void settleRound(GameJackpot jp, long round, List<GameBet> bets) {
        Outcome o = outcomeOf(round);
        Set<Integer> win = winners(o);

        // Jackpot: winners (cost >= 1,000) share a % of the pool, in proportion to their winning cost.
        Map<String, Long> jackpotShare = new HashMap<>();
        if (o.bonus() == B_JACKPOT && jp.getLastPaidRound() < round) {
            Map<String, Long> cost = new LinkedHashMap<>();
            for (GameBet b : bets) if (win.contains(b.getOptionIndex())) cost.merge(b.getUserId(), b.getAmount(), Long::sum);
            cost.values().removeIf(v -> v < JACKPOT_MIN_COST);
            if (!cost.isEmpty()) {
                long max = Collections.max(cost.values());
                int pct = max >= 1_000_000 ? 70 : max >= 100_000 ? 50 : max >= 10_000 ? 30 : 10;
                long pot = jp.getPool() * pct / 100;
                long totalCost = cost.values().stream().mapToLong(Long::longValue).sum();
                long paid = 0;
                for (Map.Entry<String, Long> e : cost.entrySet()) {
                    long share = pot * e.getValue() / totalCost;
                    if (share > 0) {
                        jackpotShare.put(e.getKey(), share);
                        paid += share;
                    }
                }
                jp.setPool(Math.max(JACKPOT_FLOOR, jp.getPool() - paid));
            }
            jp.setLastPaidRound(round);
            jackpotRepository.save(jp);
        }

        for (GameBet b : bets) {
            long payout = payoutFor(b.getOptionIndex(), b.getAmount(), o);
            if (payout > 0) {
                Long share = jackpotShare.remove(b.getUserId()); // paid on the user's first winning bet
                if (share != null) payout += share;
            }
            // Only one caller can flip settled false -> true, so a bet is paid at most once.
            if (betRepository.markSettled(b.getId(), payout) == 1 && payout > 0) {
                walletService.adjust(b.getUserId(), WalletService.COINS, payout, "GAME_WIN", GAME + ":" + b.getId(),
                        "Fruit Party win · round " + round);
            }
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

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
            m.put("bet", ((Number) r[1]).longValue());
            m.put("win", ((Number) r[2]).longValue());
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
