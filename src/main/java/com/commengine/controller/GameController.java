package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.service.LuckyDrawService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final LuckyDrawService luckyDrawService;

    public GameController(LuckyDrawService luckyDrawService) {
        this.luckyDrawService = luckyDrawService;
    }

    @GetMapping("/lucky-draw/state")
    public Object luckyDrawState() {
        return ApiResponse.ok(luckyDrawService.state());
    }
}
