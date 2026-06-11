/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  javax.inject.Inject
 *  javax.ws.rs.core.Response
 *  javax.ws.rs.ext.ExceptionMapper
 *  javax.ws.rs.ext.Provider
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class JimThrowableExceptionMapper
implements ExceptionMapper<Throwable> {
    private static final Logger log = LoggerFactory.getLogger(JimThrowableExceptionMapper.class);
    private final JiraAuthenticationContext authenticationContext;

    @Inject
    public JimThrowableExceptionMapper(JiraAuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    public Response toResponse(Throwable exception) {
        if (exception instanceof JimMessengerException) {
            JimMessengerException jimException = (JimMessengerException)exception;
            return JimRestResponses.errorJson(jimException.getStatusCode(), "request_failed", jimException.getMessage());
        }
        String userKey = this.resolveUserKey();
        if (exception instanceof Exception) {
            return JimRestResponses.internalError(log, "rest/jim", userKey, (Exception)exception, "internal_error", "An internal error occurred while processing the request.");
        }
        log.error("endpoint=rest/jim stage=unhandled outcome=error userKey={} exceptionClass={} message={}", new Object[]{userKey, exception.getClass().getName(), exception.getMessage(), exception});
        return JimRestResponses.errorJson(500, "internal_error", "An internal error occurred while processing the request.");
    }

    private String resolveUserKey() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        return user != null ? user.getKey() : "anonymous";
    }
}

