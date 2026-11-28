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
import java.time.Instant;
import java.util.*;

/**
 * Fruit Wheel: a 9-segment wheel (77 + 4 watermelon + 4 plum). Players bet on
 * watermelon (x2), 77 (x8) or plum (x2). Shared 40s rounds: 25s betting -> 8s spin -> 7s result.
 *
 * Options: 0 watermelon, 1 seven (77), 2 plum.
 * Segments clockwise from the top: 0 = 77, then watermelon, plum, watermelon, plum, ... (see SEG).
 *
 * "When lucky 77 occurs there is a chance to hit multiple awards": on a 77 there is a
 * BONUS_PERCENT chance that watermelon OR plum also pays out (set BONUS_PERCENT to 0 to switch it off).
 *
 * Round + phase come from the server clock, the result from a secret HMAC; bets are debited at once
 * and winners are paid by the server (settler), never by the client.
 */
@Service
public class FruitWheelService {

    public static final String GAME = "fruit-wheel";
    public static final long BETTING_MS = 25_000;
    public static final long DRAW_MS = 8_000;
    public static final long RESULT_MS = 7_000;
    public static final long CYCLE_MS = BETTING_MS + DRAW_MS + RESULT_MS;
    public static final long EPOCH_MS = Instant.parse("2026-11-01T00:00:00Z").toEpochMilli();

    /** option for each wheel segment, clockwise from the top */
    private static final int[] SEG = {1, 0, 2, 0, 2, 0, 2, 0, 2};
    private static final int[] MULT_X100 = {200, 800, 200};
    public static final long[] CHIPS = {100, 500, 1_000, 2_000, 5_000};
    private static final long MAX_PER_ROUND = 2_000_000;
    private static final int BONUS_PERCENT = 25;
    private static final long CLOSE_GUARD_MS = 600;
    private static final int HISTORY = 48;

    private final GameBetRepository betRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;

    @Value("${GAME_SECRET:${JWT_SECRET:change_me}}")
    private String secret;

    public record BetLine(int option, long amount) {}

    private record Clock(long now, long round, String phase, long remaining) {}

    /** segment = wheel segment index, symbol = winning option, bonus = extra winning option or -1 */
    public record Outcome(int segment, int symbol, int bonus) {}

    public FruitWheelService(GameBetRepository betRepository, WalletService walletService, UserRepository userRepository) {
        this.betRepository = betRepository;
        this.walletService = walletService;
        this.userRepository = userRepository;
    }

    // ---- round maths -------------------------------------------------------------------------

