package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.corbitlogic.jira.internalmessenger.ao.JimAgentTriggerJob;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;
import com.corbitlogic.jira.internalmessenger.model.JimSenderType;

import net.java.ao.DBParam;
import net.java.ao.Query;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ChatGPT Workspace Agent trigger service. Mirrors the established secret-storage
 * pattern (write-only PluginSettings namespace; status exposes booleans only) and
 * the async-coordinator pattern (single-thread executor + durable AO job ledger
 * for idempotency/retry). The trigger sends IDENTIFIERS ONLY — the Agent reads the
 * official content through MCP and posts its reply back through MCP.
 */
public class JimWorkspaceAgentServiceImpl implements JimWorkspaceAgentService {

    private static final Logger log = LoggerFactory.getLogger(JimWorkspaceAgentServiceImpl.class);
    private static final String NS = "com.corbitlogic.jira.internalmessenger.workspaceagent:";
    private static final String ASSISTANT_USERNAME = "mcp.bootstrap";

    static final String STATUS_PENDING = "pending";
    static final String STATUS_RUNNING = "running";
    static final String STATUS_SUCCEEDED = "succeeded";
    static final String STATUS_FAILED = "failed";
    static final String STATUS_DEAD_LETTER = "dead_letter";

    private static final int DEFAULT_MAX_RETRIES = 5;
    private static final long DEFAULT_BACKOFF_MS = 15000L;
    private static final int HTTP_TIMEOUT_MS = 20000;
    /**
     * Per-IP TCP reachability probe budget. api.chatgpt.com is Cloudflare anycast and
     * publishes multiple A records where one anycast IP can be a black hole from this
     * network. HttpURLConnection pins to the first resolved IP, so it must NOT be a
     * dead one; we probe each IP (serially) up to this budget and pin the request to
     * the first reachable address. Must exceed the observed ~4.3s good-path connect.
     */
    private static final int PER_IP_CONNECT_MS = 8000;
    /**
     * Delivery SLA. A trigger reaching STATUS_SUCCEEDED means the API Channel ACCEPTED it
     * (HTTP 202) — not that the Agent replied. Accepted triggers older than this with no
     * assistant reply in the conversation are reported as delivery_missing.
     */
    private static final long DELIVERY_SLA_MS = 180000L;
    private static final int FALLBACK_SCAN_LIMIT = 200;

    /**
     * Singleton holder so the message-create hook can reach this optional service
     * without a hard constructor dependency (getOSGiComponentInstanceOfType does not
     * resolve plugin-internal services reliably). Set on construction, cleared on shutdown.
     */
    private static volatile JimWorkspaceAgentService INSTANCE;

    public static JimWorkspaceAgentService instance() {
        return INSTANCE;
    }

    private final ActiveObjects ao;
    private final PluginSettings settings;
    private final ScheduledExecutorService scheduler;

    // Lightweight in-memory diagnostics (durable state lives in the AO ledger).
    private volatile Long lastTriggerSuccessAt;
    private volatile Long lastTriggerFailureAt;
    private volatile String lastFailureSummary;

