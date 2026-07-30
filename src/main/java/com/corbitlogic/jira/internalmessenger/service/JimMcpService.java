package com.corbitlogic.jira.internalmessenger.service;

import java.util.List;
import java.util.Map;

/**
 * MCP Control Plane service: administrative source of truth for MCP settings and
 * per-tool policy. Builds the signed policy document consumed by the Corbit Logic
 * Gateway. Never stores or returns raw secrets.
 */
public interface JimMcpService {

    /** Seed/refresh the tool registry rows from the bundled contract. Returns count. */
    int seedFromRegistry();

    /** General status for the admin UI (enabled, gateway config/reachability, counts, revision). */
    Map<String, Object> getStatus();

    /** All tool policy rows for the admin UI. */
    List<Map<String, Object>> listTools();

    void setToolEnabled(String toolName, boolean enabled);

    void setToolConfirm(String toolName, boolean confirm);

    /** Bulk operations: enable_all, disable_all, enable_read, enable_write, enable_destructive. */
    int bulk(String op);

    /** Update global settings: mcpEnabled, classes, allowedProjects, limits, gatewayUrl. */
    void setGlobal(Map<String, Object> body);

    /** Build the signed effective policy document served to the Gateway. */
    Map<String, Object> buildSignedPolicy();

    /** Machine-to-machine auth for the Gateway policy pull. */
    boolean checkGatewayToken(String provided);

    /** Record an authenticated heartbeat from the Corbit Logic Gateway. */
    void recordHeartbeat(Map<String, Object> heartbeat);

    /** True when both the shared signing secret and gateway pull token are configured. */
    boolean isConfigured();

    /** Store the shared signing secret and gateway pull token (write-only, never returned). */
    void configureSecrets(String sharedSecret, String gatewayToken);

    void setGatewayUrl(String url);

    /** Current configuration revision (increments on every policy change). */
    int getRevision();
}
