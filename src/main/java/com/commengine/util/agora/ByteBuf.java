package com.commengine.util.agora;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minimal little-endian packing buffer, byte-for-byte compatible with the
 * "ByteBuf" helper inside Agora's official AccessToken2 reference implementation
 * (see the `agora-token` npm package, src/AccessToken2.js).
 */
public final class ByteBuf {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public ByteBuf putUint16(int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        return this;
    }

    public ByteBuf putInt16(int v) {
        return putUint16(v);
    }

    public ByteBuf putUint32(long v) {
        out.write((int) (v & 0xFF));
        out.write((int) ((v >>> 8) & 0xFF));
        out.write((int) ((v >>> 16) & 0xFF));
        out.write((int) ((v >>> 24) & 0xFF));
        return this;
    }

    public ByteBuf putBytes(byte[] bytes) {
        putUint16(bytes.length);
        out.writeBytes(bytes);
        return this;
    }

    public ByteBuf putString(String s) {
        return putBytes(s.getBytes(StandardCharsets.UTF_8));
    }

    /** Privileges map: uint16 count, then for each entry (ascending key order): uint16 key + uint32 value. */
    public ByteBuf putTreeMapUInt32(Map<Integer, Long> map) {
        TreeMap<Integer, Long> sorted = new TreeMap<>(map == null ? Map.of() : map);
        putUint16(sorted.size());
        for (Map.Entry<Integer, Long> e : sorted.entrySet()) {
            putUint16(e.getKey());
            putUint32(e.getValue());
        }
        return this;
    }

    public byte[] pack() {
        return out.toByteArray();
    }
}
