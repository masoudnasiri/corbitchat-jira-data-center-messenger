/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.config.util.JiraHome
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.attachment;

import com.atlassian.jira.config.util.JiraHome;
import com.corbitlogic.jira.internalmessenger.ao.JimAttachment;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentPolicy;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimAttachmentStorageService {
    private static final Logger log = LoggerFactory.getLogger(JimAttachmentStorageService.class);
    private final JiraHome jiraHome;

    public JimAttachmentStorageService(JiraHome jiraHome) {
        this.jiraHome = jiraHome;
    }

    public File getStorageRoot() {
        if (this.jiraHome == null || this.jiraHome.getHome() == null) {
            throw JimMessengerException.internalError("Jira home directory is not configured");
        }
        return new File(this.jiraHome.getHome(), "data/corbitchat/attachments");
    }

    public boolean storageRootExists() {
        try {
            return this.getStorageRoot().exists();
        }
        catch (Exception ex) {
            return false;
        }
    }

    public StoredAttachmentFile storeUploadedFile(File sourceFile, String contentType, String originalFilename) throws IOException {
        return this.storeUploadedFile(sourceFile, contentType, originalFilename, null);
    }

    public StoredAttachmentFile storeUploadedFile(File sourceFile, String contentType, String originalFilename, String adminAllowedExtensionsCsv) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            throw JimMessengerException.badRequest("Uploaded file is missing");
        }
        JimAttachmentPolicy.validateUpload(sourceFile.length(), contentType, originalFilename, adminAllowedExtensionsCsv);
        String sanitizedOriginal = JimAttachmentPolicy.sanitizeOriginalFilename(originalFilename);
        String storedFilename = UUID.randomUUID().toString().replace("-", "") + this.buildStoredExtension(sanitizedOriginal);
        String relativePath = this.buildRelativePath(storedFilename);
        File destination = this.resolveRelativePath(relativePath);
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create attachment storage directory: " + parent.getAbsolutePath());
        }
        Files.copy(sourceFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        log.info("event=attachment stage=store outcome=success storedFilename={} size={}", (Object)storedFilename, (Object)destination.length());
        return new StoredAttachmentFile(sanitizedOriginal, storedFilename, relativePath, destination.length(), JimAttachmentPolicy.normalizeContentType(contentType), JimAttachmentPolicy.resolveFileKind(contentType));
    }

    public File resolveAttachmentFile(JimAttachment attachment) {
        if (attachment == null) {
            throw JimMessengerException.notFound("Attachment not found");
        }
        if (this.isDeleted(attachment)) {
            throw JimMessengerException.notFound("Attachment not found");
        }
        String storagePath = attachment.getStoragePath();
        if (storagePath == null || storagePath.trim().isEmpty()) {
            throw JimMessengerException.notFound("Attachment file is missing");
        }
        File file = this.resolveRelativePath(storagePath.trim());
        if (!file.exists() || !file.isFile()) {
            throw JimMessengerException.notFound("Attachment file is missing");
        }
        return file;
    }

    public File resolveRelativePath(String relativePath) {
        String normalized = this.normalizeRelativePath(relativePath);
        File root = this.getStorageRoot().getAbsoluteFile();
        File candidate = new File(root, normalized).getAbsoluteFile();
        if (!candidate.getPath().startsWith(root.getPath())) {
            throw JimMessengerException.forbidden("Invalid attachment path");
        }
        return candidate;
    }

    public void ensureStorageRootExists() throws IOException {
        File root = this.getStorageRoot();
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("Unable to create attachment storage root: " + root.getAbsolutePath());
        }
    }

    public File createUploadTempDirectory() throws IOException {
        this.ensureStorageRootExists();
        File tempDir = new File(this.getStorageRoot(), "tmp");
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw new IOException("Unable to create attachment temp directory");
        }
        return tempDir;
    }

    private String buildRelativePath(String storedFilename) {
        Instant now = Instant.now();
        String year = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC).format(now);
        String month = DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC).format(now);
        String day = DateTimeFormatter.ofPattern("dd").withZone(ZoneOffset.UTC).format(now);
        return year + "/" + month + "/" + day + "/" + storedFilename;
    }

    private String buildStoredExtension(String sanitizedOriginal) {
        int dot = sanitizedOriginal.lastIndexOf(46);
        if (dot < 0 || dot == sanitizedOriginal.length() - 1) {
            return "";
        }
        String extension = sanitizedOriginal.substring(dot).toLowerCase();
        if (extension.length() > 10 || extension.contains("/") || extension.contains("\\")) {
            return "";
        }
        return extension;
    }

    private String normalizeRelativePath(String relativePath) {
        String normalized = relativePath.replace('\\', '/').trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            throw JimMessengerException.badRequest("Invalid attachment path");
        }
        return normalized;
    }

    private boolean isDeleted(JimAttachment attachment) {
        Integer deleted = attachment.getDeleted();
        return deleted != null && deleted != 0;
    }

    public static final class StoredAttachmentFile {
        private final String originalFilename;
        private final String storedFilename;
        private final String storagePath;
        private final long fileSize;
        private final String contentType;
        private final String fileKind;

        public StoredAttachmentFile(String originalFilename, String storedFilename, String storagePath, long fileSize, String contentType, String fileKind) {
            this.originalFilename = originalFilename;
            this.storedFilename = storedFilename;
            this.storagePath = storagePath;
            this.fileSize = fileSize;
            this.contentType = contentType;
            this.fileKind = fileKind;
        }

        public String getOriginalFilename() {
            return this.originalFilename;
        }

        public String getStoredFilename() {
            return this.storedFilename;
        }

        public String getStoragePath() {
            return this.storagePath;
        }

        public long getFileSize() {
            return this.fileSize;
        }

        public String getContentType() {
            return this.contentType;
        }

        public String getFileKind() {
            return this.fileKind;
        }
    }
}

