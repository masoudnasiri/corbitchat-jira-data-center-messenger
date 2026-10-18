package com.corbitlogic.jira.internalmessenger.mobile;

import java.util.Locale;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

/**
 * Shared constants/helpers for CorbitChat Mobile session handling (Sprint 01B).
 *
 * <p>The plugin session token travels in a dedicated header rather than
 * {@code Authorization}, because Jira rejects unknown {@code Bearer} tokens with
 * 401 before the request reaches the plugin. Keeping our token out of
 * {@code Authorization} also means PAT-based requests are completely untouched.</p>
 *
 * <p>Sprint 11.5 (PWA-01) adds a browser-friendly path: the same session token
 * can be delivered as an {@code HttpOnly; Secure; SameSite=Lax} cookie so the
 * Flutter Web PWA never has to hold the token in JavaScript-readable storage.
 * The header path (native Android/iOS) is unchanged and always wins.</p>
 */
public final class MobileSessionSupport {

    /** Header carrying the raw (client-held) mobile session token. */
    public static final String SESSION_HEADER = "X-CorbitChat-Session";

    /**
     * HttpOnly cookie carrying the raw session token for the Flutter Web PWA.
     * Distinct from Jira's own web session cookie so the two never collide.
     */
    public static final String PWA_SESSION_COOKIE = "corbitchat-pwa-session";

    /**
     * Custom marker header the PWA sends on every request. For cookie-based
     * auth it is REQUIRED on state-changing requests: a cross-site page cannot
     * set a custom header on a "simple" request, and any attempt to add it
     * triggers a CORS preflight that this same-origin-only API does not answer.
     * That, combined with {@code SameSite=Lax}, is the CSRF defense for the
     * cookie session (GET/HEAD are exempt because they are not state-changing).
     */
    public static final String PWA_MARKER_HEADER = "X-CorbitChat-PWA";

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

    /** The raw session token from the PWA cookie, or null when absent/blank. */
    public static String extractCookieToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie != null && PWA_SESSION_COOKIE.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null) {
                    String trimmed = value.trim();
                    if (!trimmed.isEmpty()) {
                        return trimmed;
                    }
                }
            }
        }
        return null;
    }

    /** True for HTTP methods that mutate state (everything except GET/HEAD/OPTIONS). */
    public static boolean isStateChangingMethod(String method) {
        if (method == null) {
            return true;
        }
        switch (method.toUpperCase(Locale.ROOT)) {
            case "GET":
            case "HEAD":
            case "OPTIONS":
                return false;
            default:
                return true;
        }
    }

    /** True when the request carries the PWA marker header (any non-null value). */
    public static boolean hasPwaMarker(HttpServletRequest request) {
        return request != null && request.getHeader(PWA_MARKER_HEADER) != null;
    }

    /**
     * Builds the {@code Set-Cookie} value that stores the raw session token for
     * the PWA. {@code HttpOnly} keeps it out of JavaScript; {@code SameSite=Lax}
     * blocks it from cross-site subrequests; {@code Secure} is added when the
     * request arrived over TLS.
     */
    public static String buildSessionSetCookie(String rawToken, long maxAgeSeconds, boolean secure) {
        StringBuilder sb = new StringBuilder();
        sb.append(PWA_SESSION_COOKIE).append('=').append(rawToken == null ? "" : rawToken);
        sb.append("; Path=/");
        sb.append("; Max-Age=").append(Math.max(0L, maxAgeSeconds));
        sb.append("; HttpOnly");
        sb.append("; SameSite=Lax");
        if (secure) {
            sb.append("; Secure");
        }
        return sb.toString();
    }

    /** Builds the {@code Set-Cookie} value that immediately expires the PWA cookie. */
    public static String buildClearSetCookie(boolean secure) {
        StringBuilder sb = new StringBuilder();
        sb.append(PWA_SESSION_COOKIE).append('=');
        sb.append("; Path=/");
        sb.append("; Max-Age=0");
        sb.append("; HttpOnly");
        sb.append("; SameSite=Lax");
        if (secure) {
            sb.append("; Secure");
        }
        return sb.toString();
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
