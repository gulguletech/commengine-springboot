package com.commengine.service;

import com.commengine.config.AppProperties;
import com.commengine.util.agora.RtcTokenBuilder2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ported from config/agora.js. */
@Service
public class AgoraService {

    private static final Logger log = LoggerFactory.getLogger(AgoraService.class);

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
        if (appId.length() != 32 || appCertificate.length() != 32) {
            // Not fatal here (AccessToken2.build() will throw its own clear error), but log
            // it loudly: a wrong-length value almost always means a copy-paste mistake
            // (extra whitespace/quotes) in the Render env var, and every token issued with
            // a bad certificate will be silently rejected by Agora at publish/subscribe
            // time even though signaling/join can still appear to work.
            log.warn("AGORA_APP_ID or AGORA_APP_CERTIFICATE is not exactly 32 characters " +
                    "(appId.length={}, appCertificate.length={}) - double check these against " +
                    "the Agora console, this will produce invalid tokens.", appId.length(), appCertificate.length());
        }

        int expirySeconds = props.getAgora().getTokenExpirySeconds();
        RtcTokenBuilder2.Role rtcRole = "publisher".equals(role) ? RtcTokenBuilder2.Role.PUBLISHER : RtcTokenBuilder2.Role.SUBSCRIBER;

        // NOTE: Agora's builder expects durations expressed as "seconds from now", not
        // absolute unix timestamps, for both the token expiry and each privilege's expiry.
        String token = RtcTokenBuilder2.buildTokenWithUid(appId, appCertificate, channelName, uid, rtcRole, expirySeconds, expirySeconds);

        long expiresAt = Instant.now().getEpochSecond() + expirySeconds;

        log.info("Issued Agora RTC token: channel={} uid={} role={} appId={}... expiresInSec={}",
                channelName, uid, role, appId.substring(0, Math.min(6, appId.length())), expirySeconds);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("appId", appId);
        result.put("channelName", channelName);
        result.put("uid", uid);
        result.put("expiresAt", expiresAt);
        return result;
    }
}