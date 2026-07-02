package com.corbitlogic.jira.internalmessenger.mobile;

import com.atlassian.jira.user.ApplicationUser;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Centralizes the mobile avatar proxy paths (Sprint 04C).
 *
 * <p>Jira builds avatar URLs from its <em>configured base URL</em>, which on our
 * servers points at hosts/schemes the mobile client cannot reach (e.g.
 * {@code https://<raw-ip>} with no TLS listener, an internal-only host, or an
 * external gravatar URL). Instead of leaking those, every mobile payload returns
 * a <b>relative</b> path to the BFF avatar proxy
 * ({@link com.corbitlogic.jira.internalmessenger.mobile.rest.CorbitMobileAvatarResource}).
 * The mobile app resolves it against the base URL it is actually connected to and
 * sends its normal auth header — so avatars work with both PAT and mobile-session
 * auth, over the same connection as every other call, with no token in the URL.</p>
 */
public final class JimMobileAvatars {

    private static final String USER_PREFIX = "/rest/corbit-mobile/1.0/avatar/user/";
    private static final String PROJECT_PREFIX = "/rest/corbit-mobile/1.0/avatar/project/";

    private JimMobileAvatars() {
    }

    public static String userPath(ApplicationUser user) {
        return user == null ? null : userPath(user.getKey());
    }

    public static String userPath(String userKey) {
        if (userKey == null || userKey.trim().isEmpty()) {
            return null;
        }
        return USER_PREFIX + encode(userKey.trim());
    }

    public static String projectPath(String projectKey) {
        if (projectKey == null || projectKey.trim().isEmpty()) {
            return null;
        }
        return PROJECT_PREFIX + encode(projectKey.trim());
    }

    private static String encode(String segment) {
        try {
            // Encode as a path segment: URLEncoder is form-encoding, so restore
            // spaces to %20 (path segments never use '+').
            return URLEncoder.encode(segment, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            return segment;
        }
    }
}
