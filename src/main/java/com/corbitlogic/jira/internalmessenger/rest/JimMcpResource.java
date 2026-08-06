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
import javax.ws.rs.QueryParam;
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
    public Response policy(@HeaderParam("X-MCP-Gateway-Token") String token,
                           @HeaderParam("X-MCP-Timestamp") String ts,
                           @HeaderParam("X-MCP-Nonce") String nonce,
                           @HeaderParam("X-MCP-Signature") String sig) {
        String reason = this.mcpService.verifyGatewayRequest(token, "GET", "/mcp/policy", ts, nonce, sig);
        if (reason != null) {
            log.warn("MCP policy pull rejected: {}", reason);
            return JimRestResponses.errorJson(401, "unauthorized", reason);
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
    public Response heartbeat(@HeaderParam("X-MCP-Gateway-Token") String token,
                              @HeaderParam("X-MCP-Timestamp") String ts,
                              @HeaderParam("X-MCP-Nonce") String nonce,
                              @HeaderParam("X-MCP-Signature") String sig,
                              Map<String, Object> body) {
        String reason = this.mcpService.verifyGatewayRequest(token, "POST", "/mcp/heartbeat", ts, nonce, sig);
        if (reason != null) {
            log.warn("MCP heartbeat rejected: {}", reason);
            return JimRestResponses.errorJson(401, "unauthorized", reason);
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

    // -------------------------------------------------------------- MCP Access management (Phase 2)
    @GET
    @Path("/admin/access-policy")
    public Response getAccessPolicy() {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        return JimRestResponses.okJson(this.mcpService.getAccessPolicy());
    }

    @POST
    @Path("/admin/access-policy")
    @Consumes({"application/json"})
    public Response applyAccessPolicy(Map<String, Object> body) {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        String actor = null;
        try { ApplicationUser u = this.authenticationContext.getLoggedInUser(); actor = u == null ? null : u.getKey(); } catch (Exception ignore) { }
        Map<String, Object> res = this.mcpService.setAccessPolicy(body, actor);
        if (Boolean.TRUE.equals(res.get("ok"))) return JimRestResponses.okJson(res);
        String err = String.valueOf(res.getOrDefault("error", "error"));
        int code = "revision_conflict".equals(err) ? 409 : 400;
        return JimRestResponses.errorJson(code, err, String.valueOf(res.getOrDefault("message", "apply failed")));
    }

    @GET
    @Path("/admin/access-simulate")
    public Response simulateAccess(@QueryParam("user") String user, @QueryParam("tool") String tool) {
        Response denied = requireSysAdmin();
        if (denied != null) return denied;
        if (user == null || user.isEmpty() || tool == null || tool.isEmpty())
            return JimRestResponses.errorJson(400, "bad_request", "user and tool query params are required");
        return JimRestResponses.okJson(this.mcpService.simulateAccess(user, tool));
    }

    @GET
    @Path("/access-policy")
    @AnonymousAllowed
    public Response accessPolicy(@HeaderParam("X-MCP-Gateway-Token") String token,
                                 @HeaderParam("X-MCP-Timestamp") String ts,
                                 @HeaderParam("X-MCP-Nonce") String nonce,
                                 @HeaderParam("X-MCP-Signature") String sig) {
        String reason = this.mcpService.verifyGatewayRequest(token, "GET", "/mcp/access-policy", ts, nonce, sig);
        if (reason != null) {
            log.warn("MCP access-policy pull rejected: {}", reason);
            return JimRestResponses.errorJson(401, "unauthorized", reason);
        }
        try {
            return JimRestResponses.okJson(this.mcpService.buildSignedAccessPolicy());
        } catch (Exception e) {
            log.error("MCP buildSignedAccessPolicy failed", e);
            return JimRestResponses.errorJson(500, "policy_error", "failed to build access policy");
        }
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
