package com.commengine.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The strict upload rules for every store item. Plain JDK only (no Spring), so it can be unit-tested on its own.
 * Every method throws IllegalArgumentException with a message an admin can act on.
 *
 * The numbers here are mirrored in the app renderers (Avatar frame, ChatBubble 9-slice, entry-effect player):
 * change a number here and the matching constant in the app must change with it.
 */
public final class StoreMediaValidator {

    private StoreMediaValidator() {}

    // ------------------------------------------------------------------ the rules (single source of truth)

    /** FRAME: square PNG, transparent circular hole in the middle where the avatar sits. */
    public static final int FRAME_SIZE = 512;
    /** The avatar fills this share of the frame's width. Must stay equal to FRAME_HOLE_RATIO in the app's Avatar.tsx. */
    public static final double FRAME_HOLE_RATIO = 0.70;
    public static final long FRAME_MAX_BYTES = 400L * 1024;

    /** BUBBLE: 9-slice PNG. Corners (CAP_X x CAP_Y) keep their size, the edges and centre stretch. Same constants in ChatBubble.tsx. */
    public static final int BUBBLE_W = 160;
    public static final int BUBBLE_H = 96;
    public static final int BUBBLE_CAP_X = 40;
    public static final int BUBBLE_CAP_Y = 30;
    public static final long BUBBLE_MAX_BYTES = 150L * 1024;

    /** BACKGROUND: 9:16 portrait picture, fully opaque. */
    public static final int BG_W = 1080;
    public static final int BG_H = 1920;
    public static final long BG_MAX_BYTES = 2L * 1024 * 1024;

    /** ENTRY_EFFECT: small preview picture for the shelf + the video played in the room. */
    public static final int PREVIEW_SIZE = 256;
    public static final long PREVIEW_MAX_BYTES = 200L * 1024;
    public static final int VIDEO_W = 720;
    public static final int VIDEO_H = 720;
    public static final double VIDEO_MIN_SECONDS = 1.0;
    public static final double VIDEO_MAX_SECONDS = 5.0;
    /** A little slack for container rounding, same idea as gift videos. */
    private static final double VIDEO_SECONDS_SLACK = 0.3;
    public static final long VIDEO_MAX_BYTES = 6L * 1024 * 1024;

    private static final int ALPHA_CLEAR = 8;       // <= this counts as "transparent"
    private static final int STRETCH_TOLERANCE = 16; // per-channel difference allowed inside a stretch zone

    // ------------------------------------------------------------------ rules as data (for the admin page)

