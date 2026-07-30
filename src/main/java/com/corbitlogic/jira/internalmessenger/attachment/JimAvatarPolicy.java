package com.corbitlogic.jira.internalmessenger.attachment;

import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import java.util.Locale;

/**
 * Validation policy for group/project custom chat avatars (Phase 1). Deliberately
 * stricter than the general attachment policy: a small allow-list of raster image
 * types only (PNG / JPEG / WebP), a 2&nbsp;MB ceiling, sane dimension bounds and a
 * magic-byte sniff so a spoofed content-type (e.g. an SVG or script mislabelled as
 * {@code image/png}) is rejected regardless of what the client claims. SVG is never
 * accepted because it can carry active content.
 */
public final class JimAvatarPolicy {

    /** Hard ceiling: 2 MB. */
    public static final long MAX_AVATAR_SIZE_BYTES = 2L * 1024L * 1024L;
    /** Reject absurdly small or huge source images. */
    public static final int MIN_DIMENSION = 8;
    public static final int MAX_DIMENSION = 6000;
    /** Square thumbnail edge produced by re-encoding when practical. */
    public static final int THUMBNAIL_EDGE = 512;

    public static final String TYPE_PNG = "image/png";
    public static final String TYPE_JPEG = "image/jpeg";
    public static final String TYPE_WEBP = "image/webp";

    private JimAvatarPolicy() {
    }

    /** True for the three raster types we allow. SVG and everything else is false. */
    public static boolean isAllowedContentType(String contentType) {
        String t = normalize(contentType);
        return TYPE_PNG.equals(t) || TYPE_JPEG.equals(t) || TYPE_WEBP.equals(t);
    }

    public static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        String trimmed = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = trimmed.indexOf(59);
        if (semicolon >= 0) {
            trimmed = trimmed.substring(0, semicolon).trim();
        }
        return trimmed;
    }

    /**
     * Detect the real image type from magic bytes, independent of the claimed
     * content-type. Returns one of the TYPE_* constants or null when the bytes
     * are not a supported raster image (this is how SVG / scripts are rejected).
     */
    public static String sniff(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return TYPE_PNG;
        }
        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return TYPE_JPEG;
        }
        // WebP: "RIFF" .... "WEBP"
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return TYPE_WEBP;
        }
        return null;
    }

    /** Size + declared-type gate. Byte-content is validated separately via {@link #sniff}. */
    public static void validateSizeAndType(long size, String contentType) {
        if (size <= 0L) {
            throw JimMessengerException.badRequest("Avatar image is empty");
        }
        if (size > MAX_AVATAR_SIZE_BYTES) {
            throw JimMessengerException.badRequest("Avatar image exceeds the maximum allowed size of 2 MB");
        }
        if (!isAllowedContentType(contentType)) {
            throw JimMessengerException.badRequest("Avatar must be a PNG, JPEG or WebP image");
        }
    }

    public static void validateDimensions(int width, int height) {
        if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
            throw JimMessengerException.badRequest("Avatar image is too small");
        }
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw JimMessengerException.badRequest("Avatar image dimensions are too large");
        }
    }
}
