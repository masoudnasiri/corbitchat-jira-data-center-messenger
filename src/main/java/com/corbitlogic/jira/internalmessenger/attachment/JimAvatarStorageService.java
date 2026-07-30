package com.corbitlogic.jira.internalmessenger.attachment;

import com.atlassian.jira.config.util.JiraHome;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filesystem storage for group/project custom chat avatars (Phase 1). Mirrors the
 * {@link JimAttachmentStorageService} pattern but with a dedicated root
 * ({JiraHome}/data/corbitchat/avatars/{conversationId}/) and much stricter
 * validation via {@link JimAvatarPolicy}.
 *
 * <p>Storage is done SYNCHRONOUSLY in the REST thread. There is exactly one avatar
 * per conversation; a new upload replaces the previous file. PNG/JPEG sources are
 * re-encoded (via ImageIO) to a square PNG thumbnail, which both strips metadata
 * and normalises dimensions. WebP sources are kept as-is when the JVM's ImageIO has
 * no WebP decoder (common), after a magic-byte + size check.</p>
 */
public class JimAvatarStorageService {

    private static final Logger log = LoggerFactory.getLogger(JimAvatarStorageService.class);

    private final JiraHome jiraHome;

    public JimAvatarStorageService(JiraHome jiraHome) {
        this.jiraHome = jiraHome;
    }

    public File getStorageRoot() {
        if (this.jiraHome == null || this.jiraHome.getHome() == null) {
            throw JimMessengerException.internalError("Jira home directory is not configured");
        }
        return new File(this.jiraHome.getHome(), "data/corbitchat/avatars");
    }

    public boolean storageRootExists() {
        try {
            return this.getStorageRoot().exists();
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Validate, normalise and store the uploaded avatar for a conversation.
     * Replaces any previous avatar file for that conversation. Never trusts the
     * client-supplied content-type: the bytes are sniffed and (for PNG/JPEG)
     * fully decoded before being accepted.
     */
    public StoredAvatar storeAvatar(int conversationId, File sourceFile, String contentType) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            throw JimMessengerException.badRequest("Uploaded avatar is missing");
        }
        long size = sourceFile.length();
        JimAvatarPolicy.validateSizeAndType(size, contentType);
        byte[] raw = Files.readAllBytes(sourceFile.toPath());
        String sniffed = JimAvatarPolicy.sniff(raw);
        if (sniffed == null) {
            // Not a PNG/JPEG/WebP by content (covers SVG, scripts, spoofed types).
            throw JimMessengerException.badRequest("Avatar must be a valid PNG, JPEG or WebP image");
        }

        byte[] outBytes;
        String outContentType;
        String extension;
        BufferedImage decoded = tryDecode(raw);
        if (decoded != null) {
            JimAvatarPolicy.validateDimensions(decoded.getWidth(), decoded.getHeight());
            // Re-encode to a square PNG thumbnail: strips metadata and bounds size.
            outBytes = encodeSquarePng(decoded);
            outContentType = JimAvatarPolicy.TYPE_PNG;
            extension = ".png";
        } else if (JimAvatarPolicy.TYPE_WEBP.equals(sniffed)) {
            // No WebP decoder in this JVM; keep the validated original bytes.
            outBytes = raw;
            outContentType = JimAvatarPolicy.TYPE_WEBP;
            extension = ".webp";
        } else {
            // Claimed to be PNG/JPEG but could not be decoded -> corrupt/hostile.
            throw JimMessengerException.badRequest("Avatar image could not be read");
        }

        if (outBytes.length > JimAvatarPolicy.MAX_AVATAR_SIZE_BYTES) {
            throw JimMessengerException.badRequest("Avatar image exceeds the maximum allowed size of 2 MB");
        }

        File dir = this.resolveConversationDir(conversationId);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create avatar storage directory: " + dir.getAbsolutePath());
        }
        // Remove any previous avatar files for this conversation.
        deleteFilesIn(dir);
        String storedFilename = "avatar-" + System.currentTimeMillis() + extension;
        File destination = new File(dir, storedFilename);
        Files.write(destination.toPath(), outBytes);

