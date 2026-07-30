package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.corbitlogic.jira.internalmessenger.service.JimMcpService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP Control Plane REST API.
 *
 * <ul>
 *   <li>{@code GET /rest/jim/1.0/mcp/policy} — machine-to-machine pull by the Corbit Logic
 *       Gateway, authenticated by the shared {@code X-MCP-Gateway-Token} header (NOT a
 *       Jira session). Returns the signed effective policy.</li>
 *   <li>{@code /rest/jim/1.0/mcp/admin/*} — Jira System Administrator only.</li>
 * </ul>
 *
 * No secret value is ever returned by any endpoint.
 */
@Path("/mcp")
@Produces({"application/json"})
public class JimMcpResource {

    private static final Logger log = LoggerFactory.getLogger(JimMcpResource.class);

    private final JiraAuthenticationContext authenticationContext;
    private final GlobalPermissionManager globalPermissionManager;
    private final JimMcpService mcpService;

    @Inject
    public JimMcpResource(JiraAuthenticationContext authenticationContext,
                          GlobalPermissionManager globalPermissionManager,
                          JimMcpService mcpService) {
        this.authenticationContext = authenticationContext;
        this.globalPermissionManager = globalPermissionManager;
        this.mcpService = mcpService;
    }

    // -------------------------------------------------------------- gateway pull
    @GET
    @Path("/policy")
    @AnonymousAllowed
    public Response policy(@HeaderParam("X-MCP-Gateway-Token") String token) {
        if (!this.mcpService.checkGatewayToken(token)) {
            return JimRestResponses.errorJson(401, "unauthorized",
                    "valid X-MCP-Gateway-Token required");
        }
        try {
            return JimRestResponses.okJson(this.mcpService.buildSignedPolicy());
        } catch (Exception e) {
            log.error("MCP buildSignedPolicy failed", e);
            return JimRestResponses.errorJson(500, "policy_error", "failed to build policy");
        }
    }

    // -------------------------------------------------------------- gateway heartbeat
    @POST
    @Path("/heartbeat")
    @AnonymousAllowed
    @Consumes({"application/json"})
    public Response heartbeat(@HeaderParam("X-MCP-Gateway-Token") String token, Map<String, Object> body) {
        if (!this.mcpService.checkGatewayToken(token)) {
            return JimRestResponses.errorJson(401, "unauthorized", "valid X-MCP-Gateway-Token required");
        }
        this.mcpService.recordHeartbeat(body == null ? new LinkedHashMap<>() : body);
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        return JimRestResponses.okJson(out);
    }

    // -------------------------------------------------------------- admin (sysadmin)
    @GET
    @Path("/admin/status")
    public Response status() {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        return JimRestResponses.okJson(this.mcpService.getStatus());
    }

    @GET
    @Path("/admin/tools")
    public Response tools() {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        List<Map<String, Object>> list = this.mcpService.listTools();
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("tools", list);
        body.put("count", list.size());
        return JimRestResponses.okJson(body);
    }

    @PUT
    @Path("/admin/tools/{name}")
    @Consumes({"application/json"})
    public Response toggleTool(@PathParam("name") String name, Map<String, Object> body) {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        try {
            if (body != null && body.containsKey("enabled")) {
                this.mcpService.setToolEnabled(name, truthy(body.get("enabled")));
            }
            if (body != null && body.containsKey("confirm")) {
                this.mcpService.setToolConfirm(name, truthy(body.get("confirm")));
            }
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("tool", name);
            out.put("revision", this.mcpService.getRevision());
            return JimRestResponses.okJson(out);
        } catch (IllegalArgumentException e) {
            return JimRestResponses.errorJson(404, "unknown_tool", e.getMessage());
        } catch (Exception e) {
            log.error("MCP toggleTool failed", e);
            return JimRestResponses.errorJson(500, "error", "toggle failed");
        }
    }

    @POST
    @Path("/admin/bulk")
    @Consumes({"application/json"})
    public Response bulk(Map<String, Object> body) {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        String op = body == null ? "" : String.valueOf(body.get("op"));
        int changed = this.mcpService.bulk(op);
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("op", op);
        out.put("changed", changed);
        out.put("revision", this.mcpService.getRevision());
        return JimRestResponses.okJson(out);
    }

    @PUT
    @Path("/admin/global")
    @Consumes({"application/json"})
    public Response global(Map<String, Object> body) {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        this.mcpService.setGlobal(body == null ? new LinkedHashMap<>() : body);
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("revision", this.mcpService.getRevision());
        return JimRestResponses.okJson(out);
    }

    @POST
    @Path("/admin/secrets")
    @Consumes({"application/json"})
    public Response secrets(Map<String, Object> body) {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        String sharedSecret = body == null ? null : asString(body.get("sharedSecret"));
        String gatewayToken = body == null ? null : asString(body.get("gatewayToken"));
        this.mcpService.configureSecrets(sharedSecret, gatewayToken);
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("configured", this.mcpService.isConfigured());
        return JimRestResponses.okJson(out);
    }

    @POST
    @Path("/admin/seed")
    public Response seed() {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        int n = this.mcpService.seedFromRegistry();
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("seeded", n);
        return JimRestResponses.okJson(out);
    }

    // -------------------------------------------------------------- helpers
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

    private boolean truthy(Object o) {
        return o instanceof Boolean ? (Boolean) o : Boolean.parseBoolean(String.valueOf(o));
    }

    private String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
