package com.commengine.socket;

import com.commengine.entity.User;
import com.commengine.repository.UserRepository;
import com.corundumstudio.socketio.SocketIOServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ported from sockets/index.js. */
@Component
public class SocketIOBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SocketIOBootstrap.class);

    private final SocketIOServer server;
    private final SocketUserResolver userResolver;
    private final UserRepository userRepository;
    private final ChatSocketHandler chatSocketHandler;
    private final AudioRoomSocketHandler audioRoomSocketHandler;
    private final VideoRoomSocketHandler videoRoomSocketHandler;

    public SocketIOBootstrap(SocketIOServer server, SocketUserResolver userResolver, UserRepository userRepository,
                              ChatSocketHandler chatSocketHandler, AudioRoomSocketHandler audioRoomSocketHandler,
                              VideoRoomSocketHandler videoRoomSocketHandler) {
        this.server = server;
        this.userResolver = userResolver;
        this.userRepository = userRepository;
        this.chatSocketHandler = chatSocketHandler;
        this.audioRoomSocketHandler = audioRoomSocketHandler;
        this.videoRoomSocketHandler = videoRoomSocketHandler;
    }

    @PostConstruct
    public void start() {
        server.addConnectListener(client -> {
            User user = userResolver.resolveFromHandshake(client);
            if (user == null) {
                client.disconnect();
                return;
            }
            userResolver.cacheOnClient(client, user);
            client.joinRoom("user:" + user.getId());
            log.info("Socket connected: {} (user {})", client.getSessionId(), user.getId());

            markOnline(user.getId(), true);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", user.getId());
            server.getBroadcastOperations().sendEvent("presence:online", client, data);
        });

        server.addDisconnectListener(client -> {
            User user = userResolver.currentUser(client);
            if (user == null) return;
            log.info("Socket disconnected: {} (user {})", client.getSessionId(), user.getId());
            markOnline(user.getId(), false);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", user.getId());
            server.getBroadcastOperations().sendEvent("presence:offline", client, data);
        });

        chatSocketHandler.register(server);
        audioRoomSocketHandler.register(server);
        videoRoomSocketHandler.register(server);

        server.start();
        log.info("Socket.IO server started");
    }

    @Transactional
    void markOnline(String userId, boolean online) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setOnline(online);
            u.setLastSeen(Instant.now());
            userRepository.save(u);
        });
    }

    @PreDestroy
    public void stop() {
        server.stop();
    }
}
