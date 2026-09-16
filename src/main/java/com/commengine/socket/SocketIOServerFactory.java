package com.commengine.socket;

import com.commengine.config.AppProperties;
import com.commengine.security.JwtService;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOServer;
import io.jsonwebtoken.Claims;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Builds the Socket.IO server, ported from server.js's `new Server(server, {...})` setup.
 *
 * ARCHITECTURE NOTE: the original Node app ran Socket.IO on the SAME HTTP port as the REST
 * API (one shared http.Server). A JVM servlet container (Tomcat, used by spring-boot-starter-web)
 * and a Netty-based Socket.IO server cannot share a single port, so this server listens on its
 * own port (app.socketio.port, default 5001) while the REST API stays on server.port (default
 * 5000). Point the mobile app's SOCKET_URL at that port when deploying.
 *
 * AUTH NOTE: socket.io-client sends its `auth: { token }` payload as part of the Socket.IO
 * protocol handshake, one layer above what netty-socketio's AuthorizationListener can inspect.
 * To keep this reliably compatible across socket.io-client versions/transports, the JWT is
 * additionally read from a URL query parameter — add `query: { token }` alongside the
 * existing `auth: { token }` option in the app's src/lib/socket.ts `io(...)` call.
 */
@Component
public class SocketIOServerFactory {

    private final AppProperties props;
    private final JwtService jwtService;

    public SocketIOServerFactory(AppProperties props, JwtService jwtService) {
        this.props = props;
        this.jwtService = jwtService;
    }

    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(props.getSocketio().getHost());
        config.setPort(props.getSocketio().getPort());
        config.setOrigin(props.getClientUrl());

        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        config.setSocketConfig(socketConfig);

        // Handshake-level gate: reject unauthenticated/expired/temp-only tokens outright.
        config.setAuthorizationListener(data -> {
            try {
                String token = data.getSingleUrlParam("token");
                if (token == null) return false;
                Claims claims = jwtService.verify(token);
                return !Boolean.TRUE.equals(claims.get("temp", Boolean.class));
            } catch (Exception e) {
                return false;
            }
        });

        return new SocketIOServer(config);
    }
}
