package com.commengine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Pays Galaxy Feast winners every couple of seconds, even if nobody has the game open. */
@Component
public class GalaxyFeastSettler {

    private static final Logger log = LoggerFactory.getLogger(GalaxyFeastSettler.class);
    private final GalaxyFeastService service;

    public GalaxyFeastSettler(GalaxyFeastService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 6000)
    public void run() {
        try {
            service.settleDue();
        } catch (Exception e) {
            log.warn("Galaxy Feast settle failed: {}", e.getMessage());
        }
    }
}
