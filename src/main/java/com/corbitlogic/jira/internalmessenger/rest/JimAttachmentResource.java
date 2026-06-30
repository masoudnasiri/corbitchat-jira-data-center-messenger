/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  javax.inject.Inject
 *  javax.ws.rs.GET
 *  javax.ws.rs.HeaderParam
 *  javax.ws.rs.Path
 *  javax.ws.rs.PathParam
 *  javax.ws.rs.core.Response
 *  javax.ws.rs.core.Response$ResponseBuilder
 *  javax.ws.rs.core.StreamingOutput
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimAttachment;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentPolicy;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentStorageService;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimAttachmentService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(value="/attachments")
public class JimAttachmentResource {
    private static final Logger log = LoggerFactory.getLogger(JimAttachmentResource.class);
    private final JiraAuthenticationContext authenticationContext;
    private final JimPermissionService permissionService;
    private final JimAttachmentService attachmentService;
    private final JimAttachmentStorageService storageService;

    @Inject
    public JimAttachmentResource(JiraAuthenticationContext authenticationContext, JimPermissionService permissionService, JimAttachmentService attachmentService, JimAttachmentStorageService storageService) {
        this.authenticationContext = authenticationContext;
        this.permissionService = permissionService;
        this.attachmentService = attachmentService;
        this.storageService = storageService;
    }

    @GET
    @Path(value="/{attachmentId}/download")
    public Response downloadAttachment(@PathParam(value="attachmentId") int attachmentId, @HeaderParam(value="Range") String rangeHeader) {
        return this.streamAttachment(attachmentId, false, rangeHeader);
    }

    @GET
    @Path(value="/{attachmentId}/preview")
    public Response previewAttachment(@PathParam(value="attachmentId") int attachmentId, @HeaderParam(value="Range") String rangeHeader) {
        return this.streamAttachment(attachmentId, true, rangeHeader);
    }

    private Response streamAttachment(int attachmentId, boolean inlinePreview, String rangeHeader) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            this.permissionService.requireAuthenticatedUserKey();
            JimAttachment attachment = this.attachmentService.getAttachmentForUser(attachmentId, userKey);
            File file = this.storageService.resolveAttachmentFile(attachment);
            String contentType = JimAttachmentPolicy.normalizeContentType(attachment.getContentType());
            String filename = JimAttachmentPolicy.sanitizeOriginalFilename(attachment.getOriginalFilename());
            String dispositionType = this.resolveDispositionType(attachment, inlinePreview);
            long fileLength = file.length();
            long[] range = this.parseRange(rangeHeader, fileLength);
            if (range == null) {
                return Response.status((int)416).header("Content-Range", (Object)("bytes */" + fileLength)).header("Accept-Ranges", (Object)"bytes").build();
            }
            long start = range[0];
            long end = range[1];
            boolean partial = start > 0L || end < fileLength - 1L;
            long contentLength = end - start + 1L;
            Response.ResponseBuilder builder = partial ? Response.status((int)206) : Response.ok();
            String disposition = buildContentDispositionHeader(dispositionType, filename);
            builder.entity((Object)this.buildRangeStream(file, start, contentLength)).type(contentType).header("Accept-Ranges", (Object)"bytes").header("Content-Length", (Object)contentLength).header("Content-Disposition", (Object)disposition);
            if (partial) {
                builder.header("Content-Range", (Object)("bytes " + start + "-" + end + "/" + fileLength));
            }
            return builder.build();
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/attachments/" + attachmentId + (inlinePreview ? "/preview" : "/download"), userKey, ex, "internal_error", "An internal error occurred while loading the attachment.");
        }
    }

    private long[] parseRange(String rangeHeader, long fileLength) {
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
                long l = end = endPart.isEmpty() ? fileLength - 1L : Long.parseLong(endPart);
            }
            if (start < 0L || start >= fileLength || start > end) {
                return null;
            }
            return new long[]{start, Math.min(end, fileLength - 1L)};
        }
        catch (NumberFormatException malformed) {
            return fullRange;
        }
    }

    private StreamingOutput buildRangeStream(File file, long start, long length) {
        return output -> {
            byte[] buffer = new byte[8192];
            try (FileInputStream input = new FileInputStream(file);){
                int read;
                long skipped;
                for (long toSkip = start; toSkip > 0L; toSkip -= skipped) {
                    skipped = ((InputStream)input).skip(toSkip);
                    if (skipped > 0L) continue;
                    throw new IOException("Unable to seek to requested range start");
                }
                for (long remaining = length; remaining > 0L && (read = ((InputStream)input).read(buffer, 0, (int)Math.min((long)buffer.length, remaining))) >= 0; remaining -= (long)read) {
                    output.write(buffer, 0, read);
                }
                output.flush();
            }
        };
    }

    private String resolveDispositionType(JimAttachment attachment, boolean inlinePreview) {
        if (inlinePreview && ("IMAGE".equals(attachment.getFileKind()) || "AUDIO".equals(attachment.getFileKind()))) {
            return "inline";
        }
        return "attachment";
    }

    /**
     * Build a Content-Disposition header that preserves non-ASCII
     * filenames (Persian, Arabic, CJK, accented Latin, etc.) when the
     * user downloads an attachment.
     *
     * Per RFC 6266 / RFC 5987 we emit two `filename` parameters:
     *
     *   Content-Disposition: attachment;
     *       filename="ASCII fallback.ext";
     *       filename*=UTF-8''<percent-encoded-UTF-8 bytes>
     *
     * Modern browsers prefer `filename*` and decode it as UTF-8. Older
     * browsers and most CDNs fall back to the ASCII-only `filename`.
     * Without this, Chrome/Edge save Persian filenames as a literal
     * "download" or "?" because the raw multi-byte chars in a plain
     * `filename="..."` header are not decoded as UTF-8.
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
            // Filename is already pure ASCII; no need for the
            // RFC 5987 parameter.
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

    /**
     * Build an ASCII-only fallback used by older clients. Non-ASCII
     * characters become '_'. Quotes and backslashes (which would
     * break the quoted-string syntax) are also replaced.
     */
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

    /**
     * RFC 5987 ext-value encoding. UTF-8 bytes outside the
     * {@code attr-char} set are percent-encoded as %XX (uppercase
     * hex). attr-char = ALPHA / DIGIT / "!" / "#" / "$" / "&amp;" /
     * "+" / "-" / "." / "^" / "_" / "`" / "|" / "~" .
     */
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
            // Cannot happen: UTF-8 is required by the JLS.
            return s == null ? "" : s;
        }
    }

    private String resolveCurrentUserKey() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        return user != null ? user.getKey() : null;
    }
}

