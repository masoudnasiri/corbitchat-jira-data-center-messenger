package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

/**
 * Mobile BFF liveness probe. Mounted under {@code /rest/corbit-mobile/1.0/health}.
 *
 * <p>Additive to the plugin: the mobile client (CorbitChat Mobile, see
 * {@code docs/mobile-roadmap/}) uses a dedicated REST module so its endpoints
 * evolve independently from the web surface at {@code /rest/jim/1.0/*}.</p>
 */
@Path("/health")
@Produces({"application/json"})
public class CorbitMobileHealthResource {

    @GET
    public Response health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("module", "corbit-mobile");
        body.put("apiVersion", "1.0");
        body.put("status", "running");
        return JimRestResponses.okJson(body);
    }
}
