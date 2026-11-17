package com.commengine.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Strict rules for event banner pictures: one exact size (16:5), PNG or JPG, small file.
 * Plain JDK only. Throws IllegalArgumentException with a message the admin can act on.
 * The app shows banners at this same 16:5 ratio (BannerCarousel.tsx) - change both together.
 */
public final class BannerImageValidator {

    private BannerImageValidator() {}

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 400;
    public static final long MAX_BYTES = 1_500_000L; // 1.5 MB

    public static void validate(byte[] d) {
        if (d == null || d.length == 0) throw new IllegalArgumentException("Banner image is empty");
        if (d.length > MAX_BYTES) {
            throw new IllegalArgumentException("Banner image must be under " + (MAX_BYTES / 1_000_000.0) + " MB (yours is "
                    + String.format("%.2f", d.length / 1_000_000.0) + " MB)");
        }
        boolean png = d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
        boolean jpg = d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
        if (!png && !jpg) throw new IllegalArgumentException("Banner must be a real PNG or JPG file");

        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(d));
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Banner could not be read - re-export it as a standard sRGB image (CMYK is not supported)");
        }
        if (img == null) throw new IllegalArgumentException("Banner could not be read - re-export it as a standard sRGB image");
        if (img.getWidth() != WIDTH || img.getHeight() != HEIGHT) {
            throw new IllegalArgumentException("Banner must be exactly " + WIDTH + " x " + HEIGHT + " px (yours is "
                    + img.getWidth() + " x " + img.getHeight() + ")");
        }
    }
}
