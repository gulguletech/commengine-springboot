package com.commengine.controller;

import com.commengine.common.ApiException;
import com.commengine.common.ApiResponse;
import com.commengine.entity.User;
import com.commengine.repository.UserRepository;
import com.commengine.service.WalletService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Give or take coins/beans by hand until payments exist.
 * Protected by the WALLET_ADMIN_KEY env var (header X-Admin-Key). If the env var is not set, this is disabled.
 */
@RestController
@RequestMapping("/api/admin/wallet")
public class AdminWalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    @Value("${WALLET_ADMIN_KEY:}")
    private String adminKey;

    public AdminWalletController(WalletService walletService, UserRepository userRepository) {
        this.walletService = walletService;
        this.userRepository = userRepository;
    }

    @PostMapping("/credit")
    public Object credit(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                         @RequestBody Map<String, Object> body) {
        if (adminKey == null || adminKey.isBlank() || key == null
                || !MessageDigest.isEqual(adminKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            throw ApiException.forbidden("Forbidden");
        }

        String userId = str(body.get("userId"));
        String phone = str(body.get("phone"));
        User user = null;
        if (userId != null) user = userRepository.findById(userId).orElse(null);
        else if (phone != null) user = userRepository.findByPhone(phone).orElse(null);
        if (user == null) throw ApiException.notFound("User not found");

        String currency = str(body.get("currency"));
        currency = currency == null ? "" : currency.toUpperCase();
        Object a = body.get("amount");
        if (!(a instanceof Number)) throw ApiException.badRequest("amount must be a number");
        long amount = ((Number) a).longValue();
        String note = str(body.get("note"));

        var w = walletService.adjust(user.getId(), currency, amount, "ADMIN_ADJUST", null, note == null ? "Admin adjustment" : note);
        return ApiResponse.ok(WalletService.toMap(w), "Wallet updated");
    }

    private static String str(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString().trim();
    }
}
