package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal Firebase Cloud Messaging HTTP v1 client (Sprint 05).
 *
 * <p>Uses a Google service account (project id + client email + PEM private key)
 * to mint an OAuth2 access token via the JWT-bearer flow, then posts data
 * messages to {@code /v1/projects/{projectId}/messages:send}. No third-party SDK
 * is used, matching the plugin's dependency-free HTTP style.</p>
 *
 * <p>Configuration is stored in {@link PluginSettings}. If not configured, the
 * client reports {@link #isConfigured()} == false and never attempts a send, so
 * missing credentials produce clear diagnostics rather than fake success. The
 * private key is never logged.</p>
 */
public class JimMobileFcmClient {

    private static final Logger log = LoggerFactory.getLogger(JimMobileFcmClient.class);

    private static final String SETTINGS_NAMESPACE = "com.corbitlogic.jira.internalmessenger.mobilepush";
    private static final String SETTING_PROJECT_ID = "fcmProjectId";
    private static final String SETTING_CLIENT_EMAIL = "fcmClientEmail";
    private static final String SETTING_PRIVATE_KEY = "fcmPrivateKeyPem";
    private static final String SETTING_TOKEN_URI = "fcmTokenUri";

    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final long TOKEN_SKEW_SECONDS = 120L;

    /** Outcome of a single-device send. */
    public enum SendResult { SUCCESS, UNREGISTERED, RETRYABLE, PERMANENT, NOT_CONFIGURED }

    private final PluginSettingsFactory pluginSettingsFactory;
    private final Object tokenLock = new Object();
    private volatile String cachedAccessToken;
    private volatile long cachedAccessTokenExpiry;

    public JimMobileFcmClient(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    // ----- configuration ------------------------------------------------------

    /**
     * Persist FCM service-account configuration. Accepts either the split fields
     * ({@code projectId}/{@code clientEmail}/{@code privateKey}) or the raw
     * Google service-account JSON keys ({@code project_id}/{@code client_email}/
     * {@code private_key}/{@code token_uri}) already parsed into a map.
     */
    public void configure(Map<String, Object> body) {
        String projectId = firstNonBlank(str(body, "projectId"), str(body, "project_id"));
        String clientEmail = firstNonBlank(str(body, "clientEmail"), str(body, "client_email"));
        String privateKey = firstNonBlank(str(body, "privateKey"), str(body, "private_key"));
        String tokenUri = firstNonBlank(str(body, "tokenUri"), str(body, "token_uri"));
        if (projectId == null || clientEmail == null || privateKey == null) {
            throw JimMessengerException.badRequest(
                    "projectId, clientEmail and privateKey are required (or a service-account JSON body)");
        }
        PluginSettings settings = this.pluginSettingsFactory.createSettingsForKey(SETTINGS_NAMESPACE);
        settings.put(SETTING_PROJECT_ID, projectId.trim());
        settings.put(SETTING_CLIENT_EMAIL, clientEmail.trim());
        settings.put(SETTING_PRIVATE_KEY, privateKey.trim());
        settings.put(SETTING_TOKEN_URI, tokenUri != null ? tokenUri.trim() : DEFAULT_TOKEN_URI);
        invalidateToken();
        log.info("event=mobilepush stage=configure outcome=success projectId={}", projectId.trim());
    }

    public void clearConfig() {
        PluginSettings settings = this.pluginSettingsFactory.createSettingsForKey(SETTINGS_NAMESPACE);
        settings.remove(SETTING_PROJECT_ID);
        settings.remove(SETTING_CLIENT_EMAIL);
        settings.remove(SETTING_PRIVATE_KEY);
        settings.remove(SETTING_TOKEN_URI);
        invalidateToken();
        log.info("event=mobilepush stage=configure outcome=cleared");
    }

    public boolean isConfigured() {
        PluginSettings settings = this.pluginSettingsFactory.createSettingsForKey(SETTINGS_NAMESPACE);
        return settings.get(SETTING_PROJECT_ID) instanceof String
                && settings.get(SETTING_CLIENT_EMAIL) instanceof String
                && settings.get(SETTING_PRIVATE_KEY) instanceof String;
    }

    public String getProjectId() {
        Object v = this.pluginSettingsFactory.createSettingsForKey(SETTINGS_NAMESPACE).get(SETTING_PROJECT_ID);
        return v instanceof String ? (String) v : null;
    }

    public String getClientEmail() {
        Object v = this.pluginSettingsFactory.createSettingsForKey(SETTINGS_NAMESPACE).get(SETTING_CLIENT_EMAIL);
        return v instanceof String ? (String) v : null;
    }

    // ----- send ---------------------------------------------------------------

    /**
     * Send a single message. {@code data} is the safe routing payload; a generic
     * {@code title}/{@code body} is included as an FCM notification block so the
     * OS can display it when the app is backgrounded. The raw token is used only
     * for the request and never logged.
     */
    public SendResult send(String rawToken, Map<String, String> data, String title, String body) {
        return send(rawToken, data, title, body, true);
    }

    /**
     * Send a single message. When {@code includeNotification} is false the message
     * is <b>data-only</b>: no FCM {@code notification} block is attached, so the
     * Flutter app always receives it (foreground/background/terminated) and renders
     * a rich local notification itself (sender avatar, message preview) honoring
     * the user's preferences. The raw token is used only for the request and never
     * logged.
     */
    public SendResult send(String rawToken, Map<String, String> data, String title, String body,
                           boolean includeNotification) {
        if (!isConfigured()) {
            return SendResult.NOT_CONFIGURED;
        }
        try {
            String accessToken = accessToken();
            if (accessToken == null) {
                return SendResult.RETRYABLE;
            }
            String url = "https://fcm.googleapis.com/v1/projects/" + getProjectId() + "/messages:send";
            String payload = buildMessageJson(rawToken, data, includeNotification ? title : null,
                    includeNotification ? body : null);
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            try {
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) {
                    return SendResult.SUCCESS;
                }
                String errorBody = readBody(connection.getErrorStream());
                if (status == 404 || status == 400
                        || errorBody.contains("UNREGISTERED") || errorBody.contains("INVALID_ARGUMENT")) {
                    log.info("event=mobilepush stage=send outcome=unregistered status={}", status);
                    return SendResult.UNREGISTERED;
                }
                if (status == 401 || status == 403) {
                    invalidateToken();
                    log.warn("event=mobilepush stage=send outcome=auth_error status={}", status);
                    return SendResult.RETRYABLE;
                }
                log.warn("event=mobilepush stage=send outcome=rejected status={}", status);
                return status >= 500 ? SendResult.RETRYABLE : SendResult.PERMANENT;
            } finally {
                connection.disconnect();
            }
        } catch (Exception ex) {
            log.warn("event=mobilepush stage=send outcome=error message={}", ex.getMessage());
            return SendResult.RETRYABLE;
        }
    }

    // ----- OAuth --------------------------------------------------------------

    private String accessToken() throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        if (this.cachedAccessToken != null && now < this.cachedAccessTokenExpiry - TOKEN_SKEW_SECONDS) {
            return this.cachedAccessToken;
        }
        synchronized (this.tokenLock) {
            if (this.cachedAccessToken != null && now < this.cachedAccessTokenExpiry - TOKEN_SKEW_SECONDS) {
                return this.cachedAccessToken;
            }
            PluginSettings settings = this.pluginSettingsFactory.createSettingsForKey(SETTINGS_NAMESPACE);
            String clientEmail = (String) settings.get(SETTING_CLIENT_EMAIL);
            String privateKeyPem = (String) settings.get(SETTING_PRIVATE_KEY);
            Object tokenUriObj = settings.get(SETTING_TOKEN_URI);
            String tokenUri = tokenUriObj instanceof String ? (String) tokenUriObj : DEFAULT_TOKEN_URI;

            String jwt = buildServiceAccountJwt(clientEmail, privateKeyPem, tokenUri, now);
            String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8")
                    + "&assertion=" + URLEncoder.encode(jwt, "UTF-8");

            HttpURLConnection connection = (HttpURLConnection) new URL(tokenUri).openConnection();
            try {
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                byte[] bytes = form.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    log.warn("event=mobilepush stage=oauth outcome=error status={}", status);
                    return null;
                }
                String responseBody = readBody(connection.getInputStream());
                String accessToken = extractJsonString(responseBody, "access_token");
                long expiresIn = extractJsonLong(responseBody, "expires_in", 3600L);
                if (accessToken == null) {
                    return null;
                }
                this.cachedAccessToken = accessToken;
                this.cachedAccessTokenExpiry = now + expiresIn;
                return accessToken;
            } finally {
                connection.disconnect();
            }
        }
    }

    private void invalidateToken() {
        this.cachedAccessToken = null;
        this.cachedAccessTokenExpiry = 0L;
    }

    private static String buildServiceAccountJwt(String clientEmail, String privateKeyPem,
                                                 String tokenUri, long now) throws Exception {
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        long exp = now + 3600L;
        String claims = "{\"iss\":\"" + clientEmail + "\",\"scope\":\"" + SCOPE
                + "\",\"aud\":\"" + tokenUri + "\",\"iat\":" + now + ",\"exp\":" + exp + "}";
        String signingInput = header + "." + base64Url(claims.getBytes(StandardCharsets.UTF_8));
        PrivateKey privateKey = parsePrivateKey(privateKeyPem);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + base64Url(signature.sign());
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    // ----- JSON (build minimal, parse controlled Google responses) ------------

    private static String buildMessageJson(String token, Map<String, String> data, String title, String body) {
        StringBuilder json = new StringBuilder("{\"message\":{");
        json.append("\"token\":\"").append(escape(token)).append('"');
        if (data != null && !data.isEmpty()) {
            json.append(",\"data\":{");
            boolean first = true;
            for (Map.Entry<String, String> e : data.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append('"').append(escape(e.getKey())).append("\":\"").append(escape(e.getValue())).append('"');
            }
            json.append('}');
        }
        if (title != null || body != null) {
            json.append(",\"notification\":{");
            json.append("\"title\":\"").append(escape(title != null ? title : "CorbitHub")).append('"');
            json.append(",\"body\":\"").append(escape(body != null ? body : "You have a new notification")).append('"');
            json.append('}');
        }
        json.append(",\"android\":{\"priority\":\"high\"}");
        json.append("}}");
        return json.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String extractJsonString(String json, String key) {
        if (json == null) {
            return null;
        }
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return null;
        }
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = firstQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(json.charAt(++i));
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static long extractJsonLong(String json, String key, long fallback) {
        if (json == null) {
            return fallback;
        }
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return fallback;
        }
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return fallback;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = colon + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
            } else if (sb.length() > 0) {
                break;
            }
        }
        try {
            return sb.length() > 0 ? Long.parseLong(sb.toString()) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String readBody(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a;
        }
        return b != null && !b.trim().isEmpty() ? b : null;
    }
}
