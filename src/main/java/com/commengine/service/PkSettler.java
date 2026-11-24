package com.commengine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Ends PK battles when their time is up, even if nobody has the room open. */
@Component
public class PkSettler {

    private static final Logger log = LoggerFactory.getLogger(PkSettler.class);
    private final PkService pk;

    public PkSettler(PkService pk) {
        this.pk = pk;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    public void run() {
        try {
            for (String id : pk.dueIds()) {
                try {
                    pk.settle(id);
                } catch (Exception e) {
                    log.warn("PK settle failed for {}: {}", id, e.toString());
                }
            }
        } catch (Exception e) {
            log.warn("PK sweep failed: {}", e.toString());
        }
    }
}
