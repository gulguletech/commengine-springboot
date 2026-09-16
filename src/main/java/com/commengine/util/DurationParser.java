package com.commengine.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the same style of duration strings the Node "jsonwebtoken" library accepts
 * for expiresIn (e.g. "30d", "15m", "3600" as raw seconds, "1h").
 */
public final class DurationParser {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)\\s*(ms|s|m|h|d|w|y)?$", Pattern.CASE_INSENSITIVE);

    private DurationParser() {}

    /** Returns the duration in whole seconds. */
    public static long toSeconds(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Duration string is required");
        }
        Matcher m = PATTERN.matcher(value.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Unsupported duration format: " + value);
        }
        long amount = Long.parseLong(m.group(1));
        String unit = m.group(2) == null ? "s" : m.group(2).toLowerCase();
        return switch (unit) {
            case "ms" -> Math.max(1, amount / 1000);
            case "s" -> amount;
            case "m" -> amount * 60;
            case "h" -> amount * 3600;
            case "d" -> amount * 86400;
            case "w" -> amount * 604800;
            case "y" -> amount * 31536000;
            default -> throw new IllegalArgumentException("Unsupported duration unit: " + unit);
        };
    }
}
