package com.commengine.controller;

import com.commengine.common.ApiException;
import com.commengine.common.ApiResponse;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.FruitPartyService;
import com.commengine.service.FruitPartyService.BetLine;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/games/fruit-party")
public class FruitPartyController {

    private final FruitPartyService service;

    public FruitPartyController(FruitPartyService service) {
        this.service = service;
    }

    @GetMapping("/state")
    public Object state(@CurrentUser User user) {
        return ApiResponse.ok(service.state(user));
    }

    /** Light poll: jackpot + everyone's total on each fruit. */
    @GetMapping("/pool")
    public Object pool(@CurrentUser User user) {
        return ApiResponse.ok(service.pool());
    }

    @PostMapping("/bet")
    public Object bet(@CurrentUser User user, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.placeBets(user, List.of(parse(body))));
    }

    /** Many bets at once (Auto / repeat): { bets: [ { option, amount }, ... ] } */
    @PostMapping("/bets")
    public Object bets(@CurrentUser User user, @RequestBody Map<String, Object> body) {
        Object raw = body.get("bets");
        if (!(raw instanceof List<?> list)) throw ApiException.badRequest("bets is required");
        List<BetLine> lines = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) throw ApiException.badRequest("Invalid bet");
            Object opt = m.get("option");
            Object amt = m.get("amount");
            if (!(opt instanceof Number) || !(amt instanceof Number)) throw ApiException.badRequest("option and amount are required");
            lines.add(new BetLine(((Number) opt).intValue(), ((Number) amt).longValue()));
        }
        return ApiResponse.ok(service.placeBets(user, lines));
    }

    /** "Mine": the player's own finished rounds. */
    @GetMapping("/history")
    public Object history(@CurrentUser User user, @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.history(user, limit));
    }

    /** Trophy button: today's biggest winners. */
    @GetMapping("/rank")
    public Object rank(@CurrentUser User user) {
        return ApiResponse.ok(service.rank());
    }

    private static BetLine parse(Map<String, Object> body) {
        Object o = body.get("option");
        Object a = body.get("amount");
        if (!(o instanceof Number) || !(a instanceof Number)) throw ApiException.badRequest("option and amount are required");
        return new BetLine(((Number) o).intValue(), ((Number) a).longValue());
    }
}
