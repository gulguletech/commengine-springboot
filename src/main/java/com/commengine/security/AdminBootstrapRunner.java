package com.commengine.security;

import com.commengine.entity.Admin;
import com.commengine.repository.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Creates the very first admin from env vars so there is a way in before any admin exists.
 * It only does anything while the `admins` table is EMPTY - once an admin exists it is a no-op
 * (further admins are created from inside the admin panel).
 *
 *   ADMIN_BOOTSTRAP_USERNAME=...   ADMIN_BOOTSTRAP_PASSWORD=...
 */
@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.bootstrap.username:}")
    private String bootstrapUsername;

    @Value("${admin.bootstrap.password:}")
    private String bootstrapPassword;

    public AdminBootstrapRunner(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (adminRepository.count() > 0) return;

        if (bootstrapUsername == null || bootstrapUsername.isBlank()
                || bootstrapPassword == null || bootstrapPassword.isBlank()) {
            log.warn("No admin accounts exist and ADMIN_BOOTSTRAP_USERNAME / ADMIN_BOOTSTRAP_PASSWORD are not set. "
                    + "Admin login is unusable until they are set (then restart).");
            return;
        }

        Admin admin = new Admin();
        admin.setUsername(bootstrapUsername.trim());
        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
        admin.setPasswordChangedAt(Instant.now());
        adminRepository.save(admin);
        log.info("Bootstrapped the first admin '{}'. You can now remove ADMIN_BOOTSTRAP_USERNAME / "
                + "ADMIN_BOOTSTRAP_PASSWORD from the environment and add more admins from the panel.", admin.getUsername());
    }
}
