package com.commengine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Pays Fruit Wheel winners every couple of seconds, even if nobody has the game open. */
@Component
public class FruitWheelSettler {

    private static final Logger log = LoggerFactory.getLogger(FruitWheelSettler.class);
    private final FruitWheelService service;

    public FruitWheelSettler(FruitWheelService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 7000)
    public void run() {
        try {
            service.settleDue();
        } catch (Exception e) {
            log.warn("Fruit Wheel settle failed: {}", e.getMessage());
        }
    }
}
