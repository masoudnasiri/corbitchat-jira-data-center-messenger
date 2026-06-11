/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.attachment;

import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class JimAttachmentPolicy {
    public static final long MAX_FILE_SIZE_BYTES = 0xA00000L;
    public static final String STORAGE_SUBDIR = "data/corbitchat/attachments";
    private static final Set<String> ALLOWED_IMAGE_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("image/png", "image/jpeg", "image/gif", "image/webp")));
    private static final Set<String> ALLOWED_FILE_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("application/pdf", "text/plain", "application/zip", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint")));
    private static final Set<String> ALLOWED_AUDIO_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav", "audio/x-wav", "audio/aac")));
    private static final Set<String> BLOCKED_EXTENSIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("exe", "bat", "cmd", "com", "msi", "sh", "bash", "js", "jar", "jsp", "php", "html", "htm", "svg")));

    private JimAttachmentPolicy() {
    }

    public static Set<String> allowedMimeTypes() {
        HashSet<String> allowed = new HashSet<String>();
        allowed.addAll(ALLOWED_IMAGE_TYPES);
        allowed.addAll(ALLOWED_FILE_TYPES);
        allowed.addAll(ALLOWED_AUDIO_TYPES);
        return Collections.unmodifiableSet(allowed);
    }

    public static int allowedMimeTypeCount() {
        return JimAttachmentPolicy.allowedMimeTypes().size();
    }

    public static void validateUpload(long fileSize, String contentType, String originalFilename) {
        if (fileSize <= 0L) {
            throw JimMessengerException.badRequest("Uploaded file is empty");
        }
        if (fileSize > 0xA00000L) {
            throw JimMessengerException.badRequest("File exceeds the maximum allowed size of 10 MB");
        }
        String normalizedType = JimAttachmentPolicy.normalizeContentType(contentType);
        if (!JimAttachmentPolicy.allowedMimeTypes().contains(normalizedType)) {
            throw JimMessengerException.badRequest("File type is not allowed");
        }
        String sanitizedName = JimAttachmentPolicy.sanitizeOriginalFilename(originalFilename);
        String extension = JimAttachmentPolicy.extractExtension(sanitizedName);
        if (extension != null && BLOCKED_EXTENSIONS.contains(extension)) {
            throw JimMessengerException.badRequest("File extension is not allowed");
        }
    }

    public static String resolveFileKind(String contentType) {
        String normalizedType = JimAttachmentPolicy.normalizeContentType(contentType);
        if (ALLOWED_IMAGE_TYPES.contains(normalizedType)) {
            return "IMAGE";
        }
        if (ALLOWED_AUDIO_TYPES.contains(normalizedType)) {
            return "AUDIO";
        }
        return "FILE";
    }

    public static String sanitizeOriginalFilename(String originalFilename) {
        if (originalFilename == null) {
            return "attachment";
        }
        String trimmed = originalFilename.trim();
        if (trimmed.isEmpty()) {
            return "attachment";
        }
        String normalized = trimmed.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf(47);
        if (slashIndex >= 0) {
            normalized = normalized.substring(slashIndex + 1);
        }
        if ((normalized = normalized.replace("\u0000", "")).contains("..")) {
            throw JimMessengerException.badRequest("Invalid filename");
        }
        if ((normalized = normalized.replaceAll("[\\x00-\\x1f]", "")).length() > 200) {
            normalized = normalized.substring(0, 200);
        }
        return normalized.isEmpty() ? "attachment" : normalized;
    }

    public static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "application/octet-stream";
        }
        String trimmed = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = trimmed.indexOf(59);
        if (semicolon >= 0) {
            trimmed = trimmed.substring(0, semicolon).trim();
        }
        return trimmed.isEmpty() ? "application/octet-stream" : trimmed;
    }

    public static String buildPreviewText(String body, String fileKind, String originalFilename) {
        String normalizedBody;
        String string = normalizedBody = body != null ? body.trim() : "";
        if (!normalizedBody.isEmpty()) {
            return normalizedBody;
        }
        if ("IMAGE".equals(fileKind)) {
            return "Image";
        }
        if ("AUDIO".equals(fileKind)) {
            return "Voice message";
        }
        return "File: " + JimAttachmentPolicy.sanitizeOriginalFilename(originalFilename);
    }

    private static String extractExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf(46);
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

