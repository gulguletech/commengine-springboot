package com.commengine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Pays Fruit Party winners (and the jackpot) every couple of seconds, even if nobody has the game open. */
@Component
public class FruitPartySettler {

    private static final Logger log = LoggerFactory.getLogger(FruitPartySettler.class);
    private final FruitPartyService service;

    public FruitPartySettler(FruitPartyService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 9000)
    public void run() {
        try {
            service.settleDue();
        } catch (Exception e) {
            log.warn("Fruit Party settle failed: {}", e.getMessage());
        }
    }
}
