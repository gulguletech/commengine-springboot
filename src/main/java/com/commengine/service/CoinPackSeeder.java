package com.commengine.service;

import com.commengine.entity.CoinPack;
import com.commengine.repository.CoinPackRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * First start with an empty `coin_packs` table: copy in 10 starter packs so the wallet isn't empty.
 * Edit / delete / add them from the admin panel afterwards. Set COIN_PACKS_SEED_DEFAULTS=false to switch off.
 */
@Component
public class CoinPackSeeder implements ApplicationRunner {

    private static final long[][] DEFAULTS = {
            {100, 79}, {250, 189}, {500, 379}, {1_000, 749}, {2_000, 1_449},
            {3_000, 2_149}, {5_000, 3_499}, {7_500, 5_249}, {10_000, 6_999}, {20_000, 13_499}
    };

    private final CoinPackRepository repo;
    private final boolean enabled;

    public CoinPackSeeder(CoinPackRepository repo, @Value("${app.coin-packs.seed-defaults:true}") boolean enabled) {
        this.repo = repo;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || repo.count() > 0) return;
        int order = 0;
        for (long[] d : DEFAULTS) {
            CoinPack p = new CoinPack();
            p.setCoins(d[0]);
            p.setPriceInr(d[1]);
            p.setSortOrder(order++);
            repo.save(p);
        }
    }
}
