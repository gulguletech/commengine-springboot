package com.commengine.service;

import com.commengine.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/** Short, friendly, unique user IDs (7 digits, e.g. 4829153) that people can search for and share. */
@Service
public class PublicIdService {

    private static final SecureRandom RNG = new SecureRandom();
    private final UserRepository users;

    public PublicIdService(UserRepository users) {
        this.users = users;
    }

    public String generate() {
        for (int i = 0; i < 40; i++) {
            String id = String.valueOf(1_000_000 + RNG.nextInt(9_000_000)); // 1000000..9999999
            if (!users.existsByPublicId(id)) return id;
        }
        // 7-digit space is crowded: fall back to 8 digits so signup never fails
        for (int i = 0; i < 40; i++) {
            String id = String.valueOf(10_000_000 + RNG.nextInt(90_000_000));
            if (!users.existsByPublicId(id)) return id;
        }
        throw new IllegalStateException("Could not generate a unique user ID");
    }
}
