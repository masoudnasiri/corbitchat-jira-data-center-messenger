/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.permission.GlobalPermissionKey
 *  com.atlassian.jira.security.GlobalPermissionManager
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  javax.inject.Inject
 *  javax.ws.rs.GET
 *  javax.ws.rs.Path
 *  javax.ws.rs.Produces
 *  javax.ws.rs.core.Response
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimAoSchemaDiagnostics;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(value="/dev/ao-schema")
@Produces(value={"application/json"})
public class JimDevAoSchemaResource {
    private static final Logger log = LoggerFactory.getLogger(JimDevAoSchemaResource.class);
    private final JiraAuthenticationContext authenticationContext;
    private final GlobalPermissionManager globalPermissionManager;
    private final JimAoSchemaDiagnostics aoSchemaDiagnostics;

    @Inject
    public JimDevAoSchemaResource(JiraAuthenticationContext authenticationContext, GlobalPermissionManager globalPermissionManager, JimAoSchemaDiagnostics aoSchemaDiagnostics) {
        this.authenticationContext = authenticationContext;
        this.globalPermissionManager = globalPermissionManager;
        this.aoSchemaDiagnostics = aoSchemaDiagnostics;
    }

    @GET
    public Response aoSchema() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        if (!this.globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, user)) {
            return JimRestResponses.errorJson(403, "forbidden", "Jira administrator permission is required");
        }
        try {
            Map<String, Object> body = this.aoSchemaDiagnostics.buildSchemaReport();
            return JimRestResponses.okJson(body);
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/dev/ao-schema", user.getKey(), ex, "internal_error", "An internal error occurred while building the AO schema report.");
        }
    }
}