        String relativePath = conversationId + "/" + storedFilename;
        String checksum = sha256(outBytes);
        log.info("event=avatar stage=store outcome=success conversationId={} size={} type={}",
                new Object[]{conversationId, outBytes.length, outContentType});
        return new StoredAvatar(relativePath, outContentType, outBytes.length, checksum);
    }

    /** Resolve the stored avatar file for a conversation from its relative storage path. */
    public File resolveAvatarFile(String storagePath) {
        if (storagePath == null || storagePath.trim().isEmpty()) {
            throw JimMessengerException.notFound("Avatar file is missing");
        }
        File file = this.resolveRelativePath(storagePath.trim());
        if (!file.exists() || !file.isFile()) {
            throw JimMessengerException.notFound("Avatar file is missing");
        }
        return file;
    }

    /** Delete all stored avatar files for a conversation (reset). Best-effort. */
    public void deleteAvatar(int conversationId) {
        try {
            File dir = this.resolveConversationDir(conversationId);
            if (dir.isDirectory()) {
                deleteFilesIn(dir);
                if (!dir.delete()) {
                    log.debug("event=avatar stage=delete_dir outcome=skipped conversationId={}", conversationId);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("event=avatar stage=delete outcome=error conversationId={} : {}",
                    conversationId, ex.getClass().getSimpleName());
        }
    }

    public File createUploadTempDirectory() throws IOException {
        File root = this.getStorageRoot();
        File tempDir = new File(root, "tmp");
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw new IOException("Unable to create avatar temp directory");
        }
        return tempDir;
    }

    private File resolveConversationDir(int conversationId) {
        if (conversationId <= 0) {
            throw JimMessengerException.badRequest("Invalid conversation id");
        }
        return new File(this.getStorageRoot().getAbsoluteFile(), Integer.toString(conversationId));
    }

    private File resolveRelativePath(String relativePath) {
        String normalized = relativePath.replace('\\', '/').trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            throw JimMessengerException.badRequest("Invalid avatar path");
        }
        File root = this.getStorageRoot().getAbsoluteFile();
        File candidate = new File(root, normalized).getAbsoluteFile();
        if (!candidate.getPath().startsWith(root.getPath())) {
            throw JimMessengerException.forbidden("Invalid avatar path");
        }
        return candidate;
    }

    private static void deleteFilesIn(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isFile() && !f.delete()) {
                log.debug("event=avatar stage=delete_file outcome=skipped file={}", f.getName());
            }
        }
    }

    private static BufferedImage tryDecode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception ex) {
            return null;
        }
    }

    private static byte[] encodeSquarePng(BufferedImage src) throws IOException {
        int edge = Math.min(JimAvatarPolicy.THUMBNAIL_EDGE, Math.max(src.getWidth(), src.getHeight()));
        if (edge < JimAvatarPolicy.MIN_DIMENSION) {
            edge = JimAvatarPolicy.MIN_DIMENSION;
        }
        // Center-crop to a square, then scale to the target edge.
        int srcEdge = Math.min(src.getWidth(), src.getHeight());
        int sx = (src.getWidth() - srcEdge) / 2;
        int sy = (src.getHeight() - srcEdge) / 2;
        BufferedImage square = new BufferedImage(edge, edge, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, edge, edge, sx, sy, sx + srcEdge, sy + srcEdge, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(square, "png", out)) {
            throw new IOException("Unable to encode avatar image");
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            return Integer.toHexString(java.util.Arrays.hashCode(bytes));
        }
    }

    /** Immutable descriptor of a stored avatar file. */
    public static final class StoredAvatar {
        private final String storagePath;
        private final String contentType;
        private final long size;
        private final String checksum;

        public StoredAvatar(String storagePath, String contentType, long size, String checksum) {
            this.storagePath = storagePath;
            this.contentType = contentType;
            this.size = size;
            this.checksum = checksum;
        }

        public String getStoragePath() {
            return this.storagePath;
        }

        public String getContentType() {
            return this.contentType;
        }

        public long getSize() {
            return this.size;
        }

        public String getChecksum() {
            return this.checksum;
        }
    }
}
