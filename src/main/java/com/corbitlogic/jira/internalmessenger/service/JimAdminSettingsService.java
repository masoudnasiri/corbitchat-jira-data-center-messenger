/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import java.util.List;
import java.util.Map;

public interface JimAdminSettingsService {
    public static final String CHAT_MODE_ALLOW_ALL = "ALLOW_ALL";
    public static final String CHAT_MODE_RESTRICTED = "RESTRICTED";
    public static final String CHAT_MODE_DISABLED = "DISABLED";
    public static final String DETAIL_FULL_MESSAGE = "FULL_MESSAGE";
    public static final String DETAIL_SENDER_ONLY = "SENDER_ONLY";
    public static final String DETAIL_GENERIC_ONLY = "GENERIC_ONLY";

    public String getChatMode();

    public boolean isWebPushEnabled();

    public String getNotificationDetailLevel();

    public boolean isAggregateNotifications();

    public boolean isMentionNotificationsEnabled();

    public boolean isAssignmentNotificationsEnabled();

    public boolean isAttachmentsEnabled();

    public int getMaxAttachmentSizeMb();

    public String getAllowedExtensions();

    public boolean isImagePreviewEnabled();

    public Map<String, Object> getAllSettings();

    public List<String> updateSettings(Map<String, Object> var1);
}

