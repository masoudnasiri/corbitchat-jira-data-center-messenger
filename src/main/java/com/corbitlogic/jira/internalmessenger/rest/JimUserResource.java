/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  javax.inject.Inject
 *  javax.ws.rs.Consumes
 *  javax.ws.rs.GET
 *  javax.ws.rs.Path
 *  javax.ws.rs.Produces
 *  javax.ws.rs.QueryParam
 *  javax.ws.rs.core.Response
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.dto.UserSearchResultDto;
import com.corbitlogic.jira.internalmessenger.rest.JimRestJsonMapper;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimLicenseService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimUserSearchService;
import java.util.LinkedHashMap;
import java.util.List;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(value="/users")
@Consumes(value={"application/json"})
@Produces(value={"application/json"})
public class JimUserResource {
    private static final Logger log = LoggerFactory.getLogger(JimUserResource.class);
    private final JiraAuthenticationContext authenticationContext;
    private final JimPermissionService permissionService;
    private final JimUserSearchService userSearchService;
    private final JimRestJsonMapper restJsonMapper;
    private final JimLicenseService licenseService;

    @Inject
    public JimUserResource(JiraAuthenticationContext authenticationContext, JimPermissionService permissionService, JimUserSearchService userSearchService, JimRestJsonMapper restJsonMapper, JimLicenseService licenseService) {
        this.authenticationContext = authenticationContext;
        this.permissionService = permissionService;
        this.userSearchService = userSearchService;
        this.restJsonMapper = restJsonMapper;
        this.licenseService = licenseService;
    }

    @GET
    @Path(value="/search")
    public Response searchUsers(@QueryParam(value="query") String query) {
        String userKey = this.resolveCurrentUserKey();
        if (userKey == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        if (query == null) {
            return JimRestResponses.errorJson(400, "bad_request", "query is required");
        }
        if (!this.licenseService.canUseMessaging()) {
            return JimRestResponses.licenseBlocked();
        }
        try {
            this.permissionService.requireAuthenticatedUserKey();
            List<UserSearchResultDto> users = this.userSearchService.searchActiveUsers(query);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("users", this.restJsonMapper.toUserSearchMaps(users));
            return JimRestResponses.okJson(body);
        }
        catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/users/search", userKey, ex, "internal_error", "An internal error occurred while searching users.");
        }
    }

    private String resolveCurrentUserKey() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        return user != null ? user.getKey() : null;
    }
}

