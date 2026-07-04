package com.corbitlogic.jira.internalmessenger.attachment;

import com.corbitlogic.jira.internalmessenger.ao.JimAttachment;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

/**
 * Shared attachment streaming logic (Range support + RFC 6266/5987 filename
 * handling), extracted so the mobile BFF can serve attachments over the
 * session-authenticated {@code /rest/corbit-mobile/1.0/*} path without touching
 * the web {@code /rest/jim/1.0/attachments/*} resource.
 *
 * <p>The logic is a verbatim extraction of {@code JimAttachmentResource}'s
 * private streaming helpers, so both surfaces behave identically. Permission and
 * existence checks are done by the caller (which resolves the {@link JimAttachment}
 * and its {@link File}); this class only builds the streaming {@link Response}.</p>
 */
public final class JimAttachmentStreaming {

    private JimAttachmentStreaming() {
    }

    /**
     * Build a byte-range aware streaming response for an already-resolved,
     * permission-checked attachment file.
     */
    public static Response stream(JimAttachment attachment, File file, boolean inlinePreview, String rangeHeader) {
        String contentType = JimAttachmentPolicy.normalizeContentType(attachment.getContentType());
        String filename = JimAttachmentPolicy.sanitizeOriginalFilename(attachment.getOriginalFilename());
        String dispositionType = resolveDispositionType(attachment, inlinePreview);
        return stream(file, contentType, filename, dispositionType, rangeHeader);
    }

    /**
     * Same byte-range aware streaming as {@link #stream(JimAttachment, File,
     * boolean, String)} but for an arbitrary already-resolved,
     * permission-checked file (used by the mobile BFF to serve <em>Jira issue</em>
     * attachments referenced by comment bodies). The caller supplies the
     * content type, display filename and disposition ({@code "inline"} for image/
     * audio/video previews, else {@code "attachment"}).
     */
    public static Response stream(File file, String contentType, String filename,
                                  String dispositionType, String rangeHeader) {
        long fileLength = file.length();
        long[] range = parseRange(rangeHeader, fileLength);
        if (range == null) {
            return Response.status(416)
                    .header("Content-Range", "bytes */" + fileLength)
                    .header("Accept-Ranges", "bytes")
                    .build();
        }
        long start = range[0];
        long end = range[1];
        boolean partial = start > 0L || end < fileLength - 1L;
        long contentLength = end - start + 1L;
        Response.ResponseBuilder builder = partial ? Response.status(206) : Response.ok();
        String disposition = buildContentDispositionHeader(dispositionType, filename);
        builder.entity(buildRangeStream(file, start, contentLength))
                .type(contentType)
                .header("Accept-Ranges", "bytes")
                .header("Content-Length", contentLength)
                .header("Content-Disposition", disposition);
        if (partial) {
            builder.header("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
        }
        return builder.build();
    }

    private static long[] parseRange(String rangeHeader, long fileLength) {
        int dash;
        long[] fullRange = new long[]{0L, Math.max(0L, fileLength - 1L)};
        if (rangeHeader == null || fileLength <= 0L) {
            return fullRange;
        }
        String trimmed = rangeHeader.trim();
        if (!trimmed.startsWith("bytes=")) {
            return fullRange;
        }
        String spec = trimmed.substring("bytes=".length()).trim();
        int comma = spec.indexOf(44);
        if (comma >= 0) {
            spec = spec.substring(0, comma).trim();
        }
        if ((dash = spec.indexOf(45)) < 0) {
            return fullRange;
        }
        String startPart = spec.substring(0, dash).trim();
        String endPart = spec.substring(dash + 1).trim();
        try {
            long end;
            long start;
            if (startPart.isEmpty()) {
                if (endPart.isEmpty()) {
                    return fullRange;
                }
                long suffixLength = Long.parseLong(endPart);
                if (suffixLength <= 0L) {
                    return null;
                }
                start = Math.max(0L, fileLength - suffixLength);
                end = fileLength - 1L;
            } else {
                start = Long.parseLong(startPart);
                end = endPart.isEmpty() ? fileLength - 1L : Long.parseLong(endPart);
            }
            if (start < 0L || start >= fileLength || start > end) {
                return null;
            }
            return new long[]{start, Math.min(end, fileLength - 1L)};
        } catch (NumberFormatException malformed) {
            return fullRange;
        }
    }

    private static StreamingOutput buildRangeStream(File file, long start, long length) {
        return output -> {
            byte[] buffer = new byte[8192];
            try (FileInputStream input = new FileInputStream(file)) {
                int read;
                long skipped;
                for (long toSkip = start; toSkip > 0L; toSkip -= skipped) {
                    skipped = input.skip(toSkip);
                    if (skipped > 0L) {
                        continue;
                    }
                    throw new IOException("Unable to seek to requested range start");
                }
                for (long remaining = length;
                     remaining > 0L && (read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining))) >= 0;
                     remaining -= read) {
                    output.write(buffer, 0, read);
                }
                output.flush();
            }
        };
    }

    private static String resolveDispositionType(JimAttachment attachment, boolean inlinePreview) {
        if (inlinePreview && ("IMAGE".equals(attachment.getFileKind()) || "AUDIO".equals(attachment.getFileKind())
                || "VIDEO".equals(attachment.getFileKind()))) {
            return "inline";
        }
        return "attachment";
    }

    /**
     * Build a Content-Disposition header that preserves non-ASCII filenames
     * (Persian, Arabic, CJK, accented Latin, etc.) per RFC 6266 / RFC 5987.
     */
    static String buildContentDispositionHeader(String dispositionType, String filename) {
        String safe = filename == null ? "" : filename;
        String ascii = toAsciiFallback(safe);
        if (ascii.isEmpty()) {
            ascii = "download";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(dispositionType == null ? "attachment" : dispositionType);
        sb.append("; filename=\"").append(ascii).append('"');
        if (isAscii(safe)) {
            return sb.toString();
        }
        sb.append("; filename*=UTF-8''").append(rfc5987Encode(safe));
        return sb.toString();
    }

    private static boolean isAscii(String s) {
        if (s == null) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x80 || c < 0x20) {
                return false;
            }
        }
        return true;
    }

    private static String toAsciiFallback(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F || c >= 0x80 || c == '"' || c == '\\') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String rfc5987Encode(String s) {
        try {
            byte[] bytes = s.getBytes("UTF-8");
            StringBuilder sb = new StringBuilder(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                int b = bytes[i] & 0xFF;
                boolean attrChar = (b >= '0' && b <= '9')
                        || (b >= 'A' && b <= 'Z')
                        || (b >= 'a' && b <= 'z')
                        || b == '!' || b == '#' || b == '$' || b == '&'
                        || b == '+' || b == '-' || b == '.' || b == '^'
                        || b == '_' || b == '`' || b == '|' || b == '~';
                if (attrChar) {
                    sb.append((char) b);
                } else {
                    sb.append('%');
                    sb.append(Character.toUpperCase(Character.forDigit((b >>> 4) & 0xF, 16)));
                    sb.append(Character.toUpperCase(Character.forDigit(b & 0xF, 16)));
                }
            }
            return sb.toString();
        } catch (java.io.UnsupportedEncodingException e) {
            return s == null ? "" : s;
        }
    }
}
