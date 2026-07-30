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
     * True when the given Jira user key belongs to the reviewer account
     * ({@code pluginuser}). As of s12e-fix2 the reviewer is a REAL Jira user
     * authenticated through Jira/Crowd, so identity is resolved by user key →
     * username. The legacy plugin-owned sentinel key is also treated as the
     * reviewer for backward compatibility with any older issued session.
     *
     * <p>WARNING — this matches the <em>configured reviewer user</em> (default
     * {@code m.nasiri}), who is an ordinary Jira user. Do NOT use it to hide
     * users from normal product surfaces (search / mentions / member pickers):
     * that hides a real person. Use {@link #isHiddenSystemAccountUserKey} /
     * {@link #isHiddenSystemAccountUsername} for hiding instead.
     */
    boolean isReviewerUserKey(String userKey);

    /**
     * True ONLY for the dedicated hidden system account ({@link #USERNAME},
     * currently {@code pluginuser}) — the single account that must never appear
     * on normal product surfaces. This is deliberately NARROW: it does NOT match
     * the configured reviewer user, Jira admins, reviewer-capable users, or the
     * owner/operator. It is the correct predicate for all user-hiding filters
     * (chat search, mention autocomplete, group add-member, member lists,
     * forward picker, …). Matches the fixed hidden username only.
     */
    boolean isHiddenSystemAccountUsername(String username);

    /**
     * User-key form of {@link #isHiddenSystemAccountUsername}: resolves the key
     * to a Jira user and matches only the dedicated hidden account
     * ({@code pluginuser}); the legacy plugin-owned sentinel key
     * ({@link #SESSION_USER_KEY}) is also treated as hidden for backward compat.
     * Returns false for every ordinary user (including the reviewer user).
     */
    boolean isHiddenSystemAccountUserKey(String userKey);

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
