package com.commengine.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless, clock-driven lucky draw. Every client computes the same round from the server clock,
 * so everyone sees the same timer and the same winner without any DB or scheduler.
 * Cycle: 30s BETTING -> 5s DRAW -> 5s RESULT -> next round.
 */
@Service
public class LuckyDrawService {

    public static final long BETTING_MS = 30_000;
    public static final long DRAW_MS = 5_000;
    public static final long RESULT_MS = 5_000;
    public static final long CYCLE_MS = BETTING_MS + DRAW_MS + RESULT_MS;
    public static final int OPTIONS = 6;
    private static final int HISTORY = 8;

    // TODO before real coins are added: replace with a secret/SecureRandom-based result stored per round.
    public int winnerOf(long round) {
        long x = round * 0x9E3779B97F4A7C15L ^ 0x5DEECE66DL;
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return (int) Math.floorMod(x, (long) OPTIONS);
    }

    public Map<String, Object> state() {
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

        List<Integer> history = new ArrayList<>();
        for (int i = 1; i <= HISTORY; i++) history.add(winnerOf(round - i));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("round", round);
        data.put("phase", phase);
        data.put("phaseRemainingMs", remaining);
        data.put("serverTime", now);
        // Never reveal the winner while bets are open.
        data.put("winner", "BETTING".equals(phase) ? null : winnerOf(round));
        data.put("history", history);
        data.put("bettingMs", BETTING_MS);
        data.put("drawMs", DRAW_MS);
        data.put("resultMs", RESULT_MS);
        data.put("cycleMs", CYCLE_MS);
        data.put("options", OPTIONS);
        return data;
    }
}
