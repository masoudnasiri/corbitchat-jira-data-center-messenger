package com.corbitlogic.jira.internalmessenger.mobile;

import javax.servlet.http.HttpServletRequest;

/**
 * Shared constants/helpers for CorbitChat Mobile session handling (Sprint 01B).
 *
 * <p>The plugin session token travels in a dedicated header rather than
 * {@code Authorization}, because Jira rejects unknown {@code Bearer} tokens with
 * 401 before the request reaches the plugin. Keeping our token out of
 * {@code Authorization} also means PAT-based requests are completely untouched.</p>
 */
public final class MobileSessionSupport {

    /** Header carrying the raw (client-held) mobile session token. */
    public static final String SESSION_HEADER = "X-CorbitChat-Session";

    /**
     * JVM system property that permits username/password login over plain HTTP.
     * Off by default: production must use HTTPS (or a TLS-terminating proxy that
     * forwards {@code X-Forwarded-Proto: https}). Intended for local/dev only.
     */
    public static final String ALLOW_INSECURE_LOGIN_PROP = "corbitchat.mobile.allowInsecureLogin";

    private MobileSessionSupport() {
    }

    /** The raw session token from the request header, or null when absent/blank. */
    public static String extractSessionToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader(SESSION_HEADER);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * True when the request arrived over TLS, either directly or via a
     * TLS-terminating reverse proxy that forwards the standard headers. The
     * origin HTTP connector must not be publicly exposed for this to be safe.
     */
    public static boolean isSecureRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if (request.isSecure()) {
            return true;
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        if (proto != null) {
            // May be a comma-separated list; the left-most is the client-facing scheme.
            String first = proto.split(",")[0].trim();
            if ("https".equalsIgnoreCase(first)) {
                return true;
            }
        }
        String ssl = request.getHeader("X-Forwarded-Ssl");
        return "on".equalsIgnoreCase(ssl);
    }

    /** Whether insecure (HTTP) username/password login is explicitly permitted. */
    public static boolean isInsecureLoginAllowed() {
        return Boolean.getBoolean(ALLOW_INSECURE_LOGIN_PROP);
    }
}
