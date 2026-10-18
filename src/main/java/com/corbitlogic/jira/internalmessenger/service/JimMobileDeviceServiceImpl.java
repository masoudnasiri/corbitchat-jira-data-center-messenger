package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.corbitlogic.jira.internalmessenger.ao.JimMobileDevice;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import net.java.ao.DBParam;
import net.java.ao.Query;
import net.java.ao.RawEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Active Objects backed {@link JimMobileDeviceService}.
 *
 * <p>Tokens are encrypted with AES-256-GCM using a plugin-scoped key that is
 * generated once and stored in {@link PluginSettings}. The ciphertext layout is
 * {@code base64(iv[12] || ciphertext||tag)}. A SHA-256 hash of the raw token is
 * stored alongside for idempotent lookup. Raw tokens are never logged.</p>
 */
public class JimMobileDeviceServiceImpl implements JimMobileDeviceService {

    private static final Logger log = LoggerFactory.getLogger(JimMobileDeviceServiceImpl.class);

    private static final String SETTINGS_NAMESPACE = "com.corbitlogic.jira.internalmessenger.mobilepush";
    private static final String SETTING_AES_KEY = "deviceTokenAesKey";
    private static final int MAX_DEVICES_PER_USER = 10;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    /** After this many consecutive failures a device is pruned. */
    private static final int MAX_FAILURES = 5;

    private final ActiveObjects activeObjects;
    private final PluginSettingsFactory pluginSettingsFactory;
    private final Object keyLock = new Object();
    private volatile SecretKeySpec cachedKey;

