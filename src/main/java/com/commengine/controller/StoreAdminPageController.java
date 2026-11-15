package com.commengine.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Serves the self-contained store upload page at GET /store-admin.
 * (Static resources are switched off in application.yml, so it is served from the classpath explicitly.)
 * The page itself holds no secrets: every action goes through /api/admin/** and needs the admin login.
 */
@RestController
public class StoreAdminPageController {

    @GetMapping(value = "/store-admin", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page() throws IOException {
        byte[] html = new ClassPathResource("admin/store-admin.html").getInputStream().readAllBytes();
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new String(html, StandardCharsets.UTF_8));
    }
}
