package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.corbitlogic.jira.internalmessenger.ao.JimReviewer;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.inject.Inject;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimReviewerServiceImpl implements JimReviewerService {

    private static final Logger log = LoggerFactory.getLogger(JimReviewerServiceImpl.class);
    private static final int ITERATIONS = 120000;
    private static final int SALT_BYTES = 32;
    private static final int HASH_BITS = 256;
    private static final int MIN_PASSWORD_LEN = 8;

    private final ActiveObjects activeObjects;
    private final UserManager userManager;
    private final SecureRandom random = new SecureRandom();

    @Inject
    public JimReviewerServiceImpl(ActiveObjects activeObjects, UserManager userManager) {
        this.activeObjects = activeObjects;
        this.userManager = userManager;
    }

    /**
     * s12e-reviewerfix: the reviewer is a REAL Jira user (default {@code m.nasiri}),
     * configurable via the {@code corbitchat.reviewer.username} JVM property. It is
     * NOT a plugin-owned account and is otherwise a normal chatting user — the
     * read-only guarantee comes from the reviewer app + read-only /reviewer/*
     * endpoints, not from blocking this user's normal mutations.
     */
    private String reviewerUsername() {
        String u = System.getProperty("corbitchat.reviewer.username");
        return (u != null && !u.trim().isEmpty()) ? u.trim() : "m.nasiri";
    }

    @Override
    public boolean isReviewerUsername(String username) {
        return username != null && reviewerUsername().equalsIgnoreCase(username.trim());
    }

    @Override
    public boolean isReviewerUserKey(String userKey) {
        if (userKey == null) {
            return false;
        }
        // Backward compat: any legacy plugin-owned reviewer session.
        if (SESSION_USER_KEY.equals(userKey)) {
            return true;
        }
        try {
            ApplicationUser user = this.userManager.getUserByKey(userKey);
            return user != null && isReviewerUsername(user.getUsername());
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public boolean isHiddenSystemAccountUsername(String username) {
        // ONLY the dedicated hidden account (pluginuser) — never the configured
        // reviewer user (m.nasiri), admins, or any ordinary user.
        return username != null && USERNAME.equalsIgnoreCase(username.trim());
    }

    @Override
    public boolean isHiddenSystemAccountUserKey(String userKey) {
        if (userKey == null) {
            return false;
        }
        // Legacy plugin-owned reviewer session sentinel resolves to no Jira user.
        if (SESSION_USER_KEY.equals(userKey)) {
            return true;
        }
        try {
            ApplicationUser user = this.userManager.getUserByKey(userKey);
            return user != null && isHiddenSystemAccountUsername(user.getUsername());
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void ensureSeeded() {
        try {
            if (findRow() != null) {
                return;
            }
            String initial = System.getProperty(INITIAL_PASSWORD_PROP);
            if (initial == null || initial.trim().isEmpty()) {
                // Not seeded and no property provided — reviewer cannot log in yet.
                return;
            }
            final byte[] salt = newSalt();
            final String hash = hash(initial, salt);
            this.activeObjects.executeInTransaction(() -> {
                // Double-check inside the transaction to avoid a race creating two rows.
                if (findRow() != null) {
                    return null;
                }
                JimReviewer row = this.activeObjects.create(JimReviewer.class);
                row.setUsername(USERNAME);
                row.setSalt(Base64.getEncoder().encodeToString(salt));
                row.setPasswordHash(hash);
                long now = System.currentTimeMillis();
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                row.save();
                return row;
            });
            log.info("CorbitChat reviewer account seeded (username={})", USERNAME);
        } catch (Exception ex) {
            // Never leak the password; log only the failure class.
            log.warn("reviewer seed failed: {}", ex.getClass().getSimpleName());
        }
    }

    @Override
    public boolean authenticate(String password) {
        if (password == null) {
            return false;
        }
        JimReviewer row = findRow();
        if (row == null) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(row.getSalt());
            String candidate = hash(password, salt);
            return constantTimeEquals(candidate, row.getPasswordHash());
        } catch (Exception ex) {
            log.warn("reviewer authenticate error: {}", ex.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public boolean isConfigured() {
        return findRow() != null;
    }

    @Override
    public boolean changePassword(String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LEN) {
            throw new IllegalArgumentException("New password must be at least " + MIN_PASSWORD_LEN + " characters.");
        }
        JimReviewer row = findRow();
        if (row == null || !authenticate(currentPassword)) {
            return false;
        }
        try {
            final byte[] salt = newSalt();
            final String hash = hash(newPassword, salt);
            this.activeObjects.executeInTransaction(() -> {
                row.setSalt(Base64.getEncoder().encodeToString(salt));
                row.setPasswordHash(hash);
                row.setUpdatedAt(System.currentTimeMillis());
                row.save();
                return row;
            });
            return true;
        } catch (Exception ex) {
            log.warn("reviewer changePassword error: {}", ex.getClass().getSimpleName());
            return false;
        }
    }

    private JimReviewer findRow() {
        JimReviewer[] rows = this.activeObjects.find(JimReviewer.class,
                Query.select().where("USERNAME = ?", USERNAME).limit(1));
        return rows.length > 0 ? rows[0] : null;
    }

    private byte[] newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        this.random.nextBytes(salt);
        return salt;
    }

    private static String hash(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, HASH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] hash = factory.generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(hash);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] ab = a.getBytes();
        byte[] bb = b.getBytes();
        if (ab.length != bb.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < ab.length; i++) {
            result |= ab[i] ^ bb[i];
        }
        return result == 0;
    }
}
