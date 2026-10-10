package com.commengine.service;

import com.commengine.common.ApiException;
import com.commengine.entity.Wallet;
import com.commengine.entity.WalletTransaction;
import com.commengine.repository.WalletRepository;
import com.commengine.repository.WalletTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WalletService {

    public static final String COINS = "COINS";
    public static final String BEANS = "BEANS";

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository txRepository;

    // Optional env var: coins given to every new wallet (default 0). Handy for testing.
    @Value("${WALLET_WELCOME_COINS:0}")
    private long welcomeCoins;

    public WalletService(WalletRepository walletRepository, WalletTransactionRepository txRepository) {
        this.walletRepository = walletRepository;
        this.txRepository = txRepository;
    }

    @Transactional
    public Wallet ensure(String userId) {
        Wallet existing = walletRepository.findById(userId).orElse(null);
        if (existing != null) return existing;
        int created = walletRepository.createIfAbsent(userId, welcomeCoins);
        Wallet w = walletRepository.findById(userId).orElseThrow();
        if (created == 1 && welcomeCoins > 0) {
            ledger(userId, COINS, welcomeCoins, w.getCoins(), "WELCOME_BONUS", null, "Welcome bonus");
        }
        return w;
    }

    @Transactional
    public Map<String, Object> getBalance(String userId) {
        return toMap(ensure(userId));
    }

    /**
     * The ONLY way balances change. Atomic and never negative.
     * If ref is given and this (user, type, ref) was already applied, nothing happens (idempotent).
     */
    @Transactional
    public Wallet adjust(String userId, String currency, long amount, String type, String ref, String note) {
        if (!COINS.equals(currency) && !BEANS.equals(currency)) throw ApiException.badRequest("Invalid currency");
        if (amount == 0) throw ApiException.badRequest("Amount must not be zero");
        ensure(userId);

        if (ref != null && txRepository.existsByUserIdAndTypeAndRef(userId, type, ref)) {
            return walletRepository.findById(userId).orElseThrow();
        }

        int updated = COINS.equals(currency)
                ? walletRepository.adjustCoins(userId, amount)
                : walletRepository.adjustBeans(userId, amount);
        if (updated == 0) {
            throw ApiException.unprocessable("Insufficient " + currency.toLowerCase());
        }
        Wallet w = walletRepository.findById(userId).orElseThrow();
        long after = COINS.equals(currency) ? w.getCoins() : w.getBeans();
        ledger(userId, currency, amount, after, type, ref, note);
        return w;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> transactions(String userId, int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        return txRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, size)).stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("currency", t.getCurrency());
            m.put("amount", t.getAmount());
            m.put("balanceAfter", t.getBalanceAfter());
            m.put("type", t.getType());
            m.put("note", t.getNote());
            m.put("createdAt", t.getCreatedAt().toString());
            return m;
        }).toList();
    }

    public static Map<String, Object> toMap(Wallet w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("coins", w.getCoins());
        m.put("beans", w.getBeans());
        return m;
    }

    private void ledger(String userId, String currency, long amount, long after, String type, String ref, String note) {
        WalletTransaction t = new WalletTransaction();
        t.setUserId(userId);
        t.setCurrency(currency);
        t.setAmount(amount);
        t.setBalanceAfter(after);
        t.setType(type);
        t.setRef(ref);
        t.setNote(note);
        txRepository.save(t);
    }
}
