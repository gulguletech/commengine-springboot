package com.commengine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Pays winners every couple of seconds, even if nobody has the game open. */
@Component
public class LuckyBoxesSettler {

    private static final Logger log = LoggerFactory.getLogger(LuckyBoxesSettler.class);
    private final LuckyBoxesService service;

    public LuckyBoxesSettler(LuckyBoxesService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void run() {
        try {
            service.settleDue();
        } catch (Exception e) {
            log.warn("Lucky Star settle failed: {}", e.getMessage());
        }
    }
}
