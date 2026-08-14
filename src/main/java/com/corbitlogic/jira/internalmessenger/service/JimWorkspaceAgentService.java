package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimMessage;

import java.util.List;
import java.util.Map;

/**
 * ChatGPT Workspace Agent two-way chat integration (plugin side). Triggers an
 * external Workspace Agent HTTP channel when a real user sends a CorbitChat
 * message to the assistant bot user ({@code mcp.bootstrap} / "GPT Assistant").
 * The Agent reads the official message + Jira data through the 7gtech MCP and
 * posts its reply back into the same conversation through MCP — the trigger call
 * NEVER returns the answer. Secrets are stored write-only and never echoed.
 */
public interface JimWorkspaceAgentService {

    /** True when the integration is enabled AND token + channel are configured. */
    boolean isEnabled();

    /**
     * Post-commit hook for a newly-created USER message. Best-effort: evaluates
     * eligibility (direct-to-bot or bot-mention) and enqueues a durable trigger
     * job. Never throws to the caller and never blocks message creation.
     */
    void onUserMessage(int conversationId, JimMessage created);

    /** Store the Workspace Agent access token (write-only; never returned). */
    void configureToken(String token);

    /** Locally delete/revoke the stored token. */
    void revokeToken();

    /** Update non-secret config (enabled, channelId, triggerUrl, policy...). Returns change descriptions. */
    List<String> configure(Map<String, Object> config);

    /** Masked diagnostics/status for the admin page (never includes the token). */
    Map<String, Object> getStatus();

    /**
     * Read-only, message-based delivery verification: distinguishes accepted (HTTP 202)
     * triggers from actually-delivered assistant replies, and surfaces delivery_missing
     * for accepted triggers past the SLA with no reply. Does not mutate jobs.
     */
    Map<String, Object> deliveryReport(int limit);

    /** Fallback scan: enqueue eligible unprocessed messages (shares idempotency). Returns count enqueued. */
    int fallbackScan();

    /** Send a synthetic test trigger (optionally for a specific conversation). Returns a success/failure summary. */
    Map<String, Object> sendTestTrigger(Integer conversationId);

    /** Requeue a dead-letter/failed job by id (admin action). */
    boolean requeue(int jobId);

    /** Plugin shutdown hook. */
    void shutdown();
}
