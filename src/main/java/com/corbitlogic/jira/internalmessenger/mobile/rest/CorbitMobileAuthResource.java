package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.crowd.exception.FailedAuthenticationException;
import com.atlassian.crowd.embedded.api.CrowdService;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.corbitlogic.jira.internalmessenger.mobile.MobileSessionSupport;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimChatAuditService;
import com.corbitlogic.jira.internalmessenger.service.JimMobileSessionService;
import com.corbitlogic.jira.internalmessenger.service.JimReviewerService;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mobile authentication for CorbitChat Mobile (Sprint 01B). Mounted under
 * {@code /rest/corbit-mobile/1.0/auth}.
 *
 * <ul>
 *   <li>{@code POST /auth/login} — validate username/password against Jira
 *       ({@code CrowdService}) and issue a plugin session token. Requires a
 *       secure transport.</li>
 *   <li>{@code POST /auth/logout} — revoke the presented session token.</li>
 *   <li>{@code GET /auth/session} — report the current session's user (resolved
 *       by the session filter or PAT).</li>
 * </ul>
 *
 * <p>Never logs passwords, tokens, or Authorization/cookie headers.</p>
 */
@Path("/auth")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobileAuthResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileAuthResource.class);

    private final CrowdService crowdService;
    private final UserManager userManager;
    private final JiraAuthenticationContext authenticationContext;
    private final JimMobileSessionService sessionService;
    private final JimReviewerService reviewerService;
    private final JimChatAuditService auditService;

    @Inject
    public CorbitMobileAuthResource(CrowdService crowdService,
                                    UserManager userManager,
                                    JiraAuthenticationContext authenticationContext,
                                    JimMobileSessionService sessionService,
                                    JimReviewerService reviewerService,
                                    JimChatAuditService auditService) {
        this.crowdService = crowdService;
        this.userManager = userManager;
        this.authenticationContext = authenticationContext;
        this.sessionService = sessionService;
        this.reviewerService = reviewerService;
        this.auditService = auditService;
    }

    @POST
    @Path("/login")
    @Consumes({"application/json"})
    public Response login(Map<String, Object> body, @Context HttpServletRequest request,
                          @Context HttpServletResponse httpResponse) {
        final String username = str(body, "username");
        final String password = str(body, "password");
        final String device = str(body, "device");

        if (isBlank(username) || isBlank(password)) {
            return JimRestResponses.errorJson(400, "MISSING_CREDENTIALS",
                    "Username and password are required.");
        }

        if (!MobileSessionSupport.isSecureRequest(request)
                && !MobileSessionSupport.isInsecureLoginAllowed()) {
            return JimRestResponses.errorJson(400, "INSECURE_TRANSPORT",
                    "Username/password login requires HTTPS.");
        }

        // Hidden reviewer (Sprint 12): a plugin-owned account, NOT a Jira user.
        // Authenticated only here (app/PWA), never against Jira directories, and
        // issued a reviewer-flagged session whose sentinel key resolves to no
        // Jira user (so it works only on read-only /reviewer endpoints).
        if (this.reviewerService.isReviewerUsername(username)) {
            this.reviewerService.ensureSeeded();
            if (!this.reviewerService.authenticate(password)) {
                return JimRestResponses.errorJson(401, "INVALID_CREDENTIALS",
                        "Incorrect username or password.");
            }
            try {
                JimMobileSessionService.IssuedSession issued =
                        this.sessionService.create(JimReviewerService.SESSION_USER_KEY, device);
                Map<String, Object> userMap = new LinkedHashMap<>();
                userMap.put("key", JimReviewerService.SESSION_USER_KEY);
                userMap.put("name", JimReviewerService.USERNAME);
                userMap.put("displayName", "Chat Reviewer");
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", true);
                result.put("token", issued.getRawToken());
                result.put("expiresAt", issued.getExpiresAt());
                result.put("reviewer", true);
                result.put("user", userMap);
                if (httpResponse != null) {
                    boolean secure = MobileSessionSupport.isSecureRequest(request);
                    long maxAgeSeconds =
                            (issued.getExpiresAt() - System.currentTimeMillis()) / 1000L;
                    httpResponse.addHeader("Set-Cookie", MobileSessionSupport.buildSessionSetCookie(
                            issued.getRawToken(), maxAgeSeconds, secure));
                    httpResponse.setHeader("Cache-Control", "no-store");
                }
                this.auditService.record(JimReviewerService.SESSION_USER_KEY, "reviewer.login",
                        null, null, null, "device=" + (device == null ? "" : device));
                return JimRestResponses.okJson(result);
            } catch (Exception ex) {
                return JimRestResponses.internalError(log,
                        "POST /rest/corbit-mobile/1.0/auth/login", "(reviewer)", ex,
                        "LOGIN_ERROR", "Could not complete sign-in. Please try again.");
            }
        }

        // Validate credentials against Jira's user directories. Never log them.
        try {
            this.crowdService.authenticate(username, password);
        } catch (FailedAuthenticationException ex) {
            // Covers wrong password, expired credentials, and inactive accounts.
            log.info("endpoint=POST /rest/corbit-mobile/1.0/auth/login outcome=denied reason={}",
                    ex.getClass().getSimpleName());
            return JimRestResponses.errorJson(401, "INVALID_CREDENTIALS",
                    "Incorrect username or password.");
        } catch (Exception ex) {
            // Do not log the username here — it is user-supplied credential input.
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/auth/login", "(login)", ex,
                    "LOGIN_ERROR", "Could not complete sign-in. Please try again.");
        }

        final ApplicationUser user = this.userManager.getUserByName(username);
        if (user == null || !user.isActive()) {
            return JimRestResponses.errorJson(401, "INVALID_CREDENTIALS",
                    "Incorrect username or password.");
        }

        try {
            JimMobileSessionService.IssuedSession issued =
                    this.sessionService.create(user.getKey(), device);

            Map<String, Object> userMap = new LinkedHashMap<>();
            userMap.put("key", user.getKey());
            userMap.put("name", user.getName());
            userMap.put("displayName", user.getDisplayName());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("token", issued.getRawToken());
            result.put("expiresAt", issued.getExpiresAt());
            result.put("user", userMap);

            // PWA-01: also set the token as an HttpOnly cookie so the Flutter Web
            // client never has to store it in JavaScript-readable storage. Native
            // clients simply ignore the Set-Cookie and keep using the header.
            if (httpResponse != null) {
                boolean secure = MobileSessionSupport.isSecureRequest(request);
                long maxAgeSeconds =
                        (issued.getExpiresAt() - System.currentTimeMillis()) / 1000L;
                httpResponse.addHeader("Set-Cookie", MobileSessionSupport.buildSessionSetCookie(
                        issued.getRawToken(), maxAgeSeconds, secure));
                httpResponse.setHeader("Cache-Control", "no-store");
            }

            log.info("endpoint=POST /rest/corbit-mobile/1.0/auth/login outcome=success userKey={}",
                    user.getKey());
            return JimRestResponses.okJson(result);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/auth/login", user.getKey(), ex,
                    "LOGIN_ERROR", "Could not complete sign-in. Please try again.");
        }
    }

    @POST
    @Path("/logout")
    public Response logout(@Context HttpServletRequest request,
                           @Context HttpServletResponse httpResponse) {
        // Read the raw token from the header or, for the PWA, the cookie so logout
        // works even for an already-expired session; revoke it server-side
        // (idempotent).
        String rawToken = MobileSessionSupport.extractSessionToken(request);
        if (rawToken == null) {
            rawToken = MobileSessionSupport.extractCookieToken(request);
        }
        boolean revoked = false;
        try {
            if (rawToken != null) {
                revoked = this.sessionService.revoke(rawToken);
            }
        } catch (Exception ex) {
            log.warn("endpoint=POST /rest/corbit-mobile/1.0/auth/logout outcome=error message={}",
                    ex.getMessage());
        }
        // Always clear the PWA cookie so the browser drops it on logout.
        if (httpResponse != null) {
            httpResponse.addHeader("Set-Cookie", MobileSessionSupport.buildClearSetCookie(
                    MobileSessionSupport.isSecureRequest(request)));
            httpResponse.setHeader("Cache-Control", "no-store");
        }
        return JimRestResponses.okJson(withRevoked(revoked));
    }

    /**
     * Change the current normal user's password (Sprint 12). Jira user
     * passwords are owned by Jira's own directory (which may be LDAP/SSO), so
     * to avoid corrupting external directories this endpoint verifies the
     * current password and returns a clear, safe "managed in Jira" response
     * rather than attempting a directory write. The hidden reviewer changes its
     * password through the dedicated {@code /reviewer/change-password} endpoint.
     */
    @POST
    @Path("/change-password")
    @Consumes({"application/json"})
    public Response changePassword(Map<String, Object> body) {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                    "You must be signed in.");
        }
        return JimRestResponses.errorJson(400, "unsupported",
                "Your account password is managed in Jira. Please change it from your Jira profile / your organization's login system.");
    }

    @GET
    @Path("/session")
    public Response session() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                    "Session is missing, expired, or revoked.");
        }
        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("key", user.getKey());
        userMap.put("name", user.getName());
        userMap.put("displayName", user.getDisplayName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("user", userMap);
        return JimRestResponses.okJson(result);
    }

    private static Map<String, Object> withRevoked(boolean revoked) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("revoked", revoked);
        return result;
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
