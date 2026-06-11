package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.service.JimLicenseService;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal license status for chat users. Regular users only learn whether the
 * app is licensed (true/false); full details are sysadmin-only via /admin/license.
 */
@Path(value="/license")
@Produces(value={"application/json"})
public class JimLicenseResource {
    private static final Logger log = LoggerFactory.getLogger(JimLicenseResource.class);
    private final JiraAuthenticationContext authenticationContext;
    private final JimLicenseService licenseService;

    @Inject
    public JimLicenseResource(JiraAuthenticationContext authenticationContext, JimLicenseService licenseService) {
        this.authenticationContext = authenticationContext;
        this.licenseService = licenseService;
    }

    @GET
    @Path(value="/status")
    public Response getStatus() {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        try {
            return JimRestResponses.okJson(this.licenseService.getStatus().toMinimalMap());
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/license/status", viewer.getKey(), ex, "internal_error", "Unable to read license status.");
        }
    }
}