    public JimMobileDeviceServiceImpl(ActiveObjects activeObjects, PluginSettingsFactory pluginSettingsFactory) {
        this.activeObjects = activeObjects;
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public JimMobileDevice register(String userKey, String rawToken, String platform,
                                    String appVersion, String deviceModel, String locale) {
        String owner = JimValidation.requireNonBlank(userKey, "userKey").trim();
        String token = JimValidation.requireNonBlank(rawToken, "token").trim();
        String normalizedPlatform = normalizePlatform(platform);
        String tokenHash = sha256Hex(token);
        String encrypted = encrypt(token);
        long now = System.currentTimeMillis();
        return this.activeObjects.executeInTransaction(() -> {
            JimMobileDevice device;
            JimMobileDevice[] existing = this.activeObjects.find(JimMobileDevice.class,
                    Query.select().where("TOKEN_HASH = ?", tokenHash).limit(1));
            if (existing.length > 0) {
                device = existing[0];
            } else {
                int count = this.activeObjects.count(JimMobileDevice.class,
                        Query.select().where("USER_KEY = ?", owner));
                if (count >= MAX_DEVICES_PER_USER) {
                    pruneOldest(owner);
                }
                device = this.activeObjects.create(JimMobileDevice.class, new DBParam[0]);
                device.setCreatedAt(now);
            }
            device.setUserKey(owner);
            device.setTokenHash(tokenHash);
            device.setTokenEnc(encrypted);
            device.setPlatform(normalizedPlatform);
            device.setAppVersion(trim(appVersion, 40));
            device.setDeviceModel(trim(deviceModel, 120));
            device.setLocale(trim(locale, 20));
            device.setEnabled(Boolean.TRUE);
            device.setFailCount(0);
            device.setUpdatedAt(now);
            device.setLastSeenAt(now);
            device.save();
            log.info("event=mobilepush stage=register outcome=success userKey={} deviceId={} platform={}",
                    owner, device.getID(), normalizedPlatform);
            return device;
        });
    }

    @Override
    public void revoke(String userKey, String rawToken) {
        if (userKey == null || rawToken == null || rawToken.trim().isEmpty()) {
            return;
        }
        String tokenHash = sha256Hex(rawToken.trim());
        this.activeObjects.executeInTransaction(() -> {
            JimMobileDevice[] rows = this.activeObjects.find(JimMobileDevice.class,
                    Query.select().where("TOKEN_HASH = ? AND USER_KEY = ?", tokenHash, userKey.trim()));
            if (rows.length > 0) {
                this.activeObjects.delete((RawEntity[]) rows);
                log.info("event=mobilepush stage=revoke outcome=success userKey={} devices={}",
                        userKey, rows.length);
            }
            return null;
        });
    }

    @Override
    public void revokeAllForUser(String userKey) {
        if (userKey == null || userKey.trim().isEmpty()) {
            return;
        }
        this.activeObjects.executeInTransaction(() -> {
            JimMobileDevice[] rows = this.activeObjects.find(JimMobileDevice.class,
                    Query.select().where("USER_KEY = ?", userKey.trim()));
            if (rows.length > 0) {
                this.activeObjects.delete((RawEntity[]) rows);
            }
            return null;
        });
    }

    @Override
    public List<JimMobileDevice> listDevices(String userKey) {
        if (userKey == null) {
            return new ArrayList<>();
        }
        JimMobileDevice[] rows = this.activeObjects.find(JimMobileDevice.class,
                Query.select().where("USER_KEY = ?", userKey.trim()).order("UPDATED_AT DESC"));
        return new ArrayList<>(Arrays.asList(rows));
    }

    @Override
    public List<JimMobileDevice> activeDevices(String userKey) {
        List<JimMobileDevice> all = listDevices(userKey);
        List<JimMobileDevice> enabled = new ArrayList<>();
        for (JimMobileDevice d : all) {
            if (Boolean.TRUE.equals(d.getEnabled())) {
                enabled.add(d);
            }
        }
        return enabled;
    }

    @Override
    public String decryptToken(JimMobileDevice device) {
        if (device == null || device.getTokenEnc() == null) {
            return null;
        }
        try {
            return decrypt(device.getTokenEnc());
        } catch (Exception ex) {
            log.warn("event=mobilepush stage=decrypt outcome=error deviceId={} message={}",
                    device.getID(), ex.getMessage());
            return null;
        }
    }

    @Override
    public void recordSendResult(JimMobileDevice device, boolean success, boolean unregistered) {
        if (device == null) {
            return;
        }
        try {
            if (unregistered) {
                this.activeObjects.delete(device);
                log.info("event=mobilepush stage=prune outcome=unregistered deviceId={}", device.getID());
                return;
            }
            long now = System.currentTimeMillis();
            if (success) {
                device.setFailCount(0);
                device.setLastSeenAt(now);
                device.save();
            } else {
                int fails = (device.getFailCount() != null ? device.getFailCount() : 0) + 1;
                if (fails >= MAX_FAILURES) {
                    this.activeObjects.delete(device);
                    log.info("event=mobilepush stage=prune outcome=too_many_failures deviceId={}", device.getID());
                } else {
                    device.setFailCount(fails);
                    device.save();
                }
            }
        } catch (RuntimeException ex) {
            log.warn("event=mobilepush stage=record_result outcome=error deviceId={} message={}",
                    device.getID(), ex.getMessage());
        }
    }

    @Override
    public int countActiveDevices() {
        return this.activeObjects.count(JimMobileDevice.class, Query.select().where("ENABLED = ?", Boolean.TRUE));
    }

    @Override
    public List<String> distinctActiveDeviceUserKeys() {
        JimMobileDevice[] rows = this.activeObjects.find(JimMobileDevice.class,
                Query.select().where("ENABLED = ?", Boolean.TRUE));
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        for (JimMobileDevice d : rows) {
            if (d.getUserKey() != null) {
                keys.add(d.getUserKey());
            }
        }
        return new ArrayList<>(keys);
    }

    // ----- helpers ------------------------------------------------------------

    private void pruneOldest(String userKey) {
        JimMobileDevice[] rows = this.activeObjects.find(JimMobileDevice.class,
                Query.select().where("USER_KEY = ?", userKey).order("UPDATED_AT ASC").limit(1));
        if (rows.length > 0) {
            this.activeObjects.delete((RawEntity[]) rows);
        }
    }

    private static String normalizePlatform(String platform) {
        if (platform == null) {
            return PLATFORM_ANDROID;
        }
        String p = platform.trim().toLowerCase(Locale.ROOT);
        return PLATFORM_IOS.equals(p) ? PLATFORM_IOS : PLATFORM_ANDROID;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > max ? t.substring(0, max) : t;
    }

    private String encrypt(String plaintext) {
        try {
            SecretKeySpec key = key();
            byte[] iv = new byte[GCM_IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt device token: " + ex.getMessage(), ex);
        }
    }

    private String decrypt(String encoded) throws Exception {
        byte[] all = Base64.getDecoder().decode(encoded);
        byte[] iv = Arrays.copyOfRange(all, 0, GCM_IV_BYTES);
        byte[] ct = Arrays.copyOfRange(all, GCM_IV_BYTES, all.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
    }

    private SecretKeySpec key() {
        if (this.cachedKey != null) {
            return this.cachedKey;
        }
        synchronized (this.keyLock) {
            if (this.cachedKey != null) {
                return this.cachedKey;
            }
            try {
                PluginSettings settings = this.pluginSettingsFactory.createSettingsForKey(SETTINGS_NAMESPACE);
                Object stored = settings.get(SETTING_AES_KEY);
                byte[] keyBytes;
                if (stored instanceof String) {
                    keyBytes = Base64.getDecoder().decode((String) stored);
                } else {
                    KeyGenerator generator = KeyGenerator.getInstance("AES");
                    generator.init(256);
                    keyBytes = generator.generateKey().getEncoded();
                    settings.put(SETTING_AES_KEY, Base64.getEncoder().encodeToString(keyBytes));
                    log.info("event=mobilepush stage=keygen outcome=success");
                }
                this.cachedKey = new SecretKeySpec(keyBytes, "AES");
                return this.cachedKey;
            } catch (Exception ex) {
                throw new IllegalStateException("Unable to initialise device token key: " + ex.getMessage(), ex);
            }
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
