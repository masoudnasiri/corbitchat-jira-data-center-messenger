package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.service.JimWorkspaceAgentService;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Admin REST for the ChatGPT Workspace Agent integration. System Administrator
 * only. The access token is write-only: it is set via {@code POST /token} and is
 * NEVER returned by any endpoint (status exposes {@code tokenConfigured} only).
 * {@code /rest/jim/1.0/workspace-agent/*}
 */
@Path("/workspace-agent")
@Produces({"application/json"})
public class JimWorkspaceAgentResource {

    private static final Logger log = LoggerFactory.getLogger(JimWorkspaceAgentResource.class);

    private final JiraAuthenticationContext authenticationContext;
    private final GlobalPermissionManager globalPermissionManager;
    private final JimWorkspaceAgentService agentService;

    @Inject
    public JimWorkspaceAgentResource(JiraAuthenticationContext authenticationContext,
                                     GlobalPermissionManager globalPermissionManager,
                                     JimWorkspaceAgentService agentService) {
        this.authenticationContext = authenticationContext;
        this.globalPermissionManager = globalPermissionManager;
        this.agentService = agentService;
    }

    @GET
    @Path("/status")
    public Response status() {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        return JimRestResponses.okJson(this.agentService.getStatus());
    }

    @GET
    @Path("/delivery-report")
    public Response deliveryReport(@QueryParam("limit") @DefaultValue("50") int limit) {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        return JimRestResponses.okJson(this.agentService.deliveryReport(limit));
    }

    @PUT
    @Path("/config")
    @Consumes({"application/json"})
    public Response config(Map<String, Object> body) {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        // Never accept the token through this endpoint (write-only via /token).
        if (body != null) {
            body.remove("token");
        }
        this.agentService.configure(body);
        return JimRestResponses.okJson(this.agentService.getStatus());
    }

    @POST
    @Path("/token")
    @Consumes({"application/json"})
    public Response token(Map<String, Object> body) {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        String token = body == null ? null : (body.get("token") == null ? null : String.valueOf(body.get("token")));
        if (token == null || token.trim().isEmpty()) {
            return JimRestResponses.errorJson(400, "bad_request", "token is required");
        }
        this.agentService.configureToken(token);
        LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("ok", true);
        out.put("tokenConfigured", true);
        return JimRestResponses.okJson(out); // NEVER echo the token
    }

    @DELETE
    @Path("/token")
    public Response revoke() {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        this.agentService.revokeToken();
        LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("ok", true);
        out.put("tokenConfigured", false);
        return JimRestResponses.okJson(out);
    }

    @POST
    @Path("/test")
    @Consumes({"application/json"})
    public Response test(Map<String, Object> body) {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        Integer conversationId = null;
        if (body != null && body.get("conversationId") != null) {
            try {
                conversationId = Integer.valueOf(String.valueOf(body.get("conversationId")).trim());
            } catch (NumberFormatException ignore) {
            }
        }
        return JimRestResponses.okJson(this.agentService.sendTestTrigger(conversationId));
    }

    @POST
    @Path("/scan")
    public Response scan() {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("ok", true);
        out.put("enqueued", this.agentService.fallbackScan());
        return JimRestResponses.okJson(out);
    }

    @POST
    @Path("/requeue/{jobId}")
    public Response requeue(@PathParam("jobId") int jobId) {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("ok", this.agentService.requeue(jobId));
        return JimRestResponses.okJson(out);
    }

    private Response requireSysAdmin() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        if (!this.globalPermissionManager.hasPermission(GlobalPermissionKey.SYSTEM_ADMIN, user)) {
            return JimRestResponses.errorJson(403, "forbidden",
                    "Jira System Administrator permission is required");
        }
        return null;
    }
}
