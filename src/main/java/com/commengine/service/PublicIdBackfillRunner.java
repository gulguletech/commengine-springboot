package com.commengine.service;

import com.commengine.entity.User;
import com.commengine.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** On startup, gives every existing user that has no short ID one. Does nothing once everyone has one. */
@Component
public class PublicIdBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PublicIdBackfillRunner.class);

    private final UserRepository users;
    private final PublicIdService ids;

    public PublicIdBackfillRunner(UserRepository users, PublicIdService ids) {
        this.users = users;
        this.ids = ids;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<User> missing = users.findByPublicIdIsNull();
        if (missing.isEmpty()) return;
        for (User u : missing) {
            u.setPublicId(ids.generate());
            users.saveAndFlush(u); // flush each so the next generate() sees it
        }
        log.info("Gave short IDs to {} existing users", missing.size());
    }
}
