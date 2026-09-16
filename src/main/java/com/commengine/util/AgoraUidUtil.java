package com.commengine.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Direct port of src/utils/agoraUid.js: derive a stable 32-bit unsigned Agora uid
 * from a user's UUID by taking the first 4 bytes of its MD5 hash (big-endian).
 */
public final class AgoraUidUtil {

    private AgoraUidUtil() {}

    public static long deriveAgoraUid(String userId) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] hash = md5.digest(userId.getBytes(StandardCharsets.UTF_8));
            long num = ByteBuffer.wrap(hash, 0, 4).getInt() & 0xFFFFFFFFL; // readUInt32BE(0)
            return (num % 4294967294L) + 1L; // 1 .. 2^32-1
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive Agora uid", e);
        }
    }
}
