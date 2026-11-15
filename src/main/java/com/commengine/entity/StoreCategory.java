package com.commengine.entity;

/** The four kinds of item the store sells. Exactly these four, for now. */
public enum StoreCategory {
    /** Ring drawn around a user's avatar everywhere the avatar appears. */
    FRAME,
    /** 9-slice image drawn behind a user's text messages (audio room, live room, 1:1 chat). */
    BUBBLE,
    /** Full-screen audio-room background. Applied per room by the host. */
    BACKGROUND,
    /** Short video every member of a room sees when the user enters it. */
    ENTRY_EFFECT
}
