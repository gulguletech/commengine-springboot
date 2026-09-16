package com.commengine.service;

import com.commengine.config.AppProperties;
import com.commengine.util.agora.RtcTokenBuilder2;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ported from config/agora.js. */
@Service
public class AgoraService {

    private final AppProperties props;

    public AgoraService(AppProperties props) {
        this.props = props;
    }

    /** role: "publisher" (host/broadcaster) or "subscriber" (audience). */
    public Map<String, Object> generateRtcToken(String channelName, long uid, String role) {
        String appId = props.getAgora().getAppId();
        String appCertificate = props.getAgora().getAppCertificate();
        if (appId == null || appId.isBlank() || appCertificate == null || appCertificate.isBlank()) {
            throw new IllegalStateException("Agora APP_ID / APP_CERTIFICATE not configured");
        }

        int expirySeconds = props.getAgora().getTokenExpirySeconds();
        RtcTokenBuilder2.Role rtcRole = "publisher".equals(role) ? RtcTokenBuilder2.Role.PUBLISHER : RtcTokenBuilder2.Role.SUBSCRIBER;

        // NOTE: Agora's builder expects durations expressed as "seconds from now", not
        // absolute unix timestamps, for both the token expiry and each privilege's expiry.
        String token = RtcTokenBuilder2.buildTokenWithUid(appId, appCertificate, channelName, uid, rtcRole, expirySeconds, expirySeconds);

        long expiresAt = Instant.now().getEpochSecond() + expirySeconds;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("appId", appId);
        result.put("channelName", channelName);
        result.put("uid", uid);
        result.put("expiresAt", expiresAt);
        return result;
    }
}
