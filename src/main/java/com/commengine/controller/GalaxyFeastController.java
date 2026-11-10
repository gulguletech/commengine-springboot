package com.commengine.controller;

import com.commengine.common.ApiException;
import com.commengine.common.ApiResponse;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.GalaxyFeastService;
import com.commengine.service.GalaxyFeastService.BetLine;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/games/galaxy-feast")
public class GalaxyFeastController {

    private final GalaxyFeastService service;

    public GalaxyFeastController(GalaxyFeastService service) {
        this.service = service;
    }

    @GetMapping("/state")
    public Object state(@CurrentUser User user) {
        return ApiResponse.ok(service.state(user));
    }

    /** One bet: { option, amount } */
    @PostMapping("/bet")
    public Object bet(@CurrentUser User user, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.placeBets(user, List.of(parse(body))));
    }

    /** Many bets in one go (used by Auto Play): { bets: [ { option, amount }, ... ] } */
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

    /** The player's own history (shown inside the game only). */
    @GetMapping("/history")
    public Object history(@CurrentUser User user, @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.history(user, limit));
    }

    /** Today's revenue rank. */
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
