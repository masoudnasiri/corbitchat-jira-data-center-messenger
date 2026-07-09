package com.corbitlogic.jira.internalmessenger.service;

/**
 * Plugin-owned hidden chat-reviewer account (Sprint 12). Not a Jira user; it
 * authenticates only through the app/PWA auth path. Only a salted password hash
 * is stored. The raw initial password is read once from a JVM system property
 * at seed time and never hardcoded, logged, or persisted in the clear.
 */
public interface JimReviewerService {

    /** The reviewer's login username. */
    String USERNAME = "pluginuser";

    /**
     * Sentinel used as the reviewer session's "userKey". It is intentionally not
     * a valid Jira user key, so a reviewer session resolves to no Jira user on
     * normal endpoints (which therefore return 401) — reviewer tokens only work
     * on the dedicated read-only reviewer endpoints.
     */
    String SESSION_USER_KEY = "__jim_reviewer__";

    /** JVM system property carrying the initial password (seed only). */
    String INITIAL_PASSWORD_PROP = "corbitchat.reviewer.initialPassword";

    /** Case-insensitive match for the reviewer username. */
    boolean isReviewerUsername(String username);

    /**
     * Seed the reviewer row from {@link #INITIAL_PASSWORD_PROP} if it does not
     * exist yet. No-op if already seeded or the property is absent.
     */
    void ensureSeeded();

    /** Validate the reviewer's password. False if not seeded or wrong password. */
    boolean authenticate(String password);

    /** True once a reviewer row exists (password has been seeded). */
    boolean isConfigured();

    /**
     * Change the reviewer password after verifying the current one. Returns
     * false when the current password is wrong; throws on invalid new password.
     */
    boolean changePassword(String currentPassword, String newPassword);
}
