/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  com.atlassian.sal.api.pluginsettings.PluginSettings
 *  com.atlassian.sal.api.pluginsettings.PluginSettingsFactory
 *  net.java.ao.DBParam
 *  net.java.ao.Query
 *  net.java.ao.RawEntity
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.corbitlogic.jira.internalmessenger.ao.JimPushSubscription;
import com.corbitlogic.jira.internalmessenger.event.JimEventExecutor;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPushService;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import net.java.ao.DBParam;
import net.java.ao.Query;
import net.java.ao.RawEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimPushServiceImpl
implements JimPushService {
    private static final Logger log = LoggerFactory.getLogger(JimPushServiceImpl.class);
    private static final String SETTINGS_NAMESPACE = "com.corbitlogic.jira.internalmessenger.push";
    private static final String SETTING_PRIVATE_KEY = "vapidPrivateKeyPkcs8";
    private static final String SETTING_PUBLIC_KEY = "vapidPublicKeyUncompressed";
    private static final String VAPID_SUBJECT = "mailto:support@corbitlogic.com";
    private static final long JWT_TTL_SECONDS = 43200L;
    private static final int PUSH_TTL_SECONDS = 86400;
    private static final int MAX_SUBSCRIPTIONS_PER_USER = 10;
    private final ActiveObjects activeObjects;
    private final PluginSettingsFactory pluginSettingsFactory;
    private final JimEventExecutor eventExecutor;
    private final JimAdminSettingsService adminSettingsService;
    private final Object keyLock = new Object();
    private volatile PrivateKey cachedPrivateKey;
    private volatile String cachedPublicKey;
    private final AtomicLong failedPushCount = new AtomicLong();

    public JimPushServiceImpl(ActiveObjects activeObjects, PluginSettingsFactory pluginSettingsFactory, JimEventExecutor eventExecutor, JimAdminSettingsService adminSettingsService) {
        this.activeObjects = activeObjects;
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.eventExecutor = eventExecutor;
        this.adminSettingsService = adminSettingsService;
    }

    @Override
    public String getVapidPublicKey() {
        this.ensureKeys();
        return this.cachedPublicKey;
    }

    @Override
    public JimPushSubscription subscribe(String userKey, String endpoint, String p256dh, String auth) {
        JimValidation.requireNonBlank(userKey, "userKey");
        String normalizedEndpoint = JimValidation.requireNonBlank(endpoint, "endpoint").trim();
        String normalizedP256dh = JimValidation.requireNonBlank(p256dh, "p256dh").trim();
        String normalizedAuth = JimValidation.requireNonBlank(auth, "auth").trim();
        if (!normalizedEndpoint.startsWith("https://")) {
            throw JimMessengerException.badRequest("Push endpoint must be an https URL");
        }
        String endpointHash = JimPushServiceImpl.sha256Hex(normalizedEndpoint);
        return (JimPushSubscription)this.activeObjects.executeInTransaction(() -> {
            JimPushSubscription subscription;
            JimPushSubscription[] existing = (JimPushSubscription[])this.activeObjects.find(JimPushSubscription.class, Query.select().where("ENDPOINT_HASH = ?", new Object[]{endpointHash}).limit(1));
            if (existing.length > 0) {
                subscription = existing[0];
            } else {
                int count = this.activeObjects.count(JimPushSubscription.class, Query.select().where("USER_KEY = ?", new Object[]{userKey}));
                if (count >= 10) {
                    this.pruneOldest(userKey);
                }
                subscription = (JimPushSubscription)this.activeObjects.create(JimPushSubscription.class, new DBParam[0]);
                subscription.setCreatedAt(System.currentTimeMillis());
            }
            subscription.setUserKey(userKey);
            subscription.setEndpoint(normalizedEndpoint);
            subscription.setEndpointHash(endpointHash);
            subscription.setP256dhKey(normalizedP256dh);
            subscription.setAuthKey(normalizedAuth);
            subscription.save();
            log.info("event=push stage=subscribe outcome=success userKey={} subscriptionId={}", (Object)userKey, (Object)subscription.getID());
            return subscription;
        });
    }

    @Override
    public void unsubscribe(String userKey, String endpoint) {
        if (userKey == null || endpoint == null || endpoint.trim().isEmpty()) {
            return;
        }
        String endpointHash = JimPushServiceImpl.sha256Hex(endpoint.trim());
        this.activeObjects.executeInTransaction(() -> {
            JimPushSubscription[] rows = (JimPushSubscription[])this.activeObjects.find(JimPushSubscription.class, Query.select().where("ENDPOINT_HASH = ? AND USER_KEY = ?", new Object[]{endpointHash, userKey}));
            if (rows.length > 0) {
                this.activeObjects.delete((RawEntity[])rows);
            }
            return null;
        });
    }

    @Override
    public List<JimPushSubscription> listSubscriptions(String userKey) {
        JimPushSubscription[] rows = (JimPushSubscription[])this.activeObjects.find(JimPushSubscription.class, Query.select().where("USER_KEY = ?", new Object[]{userKey}));
        return new ArrayList<JimPushSubscription>(Arrays.asList(rows));
    }

    @Override
    public void pushToUserAsync(String userKey) {
        this.pushToUserAsync(userKey, null, null, null);
    }

    @Override
    public void pushToUserAsync(String userKey, String title, String body, String tag) {
        if (userKey == null) {
            return;
        }
        if (!this.adminSettingsService.isWebPushEnabled()) {
            return;
        }
        String effectiveTitle = title;
        String effectiveBody = body;
        String effectiveTag = tag;
        if (title != null) {
            String detailLevel = this.adminSettingsService.getNotificationDetailLevel();
            if ("SENDER_ONLY".equals(detailLevel)) {
                effectiveBody = "You have a new message";
            } else if ("GENERIC_ONLY".equals(detailLevel)) {
                effectiveTitle = "CorbitChat";
                effectiveBody = "You have a new message";
            }
            if (this.adminSettingsService.isAggregateNotifications()) {
                effectiveTag = "jim-unread";
            }
        }
        String payloadJson = effectiveTitle != null ? JimPushServiceImpl.buildPayloadJson(effectiveTitle, effectiveBody, effectiveTag) : null;
        try {
            this.eventExecutor.submit("web_push", () -> this.pushToUser(userKey, payloadJson));
        }
        catch (RuntimeException ex) {
            log.warn("event=push stage=enqueue outcome=error userKey={} message={}", (Object)userKey, (Object)ex.getMessage());
        }
    }

    @Override
    public int countSubscriptions() {
        return this.activeObjects.count(JimPushSubscription.class);
    }

    @Override
    public long getFailedPushCount() {
        return this.failedPushCount.get();
    }

    private static String buildPayloadJson(String title, String body, String tag) {
        StringBuilder json = new StringBuilder("{\"title\":\"").append(JimPushServiceImpl.escapeJson(title)).append('\"');
        if (body != null && !body.isEmpty()) {
            json.append(",\"body\":\"").append(JimPushServiceImpl.escapeJson(body)).append('\"');
        }
        if (tag != null && !tag.isEmpty()) {
            json.append(",\"tag\":\"").append(JimPushServiceImpl.escapeJson(tag)).append('\"');
        }
        return json.append('}').toString();
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        block7: for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            switch (c) {
                case '\"': {
                    escaped.append("\\\"");
                    continue block7;
                }
                case '\\': {
                    escaped.append("\\\\");
                    continue block7;
                }
                case '\n': {
                    escaped.append("\\n");
                    continue block7;
                }
                case '\r': {
                    escaped.append("\\r");
                    continue block7;
                }
                case '\t': {
                    escaped.append("\\t");
                    continue block7;
                }
                default: {
                    if (c < ' ') {
                        escaped.append(String.format("\\u%04x", c));
                        continue block7;
                    }
                    escaped.append(c);
                }
            }
        }
        return escaped.toString();
    }

    private void pushToUser(String userKey, String payloadJson) {
        List<JimPushSubscription> subscriptions = this.listSubscriptions(userKey);
        if (subscriptions.isEmpty()) {
            return;
        }
        this.ensureKeys();
        for (JimPushSubscription subscription : subscriptions) {
            try {
                int status = -1;
                if (payloadJson != null) {
                    try {
                        status = this.sendPush(subscription, payloadJson);
                    }
                    catch (Exception encryptError) {
                        log.warn("event=push stage=encrypt outcome=error userKey={} message={}", (Object)userKey, (Object)encryptError.getMessage());
                        status = -1;
                    }
                    if (status >= 400 && status != 404 && status != 410) {
                        log.warn("event=push stage=send outcome=payload_rejected userKey={} status={} retrying=payloadless", (Object)userKey, (Object)status);
                        status = -1;
                    }
                }
                if (status == -1) {
                    status = this.sendPush(subscription, null);
                }
                if (status == 404 || status == 410) {
                    this.activeObjects.delete(new RawEntity[]{subscription});
                    log.info("event=push stage=send outcome=expired userKey={} subscriptionId={}", (Object)userKey, (Object)subscription.getID());
                    continue;
                }
                if (status < 200 || status >= 300) {
                    this.failedPushCount.incrementAndGet();
                    log.warn("event=push stage=send outcome=rejected userKey={} status={}", (Object)userKey, (Object)status);
                    continue;
                }
                log.info("event=push stage=send outcome=success userKey={} status={} payload={}", new Object[]{userKey, status, payloadJson != null});
            }
            catch (Exception ex) {
                this.failedPushCount.incrementAndGet();
                log.warn("event=push stage=send outcome=error userKey={} message={}", (Object)userKey, (Object)ex.getMessage());
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private int sendPush(JimPushSubscription subscription, String payloadJson) throws Exception {
        URL url = new URL(subscription.getEndpoint());
        String audience = url.getProtocol() + "://" + url.getHost() + (String)(url.getPort() > 0 ? ":" + url.getPort() : "");
        String jwt = this.buildVapidJwt(audience);
        byte[] bodyBytes = payloadJson != null ? JimPushServiceImpl.encryptPayload(payloadJson.getBytes(StandardCharsets.UTF_8), subscription.getP256dhKey(), subscription.getAuthKey()) : new byte[]{};
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bodyBytes.length);
            connection.setRequestProperty("TTL", String.valueOf(86400));
            connection.setRequestProperty("Urgency", "high");
            connection.setRequestProperty("Authorization", "vapid t=" + jwt + ", k=" + this.cachedPublicKey);
            if (bodyBytes.length > 0) {
                connection.setRequestProperty("Content-Type", "application/octet-stream");
                connection.setRequestProperty("Content-Encoding", "aes128gcm");
            }
            try (OutputStream out = connection.getOutputStream();){
                if (bodyBytes.length > 0) {
                    out.write(bodyBytes);
                }
            }
            int n = connection.getResponseCode();
            return n;
        }
        finally {
            connection.disconnect();
        }
    }

    private static byte[] encryptPayload(byte[] plaintext, String p256dhBase64Url, String authBase64Url) throws Exception {
        byte[] uaPublicBytes = Base64.getUrlDecoder().decode(p256dhBase64Url);
        byte[] authSecret = Base64.getUrlDecoder().decode(authBase64Url);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair appServerKeyPair = generator.generateKeyPair();
        byte[] asPublicBytes = JimPushServiceImpl.encodeUncompressedPoint((ECPublicKey)appServerKeyPair.getPublic());
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(appServerKeyPair.getPrivate());
        agreement.doPhase(JimPushServiceImpl.decodeUncompressedPoint(uaPublicBytes), true);
        byte[] ecdhSecret = agreement.generateSecret();
        byte[] prkKey = JimPushServiceImpl.hmacSha256(authSecret, ecdhSecret);
        ByteArrayOutputStream keyInfo = new ByteArrayOutputStream();
        keyInfo.write("WebPush: info".getBytes(StandardCharsets.US_ASCII));
        keyInfo.write(0);
        keyInfo.write(uaPublicBytes);
        keyInfo.write(asPublicBytes);
        keyInfo.write(1);
        byte[] ikm = JimPushServiceImpl.hmacSha256(prkKey, keyInfo.toByteArray());
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] prk = JimPushServiceImpl.hmacSha256(salt, ikm);
        byte[] cek = Arrays.copyOf(JimPushServiceImpl.hmacSha256(prk, JimPushServiceImpl.hkdfInfo("Content-Encoding: aes128gcm")), 16);
        byte[] nonce = Arrays.copyOf(JimPushServiceImpl.hmacSha256(prk, JimPushServiceImpl.hkdfInfo("Content-Encoding: nonce")), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(1, (Key)new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] record = new byte[plaintext.length + 1];
        System.arraycopy(plaintext, 0, record, 0, plaintext.length);
        record[plaintext.length] = 2;
        byte[] cipherText = cipher.doFinal(record);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(salt);
        int recordSize = 4096;
        out.write(recordSize >>> 24 & 0xFF);
        out.write(recordSize >>> 16 & 0xFF);
        out.write(recordSize >>> 8 & 0xFF);
        out.write(recordSize & 0xFF);
        out.write(asPublicBytes.length);
        out.write(asPublicBytes);
        out.write(cipherText);
        return out.toByteArray();
    }

    private static byte[] hkdfInfo(String label) {
        byte[] labelBytes = label.getBytes(StandardCharsets.US_ASCII);
        byte[] info = new byte[labelBytes.length + 2];
        System.arraycopy(labelBytes, 0, info, 0, labelBytes.length);
        info[labelBytes.length] = 0;
        info[labelBytes.length + 1] = 1;
        return info;
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static ECPublicKey decodeUncompressedPoint(byte[] encoded) throws Exception {
        if (encoded.length != 65 || encoded[0] != 4) {
            throw new IllegalArgumentException("Invalid uncompressed EC point");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(encoded, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(encoded, 33, 65));
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecSpec = parameters.getParameterSpec(ECParameterSpec.class);
        return (ECPublicKey)KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), ecSpec));
    }

    private String buildVapidJwt(String audience) throws Exception {
        long exp = System.currentTimeMillis() / 1000L + 43200L;
        String header = JimPushServiceImpl.base64Url("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = JimPushServiceImpl.base64Url(("{\"aud\":\"" + audience + "\",\"exp\":" + exp + ",\"sub\":\"mailto:support@corbitlogic.com\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(this.cachedPrivateKey);
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        byte[] derSignature = signature.sign();
        byte[] joseSignature = JimPushServiceImpl.derToJose(derSignature, 32);
        return signingInput + "." + JimPushServiceImpl.base64Url(joseSignature);
    }

    private static byte[] derToJose(byte[] der, int componentLength) {
        int offset = 2;
        if ((der[1] & 0x80) != 0) {
            offset += der[1] & 0x7F;
        }
        byte rLength = der[offset + 1];
        byte[] r = Arrays.copyOfRange(der, offset + 2, offset + 2 + rLength);
        offset = offset + 2 + rLength;
        byte sLength = der[offset + 1];
        byte[] s = Arrays.copyOfRange(der, offset + 2, offset + 2 + sLength);
        byte[] jose = new byte[componentLength * 2];
        JimPushServiceImpl.copyComponent(r, jose, 0, componentLength);
        JimPushServiceImpl.copyComponent(s, jose, componentLength, componentLength);
        return jose;
    }

    private static void copyComponent(byte[] component, byte[] target, int targetOffset, int length) {
        int start = component.length > length ? component.length - length : 0;
        int copyLength = component.length - start;
        System.arraycopy(component, start, target, targetOffset + (length - copyLength), copyLength);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void ensureKeys() {
        if (this.cachedPrivateKey != null && this.cachedPublicKey != null) {
            return;
        }
        Object object = this.keyLock;
        synchronized (object) {
            if (this.cachedPrivateKey != null && this.cachedPublicKey != null) {
                return;
            }
            try {
                PluginSettings settings = this.pluginSettingsFactory.createSettingsForKey(SETTINGS_NAMESPACE);
                Object storedPrivate = settings.get(SETTING_PRIVATE_KEY);
                Object storedPublic = settings.get(SETTING_PUBLIC_KEY);
                if (storedPrivate instanceof String && storedPublic instanceof String) {
                    byte[] pkcs8 = Base64.getDecoder().decode((String)storedPrivate);
                    KeyFactory keyFactory = KeyFactory.getInstance("EC");
                    this.cachedPrivateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
                    this.cachedPublicKey = (String)storedPublic;
                    return;
                }
                KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
                generator.initialize(new ECGenParameterSpec("secp256r1"));
                KeyPair keyPair = generator.generateKeyPair();
                String publicKey = JimPushServiceImpl.base64Url(JimPushServiceImpl.encodeUncompressedPoint((ECPublicKey)keyPair.getPublic()));
                settings.put(SETTING_PRIVATE_KEY, (Object)Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
                settings.put(SETTING_PUBLIC_KEY, (Object)publicKey);
                this.cachedPrivateKey = keyPair.getPrivate();
                this.cachedPublicKey = publicKey;
                log.info("event=push stage=keygen outcome=success");
            }
            catch (Exception ex) {
                throw new IllegalStateException("Unable to initialize VAPID keys: " + ex.getMessage(), ex);
            }
        }
    }

    private static byte[] encodeUncompressedPoint(ECPublicKey publicKey) {
        ECPoint point = publicKey.getW();
        byte[] x = JimPushServiceImpl.toFixedLength(point.getAffineX().toByteArray(), 32);
        byte[] y = JimPushServiceImpl.toFixedLength(point.getAffineY().toByteArray(), 32);
        byte[] encoded = new byte[65];
        encoded[0] = 4;
        System.arraycopy(x, 0, encoded, 1, 32);
        System.arraycopy(y, 0, encoded, 33, 32);
        return encoded;
    }

    private static byte[] toFixedLength(byte[] value, int length) {
        if (value.length == length) {
            return value;
        }
        byte[] fixed = new byte[length];
        if (value.length > length) {
            System.arraycopy(value, value.length - length, fixed, 0, length);
        } else {
            System.arraycopy(value, 0, fixed, length - value.length, value.length);
        }
        return fixed;
    }

    private void pruneOldest(String userKey) {
        JimPushSubscription[] rows = (JimPushSubscription[])this.activeObjects.find(JimPushSubscription.class, Query.select().where("USER_KEY = ?", new Object[]{userKey}).order("CREATED_AT ASC").limit(1));
        if (rows.length > 0) {
            this.activeObjects.delete((RawEntity[])rows);
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit(b >> 4 & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        }
        catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

