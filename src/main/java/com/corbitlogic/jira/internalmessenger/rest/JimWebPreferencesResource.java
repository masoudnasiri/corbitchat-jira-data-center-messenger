package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimWebLocaleService;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-user Jira WEB chat localization preferences (Persian UI / Jalali dates).
 * Any authenticated user manages ONLY their own preference. Web-only; does not
 * affect mobile, Jira locale, or stored dates.
 */
@Path("/web-preferences")
@Produces({"application/json"})
public class JimWebPreferencesResource {
    private static final Logger log = LoggerFactory.getLogger(JimWebPreferencesResource.class);

    private final JiraAuthenticationContext authenticationContext;
    private final JimWebLocaleService webLocaleService;

    @Inject
    public JimWebPreferencesResource(JiraAuthenticationContext authenticationContext,
                                     JimWebLocaleService webLocaleService) {
        this.authenticationContext = authenticationContext;
        this.webLocaleService = webLocaleService;
    }

    @GET
    public Response get() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        return JimRestResponses.okJson(this.webLocaleService.bootstrap(user.getKey()));
    }

    @PUT
    @Consumes({"application/json"})
    public Response put(Map<String, Object> body) {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        String language = body != null && body.get("language") != null ? String.valueOf(body.get("language")) : null;
        String calendar = body != null && body.get("calendar") != null ? String.valueOf(body.get("calendar")) : null;
        try {
            this.webLocaleService.saveUserPreferences(user.getKey(), language, calendar);
            return JimRestResponses.okJson(this.webLocaleService.bootstrap(user.getKey()));
        } catch (JimMessengerException ex) {
            return JimRestResponses.errorJson(ex.getStatusCode(), "request_failed", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log, "PUT /rest/jim/1.0/web-preferences",
                    user.getKey(), ex, "internal_error", "Could not save preferences.");
        }
    }
}
