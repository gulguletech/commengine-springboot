package com.commengine.util.agora;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;

/**
 * Java port of Agora's official AccessToken2 ("007") reference implementation
 * (the same algorithm shipped in the `agora-token` npm package used by the
 * original Node.js backend). Only the RTC service is implemented here since
 * that's all this app needs, but the packing/signing algorithm is preserved
 * exactly so tokens are byte-for-byte compatible with what Agora's SDKs expect.
 *
 * Reference: https://github.com/AgoraIO/Tools (DynamicKey/AgoraDynamicKey), nodejs/src/AccessToken2.js
 */
public final class AccessToken2 {

    private static final String VERSION = "007";
    public static final int SERVICE_TYPE_RTC = 1;

    public static final int PRIVILEGE_JOIN_CHANNEL = 1;
    public static final int PRIVILEGE_PUBLISH_AUDIO_STREAM = 2;
    public static final int PRIVILEGE_PUBLISH_VIDEO_STREAM = 3;
    public static final int PRIVILEGE_PUBLISH_DATA_STREAM = 4;

    private final String appId;
    private final String appCertificate;
    private final long issueTs;
    private final long expire;
    private final long salt;
    private final String channelName;
    private final String uid;
    private final Map<Integer, Long> rtcPrivileges = new LinkedHashMap<>();

    /**
     * @param expire token/privilege lifetime as a number of seconds from now
     *               (per Agora's docs — NOT an absolute unix timestamp).
     */
    public AccessToken2(String appId, String appCertificate, String channelName, String uid, long expire) {
        this.appId = appId;
        this.appCertificate = appCertificate;
        this.channelName = channelName;
        this.uid = uid;
        this.issueTs = System.currentTimeMillis() / 1000L;
        this.expire = expire;
        this.salt = new SecureRandom().nextInt(99_999_999) + 1L;
    }

    public void addPrivilege(int privilege, long expireSeconds) {
        rtcPrivileges.put(privilege, expireSeconds);
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }

    private byte[] signingKey() {
        byte[] step1 = hmacSha256(appCertificate.getBytes(StandardCharsets.UTF_8),
                new ByteBuf().putUint32(issueTs).pack());
        return hmacSha256(step1, new ByteBuf().putUint32(salt).pack());
    }

    private byte[] packRtcService() {
        // Service.pack() = type (uint16) + privileges map, then subclass-specific fields appended
        byte[] typeBytes = new ByteBuf().putUint16(SERVICE_TYPE_RTC).pack();
        byte[] privilegeBytes = new ByteBuf().putTreeMapUInt32(rtcPrivileges).pack();
        byte[] rtcFields = new ByteBuf().putString(channelName).putString(uid).pack();

        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        combined.writeBytes(typeBytes);
        combined.writeBytes(privilegeBytes);
        combined.writeBytes(rtcFields);
        return combined.toByteArray();
    }

    public String build() {
        if (appId == null || appId.length() != 32 || appCertificate == null || appCertificate.length() != 32) {
            throw new IllegalStateException("Agora appId/appCertificate must each be 32 characters (check AGORA_APP_ID / AGORA_APP_CERTIFICATE)");
        }

        byte[] signingKey = signingKey();

        ByteBuf signingInfo = new ByteBuf()
                .putString(appId)
                .putUint32(issueTs)
                .putUint32(expire)
                .putUint32(salt)
                .putUint16(1); // exactly one service: RTC

        ByteArrayOutputStream signingInfoBytes = new ByteArrayOutputStream();
        signingInfoBytes.writeBytes(signingInfo.pack());
        signingInfoBytes.writeBytes(packRtcService());

        byte[] signature = hmacSha256(signingKey, signingInfoBytes.toByteArray());

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        content.writeBytes(new ByteBuf().putBytes(signature).pack()); // length-prefixed raw signature bytes (must not go through UTF-8 string re-encoding)
        content.writeBytes(signingInfoBytes.toByteArray());

        byte[] compressed = deflate(content.toByteArray());
        return VERSION + Base64.getEncoder().encodeToString(compressed);
    }

    private static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, false); // zlib format (with header+adler32), matches Node zlib.deflateSync
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
        byte[] buf = new byte[4096];
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            out.write(buf, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }
}