    public Outcome outcomeOf(long round) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal((GAME + ":" + round).getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 8; i++) v = (v << 8) | (h[i] & 0xffL);
            int seg = (int) Math.floorMod(v, (long) SEG.length);
            int symbol = SEG[seg];
            int bonus = -1;
            if (symbol == 1) {
                long b = 0;
                for (int i = 8; i < 16; i++) b = (b << 8) | (h[i] & 0xffL);
                if (Math.floorMod(b, 100L) < BONUS_PERCENT) bonus = ((b >>> 20) & 1L) == 0 ? 0 : 2;
            }
            return new Outcome(seg, symbol, bonus);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute result", e);
        }
    }

    static boolean hits(int option, Outcome o) {
        return option == o.symbol() || option == o.bonus();
    }

    static long payoutFor(int option, long amount, Outcome o) {
        return hits(option, o) ? amount * MULT_X100[option] / 100 : 0;
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
        data.put("multipliers", Arrays.stream(MULT_X100).mapToDouble(m -> m / 100.0).boxed().toList());
        data.put("segments", Arrays.stream(SEG).boxed().toList());
        data.put("chips", Arrays.stream(CHIPS).boxed().toList());
        data.put("coins", wallet.getCoins());
        data.put("beans", wallet.getBeans());
        data.put("myBets", aggregate(bets));
        data.put("pool", pool().get("pool"));
        data.put("me", user.getAvatarUrl() == null
                ? Map.of("name", String.valueOf(user.getName()))
                : Map.of("name", String.valueOf(user.getName()), "avatarUrl", user.getAvatarUrl()));

        boolean show = !"BETTING".equals(c.phase()); // hidden while bets are open
        Outcome o = outcomeOf(c.round());
        data.put("winner", show ? o.symbol() : null);
        data.put("segment", show ? o.segment() : null);
        data.put("bonus", show && o.bonus() >= 0 ? o.bonus() : null);

        if ("RESULT".equals(c.phase())) {
            long total = bets.stream().mapToLong(GameBet::getAmount).sum();
            long win = bets.stream().mapToLong(b -> payoutFor(b.getOptionIndex(), b.getAmount(), o)).sum();
            data.put("result", Map.of("totalBet", total, "payout", win));
            data.put("roundTop", withProfiles(betRepository.roundTopWithBet(GAME, c.round(), PageRequest.of(0, 3))));
        }

        // Last 48 results as wheel segments, newest first (the current round counts once its result shows).
        int start = "RESULT".equals(c.phase()) ? 0 : 1;
        List<Integer> history = new ArrayList<>();
        for (int i = start; i < start + HISTORY; i++) {
            if (c.round() - i >= 0) history.add(outcomeOf(c.round() - i).segment());
        }
        data.put("history", history);
        return data;
    }

    /** Everyone's total bet on each option this round. */
    @Transactional(readOnly = true)
    public Map<String, Object> pool() {
        Clock c = clock();
        long[] pool = new long[MULT_X100.length];
        for (Object[] r : betRepository.poolByOption(GAME, c.round())) {
            int opt = ((Number) r[0]).intValue();
            if (opt >= 0 && opt < pool.length) pool[opt] = ((Number) r[1]).longValue();
        }
        return Map.of("round", c.round(), "pool", Arrays.stream(pool).boxed().toList());
    }

    @Transactional
    public Map<String, Object> placeBets(User user, List<BetLine> lines) {
        if (lines == null || lines.isEmpty()) throw ApiException.badRequest("No bets");
        if (lines.size() > 200) throw ApiException.badRequest("Too many bets");

        long total = 0;
        for (BetLine l : lines) {
            if (l.option() < 0 || l.option() >= MULT_X100.length) throw ApiException.badRequest("Invalid option");
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
                "Fruit Wheel bet · round " + c.round());

        for (BetLine l : lines) {
            GameBet b = new GameBet();
            b.setUserId(user.getId());
            b.setGame(GAME);
            b.setRound(c.round());
            b.setOptionIndex(l.option());
            b.setAmount(l.amount());
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

    /** "My Bets": the player's own finished rounds, newest first. Shown only inside the game. */
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
            m.put("winner", o.symbol());
            m.put("bonus", o.bonus() >= 0 ? o.bonus() : null);
            m.put("revenue", revenue);
            m.put("bets", aggregate(e.getValue()));
            out.add(m);
        }
        return Map.of("rounds", out);
    }

    /** Pays out every finished round. Safe to call from many places at once. */
    @Transactional
    public void settleDue() {
        Clock c = clock();
        long maxRound = "RESULT".equals(c.phase()) ? c.round() : c.round() - 1;
        List<GameBet> due = betRepository.findUnsettled(GAME, maxRound, PageRequest.of(0, 500));
        for (GameBet b : due) {
            Outcome o = outcomeOf(b.getRound());
            long payout = payoutFor(b.getOptionIndex(), b.getAmount(), o);
            // Only one caller can flip settled false -> true, so a bet is paid at most once.
            if (betRepository.markSettled(b.getId(), payout) == 1 && payout > 0) {
                walletService.adjust(b.getUserId(), WalletService.COINS, payout, "GAME_WIN", GAME + ":" + b.getId(),
                        "Fruit Wheel win · round " + b.getRound());
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
