package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server branding for the mobile app (branding polish, additive). Mounted
 * under {@code /rest/corbit-mobile/1.0/branding}.
 *
 * <p>Anonymous on purpose: the mobile login screen shows the organization's
 * branding BEFORE the user authenticates. Exposes only the admin-configured
 * branding title and logo URL (the same values the web chat header uses —
 * typically a data: URL uploaded in the CorbitChat admin console). Nothing
 * user- or session-specific is returned.</p>
 */
@Path("/branding")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobileBrandingResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileBrandingResource.class);

    private final JimAdminSettingsService adminSettingsService;

    @Inject
    public CorbitMobileBrandingResource(JimAdminSettingsService adminSettingsService) {
        this.adminSettingsService = adminSettingsService;
    }

    @GET
    public Response branding() {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("title", this.adminSettingsService.getBrandingTitle());
            body.put("logoUrl", this.adminSettingsService.getBrandingLogoUrl());
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/branding",
                    null, ex, "BRANDING_ERROR", "Could not load branding.");
        }
    }
}