    /** Machine-readable copy of the rules, served to the admin page so it never drifts from what the server enforces. */
    public static List<Map<String, Object>> specs() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(spec("FRAME", "Avatar frame",
                file("image", "Frame image", "PNG (with transparency)", FRAME_SIZE, FRAME_SIZE, FRAME_MAX_BYTES, null, List.of(
                        "Exactly " + FRAME_SIZE + " x " + FRAME_SIZE + " px, PNG with a transparent background",
                        "Keep the middle circle (diameter " + Math.round(FRAME_SIZE * FRAME_HOLE_RATIO) + " px, centred) 100% transparent - the user's avatar is shown there",
                        "All artwork (ring, wings, crown...) goes OUTSIDE that circle, inside the " + FRAME_SIZE + " px square",
                        "Max " + (FRAME_MAX_BYTES / 1024) + " KB"))));
        out.add(spec("BUBBLE", "Chat bubble",
                file("image", "Bubble image", "PNG (with transparency)", BUBBLE_W, BUBBLE_H, BUBBLE_MAX_BYTES, null, List.of(
                        "Exactly " + BUBBLE_W + " x " + BUBBLE_H + " px, PNG",
                        "It is stretched like a 9-slice: the outer " + BUBBLE_CAP_X + " px left/right and " + BUBBLE_CAP_Y + " px top/bottom are the corners and keep their size",
                        "The edges and the middle are stretched, so they must be plain: no pattern or ornament, only a flat colour or a vertical gradient",
                        "The middle (" + (BUBBLE_W - 2 * BUBBLE_CAP_X) + " x " + (BUBBLE_H - 2 * BUBBLE_CAP_Y) + " px) must be at least 60% opaque - the message text is written there",
                        "Set the text colour so the text is readable on your bubble",
                        "Max " + (BUBBLE_MAX_BYTES / 1024) + " KB"))));
        out.add(spec("BACKGROUND", "Room background",
                file("image", "Background image", "JPG or PNG", BG_W, BG_H, BG_MAX_BYTES, null, List.of(
                        "Exactly " + BG_W + " x " + BG_H + " px (9:16 portrait), JPG or PNG",
                        "Fully opaque (no transparency)",
                        "Keep the top ~15% and bottom ~25% calm: the room title, seats and chat sit on top",
                        "Max " + (BG_MAX_BYTES / (1024 * 1024)) + " MB"))));
        out.add(spec("ENTRY_EFFECT", "Entry effect",
                file("preview", "Shelf preview image", "PNG or JPG", PREVIEW_SIZE, PREVIEW_SIZE, PREVIEW_MAX_BYTES, null, List.of(
                        "Exactly " + PREVIEW_SIZE + " x " + PREVIEW_SIZE + " px, PNG or JPG - shown on the store shelf",
                        "Max " + (PREVIEW_MAX_BYTES / 1024) + " KB")),
                file("video", "Effect video", "MP4 (H.264)", VIDEO_W, VIDEO_H, VIDEO_MAX_BYTES, VIDEO_MAX_SECONDS, List.of(
                        "MP4 with H.264 video, exactly " + VIDEO_W + " x " + VIDEO_H + " px (square)",
                        "Between " + (int) VIDEO_MIN_SECONDS + " and " + (int) VIDEO_MAX_SECONDS + " seconds long",
                        "Exported 'web optimised' / fast-start, so it starts playing before it has fully downloaded",
                        "Edges fade out when played over the room, so keep the action in the middle",
                        "Max " + (VIDEO_MAX_BYTES / (1024 * 1024)) + " MB"))));
        return out;
    }

    @SafeVarargs
    private static Map<String, Object> spec(String category, String label, Map<String, Object>... files) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", category);
        m.put("label", label);
        m.put("files", List.of(files));
        return m;
    }

    private static Map<String, Object> file(String field, String label, String format, int w, int h, long maxBytes, Double maxSeconds, List<String> rules) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("field", field);
        m.put("label", label);
        m.put("format", format);
        m.put("width", w);
        m.put("height", h);
        m.put("maxBytes", maxBytes);
        if (maxSeconds != null) m.put("maxSeconds", maxSeconds);
        m.put("rules", rules);
        return m;
    }

    // ------------------------------------------------------------------ images

    private enum Kind { PNG, JPEG }

    private static Kind sniff(byte[] d, String what, boolean allowPng, boolean allowJpeg) {
        boolean png = d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
        boolean jpg = d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
        if (png && allowPng) return Kind.PNG;
        if (jpg && allowJpeg) return Kind.JPEG;
        String allowed = allowPng && allowJpeg ? "PNG or JPG" : allowPng ? "PNG" : "JPG";
        throw new IllegalArgumentException(what + " must be a real " + allowed + " file (the file contents are not " + allowed + ")");
    }

    private static BufferedImage decode(byte[] d, String what) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(d));
            if (img == null) throw new IllegalArgumentException(what + " could not be read - re-export it as a standard sRGB image");
            return img;
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException iae) throw iae;
            throw new IllegalArgumentException(what + " could not be read - re-export it as a standard sRGB image (CMYK is not supported)");
        }
    }

    private static void size(byte[] d, long max, String what) {
        if (d.length > max) {
            throw new IllegalArgumentException(what + " is too big (" + kb(d.length) + "). Maximum is " + kb(max));
        }
    }

    private static void dims(BufferedImage img, int w, int h, String what) {
        if (img.getWidth() != w || img.getHeight() != h) {
            throw new IllegalArgumentException(what + " must be exactly " + w + " x " + h + " px (yours is " + img.getWidth() + " x " + img.getHeight() + ")");
        }
    }

    private static String kb(long bytes) {
        return bytes >= 1024 * 1024 ? String.format("%.1f MB", bytes / 1048576.0) : (bytes / 1024) + " KB";
    }

    private static int alpha(int argb) { return (argb >>> 24) & 0xFF; }

    public static void validateFrame(byte[] data) {
        String what = "Frame image";
        size(data, FRAME_MAX_BYTES, what);
        sniff(data, what, true, false);
        BufferedImage img = decode(data, what);
        dims(img, FRAME_SIZE, FRAME_SIZE, what);
        if (!img.getColorModel().hasAlpha()) throw new IllegalArgumentException(what + " must be a PNG with a transparent background (it has no transparency)");

        double c = (FRAME_SIZE - 1) / 2.0;
        double r = FRAME_SIZE * FRAME_HOLE_RATIO / 2.0;
        long inHole = 0, blocked = 0, art = 0;
        for (int y = 0; y < FRAME_SIZE; y++) {
            for (int x = 0; x < FRAME_SIZE; x++) {
                int a = alpha(img.getRGB(x, y));
                double dx = x - c, dy = y - c;
                if (dx * dx + dy * dy <= r * r) {
                    inHole++;
                    if (a > ALPHA_CLEAR) blocked++;
                } else if (a >= 128) {
                    art++;
                }
            }
        }
        if (blocked > 0) {
            double pct = 100.0 * blocked / inHole;
            throw new IllegalArgumentException(String.format(
                    "Frame artwork covers the avatar area (%.1f%% of the middle circle is not transparent). "
                            + "Keep the middle circle of %d px diameter completely transparent.", pct, Math.round(FRAME_SIZE * FRAME_HOLE_RATIO)));
        }
        if (art < FRAME_SIZE * FRAME_SIZE * 0.02) {
            throw new IllegalArgumentException(what + " looks empty: draw the frame artwork around the middle circle");
        }
    }

    public static void validateBubble(byte[] data) {
        String what = "Bubble image";
        size(data, BUBBLE_MAX_BYTES, what);
        sniff(data, what, true, false);
        BufferedImage img = decode(data, what);
        dims(img, BUBBLE_W, BUBBLE_H, what);
        if (!img.getColorModel().hasAlpha()) throw new IllegalArgumentException(what + " must be a PNG with transparency (rounded corners need it)");

        int x0 = BUBBLE_CAP_X, x1 = BUBBLE_W - BUBBLE_CAP_X; // middle columns [x0, x1)
        int y0 = BUBBLE_CAP_Y, y1 = BUBBLE_H - BUBBLE_CAP_Y; // middle rows    [y0, y1)

        // centre: mostly opaque (text lives here)
        long sumA = 0, cnt = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) { sumA += alpha(img.getRGB(x, y)); cnt++; }
        if (sumA / (double) cnt < 255 * 0.6) {
            throw new IllegalArgumentException(what + ": the middle of the bubble is too transparent, the message text would be hard to read");
        }

        // stretch zones must not contain detail, or the stretch would smear it
        // top & bottom edges and the centre are stretched horizontally -> every row must be flat across x
        checkRowsFlat(img, x0, x1, 0, y0, "top edge");
        checkRowsFlat(img, x0, x1, y1, BUBBLE_H, "bottom edge");
        checkRowsFlat(img, x0, x1, y0, y1, "middle");
        // left & right edges and the centre are stretched vertically -> every column must be flat across y
        checkColsFlat(img, 0, x0, y0, y1, "left edge");
        checkColsFlat(img, x1, BUBBLE_W, y0, y1, "right edge");
        checkColsFlat(img, x0, x1, y0, y1, "middle");
    }

    private static void checkRowsFlat(BufferedImage img, int x0, int x1, int y0, int y1, String zone) {
        for (int y = y0; y < y1; y++) {
            int ref = img.getRGB(x0, y);
            for (int x = x0 + 1; x < x1; x++) {
                if (differs(ref, img.getRGB(x, y))) throw stretchError(zone, "left-to-right");
            }
        }
    }

    private static void checkColsFlat(BufferedImage img, int x0, int x1, int y0, int y1, String zone) {
        for (int x = x0; x < x1; x++) {
            int ref = img.getRGB(x, y0);
            for (int y = y0 + 1; y < y1; y++) {
                if (differs(ref, img.getRGB(x, y))) throw stretchError(zone, "top-to-bottom");
            }
        }
    }

    private static boolean differs(int a, int b) {
        return Math.abs(alpha(a) - alpha(b)) > STRETCH_TOLERANCE
                || Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) > STRETCH_TOLERANCE
                || Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) > STRETCH_TOLERANCE
                || Math.abs((a & 0xFF) - (b & 0xFF)) > STRETCH_TOLERANCE;
    }

    private static IllegalArgumentException stretchError(String zone, String direction) {
        return new IllegalArgumentException("Bubble image: the " + zone + " has detail that changes " + direction
                + ". That part gets stretched in the app, so it must be a flat colour (or a gradient in the other direction). "
                + "Only the " + BUBBLE_CAP_X + " px / " + BUBBLE_CAP_Y + " px corners may hold ornaments.");
    }

    public static void validateBackground(byte[] data) {
        String what = "Background image";
        size(data, BG_MAX_BYTES, what);
        Kind kind = sniff(data, what, true, true);
        BufferedImage img = decode(data, what);
        dims(img, BG_W, BG_H, what);
        if (kind == Kind.PNG && img.getColorModel().hasAlpha()) {
            for (int y = 0; y < BG_H; y += 4) {
                for (int x = 0; x < BG_W; x += 4) {
                    if (alpha(img.getRGB(x, y)) < 255) throw new IllegalArgumentException(what + " must be fully opaque (it has transparent areas)");
                }
            }
        }
    }

    public static void validatePreview(byte[] data) {
        String what = "Preview image";
        size(data, PREVIEW_MAX_BYTES, what);
        sniff(data, what, true, true);
        dims(decode(data, what), PREVIEW_SIZE, PREVIEW_SIZE, what);
    }

    // ------------------------------------------------------------------ MP4

    public record VideoInfo(int width, int height, double seconds, String codec) {}

    public static VideoInfo validateEntryVideo(byte[] data) {
        String what = "Effect video";
        size(data, VIDEO_MAX_BYTES, what);
        if (data.length < 12 || data[4] != 'f' || data[5] != 't' || data[6] != 'y' || data[7] != 'p') {
            throw new IllegalArgumentException(what + " must be an MP4 file");
        }
        VideoInfo info = readMp4(data);
        if (!"avc1".equals(info.codec()) && !"avc3".equals(info.codec())) {
            throw new IllegalArgumentException(what + " must use H.264 video (yours is '" + info.codec() + "'). Export as MP4 / H.264.");
        }
        if (info.width() != VIDEO_W || info.height() != VIDEO_H) {
            throw new IllegalArgumentException(what + " must be exactly " + VIDEO_W + " x " + VIDEO_H + " px (yours is " + info.width() + " x " + info.height() + ")");
        }
        if (info.seconds() < VIDEO_MIN_SECONDS) {
            throw new IllegalArgumentException(what + " is too short (" + String.format("%.1f", info.seconds()) + " s). Minimum is " + (int) VIDEO_MIN_SECONDS + " second");
        }
        if (info.seconds() > VIDEO_MAX_SECONDS + VIDEO_SECONDS_SLACK) {
            throw new IllegalArgumentException(what + " is too long (" + String.format("%.1f", info.seconds()) + " s). Maximum is " + (int) VIDEO_MAX_SECONDS + " seconds");
        }
        return info;
    }

    private static final class Mp4State {
        double seconds = -1;
        long moovAt = -1, mdatAt = -1;
        int width, height;
        String codec;
    }

    private static VideoInfo readMp4(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data); // big-endian by default, as MP4 requires
        Mp4State st = new Mp4State();
        try {
            walk(b, 0, data.length, 0, st, false);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Effect video is damaged or not a standard MP4 - re-export it");
        }
        if (st.seconds < 0 || st.codec == null || st.width <= 0) {
            throw new IllegalArgumentException("Effect video has no readable video track - re-export it as MP4 / H.264");
        }
        if (st.moovAt < 0 || st.mdatAt < 0 || st.moovAt > st.mdatAt) {
            throw new IllegalArgumentException("Effect video must be exported 'web optimised' (fast start) so it can start playing while downloading. "
                    + "In HandBrake tick 'Web optimized'; with ffmpeg add '-movflags +faststart'.");
        }
        return new VideoInfo(st.width, st.height, st.seconds, st.codec);
    }

    /** Walks MP4 boxes. Only descends into the container boxes we care about. */
    private static void walk(ByteBuffer b, int start, int end, int depth, Mp4State st, boolean inVideoTrak) {
        int pos = start;
        while (pos + 8 <= end) {
            long size = b.getInt(pos) & 0xFFFFFFFFL;
            String type = fourcc(b, pos + 4);
            int header = 8;
            if (size == 1) { size = b.getLong(pos + 8); header = 16; }
            else if (size == 0) { size = end - pos; }
            if (size < header || pos + size > end) throw new IllegalArgumentException("bad box");
            int payload = pos + header;
            int boxEnd = (int) (pos + size);

            if (depth == 0 && type.equals("moov")) st.moovAt = pos;
            if (depth == 0 && type.equals("mdat") && st.mdatAt < 0) st.mdatAt = pos;

            switch (type) {
                case "moov", "mdia", "minf", "stbl" -> walk(b, payload, boxEnd, depth + 1, st, inVideoTrak);
                case "trak" -> {
                    int beforeW = st.width;
                    walkTrak(b, payload, boxEnd, st);
                    if (st.width == 0) st.width = beforeW;
                }
                case "mvhd" -> {
                    int version = b.get(payload) & 0xFF;
                    long timescale, duration;
                    if (version == 1) { timescale = b.getInt(payload + 20) & 0xFFFFFFFFL; duration = b.getLong(payload + 24); }
                    else { timescale = b.getInt(payload + 12) & 0xFFFFFFFFL; duration = b.getInt(payload + 16) & 0xFFFFFFFFL; }
                    if (timescale > 0) st.seconds = duration / (double) timescale;
                }
                default -> { }
            }
            pos = boxEnd;
        }
    }

    /** One track: remember it only if it is a video track (non-zero width in tkhd). */
    private static void walkTrak(ByteBuffer b, int start, int end, Mp4State st) {
        Mp4State local = new Mp4State();
        scanTrak(b, start, end, local);
        if (local.width > 0 && local.codec != null && st.codec == null) {
            st.width = local.width;
            st.height = local.height;
            st.codec = local.codec;
        }
    }

    private static void scanTrak(ByteBuffer b, int start, int end, Mp4State st) {
        int pos = start;
        while (pos + 8 <= end) {
            long size = b.getInt(pos) & 0xFFFFFFFFL;
            String type = fourcc(b, pos + 4);
            int header = 8;
            if (size == 1) { size = b.getLong(pos + 8); header = 16; }
            else if (size == 0) { size = end - pos; }
            if (size < header || pos + size > end) throw new IllegalArgumentException("bad box");
            int payload = pos + header;
            int boxEnd = (int) (pos + size);
            switch (type) {
                case "mdia", "minf", "stbl" -> scanTrak(b, payload, boxEnd, st);
                case "tkhd" -> {
                    int version = b.get(payload) & 0xFF;
                    int w = b.getInt(payload + (version == 1 ? 88 : 76));
                    int h = b.getInt(payload + (version == 1 ? 92 : 80));
                    st.width = w >>> 16;   // 16.16 fixed point
                    st.height = h >>> 16;
                }
                case "stsd" -> {
                    // version/flags(4) entry_count(4) then the first sample entry: size(4) type(4)
                    if (boxEnd - payload >= 16) st.codec = fourcc(b, payload + 12);
                }
                default -> { }
            }
            pos = boxEnd;
        }
    }

    private static String fourcc(ByteBuffer b, int at) {
        return new String(new char[]{(char) (b.get(at) & 0xFF), (char) (b.get(at + 1) & 0xFF), (char) (b.get(at + 2) & 0xFF), (char) (b.get(at + 3) & 0xFF)});
    }
}
