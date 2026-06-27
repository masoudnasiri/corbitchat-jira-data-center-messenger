/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.servlet.http.HttpServletRequest
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.attachment;

import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JimMultipartParser {
    private static final Logger log = LoggerFactory.getLogger(JimMultipartParser.class);
    private static final String FILE_FIELD = "file";
    private static final String BODY_FIELD = "body";

    private JimMultipartParser() {
    }

    public static ParsedMultipartForm parse(HttpServletRequest request, File tempDirectory) throws IOException {
        if (request == null) {
            throw JimMessengerException.badRequest("Upload request is missing");
        }
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            throw JimMessengerException.badRequest("Request must be multipart/form-data");
        }
        String boundary = JimMultipartParser.extractBoundary(contentType);
        if (boundary == null) {
            throw JimMessengerException.badRequest("Multipart boundary is missing");
        }
        // Hard cap = policy max + room for headers/boundary/body field bytes.
        long maxBodyBytes = com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentPolicy.MAX_FILE_SIZE_BYTES + 65536L;
        byte[] bodyBytes = JimMultipartParser.readRequestBody((InputStream)request.getInputStream(), maxBodyBytes);
        String bodyText = new String(bodyBytes, StandardCharsets.ISO_8859_1);
        String delimiter = "--" + boundary;
        String[] parts = bodyText.split(delimiter);
        String optionalBody = null;
        File uploadedFile = null;
        String originalFilename = null;
        String fileContentType = null;
        for (String part : parts) {
            ParsedPart parsedPart;
            if (part == null || part.trim().isEmpty() || part.startsWith("--") || (parsedPart = JimMultipartParser.parsePart(part)) == null) continue;
            if (FILE_FIELD.equals(parsedPart.getFieldName())) {
                if (parsedPart.getFilename() == null || parsedPart.getFilename().trim().isEmpty()) continue;
                uploadedFile = JimMultipartParser.writeTempFile(tempDirectory, parsedPart.getContent());
                originalFilename = parsedPart.getFilename();
                fileContentType = parsedPart.getContentType();
                continue;
            }
            if (!BODY_FIELD.equals(parsedPart.getFieldName())) continue;
            optionalBody = new String(parsedPart.getContent(), StandardCharsets.UTF_8);
        }
        if (uploadedFile == null || !uploadedFile.exists() || uploadedFile.length() == 0L) {
            throw JimMessengerException.badRequest("file field is required");
        }
        return new ParsedMultipartForm(optionalBody, uploadedFile, fileContentType, originalFilename);
    }

    private static String extractBoundary(String contentType) {
        for (String token : contentType.split(";")) {
            String trimmed = token.trim();
            if (!trimmed.toLowerCase(Locale.ROOT).startsWith("boundary=")) continue;
            String boundary = trimmed.substring("boundary=".length()).trim();
            if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() >= 2) {
                boundary = boundary.substring(1, boundary.length() - 1);
            }
            return boundary;
        }
        return null;
    }

    private static byte[] readRequestBody(InputStream inputStream, long maxBytes) throws IOException {
        int read;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0L;
        while ((read = inputStream.read(chunk)) != -1) {
            if ((total += (long)read) > maxBytes) {
                throw JimMessengerException.badRequest("Uploaded request exceeds the maximum allowed size");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static ParsedPart parsePart(String rawPart) {
        int headerEnd = rawPart.indexOf("\r\n\r\n");
        if (headerEnd < 0) {
            return null;
        }
        String headers = rawPart.substring(0, headerEnd);
        String content = rawPart.substring(headerEnd + 4);
        if (content.endsWith("\r\n")) {
            content = content.substring(0, content.length() - 2);
        }
        String disposition = null;
        String contentType = null;
        for (String line : headers.split("\r\n")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("content-disposition:")) {
                disposition = line;
                continue;
            }
            if (!lower.startsWith("content-type:")) continue;
            contentType = line.substring("content-type:".length()).trim();
        }
        if (disposition == null) {
            return null;
        }
        String fieldName = JimMultipartParser.extractQuotedValue(disposition, "name");
        String filename = JimMultipartParser.extractFilename(disposition);
        byte[] bytes = content.getBytes(StandardCharsets.ISO_8859_1);
        return new ParsedPart(fieldName, filename, contentType, bytes);
    }

    /**
     * Extract a filename from a Content-Disposition header, honouring RFC 5987
     * {@code filename*=UTF-8''...} when present and otherwise recovering UTF-8
     * bytes from the legacy {@code filename="..."} form (which we read as
     * ISO-8859-1 to keep the multipart body byte-transparent).
     *
     * Without this, a Persian or other non-ASCII filename arrives as mojibake
     * (e.g. "سند.pdf" -> "Ø³Ù†Ø¯.pdf").
     */
    private static String extractFilename(String disposition) {
        String rfc5987 = JimMultipartParser.extractRawValue(disposition, "filename*");
        if (rfc5987 != null && !rfc5987.isEmpty()) {
            String decoded = JimMultipartParser.decodeRfc5987(rfc5987);
            if (decoded != null) {
                return decoded;
            }
        }
        String legacy = JimMultipartParser.extractQuotedValue(disposition, "filename");
        if (legacy == null || legacy.isEmpty()) {
            return legacy;
        }
        // The multipart body was decoded as ISO-8859-1, so each char 0-255
        // is one original byte. Re-encode and decode as UTF-8 to recover
        // non-ASCII characters that the browser sent as raw UTF-8 bytes.
        try {
            return new String(legacy.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        } catch (RuntimeException ex) {
            return legacy;
        }
    }

    /**
     * Decodes an RFC 5987 extended-value: {@code charset'language'encoded-value}.
     * Only UTF-8 and ISO-8859-1 charsets are accepted; anything else falls back
     * to UTF-8. Returns null if the encoding is malformed.
     */
    private static String decodeRfc5987(String value) {
        int firstQuote = value.indexOf('\'');
        if (firstQuote <= 0) {
            return null;
        }
        int secondQuote = value.indexOf('\'', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }
        String charset = value.substring(0, firstQuote).trim();
        String encoded = value.substring(secondQuote + 1);
        java.nio.charset.Charset cs;
        try {
            cs = java.nio.charset.Charset.forName(charset.isEmpty() ? "UTF-8" : charset);
        } catch (RuntimeException ex) {
            cs = StandardCharsets.UTF_8;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(encoded.length());
        for (int i = 0; i < encoded.length(); ) {
            char c = encoded.charAt(i);
            if (c == '%' && i + 2 < encoded.length()) {
                int hi = Character.digit(encoded.charAt(i + 1), 16);
                int lo = Character.digit(encoded.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) {
                    return null;
                }
                out.write((hi << 4) | lo);
                i += 3;
            } else if (c < 0x80) {
                out.write(c);
                i++;
            } else {
                return null;
            }
        }
        return new String(out.toByteArray(), cs);
    }

    /**
     * Returns the unquoted raw token value for an attribute (e.g. {@code filename*})
     * from a header like Content-Disposition, or null when the attribute is absent.
     */
    private static String extractRawValue(String header, String key) {
        String lowerHeader = header.toLowerCase(Locale.ROOT);
        String lowerKey = key.toLowerCase(Locale.ROOT) + "=";
        int index = lowerHeader.indexOf(lowerKey);
        if (index < 0) {
            return null;
        }
        String remainder = header.substring(index + lowerKey.length()).trim();
        if (remainder.startsWith("\"")) {
            int endQuote = remainder.indexOf('"', 1);
            return endQuote > 0 ? remainder.substring(1, endQuote) : null;
        }
        int end = remainder.indexOf(';');
        return (end >= 0 ? remainder.substring(0, end) : remainder).trim();
    }

    private static String extractQuotedValue(String header, String key) {
        int endQuote;
        String lowerKey;
        String lowerHeader = header.toLowerCase(Locale.ROOT);
        int index = lowerHeader.indexOf(lowerKey = key.toLowerCase(Locale.ROOT) + "=");
        if (index < 0) {
            return null;
        }
        String remainder = header.substring(index + lowerKey.length()).trim();
        if (remainder.startsWith("\"") && (endQuote = remainder.indexOf(34, 1)) > 1) {
            return remainder.substring(1, endQuote);
        }
        int end = remainder.indexOf(59);
        return (end >= 0 ? remainder.substring(0, end) : remainder).trim();
    }

    private static File writeTempFile(File tempDirectory, byte[] content) throws IOException {
        File file = File.createTempFile("jim-upload-", ".bin", tempDirectory);
        try (FileOutputStream outputStream = new FileOutputStream(file);){
            outputStream.write(content);
        }
        return file;
    }

    private static final class ParsedPart {
        private final String fieldName;
        private final String filename;
        private final String contentType;
        private final byte[] content;

        private ParsedPart(String fieldName, String filename, String contentType, byte[] content) {
            this.fieldName = fieldName;
            this.filename = filename;
            this.contentType = contentType;
            this.content = content;
        }

        private String getFieldName() {
            return this.fieldName;
        }

        private String getFilename() {
            return this.filename;
        }

        private String getContentType() {
            return this.contentType;
        }

        private byte[] getContent() {
            return this.content;
        }
    }

    public static final class ParsedMultipartForm {
        private final String body;
        private final File file;
        private final String contentType;
        private final String originalFilename;

        public ParsedMultipartForm(String body, File file, String contentType, String originalFilename) {
            this.body = body;
            this.file = file;
            this.contentType = contentType;
            this.originalFilename = originalFilename;
        }

        public String getBody() {
            return this.body;
        }

        public File getFile() {
            return this.file;
        }

        public String getContentType() {
            return this.contentType;
        }

        public String getOriginalFilename() {
            return this.originalFilename;
        }
    }
}

