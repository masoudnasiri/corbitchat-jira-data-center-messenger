/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  javax.inject.Inject
 *  javax.ws.rs.GET
 *  javax.ws.rs.Path
 *  javax.ws.rs.Produces
 *  javax.ws.rs.core.MediaType
 *  javax.ws.rs.core.Response
 *  javax.ws.rs.core.Response$Status
 *  net.java.ao.Query
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import java.util.LinkedHashMap;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(value="/ao-health")
@Produces(value={"application/json"})
public class JimAoHealthResource {
    private static final Logger log = LoggerFactory.getLogger(JimAoHealthResource.class);
    private final ActiveObjects activeObjects;

    @Inject
    public JimAoHealthResource(ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    @GET
    public Response aoHealth() {
        try {
            this.activeObjects.find(JimConversation.class, Query.select().limit(1));
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("ok", true);
            body.put("activeObjects", "initialized");
            return JimRestResponses.okJson(body);
        }
        catch (Throwable ex) {
            log.error("endpoint=GET /rest/jim/1.0/ao-health outcome=error message={}", (Object)ex.getMessage(), (Object)ex);
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("ok", false);
            body.put("error", "active_objects_error");
            body.put("message", "Active Objects is not initialized.");
            return Response.status((Response.Status)Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON_TYPE).entity(body).build();
        }
    }
}

