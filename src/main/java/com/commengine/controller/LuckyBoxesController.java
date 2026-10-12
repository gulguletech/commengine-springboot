package com.commengine.controller;

import com.commengine.common.ApiException;
import com.commengine.common.ApiResponse;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.LuckyBoxesService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/games/lucky-boxes")
public class LuckyBoxesController {

    private final LuckyBoxesService service;

    public LuckyBoxesController(LuckyBoxesService service) {
        this.service = service;
    }

    @GetMapping("/state")
    public Object state(@CurrentUser User user) {
        return ApiResponse.ok(service.state(user));
    }

    @PostMapping("/bet")
    public Object bet(@CurrentUser User user, @RequestBody Map<String, Object> body) {
        Object o = body.get("option");
        Object a = body.get("amount");
        if (!(o instanceof Number) || !(a instanceof Number)) throw ApiException.badRequest("option and amount are required");
        return ApiResponse.ok(service.bet(user, ((Number) o).intValue(), ((Number) a).longValue()));
    }
}
