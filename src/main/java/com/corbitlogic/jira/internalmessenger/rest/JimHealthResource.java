/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.inject.Inject
 *  javax.ws.rs.GET
 *  javax.ws.rs.Path
 *  javax.ws.rs.Produces
 *  javax.ws.rs.core.Response
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.corbitlogic.jira.internalmessenger.bootstrap.JimPluginBootstrap;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import java.util.LinkedHashMap;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Path(value="/health")
@Produces(value={"application/json"})
public class JimHealthResource {
    @Inject
    public JimHealthResource(JimPluginBootstrap pluginBootstrap) {
    }

    @GET
    public Response health() {
        LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("ok", true);
        body.put("plugin", "CorbitChat");
        body.put("status", "running");
        return JimRestResponses.okJson(body);
    }
}

