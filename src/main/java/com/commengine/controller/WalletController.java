package com.commengine.controller;

import com.commengine.common.ApiResponse;
import com.commengine.entity.User;
import com.commengine.security.CurrentUser;
import com.commengine.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /** { coins, beans } for the logged-in user. */
    @GetMapping
    public Object getWallet(@CurrentUser User user) {
        return ApiResponse.ok(walletService.getBalance(user.getId()));
    }

    @GetMapping("/transactions")
    public Object transactions(@CurrentUser User user, @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(Map.of("transactions", walletService.transactions(user.getId(), limit)));
    }
}
