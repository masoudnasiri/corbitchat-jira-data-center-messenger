/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.sal.api.pluginsettings.PluginSettings
 *  com.atlassian.sal.api.pluginsettings.PluginSettingsFactory
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JimAdminSettingsServiceImpl
implements JimAdminSettingsService {
    private static final String KEY_PREFIX = "com.corbitlogic.jim.admin.";
    static final String KEY_CHAT_MODE = "chatMode";
    static final String KEY_ENABLE_WEB_PUSH = "enableWebPush";
    static final String KEY_NOTIFICATION_DETAIL_LEVEL = "notificationDetailLevel";
    static final String KEY_AGGREGATE_NOTIFICATIONS = "aggregateNotifications";
    static final String KEY_MENTION_NOTIFICATIONS = "mentionNotifications";
    static final String KEY_ASSIGNMENT_NOTIFICATIONS = "assignmentNotifications";
    static final String KEY_ATTACHMENTS_ENABLED = "attachmentsEnabled";
    static final String KEY_MAX_ATTACHMENT_SIZE_MB = "maxAttachmentSizeMb";
    static final String KEY_ALLOWED_EXTENSIONS = "allowedExtensions";
    static final String KEY_IMAGE_PREVIEW_ENABLED = "imagePreviewEnabled";
    private static final List<String> CHAT_MODES = Arrays.asList("ALLOW_ALL", "RESTRICTED", "DISABLED");
    private static final List<String> DETAIL_LEVELS = Arrays.asList("FULL_MESSAGE", "SENDER_ONLY", "GENERIC_ONLY");
    private final PluginSettingsFactory pluginSettingsFactory;

    public JimAdminSettingsServiceImpl(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public String getChatMode() {
        return this.enumValue(KEY_CHAT_MODE, CHAT_MODES, "ALLOW_ALL");
    }

    @Override
    public boolean isWebPushEnabled() {
        return this.boolValue(KEY_ENABLE_WEB_PUSH, true);
    }

    @Override
    public String getNotificationDetailLevel() {
        return this.enumValue(KEY_NOTIFICATION_DETAIL_LEVEL, DETAIL_LEVELS, "FULL_MESSAGE");
    }

    @Override
    public boolean isAggregateNotifications() {
        return this.boolValue(KEY_AGGREGATE_NOTIFICATIONS, false);
    }

    @Override
    public boolean isMentionNotificationsEnabled() {
        return this.boolValue(KEY_MENTION_NOTIFICATIONS, true);
    }

    @Override
    public boolean isAssignmentNotificationsEnabled() {
        return this.boolValue(KEY_ASSIGNMENT_NOTIFICATIONS, true);
    }

    @Override
    public boolean isAttachmentsEnabled() {
        return this.boolValue(KEY_ATTACHMENTS_ENABLED, true);
    }

    @Override
    public int getMaxAttachmentSizeMb() {
        return this.intValue(KEY_MAX_ATTACHMENT_SIZE_MB, 10, 1, 100);
    }

    @Override
    public String getAllowedExtensions() {
        String value = this.stringValue(KEY_ALLOWED_EXTENSIONS);
        return value != null ? value : "";
    }

    @Override
    public boolean isImagePreviewEnabled() {
        return this.boolValue(KEY_IMAGE_PREVIEW_ENABLED, true);
    }

    @Override
    public Map<String, Object> getAllSettings() {
        LinkedHashMap<String, Object> settings = new LinkedHashMap<String, Object>();
        settings.put(KEY_CHAT_MODE, this.getChatMode());
        settings.put(KEY_ENABLE_WEB_PUSH, this.isWebPushEnabled());
        settings.put(KEY_NOTIFICATION_DETAIL_LEVEL, this.getNotificationDetailLevel());
        settings.put(KEY_AGGREGATE_NOTIFICATIONS, this.isAggregateNotifications());
        settings.put(KEY_MENTION_NOTIFICATIONS, this.isMentionNotificationsEnabled());
        settings.put(KEY_ASSIGNMENT_NOTIFICATIONS, this.isAssignmentNotificationsEnabled());
        settings.put(KEY_ATTACHMENTS_ENABLED, this.isAttachmentsEnabled());
        settings.put(KEY_MAX_ATTACHMENT_SIZE_MB, this.getMaxAttachmentSizeMb());
        settings.put(KEY_ALLOWED_EXTENSIONS, this.getAllowedExtensions());
        settings.put(KEY_IMAGE_PREVIEW_ENABLED, this.isImagePreviewEnabled());
        return settings;
    }

    @Override
    public List<String> updateSettings(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            throw JimMessengerException.badRequest("No settings provided");
        }
        Map<String, Object> before = this.getAllSettings();
        ArrayList<String> descriptions = new ArrayList<String>();
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            Object rawValue = entry.getValue();
            String normalized = this.normalizeAndValidate(key, rawValue);
            Object oldValue = before.get(key);
            this.settings().put(KEY_PREFIX + key, (Object)normalized);
            descriptions.add(key + ": " + String.valueOf(oldValue) + " -> " + normalized);
        }
        return descriptions;
    }

    private String normalizeAndValidate(String key, Object rawValue) {
        String value = rawValue == null ? "" : String.valueOf(rawValue).trim();
        switch (key) {
            case "chatMode": {
                return JimAdminSettingsServiceImpl.requireOneOf(key, value, CHAT_MODES);
            }
            case "notificationDetailLevel": {
                return JimAdminSettingsServiceImpl.requireOneOf(key, value, DETAIL_LEVELS);
            }
            case "enableWebPush": 
            case "aggregateNotifications": 
            case "mentionNotifications": 
            case "assignmentNotifications": 
            case "attachmentsEnabled": 
            case "imagePreviewEnabled": {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw JimMessengerException.badRequest(key + " must be true or false");
                }
                return value.toLowerCase(Locale.ROOT);
            }
            case "maxAttachmentSizeMb": {
                int size;
                try {
                    size = Integer.parseInt(value);
                }
                catch (NumberFormatException ex) {
                    throw JimMessengerException.badRequest(key + " must be a number");
                }
                if (size < 1 || size > 100) {
                    throw JimMessengerException.badRequest(key + " must be between 1 and 100");
                }
                return String.valueOf(size);
            }
            case "allowedExtensions": {
                if (value.isEmpty()) {
                    return "";
                }
                StringBuilder normalized = new StringBuilder();
                for (String part : value.split(",")) {
                    String ext = part.trim().toLowerCase(Locale.ROOT);
                    if (ext.startsWith(".")) {
                        ext = ext.substring(1);
                    }
                    if (ext.isEmpty()) continue;
                    if (!ext.matches("[a-z0-9]{1,12}")) {
                        throw JimMessengerException.badRequest("Invalid extension: " + part.trim());
                    }
                    if (normalized.length() > 0) {
                        normalized.append(',');
                    }
                    normalized.append(ext);
                }
                return normalized.toString();
            }
        }
        throw JimMessengerException.badRequest("Unknown setting: " + key);
    }

    private static String requireOneOf(String key, String value, List<String> allowed) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (!allowed.contains(upper)) {
            throw JimMessengerException.badRequest(key + " must be one of " + String.valueOf(allowed));
        }
        return upper;
    }

    private PluginSettings settings() {
        return this.pluginSettingsFactory.createGlobalSettings();
    }

    private String stringValue(String key) {
        Object value = this.settings().get(KEY_PREFIX + key);
        return value instanceof String ? (String)value : null;
    }

    private boolean boolValue(String key, boolean defaultValue) {
        String value = this.stringValue(key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value);
    }

    private int intValue(String key, int defaultValue, int min, int max) {
        String value = this.stringValue(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(min, Math.min(max, parsed));
        }
        catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String enumValue(String key, List<String> allowed, String defaultValue) {
        String value = this.stringValue(key);
        if (value == null) {
            return defaultValue;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return allowed.contains(upper) ? upper : defaultValue;
    }
}

