package com.commengine.util;

import com.commengine.common.ApiException;

import java.util.List;
import java.util.Locale;

/** Fixed lists the app and the backend agree on: room categories and user languages. */
public final class Catalog {

    private Catalog() {}

    public static final String DEFAULT_CATEGORY = "FUN";

    /** What a room is about. Also the chips shown when creating a room and in the Home filter bar. */
    public static final List<String> ROOM_CATEGORIES =
            List.of("FUN", "MUSIC", "GAME", "BIRTHDAY", "FRIENDS", "PARTY", "CHAT");

    /** Languages a user can pick in their profile. A room takes the language of its host. */
    public static final List<String> LANGUAGES = List.of(
            "Hindi", "English", "Telugu", "Punjabi", "Tamil", "Kannada", "Malayalam",
            "Bengali", "Marathi", "Gujarati", "Urdu", "Odia", "Other");

    /** Null/blank -> default (FUN). Unknown value -> 400. */
    public static String category(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_CATEGORY;
        String c = raw.trim().toUpperCase(Locale.ROOT);
        if (!ROOM_CATEGORIES.contains(c)) throw ApiException.badRequest("Unknown room type: " + raw);
        return c;
    }

    /** Null/blank -> null (not set). Unknown value -> 400. Matching is case-insensitive. */
    public static String language(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim();
        for (String l : LANGUAGES) if (l.equalsIgnoreCase(t)) return l;
        throw ApiException.badRequest("Unknown language: " + raw);
    }
}
