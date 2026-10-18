package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.corbitlogic.jira.internalmessenger.ao.JimMobileSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import net.java.ao.DBParam;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimMobileSessionServiceImpl implements JimMobileSessionService {

    private static final Logger log = LoggerFactory.getLogger(JimMobileSessionServiceImpl.class);

    /** Mobile sessions are long-lived (mobile UX); 30 days from issue. */
    static final long TTL_MILLIS = 30L * 24 * 60 * 60 * 1000;
    private static final int TOKEN_BYTES = 32; // 256-bit
    private static final int DEVICE_MAX = 255;

    private final ActiveObjects activeObjects;
    private final SecureRandom secureRandom = new SecureRandom();

    public JimMobileSessionServiceImpl(ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    @Override
    public IssuedSession create(String userKey, String device) {
        if (userKey == null || userKey.trim().isEmpty()) {
            throw new IllegalArgumentException("userKey must not be blank");
        }
        final String rawToken = generateRawToken();
        final String tokenHash = sha256Hex(rawToken);
        final long now = System.currentTimeMillis();
        final long expiresAt = now + TTL_MILLIS;
        final String deviceLabel = truncate(device, DEVICE_MAX);

        this.activeObjects.executeInTransaction(() -> {
            JimMobileSession row = this.activeObjects.create(JimMobileSession.class, new DBParam[0]);
            row.setUserKey(userKey);
            row.setTokenHash(tokenHash);
            row.setCreatedAt(now);
            row.setExpiresAt(expiresAt);
            row.setLastUsedAt(now);
            row.setRevoked(Boolean.FALSE);
            row.setDevice(deviceLabel);
            row.save();
            return null;
        });

        return new IssuedSession(rawToken, expiresAt);
    }

    @Override
    public String validate(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return null;
        }
        final String tokenHash;
        try {
            tokenHash = sha256Hex(rawToken.trim());
        } catch (RuntimeException ex) {
            return null;
        }

        JimMobileSession row = findByHash(tokenHash);
        if (row == null) {
            return null;
        }
        if (Boolean.TRUE.equals(row.getRevoked())) {
            return null;
        }
        final Long expiresAt = row.getExpiresAt();
        if (expiresAt == null || expiresAt <= System.currentTimeMillis()) {
            return null;
        }

        final String userKey = row.getUserKey();
        // Best-effort last-used refresh; never fail validation because of it.
        try {
            final int id = row.getID();
            this.activeObjects.executeInTransaction(() -> {
                JimMobileSession fresh = this.activeObjects.get(JimMobileSession.class, id);
                if (fresh != null) {
                    fresh.setLastUsedAt(System.currentTimeMillis());
                    fresh.save();
                }
                return null;
            });
        } catch (Exception ex) {
            log.debug("mobile session last-used refresh skipped: {}", ex.getMessage());
        }
        return userKey;
    }

    @Override
    public boolean revoke(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return false;
        }
        final String tokenHash = sha256Hex(rawToken.trim());
        return Boolean.TRUE.equals(this.activeObjects.executeInTransaction(() -> {
            JimMobileSession row = findByHash(tokenHash);
            if (row == null || Boolean.TRUE.equals(row.getRevoked())) {
                return Boolean.FALSE;
            }
            row.setRevoked(Boolean.TRUE);
            row.save();
            return Boolean.TRUE;
        }));
    }

    @Override
    public int revokeAllForUser(String userKey) {
        if (userKey == null || userKey.trim().isEmpty()) {
            return 0;
        }
        Integer count = this.activeObjects.executeInTransaction(() -> {
            JimMobileSession[] rows = this.activeObjects.find(JimMobileSession.class,
                    Query.select().where("USER_KEY = ? AND REVOKED = ?", new Object[]{userKey, Boolean.FALSE}));
            int n = 0;
            for (JimMobileSession row : rows) {
                row.setRevoked(Boolean.TRUE);
                row.save();
                n++;
            }
            return n;
        });
        return count == null ? 0 : count;
    }

    private JimMobileSession findByHash(String tokenHash) {
        JimMobileSession[] rows = this.activeObjects.find(JimMobileSession.class,
                Query.select().where("TOKEN_HASH = ?", new Object[]{tokenHash}).limit(1));
        return rows.length == 0 ? null : rows[0];
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        this.secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
