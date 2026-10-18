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

        // s12e-fix2: the hidden reviewer (pluginuser) is now a REAL Jira user.
        // It authenticates through the SAME Jira/Crowd path as everyone else
        // below (no plugin-owned password, no sentinel session key), so its
        // session resolves to a real ApplicationUser and bootstrap never 401s.
        // We only flag reviewer=true in the response so the client opens the
        // read-only reviewer shell instead of the normal app shell.

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

            boolean isReviewer = this.reviewerService.isReviewerUsername(user.getName());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("token", issued.getRawToken());
            result.put("expiresAt", issued.getExpiresAt());
            result.put("user", userMap);
            // s12e-fix2: real Jira reviewer user → route client to read-only shell.
            if (isReviewer) {
                result.put("reviewer", true);
                this.auditService.record(user.getKey(), "reviewer.login",
                        null, null, null, "device=" + (device == null ? "" : device));
            }

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
        String current = str(body, "currentPassword");
        String next = str(body, "newPassword");
        if (isBlank(current) || isBlank(next)) {
            return JimRestResponses.errorJson(400, "bad_request",
                    "Current and new password are required.");
        }
        if (next.length() < 8) {
            return JimRestResponses.errorJson(400, "weak_password",
                    "New password must be at least 8 characters.");
        }
        // External/read-only directories (LDAP/SSO/Crowd) don't allow writes.
        if (!this.userManager.canUpdateUserPassword(user)) {
            return JimRestResponses.errorJson(400, "managed_externally",
                    "Password change is managed by your organization directory.");
        }
        // Verify the current password before changing it. Never logged.
        try {
            this.crowdService.authenticate(user.getName(), current);
        } catch (FailedAuthenticationException ex) {
            return JimRestResponses.errorJson(401, "invalid_current_password",
                    "Current password is incorrect.");
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/auth/change-password", user.getKey(), ex,
                    "change_error", "Could not change your password. Please try again.");
        }
        try {
            com.atlassian.crowd.embedded.api.User crowdUser =
                    this.crowdService.getUser(user.getName());
            if (crowdUser == null) {
                return JimRestResponses.errorJson(400, "managed_externally",
                        "Password change is managed by your organization directory.");
            }
            this.crowdService.updateUserCredential(crowdUser, next);
            log.info("endpoint=POST /rest/corbit-mobile/1.0/auth/change-password outcome=success userKey={}",
                    user.getKey());
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("ok", true);
            return JimRestResponses.okJson(ok);
        } catch (com.atlassian.crowd.exception.OperationNotPermittedException ex) {
            return JimRestResponses.errorJson(400, "managed_externally",
                    "Password change is managed by your organization directory.");
        } catch (com.atlassian.crowd.exception.InvalidCredentialException ex) {
            return JimRestResponses.errorJson(400, "weak_password",
                    "The new password does not meet the password policy.");
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "POST /rest/corbit-mobile/1.0/auth/change-password", user.getKey(), ex,
                    "change_error", "Could not change your password. Please try again.");
        }
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
