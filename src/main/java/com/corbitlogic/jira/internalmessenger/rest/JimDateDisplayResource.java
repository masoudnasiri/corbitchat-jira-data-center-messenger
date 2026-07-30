package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

/**
 * Lightweight, read-only endpoint that tells the client whether the global
 * "Jira-wide Jalali date display" mode is enabled. Readable by any authenticated
 * user (the admin toggle itself is changed only via the sysadmin-gated
 * {@code PUT /rest/jim/1.0/admin/settings}). Returns a small, stable JSON shape
 * consumed by the global {@code jim-jira-dates.js} display transformer.
 *
 * <p>This is display metadata only: it exposes a boolean + calendar mode and a
 * transformer version for cache reconciliation. It never returns or alters any
 * stored Jira date value.
 */
@Path("/date-display")
@Produces({"application/json"})
public class JimDateDisplayResource {

    /** Bump when the client transformer's behaviour changes, for cache-busting. */
    private static final int DATE_DISPLAY_MODE_VERSION = 2;

    private final JiraAuthenticationContext authenticationContext;
    private final JimAdminSettingsService adminSettingsService;

    @Inject
    public JimDateDisplayResource(JiraAuthenticationContext authenticationContext,
                                  JimAdminSettingsService adminSettingsService) {
        this.authenticationContext = authenticationContext;
        this.adminSettingsService = adminSettingsService;
    }

    @GET
    public Response get() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        boolean enabled = this.adminSettingsService.isJiraWideJalaliEnabled();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("ok", Boolean.TRUE);
        body.put("jiraWideDateDisplayEnabled", enabled);
        body.put("jiraWideCalendar", enabled ? "jalali" : "gregorian");
        body.put("dateDisplayModeVersion", DATE_DISPLAY_MODE_VERSION);
        return JimRestResponses.okJson(body);
    }
}
