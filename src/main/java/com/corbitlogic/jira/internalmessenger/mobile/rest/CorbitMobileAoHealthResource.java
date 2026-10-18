package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mobile BFF Active Objects reachability probe. Mounted under
 * {@code /rest/corbit-mobile/1.0/ao-health}. Reuses the existing CorbitChat AO
 * schema (no new tables in Sprint 00).
 */
@Path("/ao-health")
@Produces({"application/json"})
public class CorbitMobileAoHealthResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileAoHealthResource.class);

    private final ActiveObjects activeObjects;

    @Inject
    public CorbitMobileAoHealthResource(ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    @GET
    public Response aoHealth() {
        try {
            this.activeObjects.find(JimConversation.class, Query.select().limit(1));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("module", "corbit-mobile");
            body.put("activeObjects", "initialized");
            return JimRestResponses.okJson(body);
        } catch (Throwable ex) {
            log.error("endpoint=GET /rest/corbit-mobile/1.0/ao-health outcome=error message={}",
                    ex.getMessage(), ex);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", false);
            body.put("error", "active_objects_error");
            body.put("message", "Active Objects is not initialized.");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(body)
                    .build();
        }
    }
}
