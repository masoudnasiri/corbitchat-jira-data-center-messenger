package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.corbitlogic.jira.internalmessenger.mobile.MobilePreferences;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimMobilePreferenceService;
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
 * Per-user preferences for CorbitChat Mobile. Mounted under
 * {@code /rest/corbit-mobile/1.0/preferences}. Derived solely from the
 * authenticated user; anonymous requests get 401.
 */
@Path("/preferences")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobilePreferencesResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobilePreferencesResource.class);

    private final JiraAuthenticationContext authenticationContext;
    private final JimMobilePreferenceService preferenceService;

    @Inject
    public CorbitMobilePreferencesResource(JiraAuthenticationContext authenticationContext,
                                           JimMobilePreferenceService preferenceService) {
        this.authenticationContext = authenticationContext;
        this.preferenceService = preferenceService;
    }

    @GET
    public Response get() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                    "You must be signed in to use CorbitChat Mobile.");
        }
        try {
            MobilePreferences prefs = this.preferenceService.getForUser(user.getKey());
            Map<String, Object> body = prefs.toMap();
            body.put("ok", true);
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/preferences", user.getKey(), ex,
                    "PREFERENCES_ERROR", "Could not load your preferences.");
        }
    }

    @PUT
    @Consumes({"application/json"})
    public Response update(Map<String, Object> requested) {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                    "You must be signed in to use CorbitChat Mobile.");
        }
        try {
            MobilePreferences patch = new MobilePreferences(
                    str(requested, "language"),
                    str(requested, "theme"),
                    str(requested, "calendar"),
                    str(requested, "notificationLevel"),
                    str(requested, "quietHours"),
                    0L);
            MobilePreferences saved = this.preferenceService.saveForUser(user.getKey(), patch);
            Map<String, Object> body = saved.toMap();
            body.put("ok", true);
            return JimRestResponses.okJson(body);
        } catch (IllegalArgumentException ex) {
            return JimRestResponses.errorJson(400, "INVALID_PREFERENCES", ex.getMessage());
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "PUT /rest/corbit-mobile/1.0/preferences", user.getKey(), ex,
                    "PREFERENCES_ERROR", "Could not save your preferences.");
        }
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }
}
