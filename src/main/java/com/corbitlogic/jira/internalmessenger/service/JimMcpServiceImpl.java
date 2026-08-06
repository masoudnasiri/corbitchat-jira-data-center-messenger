package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.corbitlogic.jira.internalmessenger.ao.JimMcpToolPolicy;

import net.java.ao.DBParam;
import net.java.ao.Query;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimMcpServiceImpl implements JimMcpService {

    private static final Logger log = LoggerFactory.getLogger(JimMcpServiceImpl.class);
    private static final String NS = "com.corbitlogic.jira.internalmessenger.mcp:";
    private static final String UPSTREAM_VERSION = "0.23.0";
    private static final String CONTRACT_HASH = "ae110094e3d3";
    private static final int SCHEMA_VERSION = 1;
    private static final int POLICY_TTL_SECONDS = 300;

    private final ActiveObjects ao;
    private final PluginSettings settings;

    public JimMcpServiceImpl(ActiveObjects ao, PluginSettingsFactory pluginSettingsFactory) {
        this.ao = ao;
        this.settings = pluginSettingsFactory.createGlobalSettings();
    }

    // ------------------------------------------------------------------ settings
    private String get(String key, String def) {
        Object v = this.settings.get(NS + key);
        return v == null ? def : String.valueOf(v);
    }

    private void put(String key, String value) {
        this.settings.put(NS + key, value);
    }

    private boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(get(key, Boolean.toString(def)));
    }

    private int getInt(String key, int def) {
        try { return Integer.parseInt(get(key, Integer.toString(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private long getLong(String key, long def) {
        try { return Long.parseLong(get(key, Long.toString(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private static final long HEARTBEAT_STALE_MS = 60000L;

    @Override
    public int getRevision() {
        return getInt("revision", 1);
    }

    private int bumpRevision() {
        int r = getRevision() + 1;
        put("revision", Integer.toString(r));
        return r;
    }

    // ------------------------------------------------------------------ seeding
    @Override
    public int seedFromRegistry() {
        int count = 0;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("mcp/tool-registry.tsv")) {
            if (in == null) {
                log.error("MCP tool-registry.tsv not found on classpath");
                return 0;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] f = line.split("\t", -1);
                if (f.length < 8) continue;
                final String name = f[0];
                final String display = f[1], toolset = f[2], cls = f[3], verif = f[4];
                final boolean available = "1".equals(f[5]);
                final boolean confirm = "1".equals(f[6]);
                final String desc = f[7].length() > 450 ? f[7].substring(0, 450) : f[7];
                JimMcpToolPolicy[] existing = this.ao.find(JimMcpToolPolicy.class,
                        Query.select().where("TOOL_NAME = ?", name));
                if (existing.length == 0) {
                    JimMcpToolPolicy row = this.ao.create(JimMcpToolPolicy.class, new DBParam[0]);
                    row.setToolName(name);
                    row.setEnabled(true);        // all tools enabled by default in DEV
                    row.setConfirm(confirm);
                    applyMeta(row, display, toolset, cls, verif, available, desc);
                    row.save();
                } else {
                    // preserve admin enabled/confirm choices; refresh only metadata
                    JimMcpToolPolicy row = existing[0];
                    applyMeta(row, display, toolset, cls, verif, available, desc);
                    row.save();
                }
                count++;
            }
        } catch (Exception e) {
            log.error("MCP seedFromRegistry failed", e);
        }
        return count;
    }

    private void applyMeta(JimMcpToolPolicy row, String display, String toolset, String cls,
                           String verif, boolean available, String desc) {
        row.setDisplayName(display);
        row.setToolset(toolset);
        row.setClassification(cls);
        row.setVerification(verif);
        row.setAvailable(available);
        row.setDescription(desc);
        row.setUpdatedAt(System.currentTimeMillis());
    }

    private JimMcpToolPolicy[] allRows() {
        return this.ao.find(JimMcpToolPolicy.class, Query.select().order("TOOL_NAME ASC"));
    }

    private void ensureSeeded() {
        if (this.ao.count(JimMcpToolPolicy.class) == 0) {
            this.ao.executeInTransaction(() -> { seedFromRegistry(); return null; });
        }
    }

    // ------------------------------------------------------------------ admin ops
    @Override
    public List<Map<String, Object>> listTools() {
        ensureSeeded();
        List<Map<String, Object>> out = new ArrayList<>();
        for (JimMcpToolPolicy r : allRows()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.getToolName());
            m.put("displayName", r.getDisplayName());
            m.put("toolset", r.getToolset());
            m.put("classification", r.getClassification());
            m.put("verification", r.getVerification());
            m.put("available", r.getAvailable());
            m.put("enabled", r.getEnabled());
            m.put("confirm", r.getConfirm());
            m.put("description", r.getDescription());
            out.add(m);
        }
        return out;
    }

    @Override
    public void setToolEnabled(String toolName, boolean enabled) {
        this.ao.executeInTransaction(() -> {
            JimMcpToolPolicy[] rows = this.ao.find(JimMcpToolPolicy.class,
                    Query.select().where("TOOL_NAME = ?", toolName));
            if (rows.length == 0) throw new IllegalArgumentException("unknown tool: " + toolName);
            rows[0].setEnabled(enabled);
            rows[0].setUpdatedAt(System.currentTimeMillis());
            rows[0].save();
            return null;
        });
        bumpRevision();
    }

    @Override
    public void setToolConfirm(String toolName, boolean confirm) {
        this.ao.executeInTransaction(() -> {
            JimMcpToolPolicy[] rows = this.ao.find(JimMcpToolPolicy.class,
                    Query.select().where("TOOL_NAME = ?", toolName));
            if (rows.length == 0) throw new IllegalArgumentException("unknown tool: " + toolName);
            rows[0].setConfirm(confirm);
            rows[0].setUpdatedAt(System.currentTimeMillis());
            rows[0].save();
            return null;
        });
        bumpRevision();
    }

    @Override
    public int bulk(String op) {
        ensureSeeded();
        final int[] n = {0};
        this.ao.executeInTransaction(() -> {
            for (JimMcpToolPolicy r : allRows()) {
                Boolean target = null;
                switch (op) {
                    case "enable_all":         target = true; break;
                    case "disable_all":        target = false; break;
                    case "enable_read":        if ("read".equals(r.getClassification())) target = true; break;
                    case "enable_write":       if ("write".equals(r.getClassification())) target = true; break;
                    case "enable_destructive": if ("destructive".equals(r.getClassification())) target = true; break;
                    case "disable_destructive":if ("destructive".equals(r.getClassification())) target = false; break;
                    default: break;
                }
                if (target != null && r.getEnabled() != target) {
                    r.setEnabled(target);
                    r.setUpdatedAt(System.currentTimeMillis());
                    r.save();
                    n[0]++;
                }
            }
            return null;
        });
        if (n[0] > 0) bumpRevision();
        return n[0];
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setGlobal(Map<String, Object> body) {
        if (body.containsKey("mcpEnabled")) put("enabled", Boolean.toString(truthy(body.get("mcpEnabled"))));
        Object classes = body.get("classes");
        if (classes instanceof Map) {
            Map<String, Object> c = (Map<String, Object>) classes;
            if (c.containsKey("read")) put("classRead", Boolean.toString(truthy(c.get("read"))));
            if (c.containsKey("write")) put("classWrite", Boolean.toString(truthy(c.get("write"))));
            if (c.containsKey("destructive")) put("classDestructive", Boolean.toString(truthy(c.get("destructive"))));
        }
        if (body.containsKey("allowedProjects")) {
            Object ap = body.get("allowedProjects");
            StringBuilder sb = new StringBuilder();
            if (ap instanceof List) {
                for (Object o : (List<Object>) ap) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(String.valueOf(o).trim().toUpperCase());
                }
            } else if (ap instanceof String) {
                sb.append(((String) ap).trim().toUpperCase());
            }
            put("allowedProjects", sb.toString());
        }
        Object limits = body.get("limits");
        if (limits instanceof Map) {
            Map<String, Object> l = (Map<String, Object>) limits;
            if (l.get("maxSearchResults") != null) put("maxSearchResults", String.valueOf(((Number) toNumber(l.get("maxSearchResults"))).intValue()));
            if (l.get("maxResponseBytes") != null) put("maxResponseBytes", String.valueOf(((Number) toNumber(l.get("maxResponseBytes"))).intValue()));
        }
        if (body.get("gatewayUrl") != null) put("gatewayUrl", String.valueOf(body.get("gatewayUrl")).trim());
        bumpRevision();
    }

    private Number toNumber(Object o) {
        if (o instanceof Number) return (Number) o;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private boolean truthy(Object o) {
        return o instanceof Boolean ? (Boolean) o : Boolean.parseBoolean(String.valueOf(o));
    }

    @Override
    public void setGatewayUrl(String url) {
        put("gatewayUrl", url == null ? "" : url.trim());
    }

    // ------------------------------------------------------------------ secrets
    private volatile boolean monitoringAdoptChecked = false;
    /** One-time bootstrap: if MCP secrets are unset and a JiraHome file
     *  {@code corbit-mcp-monitoring.secret} (line1=sharedSecret, line2=gatewayToken) exists, adopt it and
     *  delete the file. Lets the Control Plane be wired to the Gateway without admin UI access; the secret
     *  is never placed in env/logs/inspect and the transfer file is removed after adoption. */
    private void adoptMonitoringSecretIfPresent() {
        if (monitoringAdoptChecked) return;
        monitoringAdoptChecked = true;
        try {
            if (isConfigured()) return;
            java.io.File home;
            try { home = com.atlassian.jira.component.ComponentAccessor.getComponent(com.atlassian.jira.config.util.JiraHome.class).getHome(); }
            catch (Exception e) { home = new java.io.File(System.getProperty("jira.home", "/var/jira")); }
            java.io.File f = new java.io.File(home, "corbit-mcp-monitoring.secret");
            if (!f.exists()) return;
            java.util.List<String> lines = java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            if (lines.size() >= 2 && !lines.get(0).trim().isEmpty() && !lines.get(1).trim().isEmpty()) {
                configureSecrets(lines.get(0).trim(), lines.get(1).trim());
                log.info("MCP: adopted Control Plane monitoring secret from JiraHome bootstrap file");
            }
            try { java.nio.file.Files.deleteIfExists(f.toPath()); } catch (Exception ignore) { }
        } catch (Exception e) {
            log.warn("MCP monitoring-secret adoption skipped: {}", e.toString());
        }
    }

    @Override
    public void configureSecrets(String sharedSecret, String gatewayToken) {
        if (sharedSecret != null && !sharedSecret.isEmpty()) put("sharedSecret", sharedSecret);
        if (gatewayToken != null && !gatewayToken.isEmpty()) {
            // store only a hash for verification; never keep the raw token retrievable
            put("gatewayTokenHash", sha256(gatewayToken));
        }
    }

    @Override
    public boolean isConfigured() {
        return !get("sharedSecret", "").isEmpty() && !get("gatewayTokenHash", "").isEmpty();
    }

    @Override
    public boolean checkGatewayToken(String provided) {
        String stored = get("gatewayTokenHash", "");
        if (stored.isEmpty() || provided == null || provided.isEmpty()) return false;
        return constantTimeEquals(stored, sha256(provided));
    }

    private static final long SIG_SKEW_MS = 300000L;
    private static final int MAX_AUTH_FAILS_PER_MIN = 30;
    private final java.util.concurrent.ConcurrentHashMap<String, Long> seenNonces =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger authFailures =
            new java.util.concurrent.atomic.AtomicInteger();
    private volatile long authFailWindowStart = System.currentTimeMillis();

    @Override
    public String verifyGatewayRequest(String token, String method, String path,
                                       String timestamp, String nonce, String signature) {
        adoptMonitoringSecretIfPresent();
        long now = System.currentTimeMillis();
        if (now - this.authFailWindowStart > 60000L) { this.authFailWindowStart = now; this.authFailures.set(0); }
        if (this.authFailures.get() > MAX_AUTH_FAILS_PER_MIN) return "rate_limited";
        if (!checkGatewayToken(token)) { this.authFailures.incrementAndGet(); return "bad_token"; }
        String secret = get("sharedSecret", "");
        if (!secret.isEmpty()) {
            if (timestamp == null || nonce == null || signature == null) {
                this.authFailures.incrementAndGet(); return "missing_signature";
            }
            long tsMs;
            try { tsMs = Long.parseLong(timestamp) * 1000L; }
            catch (NumberFormatException e) { this.authFailures.incrementAndGet(); return "bad_timestamp"; }
            if (Math.abs(now - tsMs) > SIG_SKEW_MS) { this.authFailures.incrementAndGet(); return "stale_timestamp"; }
            for (java.util.Iterator<java.util.Map.Entry<String, Long>> it = this.seenNonces.entrySet().iterator(); it.hasNext(); ) {
                if (it.next().getValue() < now) it.remove();
            }
            if (this.seenNonces.putIfAbsent(nonce, now + SIG_SKEW_MS) != null) {
                this.authFailures.incrementAndGet(); return "replayed_nonce";
            }
            String expected = hmacSha256Hex(secret, method + "\n" + path + "\n" + timestamp + "\n" + nonce);
            if (!constantTimeEquals(expected, signature)) { this.authFailures.incrementAndGet(); return "bad_signature"; }
        }
        return null;
    }

    @Override
    public void recordHeartbeat(Map<String, Object> hb) {
        put("hbAt", Long.toString(System.currentTimeMillis()));
        put("hbStatus", hb.get("status") == null ? "up" : String.valueOf(hb.get("status")));
        put("hbBackend", Boolean.toString(truthy(hb.get("backendReachable"))));
        put("hbRevision", hb.get("policyRevision") == null ? "0" : String.valueOf(hb.get("policyRevision")));
        put("hbVersion", hb.get("version") == null ? "" : String.valueOf(hb.get("version")));
        put("hbPolicyFresh", Boolean.toString(truthy(hb.get("policyFresh"))));
        // live SERVED catalog reported by the Gateway (distinct from the plugin policy registry)
        putHb(hb, "release", "hbRelease");
        putHb(hb, "backendVersion", "hbBackendVersion");
        putHb(hb, "servedRevision", "hbServedRevision");
        putHb(hb, "toolCount", "hbServedTools");
        putHb(hb, "jiraToolCount", "hbServedJira");
        putHb(hb, "corbitToolCount", "hbServedCorbit");
        putHb(hb, "readCount", "hbServedRead");
        putHb(hb, "writeCount", "hbServedWrite");
        putHb(hb, "destructiveCount", "hbServedDestructive");
        putHb(hb, "contractHash", "hbContractHash");
        putHb(hb, "contractHashAlgorithm", "hbContractHashAlgo");
        putHb(hb, "schemaVersion", "hbSchemaVersion");
        putHb(hb, "toolRegistry", "hbToolRegistry");          // canonical class:confirm projection (Phase 2c)
        putHb(hb, "registryRevision", "hbRegistryRevision");
        put("hbOauthConfigured", Boolean.toString(truthy(hb.get("oauthConfigured"))));
        put("hbCredentialStoreHealthy", Boolean.toString(truthy(hb.get("credentialStoreHealthy"))));
        put("hbSharedFallbackEnabled", Boolean.toString(truthy(hb.get("sharedFallbackEnabled"))));
    }

    private void putHb(Map<String, Object> hb, String key, String setting) {
        if (hb.get(key) != null) put(setting, String.valueOf(hb.get(key)));
    }

    // ------------------------------------------------------------------ MCP Access management (Phase 2)
    private static final java.util.List<String> VALID_PRESETS =
            java.util.Arrays.asList("blocked", "read_only", "standard", "full_controlled");

    private java.util.List<String> csvList(String s) {
        java.util.List<String> out = new ArrayList<>();
        if (s != null) for (String p : s.split(",")) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }
    private Map<String, String> kvMap(String s) {
        Map<String, String> m = new LinkedHashMap<>();
        if (s != null) for (String pair : s.split(";")) {
            String p = pair.trim(); if (p.isEmpty()) continue;
            int i = p.indexOf('='); if (i <= 0) continue;
            m.put(p.substring(0, i).trim(), p.substring(i + 1).trim());
        }
        return m;
    }
    private String mapToKv(Map<String, String> m) {
        java.util.List<String> keys = new ArrayList<>(m.keySet()); java.util.Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) { if (sb.length() > 0) sb.append(';'); sb.append(k).append('=').append(m.get(k)); }
        return sb.toString();
    }
    private static String jstr(Object o) { return o == null ? "" : String.valueOf(o); }
    private static String sortedCsv(Object o) {
        java.util.List<String> l = new ArrayList<>();
        if (o instanceof java.util.List) for (Object x : (java.util.List<?>) o) l.add(String.valueOf(x));
        java.util.Collections.sort(l); return String.join(",", l);
    }
    private static String sortedKv(Object o) {
        java.util.List<String> l = new ArrayList<>();
        if (o instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) l.add(e.getKey() + ":" + e.getValue());
        java.util.Collections.sort(l); return String.join(";", l);
    }
    private String joinCsv(Object o) {
        if (o instanceof java.util.List) {
            StringBuilder sb = new StringBuilder();
            for (Object x : (java.util.List<?>) o) { if (sb.length() > 0) sb.append(','); sb.append(String.valueOf(x).trim()); }
            return sb.toString();
        }
        return o == null ? "" : String.valueOf(o);
    }
    private java.util.Set<String> presetClasses(String p) {
        java.util.Set<String> s = new java.util.HashSet<>();
        if ("read_only".equals(p)) s.add("read");
        else if ("standard".equals(p)) { s.add("read"); s.add("write"); }
        else if ("full_controlled".equals(p)) { s.add("read"); s.add("write"); s.add("destructive"); }
        return s;   // blocked -> empty
    }

    @Override
    public int getAccessRevision() {
        try { return Integer.parseInt(get("accessRevision", "1")); } catch (Exception e) { return 1; }
    }

    @Override
    public Map<String, Object> getAccessPolicy() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", get("accessMode", "all"));
        m.put("defaultPreset", get("accessDefaultPreset", "read_only"));
        m.put("userBlock", csvList(get("accessUserBlock", "")));
        m.put("groupBlock", csvList(get("accessGroupBlock", "")));
        Map<String, Object> up = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : kvMap(get("accessUserPolicy", "")).entrySet()) up.put(e.getKey(), e.getValue());
        Map<String, Object> gp = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : kvMap(get("accessGroupPolicy", "")).entrySet()) gp.put(e.getKey(), e.getValue());
        m.put("userPolicy", up);
        m.put("groupPolicy", gp);
        Map<String, Object> pr = new LinkedHashMap<>();
        pr.put("mode", get("accessProjectMode", "jira"));
        pr.put("allow", csvList(get("accessProjectAllow", "")));
        pr.put("block", csvList(get("accessProjectBlock", "")));
        m.put("projectRestrict", pr);
        m.put("revision", getAccessRevision());
        return m;
    }

    private Map<String, String> coercePresetMap(Object o, Map<String, Object> res) {
        if (o == null) return null;
        if (!(o instanceof Map)) { res.put("ok", false); res.put("error", "invalid_map"); res.put("message", "policy map expected"); return null; }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
            String v = String.valueOf(e.getValue());
            if (!VALID_PRESETS.contains(v)) { res.put("ok", false); res.put("error", "invalid_preset"); res.put("message", "preset '" + v + "' invalid"); return null; }
            out.put(String.valueOf(e.getKey()), v);
        }
        return out;
    }

    private void appendAccessHistory(int rev, String actor) {
        java.util.List<String> hist = new ArrayList<>(java.util.Arrays.asList(get("accessHistory", "").split("\\n")));
        hist.add(rev + ":" + (actor == null ? "?" : actor) + ":" + Instant.now().toString());
        while (hist.size() > 50) hist.remove(0);
        StringBuilder sb = new StringBuilder();
        for (String h : hist) if (!h.trim().isEmpty()) { if (sb.length() > 0) sb.append('\n'); sb.append(h); }
        put("accessHistory", sb.toString());
    }

    @Override
    public Map<String, Object> setAccessPolicy(Map<String, Object> draft, String actorUserKey) {
        Map<String, Object> res = new LinkedHashMap<>();
        if (draft == null) { res.put("ok", false); res.put("error", "bad_request"); res.put("message", "empty draft"); return res; }
        Object exp = draft.get("expectedRevision");
        if (exp != null) {
            int e; try { e = Integer.parseInt(String.valueOf(exp)); } catch (Exception ex) { e = -1; }
            if (e != getAccessRevision()) { res.put("ok", false); res.put("error", "revision_conflict"); res.put("message", "Policy changed; reload and retry"); res.put("currentRevision", getAccessRevision()); return res; }
        }
        String def = draft.containsKey("defaultPreset") ? String.valueOf(draft.get("defaultPreset")) : get("accessDefaultPreset", "read_only");
        if (!VALID_PRESETS.contains(def)) { res.put("ok", false); res.put("error", "invalid_preset"); res.put("message", "defaultPreset invalid"); return res; }
        Map<String, String> up = coercePresetMap(draft.get("userPolicy"), res); if (res.containsKey("error")) return res;
        Map<String, String> gp = coercePresetMap(draft.get("groupPolicy"), res); if (res.containsKey("error")) return res;
        if (draft.containsKey("mode")) put("accessMode", String.valueOf(draft.get("mode")));
        put("accessDefaultPreset", def);
        if (draft.containsKey("userBlock")) put("accessUserBlock", joinCsv(draft.get("userBlock")));
        if (draft.containsKey("groupBlock")) put("accessGroupBlock", joinCsv(draft.get("groupBlock")));
        if (up != null) put("accessUserPolicy", mapToKv(up));
        if (gp != null) put("accessGroupPolicy", mapToKv(gp));
        Object pr = draft.get("projectRestrict");
        if (pr instanceof Map) {
            Map<?, ?> p = (Map<?, ?>) pr;
            if (p.get("mode") != null) put("accessProjectMode", String.valueOf(p.get("mode")));
            put("accessProjectAllow", joinCsv(p.get("allow")));
            put("accessProjectBlock", joinCsv(p.get("block")));
        }
        int rev = getAccessRevision() + 1;
        put("accessRevision", Integer.toString(rev));
        appendAccessHistory(rev, actorUserKey);
        res.put("ok", true); res.put("revision", rev); res.put("policy", getAccessPolicy());
        return res;
    }

    /** MUST match the Gateway access_canonical (Python). */
    private String canonicalAccess(Map<String, Object> p) {
        Object pr = p.get("projectRestrict");
        Map<?, ?> prm = pr instanceof Map ? (Map<?, ?>) pr : new java.util.HashMap<Object, Object>();
        return "v1"
                + "|mode=" + jstr(p.get("mode"))
                + "|default=" + jstr(p.get("defaultPreset"))
                + "|userBlock=" + sortedCsv(p.get("userBlock"))
                + "|groupBlock=" + sortedCsv(p.get("groupBlock"))
                + "|userPolicy=" + sortedKv(p.get("userPolicy"))
                + "|groupPolicy=" + sortedKv(p.get("groupPolicy"))
                + "|projectMode=" + jstr(prm.get("mode"))
                + "|projectAllow=" + sortedCsv(prm.get("allow"))
                + "|projectBlock=" + sortedCsv(prm.get("block"))
                + "|rev=" + jstr(p.get("revision"));
    }

    @Override
    public Map<String, Object> buildSignedAccessPolicy() {
        Map<String, Object> p = getAccessPolicy();
        p.put("schemaVersion", 1);
        String secret = get("sharedSecret", "");
        p.put("sig", secret.isEmpty() ? "" : hmacSha256Hex(secret, canonicalAccess(p)));
        return p;
    }

    /** Canonical class/confirm from the Gateway registry projection (Phase 2c). Returns [class, confirm, source]
     *  or null when no fresh canonical source is available (caller must NOT guess). */
    private String[] canonicalToolMeta(String toolName) {
        long hbAt = getLong("hbAt", 0L);
        boolean fresh = hbAt > 0L && (System.currentTimeMillis() - hbAt) < HEARTBEAT_STALE_MS;
        String raw = get("hbToolRegistry", "");
        if (fresh && !raw.isEmpty()) {
            for (String e : raw.split(";")) {
                int i = e.indexOf(':');
                if (i <= 0) continue;
                if (e.substring(0, i).equals(toolName)) {
                    String[] p = e.split(":");
                    if (p.length >= 3) return new String[]{p[1], p[2], "gateway_registry"};
                }
            }
        }
        // fallback: jira_* tools are also in the local AO registry (classification only)
        for (JimMcpToolPolicy row : allRows()) {
            if (row.getToolName().equals(toolName)) {
                String c = row.getClassification();
                return new String[]{c, "destructive".equals(c) ? "1" : "0", "plugin_registry"};
            }
        }
        return null;   // no canonical source -> degraded (do not guess)
    }

    @Override
    public Map<String, Object> simulateAccess(String jiraUserKey, String toolName) {
        Map<String, Object> ap = getAccessPolicy();
        @SuppressWarnings("unchecked") Map<String, Object> up = (Map<String, Object>) ap.get("userPolicy");
        java.util.List<?> ub = (java.util.List<?>) ap.get("userBlock");
        String preset, source;
        if (ub != null && ub.contains(jiraUserKey)) { preset = "blocked"; source = "user_block:" + jiraUserKey; }
        else if (up != null && up.containsKey(jiraUserKey)) { preset = String.valueOf(up.get(jiraUserKey)); source = "user:" + jiraUserKey; }
        else { preset = String.valueOf(ap.get("defaultPreset")); source = "default"; }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("user", jiraUserKey); r.put("tool", toolName);
        r.put("effectivePreset", preset); r.put("matchedRule", source);
        r.put("policyRevision", getAccessRevision());
        r.put("registryRevision", get("hbRegistryRevision", ""));

        String[] meta = canonicalToolMeta(toolName);
        if (meta == null) {
            r.put("toolClass", "classification_unavailable");
            r.put("decision", "unknown");
            r.put("reason", "classification_unavailable");
            r.put("degraded", true);
            r.put("classificationSource", "none");
            r.put("note", "No fresh canonical registry projection from the Gateway; classification unavailable (not guessed). Ensure the Gateway heartbeat is healthy.");
            return r;
        }
        String cls = meta[0];
        boolean confirm = "1".equals(meta[1]);
        String minPreset = "read".equals(cls) ? "read_only" : ("destructive".equals(cls) ? "full_controlled" : "standard");
        boolean allow = presetClasses(preset).contains(cls);
        r.put("toolClass", cls);
        r.put("minimumPreset", minPreset);
        r.put("classificationSource", meta[2]);
        r.put("degraded", false);
        r.put("decision", allow ? "allow" : "deny");
        if (!allow) r.put("reason", "blocked".equals(preset) ? "user_blocked" : "overlay_class_denied");
        r.put("confirmationRequired", allow && confirm);
        r.put("repeatToConfirm", confirm);
        r.put("note", "Matches Gateway enforcement (canonical registry). Jira/plugin permissions are additionally enforced at execution time.");
        return r;
    }

    // ------------------------------------------------------------------ policy
    @Override
    public Map<String, Object> buildSignedPolicy() {
        ensureSeeded();
        boolean mcpEnabled = getBool("enabled", true);
        boolean cRead = getBool("classRead", true);
        boolean cWrite = getBool("classWrite", true);
        boolean cDestr = getBool("classDestructive", true);
        String projectsCsv = get("allowedProjects", "");
        List<String> projects = new ArrayList<>();
        for (String p : projectsCsv.split(",")) if (!p.trim().isEmpty()) projects.add(p.trim());
        java.util.Collections.sort(projects);
        int maxResults = getInt("maxSearchResults", 50);
        int maxResp = getInt("maxResponseBytes", 1048576);
        int revision = getRevision();
        String issuedAt = Instant.now().toString();

        TreeMap<String, Map<String, Object>> tools = new TreeMap<>();
        for (JimMcpToolPolicy r : allRows()) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("enabled", r.getEnabled());
            t.put("confirm", r.getConfirm());
            tools.put(r.getToolName(), t);
        }

        String canonical = canonical(SCHEMA_VERSION, revision, mcpEnabled, cRead, cWrite, cDestr,
                tools, projects, maxResults, maxResp, issuedAt, POLICY_TTL_SECONDS);
        String secret = get("sharedSecret", "");
        String sig = secret.isEmpty() ? "" : hmacSha256Hex(secret, canonical);

        Map<String, Object> classes = new LinkedHashMap<>();
        classes.put("read", cRead);
        classes.put("write", cWrite);
        classes.put("destructive", cDestr);
        Map<String, Object> access = new LinkedHashMap<>();
        access.put("allowedProjects", projects);
        access.put("allowedUsers", new ArrayList<>());
        access.put("allowedGroups", new ArrayList<>());
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("maxSearchResults", maxResults);
        limits.put("maxResponseBytes", maxResp);

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("revision", revision);
        policy.put("issuedAt", issuedAt);
        policy.put("ttlSeconds", POLICY_TTL_SECONDS);
        policy.put("mcpEnabled", mcpEnabled);
        policy.put("classes", classes);
        policy.put("tools", tools);
        policy.put("access", access);
        policy.put("limits", limits);
        policy.put("sig", sig);

        // record what we served (for diagnostics / "last sync")
        put("servedRevision", Integer.toString(revision));
        put("servedAt", issuedAt);
        return policy;
    }

    /** MUST match gateway.policy_canonical (Python). */
    private String canonical(int schemaVersion, int revision, boolean enabled,
                             boolean cRead, boolean cWrite, boolean cDestr,
                             TreeMap<String, Map<String, Object>> tools, List<String> projects,
                             int maxResults, int maxResp, String issuedAt, int ttl) {
        StringBuilder toolItems = new StringBuilder();
        for (Map.Entry<String, Map<String, Object>> e : tools.entrySet()) {
            if (toolItems.length() > 0) toolItems.append(";");
            toolItems.append(e.getKey()).append(":")
                    .append(b((Boolean) e.getValue().get("enabled"))).append(":")
                    .append(b((Boolean) e.getValue().get("confirm")));
        }
        StringBuilder proj = new StringBuilder();
        for (String p : projects) { if (proj.length() > 0) proj.append(","); proj.append(p); }
        return "v" + schemaVersion
                + "|rev=" + revision
                + "|enabled=" + b(enabled)
                + "|classes=read:" + b(cRead) + ",write:" + b(cWrite) + ",destructive:" + b(cDestr)
                + "|tools=" + toolItems
                + "|projects=" + proj
                + "|limits=maxResults:" + maxResults + ",maxResp:" + maxResp
                + "|issuedAt=" + issuedAt
                + "|ttl=" + ttl;
    }

    private String b(Boolean v) { return (v != null && v) ? "1" : "0"; }

    // ------------------------------------------------------------------ status
    @Override
    public Map<String, Object> getStatus() {
        ensureSeeded();
        adoptMonitoringSecretIfPresent();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mcpEnabled", getBool("enabled", true));
        m.put("configured", isConfigured());
        m.put("revision", getRevision());
        m.put("upstreamVersion", UPSTREAM_VERSION);
        m.put("contractHash", CONTRACT_HASH);
        String gwUrl = get("gatewayUrl", "");
        m.put("gatewayUrl", gwUrl);
        m.put("gatewayConfigured", !gwUrl.isEmpty());
        // Gateway health is derived from the Gateway's authenticated heartbeat, because the
        // Jira container cannot reach the Gateway's host-loopback bind directly.
        long hbAt = getLong("hbAt", 0L);
        long ageMs = hbAt > 0L ? System.currentTimeMillis() - hbAt : -1L;
        boolean fresh = hbAt > 0L && ageMs < HEARTBEAT_STALE_MS;
        m.put("gatewayHeartbeatAgeSec", hbAt > 0L ? ageMs / 1000L : null);
        m.put("gatewayReachable", fresh);
        m.put("gatewayStatus", fresh ? get("hbStatus", "up") : (hbAt > 0L ? "stale" : "never"));
        m.put("gatewayVersion", get("hbVersion", ""));
        m.put("backendReachable", fresh ? Boolean.valueOf(getBool("hbBackend", false)) : null);
        m.put("gatewayPolicyRevision", getInt("hbRevision", 0));
        m.put("jiraConnectivity", true);   // the plugin executes inside Jira
        // servedRevision: prefer the Gateway's live heartbeat value; fall back to what we last served
        m.put("servedRevision", fresh ? getInt("hbServedRevision", getInt("servedRevision", 0)) : getInt("servedRevision", 0));
        m.put("servedAt", get("servedAt", ""));
        m.put("gatewayRelease", get("hbRelease", ""));
        m.put("backendVersion", fresh ? get("hbBackendVersion", "") : "");
        m.put("oauthConfigured", fresh ? getBool("hbOauthConfigured", false) : false);
        m.put("credentialStoreHealthy", fresh ? getBool("hbCredentialStoreHealthy", false) : false);
        m.put("sharedFallbackEnabled", getBool("hbSharedFallbackEnabled", false));

        // LIVE served MCP catalog (reported by the Gateway heartbeat) — the authoritative public contract,
        // DISTINCT from the plugin policy registry below. Null when no fresh heartbeat has been received.
        Map<String, Object> served = new LinkedHashMap<>();
        if (fresh && getInt("hbServedTools", 0) > 0) {
            served.put("available", true);
            served.put("toolCount", getInt("hbServedTools", 0));
            served.put("jiraToolCount", getInt("hbServedJira", 0));
            served.put("corbitToolCount", getInt("hbServedCorbit", 0));
            served.put("read", getInt("hbServedRead", 0));
            served.put("write", getInt("hbServedWrite", 0));
            served.put("destructive", getInt("hbServedDestructive", 0));
            served.put("contractHash", get("hbContractHash", ""));
            served.put("contractHashAlgorithm", get("hbContractHashAlgo", ""));
            served.put("schemaVersion", getInt("hbSchemaVersion", 3));
        } else {
            served.put("available", false);
            served.put("reason", hbAt > 0L ? "heartbeat_stale" : "awaiting_first_heartbeat");
        }
        m.put("servedCatalog", served);

        int total = 0, enabled = 0, available = 0, read = 0, write = 0, destr = 0, na = 0;
        for (JimMcpToolPolicy r : allRows()) {
            total++;
            if (r.getEnabled()) enabled++;
            if (r.getAvailable()) available++;
            if ("read".equals(r.getClassification())) read++;
            else if ("write".equals(r.getClassification())) write++;
            else if ("destructive".equals(r.getClassification())) destr++;
            if ("NOT_APPLICABLE".equals(r.getVerification())) na++;
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("total", total);
        counts.put("enabled", enabled);
        counts.put("available", available);
        counts.put("read", read);
        counts.put("write", write);
        counts.put("destructive", destr);
        counts.put("notApplicable", na);
        m.put("toolCounts", counts);
        // Clearly labelled: this is the INTERNAL policy registry (tool-policy config records), NOT the
        // served MCP catalog. The served catalog is m.servedCatalog (live from the Gateway heartbeat).
        Map<String, Object> policyRegistry = new LinkedHashMap<>(counts);
        policyRegistry.put("note", "internal tool-policy registry; not the served MCP catalog");
        m.put("policyRegistry", policyRegistry);
        Map<String, Object> classes = new LinkedHashMap<>();
        classes.put("read", getBool("classRead", true));
        classes.put("write", getBool("classWrite", true));
        classes.put("destructive", getBool("classDestructive", true));
        m.put("classes", classes);
        m.put("allowedProjects", get("allowedProjects", ""));
        return m;
    }

    // ------------------------------------------------------------------ crypto
    private String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("hmac failed", e);
        }
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("sha256 failed", e);
        }
    }

    private String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
