package com.corbitlogic.jira.internalmessenger.service;

/**
 * Lifecycle for plugin-issued CorbitChat Mobile sessions (Sprint 01B):
 * create (after a validated login), validate (per request), and revoke (logout).
 * Only token hashes are persisted; the raw token exists client-side only.
 */
public interface JimMobileSessionService {

    /** Result of creating a session. The raw token is returned exactly once. */
    final class IssuedSession {
        private final String rawToken;
        private final long expiresAt;

        public IssuedSession(String rawToken, long expiresAt) {
            this.rawToken = rawToken;
            this.expiresAt = expiresAt;
        }

        public String getRawToken() {
            return rawToken;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }

    /**
     * Issue a new session for the given Jira user key. {@code device} is an
     * optional client label (may be null).
     */
    IssuedSession create(String userKey, String device);

    /**
     * Validate a raw token. Returns the owning user key when the session exists,
     * is not revoked, and has not expired (and refreshes last-used); otherwise
     * returns null. Never throws for an unknown/invalid token.
     */
    String validate(String rawToken);

    /** Revoke the session for a raw token. Returns true if a row was revoked. */
    boolean revoke(String rawToken);

    /** Revoke every session for a user (e.g. password change). Returns count. */
    int revokeAllForUser(String userKey);
}
