package com.corbitlogic.jira.internalmessenger.mobile;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.corbitlogic.jira.internalmessenger.service.JimMobileSessionService;
import java.io.IOException;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the real Jira user for mobile-session requests to
 * {@code /rest/corbit-mobile/1.0/*} (Sprint 01B).
 *
 * <p>When a request carries a valid {@link MobileSessionSupport#SESSION_HEADER}
 * token and is not already authenticated (PAT/cookie), this filter looks up the
 * owning user and impersonates them for the duration of the request via
 * {@link JiraAuthenticationContext#setLoggedInUser(ApplicationUser)}. That lets
 * every existing resource keep using {@code getLoggedInUser()} and Jira's
 * permission checks unchanged. The previous user is always restored in a
 * {@code finally} block so no thread-local state leaks.</p>
 *
 * <p>PAT requests already have a logged-in user by the time this filter runs, so
 * they are passed through untouched. Requests without a session token (e.g.
 * {@code /auth/login}) also pass through as anonymous.</p>
 */
public class CorbitMobileSessionFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileSessionFilter.class);

    private final JiraAuthenticationContext authenticationContext;
    private final UserManager userManager;
    private final JimMobileSessionService sessionService;

    @Inject
    public CorbitMobileSessionFilter(JiraAuthenticationContext authenticationContext,
                                     UserManager userManager,
                                     JimMobileSessionService sessionService) {
        this.authenticationContext = authenticationContext;
        this.userManager = userManager;
        this.sessionService = sessionService;
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // Sensitive-by-default: mobile BFF payloads (profile, issues, chat, ...)
        // must never be cached by browsers or intermediaries.
        if (response instanceof HttpServletResponse) {
            ((HttpServletResponse) response).setHeader("Cache-Control", "no-store");
        }

        // Do not override an existing authentication (PAT / Jira cookie).
        if (this.authenticationContext.getLoggedInUser() != null) {
            chain.doFilter(request, response);
            return;
        }

        // Native clients send the token in a header. The Flutter Web PWA has no
        // JavaScript-readable token; the browser sends it automatically as an
        // HttpOnly cookie. Header wins; cookie is the fallback.
        String rawToken = MobileSessionSupport.extractSessionToken(httpRequest);
        boolean viaCookie = false;
        if (rawToken == null) {
            rawToken = MobileSessionSupport.extractCookieToken(httpRequest);
            viaCookie = rawToken != null;
        }
        if (rawToken == null) {
            chain.doFilter(request, response);
            return;
        }

        // CSRF defense for the cookie session: a state-changing request must also
        // carry the custom PWA marker header. A cross-site attacker cannot add it
        // without a CORS preflight this API never answers, so a forged cookie
        // request stays anonymous (resources return 401/403). Header auth and
        // safe methods are exempt.
        if (viaCookie
                && MobileSessionSupport.isStateChangingMethod(httpRequest.getMethod())
                && !MobileSessionSupport.hasPwaMarker(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String userKey = safeValidate(rawToken);
        if (userKey == null) {
            // Invalid/expired/revoked token: stay anonymous. Resources return 401.
            chain.doFilter(request, response);
            return;
        }

        ApplicationUser user = this.userManager.getUserByKey(userKey);
        if (user == null || !user.isActive()) {
            chain.doFilter(request, response);
            return;
        }

        ApplicationUser previous = this.authenticationContext.getLoggedInUser();
        try {
            this.authenticationContext.setLoggedInUser(user);
            chain.doFilter(request, response);
        } finally {
            this.authenticationContext.setLoggedInUser(previous);
        }
    }

    @Override
    public void destroy() {
    }

    private String safeValidate(String rawToken) {
        try {
            return this.sessionService.validate(rawToken);
        } catch (Exception ex) {
            log.warn("mobile session validation error: {}", ex.getMessage());
            return null;
        }
    }
}