    public JimWorkspaceAgentServiceImpl(ActiveObjects ao, PluginSettingsFactory pluginSettingsFactory) {
        this.ao = ao;
        this.settings = pluginSettingsFactory.createGlobalSettings();
        INSTANCE = this;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jim-workspace-agent");
            t.setDaemon(true);
            return t;
        });
        // Recover pending/retry-due jobs after restart + periodic drain.
        try {
            this.scheduler.scheduleWithFixedDelay(this::processDueJobsSafely, 30, 30, TimeUnit.SECONDS);
            // Hourly fallback scan (backup path; near-real-time trigger is primary).
            this.scheduler.scheduleWithFixedDelay(this::hourlyFallbackSafely, 300, 3600, TimeUnit.SECONDS);
        } catch (Throwable t) {
            log.warn("workspace-agent scheduler init failed: {}", t.toString());
        }
    }

    // ---------------------------------------------------------------- settings
    private String get(String k, String def) {
        Object v = this.settings.get(NS + k);
        return v == null ? def : String.valueOf(v);
    }

    private void put(String k, String v) {
        this.settings.put(NS + k, v);
    }

    private boolean getBool(String k, boolean def) {
        return Boolean.parseBoolean(get(k, Boolean.toString(def)));
    }

    private int getInt(String k, int def) {
        try {
            return Integer.parseInt(get(k, Integer.toString(def)));
        } catch (Exception e) {
            return def;
        }
    }

    private boolean hasToken() {
        return !get("token", "").isEmpty();
    }

    private String channelId() {
        return get("channelId", "");
    }

    private String triggerUrl() {
        return get("triggerUrl", "");
    }

    private String tenantId() {
        String t = get("tenantId", "");
        return t.isEmpty() ? "default" : t;
    }

    private int maxRetries() {
        return Math.max(0, getInt("maxRetries", DEFAULT_MAX_RETRIES));
    }

    private long backoffMs() {
        try {
            long v = Long.parseLong(get("backoffMs", Long.toString(DEFAULT_BACKOFF_MS)));
            return v > 0 ? v : DEFAULT_BACKOFF_MS;
        } catch (Exception e) {
            return DEFAULT_BACKOFF_MS;
        }
    }

    @Override
    public boolean isEnabled() {
        return getBool("enabled", false) && hasToken() && !triggerUrl().isEmpty();
    }

    @Override
    public void configureToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        put("token", token.trim());
    }

    @Override
    public void revokeToken() {
        this.settings.remove(NS + "token");
    }

    @Override
    public List<String> configure(Map<String, Object> config) {
        List<String> changes = new ArrayList<String>();
        if (config == null) {
            return changes;
        }
        if (config.containsKey("enabled")) {
            put("enabled", Boolean.toString(Boolean.TRUE.equals(config.get("enabled"))
                    || "true".equalsIgnoreCase(String.valueOf(config.get("enabled")))));
            changes.add("workspaceAgentEnabled=" + get("enabled", "false"));
        }
        if (config.containsKey("channelId")) {
            String v = String.valueOf(config.get("channelId")).trim();
            put("channelId", v);
            changes.add("channelId set");
        }
        if (config.containsKey("triggerUrl")) {
            String v = String.valueOf(config.get("triggerUrl")).trim();
            put("triggerUrl", v);
            changes.add("triggerUrl set");
        }
        if (config.containsKey("tenantId")) {
            put("tenantId", String.valueOf(config.get("tenantId")).trim());
            changes.add("tenantId set");
        }
        if (config.containsKey("groupMentionMode")) {
            put("groupMentionMode", Boolean.toString(Boolean.TRUE.equals(config.get("groupMentionMode"))
                    || "true".equalsIgnoreCase(String.valueOf(config.get("groupMentionMode")))));
            changes.add("groupMentionMode=" + get("groupMentionMode", "false"));
        }
        if (config.containsKey("projectAllowlist")) {
            put("projectAllowlist", String.valueOf(config.get("projectAllowlist")).trim());
            changes.add("projectAllowlist set");
        }
        if (config.containsKey("conversationBlocklist")) {
            put("conversationBlocklist", String.valueOf(config.get("conversationBlocklist")).trim());
            changes.add("conversationBlocklist set");
        }
        if (config.containsKey("maxRetries")) {
            put("maxRetries", String.valueOf(config.get("maxRetries")).trim());
            changes.add("maxRetries set");
        }
        if (config.containsKey("backoffMs")) {
            put("backoffMs", String.valueOf(config.get("backoffMs")).trim());
            changes.add("backoffMs set");
        }
        if (config.containsKey("fallbackScanEnabled")) {
            put("fallbackScanEnabled", Boolean.toString(Boolean.TRUE.equals(config.get("fallbackScanEnabled"))
                    || "true".equalsIgnoreCase(String.valueOf(config.get("fallbackScanEnabled")))));
            changes.add("fallbackScanEnabled=" + get("fallbackScanEnabled", "true"));
        }
        return changes;
    }

    // ---------------------------------------------------------------- bot user
    private String botUserKey() {
        try {
            UserManager um = ComponentAccessor.getUserManager();
            ApplicationUser bot = um != null ? um.getUserByName(ASSISTANT_USERNAME) : null;
            return (bot != null && bot.isActive()) ? bot.getKey() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- eligibility + enqueue
    @Override
    public void onUserMessage(int conversationId, JimMessage created) {
        try {
            if (!isEnabled() || created == null) {
                return;
            }
            // Loop prevention 1: only real USER messages.
            if (!JimSenderType.USER.name().equals(created.getSenderType())) {
                return;
            }
            if (created.getDeleted() != null && created.getDeleted().intValue() == 1) {
                return;
            }
            String senderKey = created.getSenderUserKey();
            String botKey = botUserKey();
            if (botKey == null) {
                return; // assistant user not configured/active
            }
            // Loop prevention 2: never trigger on the assistant's own messages.
            if (botKey.equals(senderKey)) {
                return;
            }
            JimConversation c = this.ao.get(JimConversation.class, conversationId);
            if (c == null) {
                return;
            }
            if (!isEligibleConversation(c, created, botKey)) {
                return;
            }
            enqueue(created, "realtime");
        } catch (Throwable t) {
            // Best-effort: never affect message creation.
            log.warn("workspace-agent onUserMessage failed for conv {}: {}", conversationId, t.toString());
        }
    }

    private boolean isEligibleConversation(JimConversation c, JimMessage m, String botKey) {
        String type = c.getConversationType();
        // Blocklist by conversation id.
        for (String b : csv(get("conversationBlocklist", ""))) {
            if (b.equals(String.valueOf(c.getID()))) {
                return false;
            }
        }
        if (JimConversationType.DIRECT.name().equals(type)) {
            return botKey.equals(c.getUserAKey()) || botKey.equals(c.getUserBKey());
        }
        // Group/project: only when the mention mode is enabled AND the message
        // mentions the assistant bot.
        if (JimConversationType.GROUP.name().equals(type) || JimConversationType.PROJECT.name().equals(type)) {
            if (!getBool("groupMentionMode", false)) {
                return false;
            }
            if (!allowedProject(c)) {
                return false;
            }
            String body = m.getBody() == null ? "" : m.getBody();
            return body.contains("[~" + ASSISTANT_USERNAME + "]");
        }
        return false; // SYSTEM feed etc. never triggers
    }

    private boolean allowedProject(JimConversation c) {
        String allow = get("projectAllowlist", "").trim();
        if (allow.isEmpty()) {
            return true;
        }
        String pk = c.getProjectKey();
        if (pk == null) {
            return true; // non-project group: allowlist only constrains project chats
        }
        for (String a : csv(allow)) {
            if (a.equalsIgnoreCase(pk)) {
                return true;
            }
        }
        return false;
    }

    private synchronized void enqueue(JimMessage m, String source) {
        String key = "corbit-message-" + m.getID();
        // Idempotency: one job per message id, ever.
        JimAgentTriggerJob[] existing = (JimAgentTriggerJob[]) this.ao.find(JimAgentTriggerJob.class,
                Query.select().where("IDEMPOTENCY_KEY = ?", key).limit(1));
        if (existing != null && existing.length > 0) {
            return;
        }
        long now = System.currentTimeMillis();
        JimAgentTriggerJob job = (JimAgentTriggerJob) this.ao.create(JimAgentTriggerJob.class, new DBParam[0]);
        job.setIdempotencyKey(key);
        job.setMessageId(m.getID());
        job.setConversationId(m.getConversationId());
        job.setSenderUserKey(m.getSenderUserKey());
        job.setThreadKey("corbit:" + tenantId() + ":" + m.getConversationId());
        job.setStatus(STATUS_PENDING);
        job.setAttempts(0);
        job.setSource(source);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        job.setNextAttemptAt(now);
        job.save();
        // Kick the async processor.
        try {
            this.scheduler.submit(this::processDueJobsSafely);
        } catch (Throwable ignore) {
        }
    }

    // ---------------------------------------------------------------- async processing
    private void processDueJobsSafely() {
        try {
            processDueJobs();
        } catch (Throwable t) {
            log.warn("workspace-agent processDueJobs failed: {}", t.toString());
        }
    }

    private void processDueJobs() {
        if (!isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        JimAgentTriggerJob[] due = (JimAgentTriggerJob[]) this.ao.find(JimAgentTriggerJob.class,
                Query.select().where("(STATUS = ? OR STATUS = ?) AND NEXT_ATTEMPT_AT <= ?",
                        STATUS_PENDING, STATUS_FAILED, now).order("NEXT_ATTEMPT_AT ASC").limit(50));
        if (due == null) {
            return;
        }
        for (JimAgentTriggerJob job : due) {
            processOne(job);
        }
    }

    private void processOne(JimAgentTriggerJob job) {
        long now = System.currentTimeMillis();
        job.setStatus(STATUS_RUNNING);
        job.setUpdatedAt(now);
        job.save();
        try {
            String runId = fireTrigger(job);
            job.setStatus(STATUS_SUCCEEDED);
            job.setAgentRunId(runId);
            job.setLastError(null);
            job.setCompletedAt(System.currentTimeMillis());
            job.setUpdatedAt(job.getCompletedAt());
            job.save();
            this.lastTriggerSuccessAt = job.getCompletedAt();
        } catch (PermanentTriggerException pe) {
            // 4xx auth/config → do not retry indefinitely.
            failJob(job, pe.getMessage(), true);
        } catch (Exception e) {
            failJob(job, e.getMessage(), false);
        }
    }

    private void failJob(JimAgentTriggerJob job, String error, boolean permanent) {
        long now = System.currentTimeMillis();
        int attempts = job.getAttempts() + 1;
        job.setAttempts(attempts);
        job.setLastError(truncate(error, 990));
        job.setUpdatedAt(now);
        this.lastTriggerFailureAt = now;
        this.lastFailureSummary = truncate(error, 200);
        if (permanent || attempts >= maxRetries()) {
            job.setStatus(STATUS_DEAD_LETTER);
            job.setCompletedAt(now);
        } else {
            job.setStatus(STATUS_FAILED);
            long delay = backoffMs() * (long) Math.pow(2, Math.min(attempts, 6));
            job.setNextAttemptAt(now + delay);
        }
        job.save();
    }

    /**
     * POST to the official OpenAI Workspace Agents API channel per
     * https://developers.openai.com/workspace-agents/trigger-runs :
     *   POST {baseUrl}/trigger   body {"input": <text>, "conversation_key": <threadKey>}
     *   headers: Authorization: Bearer <token>, Content-Type: application/json, Idempotency-Key
     *   success = 202 Accepted (empty or {conversation_url,...} body; run id not required).
     * The token is only ever an Authorization header — never logged, never in the body.
     * Returns the agent_trigger_run_id when present, else null.
     */
    private String fireTrigger(JimAgentTriggerJob job) throws Exception {
        String url = normalizeTriggerUrl(triggerUrl());
        String token = get("token", "");
        if (url.isEmpty() || token.isEmpty()) {
            throw new PermanentTriggerException("trigger url or token not configured");
        }
        String payload = buildTriggerBody(job);
        LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + token);
        headers.put("Idempotency-Key", job.getIdempotencyKey());
        HttpResult res = httpsPostReachable(url, headers, payload.getBytes(StandardCharsets.UTF_8));
        int code = res.code;
        if (code >= 200 && code < 300) {
            return extractRunId(res.body); // 202 Accepted (may be empty) → success
        }
        // Capture the API error body (no secrets in it) for diagnostics.
        String errBody = sanitizeBody(res.body);
        String detail = "HTTP " + code + (errBody.isEmpty() ? "" : (" " + errBody));
        // 4xx (except 429) = permanent config/auth/contract error.
        if (code >= 400 && code < 500 && code != 429) {
            throw new PermanentTriggerException(detail);
        }
        throw new Exception(detail); // 5xx / 429 → retry
    }

    /** Minimal HTTP response holder (status code + decoded body). */
    private static final class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }

    /**
     * HTTPS POST that dials a TCP-reachable IP of the host and writes the request over a
     * manually managed {@link SSLSocket}.
     *
     * <p>Rationale: the trigger host (api.chatgpt.com) is Cloudflare anycast and returns
     * multiple A records where one anycast IP can silently drop SYNs (black hole) from
     * this production network. {@code HttpURLConnection} connects to only the FIRST
     * resolved IP and blocks the full connect timeout on a dead one — the exact
     * "connect timed out" intermittency observed (curl's Happy-Eyeballs picks the live
     * IP instead). We probe each resolved IP and use the first that accepts a TCP
     * connection. We manage the socket directly (instead of pinning an IP into
     * {@code HttpURLConnection}) so the HTTP {@code Host} header AND the TLS SNI both
     * carry the real hostname — otherwise Cloudflare returns 421 Misdirected Request,
     * because the JDK silently drops a {@code Host} header set via setRequestProperty
     * (restricted header). The certificate is still verified against the real host.
     */
    private HttpResult httpsPostReachable(String urlStr, Map<String, String> headers, byte[] body)
            throws IOException {
        URL u = new URL(urlStr);
        if (!"https".equalsIgnoreCase(u.getProtocol())) {
            throw new IOException("trigger URL must be https");
        }
        String host = u.getHost();
        int port = u.getPort() == -1 ? 443 : u.getPort();
        String path = u.getFile();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        InetAddress[] all;
        try {
            all = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new IOException("DNS resolution failed for " + host);
        }
        List<InetAddress> order = new ArrayList<InetAddress>(Arrays.asList(all));
        // Rotate randomly so we do not always pay the dead-IP probe cost first.
        Collections.shuffle(order);

        Socket raw = null;
        IOException last = null;
        for (InetAddress addr : order) {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(addr, port), PER_IP_CONNECT_MS);
                raw = s;
                break;
            } catch (IOException ex) {
                last = ex;
                try {
                    s.close();
                } catch (IOException ignore) {
                    // best effort
                }
            }
        }
        if (raw == null) {
            throw new IOException("connect timed out (no reachable IP for " + host + " among "
                    + order.size() + " addresses)" + (last != null ? ": " + last.getMessage() : ""));
        }

        SSLSocket ssl = null;
        try {
            SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            // createSocket(rawSocket, host, port, autoClose) sets SNI to the real host.
            ssl = (SSLSocket) sf.createSocket(raw, host, port, true);
            SSLParameters params = ssl.getSSLParameters();
            params.setServerNames(Collections.<javax.net.ssl.SNIServerName>singletonList(
                    new SNIHostName(host)));
            params.setEndpointIdentificationAlgorithm("HTTPS"); // verify cert vs real host
            ssl.setSSLParameters(params);
            ssl.setSoTimeout(HTTP_TIMEOUT_MS);
            ssl.startHandshake();

            StringBuilder req = new StringBuilder();
            req.append("POST ").append(path).append(" HTTP/1.1\r\n");
            req.append("Host: ").append(host).append("\r\n");
            for (Map.Entry<String, String> h : headers.entrySet()) {
                req.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
            }
            req.append("Content-Length: ").append(body.length).append("\r\n");
            req.append("Connection: close\r\n");
            req.append("\r\n");

            OutputStream os = ssl.getOutputStream();
            os.write(req.toString().getBytes(StandardCharsets.ISO_8859_1));
            os.write(body);
            os.flush();

            java.io.InputStream is = ssl.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            int total = 0;
            while ((n = is.read(buf)) != -1 && total < 262144) {
                bos.write(buf, 0, n);
                total += n;
            }
            return parseHttpResponse(bos.toByteArray());
        } finally {
            try {
                if (ssl != null) {
                    ssl.close();
                } else {
                    raw.close();
                }
            } catch (IOException ignore) {
                // best effort
            }
        }
    }

    /** Parse a raw HTTP/1.1 response (status line + headers + optionally-chunked body). */
    private static HttpResult parseHttpResponse(byte[] raw) throws IOException {
        int sep = indexOfHeaderEnd(raw);
        String head = new String(raw, 0, sep >= 0 ? sep : raw.length, StandardCharsets.ISO_8859_1);
        int bodyStart = sep >= 0 ? sep + 4 : raw.length;
        int firstEol = head.indexOf("\r\n");
        String statusLine = firstEol >= 0 ? head.substring(0, firstEol) : head;
        String[] parts = statusLine.trim().split("\\s+");
        int code = -1;
        if (parts.length >= 2) {
            try {
                code = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignore) {
                code = -1;
            }
        }
        if (code < 0) {
            throw new IOException("malformed HTTP status line: " + truncate(statusLine, 80));
        }
        boolean chunked = head.toLowerCase(java.util.Locale.ROOT).contains("transfer-encoding: chunked");
        byte[] bodyBytes = Arrays.copyOfRange(raw, Math.min(bodyStart, raw.length), raw.length);
        String bodyStr = chunked ? decodeChunked(bodyBytes)
                : new String(bodyBytes, StandardCharsets.UTF_8);
        return new HttpResult(code, bodyStr);
    }

    private static int indexOfHeaderEnd(byte[] b) {
        for (int i = 0; i + 3 < b.length; i++) {
            if (b[i] == '\r' && b[i + 1] == '\n' && b[i + 2] == '\r' && b[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static String decodeChunked(byte[] b) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int i = 0;
        while (i < b.length) {
            int eol = findCrlf(b, i);
            if (eol < 0) {
                break;
            }
            String sizeLine = new String(b, i, eol - i, StandardCharsets.ISO_8859_1).trim();
            int semi = sizeLine.indexOf(';');
            if (semi >= 0) {
                sizeLine = sizeLine.substring(0, semi).trim();
            }
            int size;
            try {
                size = Integer.parseInt(sizeLine, 16);
            } catch (NumberFormatException e) {
                break;
            }
            i = eol + 2;
            if (size <= 0) {
                break;
            }
            int end = Math.min(i + size, b.length);
            out.write(b, i, end - i);
            i = end + 2; // skip trailing CRLF after the chunk
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static int findCrlf(byte[] b, int from) {
        for (int i = from; i + 1 < b.length; i++) {
            if (b[i] == '\r' && b[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }

    /** Ensure the trigger URL targets the official {baseUrl}/trigger endpoint. */
    private static String normalizeTriggerUrl(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        if (u.isEmpty()) {
            return "";
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (!u.endsWith("/trigger")) {
            u = u + "/trigger";
        }
        return u;
    }

    /**
     * Official request body: {@code {"input": <text>, "conversation_key": <threadKey>}}.
     * {@code input} carries ONLY an instruction + identifiers (no message body, no Jira
     * data, no history, no secrets) so the Agent reads the official content via MCP.
     * {@code conversation_key} = the stable thread key for multi-turn context.
     */
    private String buildTriggerBody(JimAgentTriggerJob job) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendStr(sb, "input", buildAgentInput(job)).append(',');
        appendStr(sb, "conversation_key", job.getThreadKey() == null ? "" : job.getThreadKey());
        sb.append('}');
        return sb.toString();
    }

    /** Compact instruction + identifiers passed as the agent's trigger input (no content). */
    private String buildAgentInput(JimAgentTriggerJob job) {
        // Admin connectivity test: never asks the agent to act on a real message.
        if (job.getIdempotencyKey() != null && job.getIdempotencyKey().startsWith("corbit-test-")) {
            return "CorbitChat API channel connectivity test from the Jira plugin. "
                    + "No real message is attached; please take no action.";
        }
        JimMessage m = job.getMessageId() > 0 ? this.ao.get(JimMessage.class, job.getMessageId()) : null;
        String issueKey = m != null && m.getIssueKey() != null ? m.getIssueKey() : "";
        StringBuilder in = new StringBuilder();
        in.append("New CorbitChat message for GPT Assistant. ");
        in.append("Read the official message and any Jira context via the 7gtech MCP tools, ");
        in.append("then post your reply back into the SAME CorbitChat conversation as mcp.bootstrap. ");
        in.append("Do not answer here. Identifiers: source=corbitchat");
        in.append(" tenantId=").append(tenantId());
        in.append(" conversationId=").append(job.getConversationId());
        in.append(" messageId=").append(job.getMessageId());
        if (job.getSenderUserKey() != null) {
            in.append(" senderUserKey=").append(job.getSenderUserKey());
        }
        in.append(" threadKey=").append(job.getThreadKey());
        if (!issueKey.isEmpty()) {
            in.append(" issueKey=").append(issueKey);
        }
        in.append(" assistantUser=mcp.bootstrap.");
        return in.toString();
    }

    /** Extract the agent_trigger_run_id from a success response body (best-effort, may be empty). */
    private String extractRunId(String resp) {
        if (resp == null || resp.isEmpty()) {
            return null;
        }
        // Official field is agent_trigger_run_id (beta); tolerate a few aliases. Best-effort.
        for (String field : new String[]{"\"agent_trigger_run_id\"", "\"run_id\"", "\"runId\""}) {
            int i = resp.indexOf(field);
            if (i >= 0) {
                int colon = resp.indexOf(':', i);
                int q1 = resp.indexOf('"', colon + 1);
                int q2 = q1 >= 0 ? resp.indexOf('"', q1 + 1) : -1;
                if (q1 >= 0 && q2 > q1) {
                    return resp.substring(q1 + 1, q2);
                }
            }
        }
        return null;
    }

    /** Sanitize an API error body for diagnostics. Contains no secrets (never the token). Truncated. */
    private String sanitizeBody(String s) {
        if (s == null) {
            return "";
        }
        return truncate(s.replaceAll("\\s+", " ").trim(), 400);
    }

    private static StringBuilder appendStr(StringBuilder sb, String k, String v) {
        return sb.append('"').append(k).append("\":\"").append(jsonEscape(v == null ? "" : v)).append('"');
    }

    private static String jsonEscape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        b.append(String.format("\\u%04x", (int) ch));
                    } else {
                        b.append(ch);
                    }
            }
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- fallback scan
    private void hourlyFallbackSafely() {
        try {
            if (isEnabled() && getBool("fallbackScanEnabled", true)) {
                fallbackScan();
            }
        } catch (Throwable t) {
            log.warn("workspace-agent hourly fallback failed: {}", t.toString());
        }
    }

    @Override
    public int fallbackScan() {
        if (!isEnabled()) {
            return 0;
        }
        String botKey = botUserKey();
        if (botKey == null) {
            return 0;
        }
        // Bounded scan of recent USER messages in DIRECT conversations with the bot
        // that have no job row yet (shares the idempotency key so no double work).
        int enqueued = 0;
        JimMessage[] recent = (JimMessage[]) this.ao.find(JimMessage.class,
                Query.select().where("SENDER_TYPE = ? AND SENDER_USER_KEY <> ? AND (DELETED IS NULL OR DELETED = 0)",
                        JimSenderType.USER.name(), botKey).order("ID DESC").limit(FALLBACK_SCAN_LIMIT));
        if (recent == null) {
            return 0;
        }
        for (JimMessage m : recent) {
            try {
                JimConversation c = this.ao.get(JimConversation.class, m.getConversationId());
                if (c == null || !isEligibleConversation(c, m, botKey)) {
                    continue;
                }
                String key = "corbit-message-" + m.getID();
                JimAgentTriggerJob[] ex = (JimAgentTriggerJob[]) this.ao.find(JimAgentTriggerJob.class,
                        Query.select().where("IDEMPOTENCY_KEY = ?", key).limit(1));
                if (ex != null && ex.length > 0) {
                    continue;
                }
                enqueue(m, "fallback_scan");
                enqueued++;
            } catch (Throwable ignore) {
            }
        }
        return enqueued;
    }

    // ---------------------------------------------------------------- test + requeue
    @Override
    public Map<String, Object> sendTestTrigger(Integer conversationId) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (!isEnabled()) {
            out.put("ok", false);
            out.put("error", "integration_disabled_or_unconfigured");
            return out;
        }
        try {
            // Build a synthetic job (not persisted) to exercise the HTTP path.
            JimAgentTriggerJob synthetic = (JimAgentTriggerJob) this.ao.create(JimAgentTriggerJob.class, new DBParam[0]);
            long now = System.currentTimeMillis();
            synthetic.setIdempotencyKey("corbit-test-" + now);
            synthetic.setMessageId(0);
            synthetic.setConversationId(conversationId != null ? conversationId.intValue() : 0);
            synthetic.setSenderUserKey("TEST");
            synthetic.setThreadKey("corbit:" + tenantId() + ":" + (conversationId != null ? conversationId : 0));
            synthetic.setStatus(STATUS_RUNNING);
            synthetic.setSource("test");
            synthetic.setCreatedAt(now);
            synthetic.setUpdatedAt(now);
            synthetic.setNextAttemptAt(now);
            synthetic.save();
            try {
                String runId = fireTrigger(synthetic);
                out.put("ok", true);
                out.put("runId", runId);
                this.lastTriggerSuccessAt = System.currentTimeMillis();
            } finally {
                // Test rows are not kept in the durable ledger.
                this.ao.delete(synthetic);
            }
        } catch (Exception e) {
            this.lastTriggerFailureAt = System.currentTimeMillis();
            this.lastFailureSummary = truncate(e.getMessage(), 200);
            out.put("ok", false);
            out.put("error", truncate(e.getMessage(), 200));
        }
        return out;
    }

    @Override
    public boolean requeue(int jobId) {
        try {
            JimAgentTriggerJob job = this.ao.get(JimAgentTriggerJob.class, jobId);
            if (job == null) {
                return false;
            }
            long now = System.currentTimeMillis();
            job.setStatus(STATUS_PENDING);
            job.setNextAttemptAt(now);
            job.setUpdatedAt(now);
            job.save();
            this.scheduler.submit(this::processDueJobsSafely);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------------------------------------------------------- diagnostics
    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("enabled", getBool("enabled", false));
        m.put("effectiveEnabled", isEnabled());
        m.put("tokenConfigured", hasToken());
        m.put("agentChannelConfigured", !channelId().isEmpty());
        m.put("triggerUrlConfigured", !triggerUrl().isEmpty());
        String botKey = botUserKey();
        m.put("assistantUser", ASSISTANT_USERNAME);
        m.put("assistantUserValid", botKey != null);
        m.put("groupMentionMode", getBool("groupMentionMode", false));
        m.put("projectAllowlist", get("projectAllowlist", ""));
        m.put("conversationBlocklist", get("conversationBlocklist", ""));
        m.put("tenantId", tenantId());
        m.put("maxRetries", maxRetries());
        m.put("backoffMs", backoffMs());
        m.put("fallbackScanEnabled", getBool("fallbackScanEnabled", true));
        m.put("lastTriggerSuccessAt", this.lastTriggerSuccessAt);
        m.put("lastTriggerFailureAt", this.lastTriggerFailureAt);
        m.put("lastFailureSummary", this.lastFailureSummary);
        m.put("queueDepth", countByStatus(STATUS_PENDING) + countByStatus(STATUS_FAILED));
        m.put("pendingCount", countByStatus(STATUS_PENDING));
        m.put("runningCount", countByStatus(STATUS_RUNNING));
        m.put("failedCount", countByStatus(STATUS_FAILED));
        m.put("deadLetterCount", countByStatus(STATUS_DEAD_LETTER));
        m.put("succeededCount", countByStatus(STATUS_SUCCEEDED));
        return m;
    }

    private int countByStatus(String status) {
        try {
            return this.ao.count(JimAgentTriggerJob.class, Query.select().where("STATUS = ?", status));
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Message-based delivery verification (read-only; no job mutation, no worker impact).
     *
     * <p>STATUS_SUCCEEDED means the API Channel ACCEPTED the trigger (HTTP 202) — it is NOT
     * proof the Agent replied. For recent accepted triggers older than {@link #DELIVERY_SLA_MS},
     * we check whether the assistant (mcp.bootstrap) posted a message in the SAME conversation
     * AFTER the triggering message: present → delivered; absent → delivery_missing. This gives
     * operational accepted-vs-delivered visibility without changing the trigger state machine.
     */
    @Override
    public Map<String, Object> deliveryReport(int limit) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        int lim = (limit > 0 && limit <= 200) ? limit : 50;
        out.put("slaSeconds", DELIVERY_SLA_MS / 1000L);
        out.put("acceptedCount", countByStatus(STATUS_SUCCEEDED));
        String botKey = botUserKey();
        if (botKey == null) {
            out.put("error", "assistant user not configured");
            return out;
        }
        long now = System.currentTimeMillis();
        int delivered = 0, missing = 0, pendingVerification = 0, checked = 0;
        List<Map<String, Object>> missingList = new ArrayList<Map<String, Object>>();
        try {
            JimAgentTriggerJob[] jobs = (JimAgentTriggerJob[]) this.ao.find(JimAgentTriggerJob.class,
                    Query.select().where("STATUS = ?", STATUS_SUCCEEDED).order("ID DESC").limit(lim));
            if (jobs != null) {
                for (JimAgentTriggerJob j : jobs) {
                    checked++;
                    Long completed = j.getCompletedAt();
                    long ref = completed != null ? completed.longValue()
                            : (j.getCreatedAt() != null ? j.getCreatedAt().longValue() : now);
                    if (now - ref < DELIVERY_SLA_MS) {
                        pendingVerification++;
                        continue; // too young to judge delivery yet
                    }
                    long triggerTs = ref;
                    JimMessage trig = j.getMessageId() > 0 ? this.ao.get(JimMessage.class, j.getMessageId()) : null;
                    if (trig != null && trig.getCreatedAt() != null) {
                        triggerTs = trig.getCreatedAt().longValue();
                    }
                    JimMessage[] reply = (JimMessage[]) this.ao.find(JimMessage.class,
                            Query.select().where(
                                    "CONVERSATION_ID = ? AND SENDER_USER_KEY = ? AND CREATED_AT > ? AND (DELETED IS NULL OR DELETED = 0)",
                                    j.getConversationId(), botKey, triggerTs).limit(1).order("ID DESC"));
                    if (reply != null && reply.length > 0) {
                        delivered++;
                    } else {
                        missing++;
                        if (missingList.size() < 25) {
                            Map<String, Object> mi = new LinkedHashMap<String, Object>();
                            mi.put("jobId", j.getID());
                            mi.put("messageId", j.getMessageId());
                            mi.put("conversationId", j.getConversationId());
                            mi.put("idempotencyKey", j.getIdempotencyKey());
                            mi.put("acceptedAt", completed);
                            mi.put("terminalStatus", "delivery_missing");
                            missingList.add(mi);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            out.put("scanError", t.getClass().getSimpleName());
        }
        out.put("checked", checked);
        out.put("verifiedDelivered", delivered);
        out.put("deliveryMissing", missing);
        out.put("pendingVerification", pendingVerification);
        out.put("deliveryMissingItems", missingList);
        // recent failures mapped to stable, safe error codes (no secrets)
        List<Map<String, Object>> fails = new ArrayList<Map<String, Object>>();
        try {
            JimAgentTriggerJob[] fj = (JimAgentTriggerJob[]) this.ao.find(JimAgentTriggerJob.class,
                    Query.select().where("STATUS = ? OR STATUS = ?", STATUS_FAILED, STATUS_DEAD_LETTER)
                            .order("ID DESC").limit(25));
            if (fj != null) {
                for (JimAgentTriggerJob j : fj) {
                    Map<String, Object> fi = new LinkedHashMap<String, Object>();
                    fi.put("jobId", j.getID());
                    fi.put("messageId", j.getMessageId());
                    fi.put("status", j.getStatus());
                    fi.put("attempts", j.getAttempts());
                    fi.put("errorCode", errorCodeFor(j.getLastError()));
                    fails.add(fi);
                }
            }
        } catch (Throwable t) {
            // best effort
        }
        out.put("recentFailures", fails);
        return out;
    }

    /** Map a sanitized last-error string to a stable, safe error code (never contains secrets). */
    private static String errorCodeFor(String lastError) {
        if (lastError == null || lastError.isEmpty()) {
            return "unknown";
        }
        String e = lastError.toLowerCase();
        if (e.contains("http 401")) return "auth_unauthorized";
        if (e.contains("http 403")) return "auth_forbidden";
        if (e.contains("http 404")) return "not_found";
        if (e.contains("http 409")) return "conflict";
        if (e.contains("http 429")) return "rate_limited";
        if (e.contains("no reachable ip") || e.contains("unreachable")) return "network_unreachable";
        if (e.contains("timed out") || e.contains("timeout")) return "network_timeout";
        if (e.contains("http 5")) return "upstream_5xx";
        if (e.contains("http 400")) return "bad_request";
        return "error";
    }

    @Override
    public void shutdown() {
        try {
            this.scheduler.shutdownNow();
        } catch (Throwable ignore) {
        }
        if (INSTANCE == this) {
            INSTANCE = null;
        }
    }

    // ---------------------------------------------------------------- helpers
    private static List<String> csv(String s) {
        if (s == null || s.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (String p : Arrays.asList(s.split(","))) {
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** 4xx auth/config error → do not retry indefinitely (dead-letter). */
    private static final class PermanentTriggerException extends Exception {
        PermanentTriggerException(String m) {
            super(m);
        }
    }
}
