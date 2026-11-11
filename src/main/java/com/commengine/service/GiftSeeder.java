package com.commengine.service;

import com.commengine.entity.Gift;
import com.commengine.repository.GiftRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * First start after gifts became dynamic: the `gifts` table is empty, so we copy in the old hard-coded
 * shelf (emoji gifts) so the app isn't suddenly empty. Delete them from the admin panel once you've
 * added real ones. Set GIFTS_SEED_DEFAULTS=false to switch this off.
 */
@Component
public class GiftSeeder implements ApplicationRunner {

    private record Seed(String name, String emoji, long price) {}

    private static final List<Seed> DEFAULTS = List.of(
            new Seed("Rose", "🌹", 10),
            new Seed("Heart", "💖", 20),
            new Seed("Ice Cream", "🍦", 50),
            new Seed("Teddy", "🧸", 100),
            new Seed("Cake", "🎂", 200),
            new Seed("Rocket", "🚀", 500),
            new Seed("Crown", "👑", 1000),
            new Seed("Diamond", "💎", 2000)
    );

    private final GiftRepository repo;
    private final boolean enabled;

    public GiftSeeder(GiftRepository repo, @Value("${app.gifts.seed-defaults:true}") boolean enabled) {
        this.repo = repo;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || repo.count() > 0) return;
        int order = 0;
        for (Seed s : DEFAULTS) {
            Gift g = new Gift();
            g.setName(s.name());
            g.setEmoji(s.emoji());
            g.setPrice(s.price());
            g.setSortOrder(order++);
            repo.save(g);
        }
    }
}
