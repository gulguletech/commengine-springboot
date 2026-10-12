package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.entity.GameBet;
import com.commengine.entity.User;
import com.commengine.entity.Wallet;
import com.commengine.repository.GameBetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lucky Boxes: 8 boxes, shared 40s rounds (30s betting -> 5s draw -> 5s result).
 * The round and phase come from the server clock, the winner from a secret HMAC, bets are debited from
 * the wallet immediately and winners are paid by the server (scheduler), never by the client.
 */
@Service
public class LuckyBoxesService {

    public static final String GAME = "lucky-boxes";
    public static final long BETTING_MS = 30_000;
    public static final long DRAW_MS = 5_000;
    public static final long RESULT_MS = 5_000;
    public static final long CYCLE_MS = BETTING_MS + DRAW_MS + RESULT_MS;

    // Box order matches the icons: ruby, emerald, sapphire, crown, coin bag, lamp, clover, star
    public static final int[] MULT = {5, 5, 5, 45, 25, 15, 10, 5};
    // weight = 450 / multiplier  ->  every box returns ~97.2% in the long run
    private static final int[] WEIGHT = {90, 90, 90, 10, 18, 30, 45, 90};
    private static final int TOTAL_WEIGHT = Arrays.stream(WEIGHT).sum();

    public static final long[] CHIPS = {100, 500, 1000, 5000};
    private static final long MAX_PER_ROUND = 100_000;
    private static final long CLOSE_GUARD_MS = 600; // reject bets arriving right at the buzzer
    private static final int HISTORY = 10;

    private final GameBetRepository betRepository;
    private final WalletService walletService;

    @Value("${GAME_SECRET:${JWT_SECRET:change_me}}")
    private String secret;

    public LuckyBoxesService(GameBetRepository betRepository, WalletService walletService) {
        this.betRepository = betRepository;
        this.walletService = walletService;
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

    private record Clock(long now, long round, long offset, String phase, long remaining) {}

    private Clock clock() {
        long now = System.currentTimeMillis();
        long round = now / CYCLE_MS;
        long offset = now % CYCLE_MS;
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
        return new Clock(now, round, offset, phase, remaining);
    }

    // ---- API ---------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> state(User user) {
        Clock c = clock();
        if ("RESULT".equals(c.phase())) settleDue(); // make sure the wallet already shows this round's payout

        Wallet wallet = walletService.ensure(user.getId());
        List<GameBet> bets = betRepository.findByUserIdAndGameAndRound(user.getId(), GAME, c.round());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("round", c.round());
        data.put("phase", c.phase());
        data.put("phaseRemainingMs", c.remaining());
        data.put("serverTime", c.now());
        data.put("bettingMs", BETTING_MS);
        data.put("drawMs", DRAW_MS);
        data.put("resultMs", RESULT_MS);
        data.put("cycleMs", CYCLE_MS);
        data.put("multipliers", Arrays.stream(MULT).boxed().toList());
        data.put("chips", Arrays.stream(CHIPS).boxed().toList());
        data.put("coins", wallet.getCoins());
        data.put("beans", wallet.getBeans());
        data.put("myBets", aggregate(bets));

        Integer winner = "BETTING".equals(c.phase()) ? null : winnerOf(c.round()); // hidden while bets are open
        data.put("winner", winner);

        if ("RESULT".equals(c.phase())) {
            long total = bets.stream().mapToLong(GameBet::getAmount).sum();
            long win = bets.stream().filter(b -> b.getOptionIndex() == winner).mapToLong(b -> b.getAmount() * MULT[b.getOptionIndex()]).sum();
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("totalBet", total);
            res.put("payout", win);
            data.put("result", res);
        }

        List<Integer> history = new ArrayList<>();
        for (int i = 1; i <= HISTORY; i++) history.add(winnerOf(c.round() - i));
        data.put("history", history);
        return data;
    }

    @Transactional
    public Map<String, Object> bet(User user, int option, long amount) {
        if (option < 0 || option >= MULT.length) throw ApiException.badRequest("Invalid box");
        if (Arrays.stream(CHIPS).noneMatch(x -> x == amount)) throw ApiException.badRequest("Invalid bet amount");

        Clock c = clock();
        if (!"BETTING".equals(c.phase()) || c.remaining() < CLOSE_GUARD_MS) {
            throw ApiException.unprocessable("Betting is closed");
        }
        long already = betRepository.sumAmount(user.getId(), GAME, c.round());
        if (already + amount > MAX_PER_ROUND) throw ApiException.unprocessable("Round bet limit reached");

        // Debit first (throws "Insufficient coins" and rolls everything back if the balance is too low)
        Wallet w = walletService.adjust(user.getId(), WalletService.COINS, -amount, "GAME_BET", null,
                "Lucky Star bet · round " + c.round());

        GameBet b = new GameBet();
        b.setUserId(user.getId());
        b.setGame(GAME);
        b.setRound(c.round());
        b.setOptionIndex(option);
        b.setAmount(amount);
        betRepository.save(b);

        List<GameBet> bets = betRepository.findByUserIdAndGameAndRound(user.getId(), GAME, c.round());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("round", c.round());
        data.put("coins", w.getCoins());
        data.put("beans", w.getBeans());
        data.put("myBets", aggregate(bets));
        return data;
    }

    /** Pays out every finished round. Safe to call from many places at once. */
    @Transactional
    public void settleDue() {
        Clock c = clock();
        long maxRound = "RESULT".equals(c.phase()) ? c.round() : c.round() - 1;
        List<GameBet> due = betRepository.findUnsettled(GAME, maxRound, PageRequest.of(0, 500));
        for (GameBet b : due) {
            String id = b.getId();
            String userId = b.getUserId();
            long round = b.getRound();
            int win = winnerOf(round);
            long payout = b.getOptionIndex() == win ? b.getAmount() * MULT[win] : 0;
            if (betRepository.markSettled(id, payout) == 1 && payout > 0) {
                walletService.adjust(userId, WalletService.COINS, payout, "GAME_WIN", GAME + ":" + id,
                        "Lucky Star win · round " + round);
            }
        }
    }

    private static List<Map<String, Object>> aggregate(List<GameBet> bets) {
        Map<Integer, Long> sums = new TreeMap<>();
        for (GameBet b : bets) sums.merge(b.getOptionIndex(), b.getAmount(), Long::sum);
        List<Map<String, Object>> out = new ArrayList<>();
        sums.forEach((k, v) -> out.add(Map.of("option", k, "amount", v)));
        return out;
    }
}
