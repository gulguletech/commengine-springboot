package com.commengine.socket;

import com.commengine.entity.User;
import com.commengine.repository.UserRepository;
import com.commengine.security.JwtService;
import com.corundumstudio.socketio.SocketIOClient;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

@Component
public class SocketUserResolver {

    private static final String STORE_KEY = "commengine_user";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public SocketUserResolver(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    /** Verifies the handshake JWT (already checked once by AuthorizationListener) and loads the user. */
    public User resolveFromHandshake(SocketIOClient client) {
        String token = client.getHandshakeData().getSingleUrlParam("token");
        if (token == null) return null;
        try {
            Claims claims = jwtService.verify(token);
            if (Boolean.TRUE.equals(claims.get("temp", Boolean.class))) return null;
            String userId = claims.get("userId", String.class);
            return userRepository.findById(userId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public void cacheOnClient(SocketIOClient client, User user) {
        client.set(STORE_KEY, user);
    }

    /** Returns the user cached at connect time, falling back to re-resolving from the handshake token. */
    public User currentUser(SocketIOClient client) {
        User cached = client.get(STORE_KEY);
        if (cached != null) return cached;
        User resolved = resolveFromHandshake(client);
        if (resolved != null) cacheOnClient(client, resolved);
        return resolved;
    }
}
