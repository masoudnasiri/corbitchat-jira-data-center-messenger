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
    static final String KEY_BRANDING_TITLE = "brandingTitle";
    static final String KEY_BRANDING_LOGO_URL = "brandingLogoUrl";
    static final String KEY_WEB_PERSIAN_UI_DEFAULT = "webPersianUiDefault";
    static final String KEY_WEB_JALALI_DATES_DEFAULT = "webJalaliDatesDefault";
    static final String KEY_JIRA_WIDE_JALALI_ENABLED = "jiraWideJalaliEnabled";
    // --- Issue Report Compliance ---
    static final String KEY_REPORT_ENABLED = "reportComplianceEnabled";
    static final String KEY_REPORT_MARKER = "reportMarker";
    static final String KEY_REPORT_WINDOW_START = "reportWindowStart";
    static final String KEY_REPORT_WINDOW_END = "reportWindowEnd";
    static final String KEY_REPORT_LEVEL_FIELD = "reportLevelFieldId";
    static final String KEY_REPORT_VALID_FIELD = "reportValidCountFieldId";
    static final String KEY_REPORT_REQUIRED_FIELD = "reportRequiredCountFieldId";
    static final String DEFAULT_REPORT_MARKER = "#\u06af\u0632\u0627\u0631\u0634"; // #گزارش
    static final String DEFAULT_REPORT_WINDOW_START = "08:00";
    static final String DEFAULT_REPORT_WINDOW_END = "21:00";
    static final String DEFAULT_REPORT_LEVEL_FIELD = "customfield_10802";
    static final String DEFAULT_REPORT_VALID_FIELD = "customfield_11801";
    static final String DEFAULT_REPORT_REQUIRED_FIELD = "customfield_11900";
    /** Internal follow-up level codes (stable; independent of Persian option text). */
    static final List<String> REPORT_LEVEL_CODES = Arrays.asList("normal", "high", "special", "managerial");
    private static final List<String> INTERVAL_UNITS = Arrays.asList("minutes", "hours");
    /** Per-level reporting lifecycle / end condition. */
    private static final List<String> REPORT_ACTIVE_UNTIL = Arrays.asList("due", "done", "earlier");
    /** Per-level required-count counting mode. */
    private static final List<String> REPORT_COUNT_MODES =
            Arrays.asList("daily", "cumulative_due", "cumulative_done", "cumulative_earlier", "fixed_minimum");
    static final String DEFAULT_ACTIVE_UNTIL = "earlier";
    static final String DEFAULT_COUNT_MODE = "daily";
    static final String KEY_REPORT_ALLOW_WITHOUT_CONFIG = "reportAllowWithoutConfig";
    static final String KEY_REPORT_REOPEN_REACTIVATION = "reportReopenReactivation";
    static final String KEY_REPORT_TIMEZONE = "reportTimezone";
    // --- Working-day counter + report-receiver pause ---
    static final String KEY_REPORT_WORKING_DAYS_ENABLED = "reportWorkingDaysEnabled";
    static final String KEY_REPORT_WORKING_DAYS_CSV = "reportWorkingDaysCsv";
    static final String KEY_REPORT_HOLIDAYS_CSV = "reportHolidaysCsv";
    // --- Working calendar (board vs plugin) source selection ---
    static final String KEY_REPORT_WORKING_CALENDAR_SOURCE = "reportWorkingCalendarSource";
    static final String KEY_REPORT_DEFAULT_BOARD_ID = "reportDefaultBoardId";
    static final String KEY_REPORT_PROJECT_BOARD_MAPPING = "reportProjectBoardMapping";
    static final List<String> WORKING_CALENDAR_SOURCES = Arrays.asList(
            "jira_board_first", "plugin_calendar_only",
            "plugin_calendar_plus_board_holidays", "time_tracking_fallback");
    static final String DEFAULT_WORKING_CALENDAR_SOURCE = "time_tracking_fallback";
    static final String KEY_REPORT_RECEIVER_PAUSE_ENABLED = "reportReceiverPauseEnabled";
    static final String REPORT_MAP_PREFIX = "reportMap_";
    // --- Report Applicability Policy (reportscope) ---
    static final String KEY_REPORT_APPLICABILITY_ENABLED = "reportApplicabilityEnabled";
    static final String KEY_REPORT_EFFECTIVE_FROM_DATE = "reportEffectiveFromDate";
    static final String KEY_REPORT_APPLICABILITY_DATE_MODE = "reportApplicabilityDateMode";
    static final String KEY_REPORT_APPLICABILITY_NON_DONE_ONLY = "reportApplicabilityNonDoneOnly";
    static final String KEY_REPORT_APPLICABILITY_MISSING_DUE_POLICY = "reportApplicabilityMissingDuePolicy";
    static final List<String> APPLICABILITY_DATE_MODES = Arrays.asList(
            "created_on_or_after", "due_on_or_after",
            "created_and_due_on_or_after", "created_or_due_on_or_after",
            // Fixed global counter start: ignore created/due as scope filters; use the
            // enforcement start date only as a scoring lower bound for all issues.
            "fixed_counter_start");
    static final List<String> APPLICABILITY_MISSING_DUE_POLICIES = Arrays.asList(
            "exclude_missing_due", "include_if_created_matches");
    static final String DEFAULT_APPLICABILITY_DATE_MODE = "created_on_or_after";
    static final String DEFAULT_APPLICABILITY_MISSING_DUE_POLICY = "exclude_missing_due";
    // --- Schedule-approval field mapping (environment-specific) ---
    static final String KEY_APPROVAL_STATUS_FIELD = "approvalStatusFieldId";
    static final String KEY_APPROVAL_APPROVER_FIELD = "approvalApproverFieldId";
    static final String KEY_APPROVAL_PROPOSED_DATE_FIELD = "approvalProposedDateFieldId";
    static final String KEY_APPROVAL_APPROVED_DATE_FIELD = "approvalApprovedDateFieldId";
    static final String KEY_APPROVAL_REASON_FIELD = "approvalRejectionReasonFieldId";
    static final List<String> APPROVAL_FIELD_KEYS = Arrays.asList(
            KEY_APPROVAL_STATUS_FIELD, KEY_APPROVAL_APPROVER_FIELD, KEY_APPROVAL_PROPOSED_DATE_FIELD,
            KEY_APPROVAL_APPROVED_DATE_FIELD, KEY_APPROVAL_REASON_FIELD);
    static final String DEFAULT_BRANDING_TITLE = "CorbitChat";
    static final int MAX_BRANDING_TITLE_LENGTH = 60;
    /** Large enough to hold a base64 data URL for a ~370 KB logo image. */
    static final int MAX_BRANDING_LOGO_URL_LENGTH = 524288;
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
        return this.intValue(KEY_MAX_ATTACHMENT_SIZE_MB, 150, 1, 200);
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
    public String getBrandingTitle() {
        String value = this.stringValue(KEY_BRANDING_TITLE);
        return value == null || value.isEmpty() ? DEFAULT_BRANDING_TITLE : value;
    }

    @Override
    public String getBrandingLogoUrl() {
        String value = this.stringValue(KEY_BRANDING_LOGO_URL);
        return value != null ? value : "";
    }

    @Override
    public boolean isWebPersianUiDefault() {
        return this.boolValue(KEY_WEB_PERSIAN_UI_DEFAULT, false);
    }

    @Override
    public boolean isWebJalaliDatesDefault() {
        return this.boolValue(KEY_WEB_JALALI_DATES_DEFAULT, false);
    }

    @Override
    public boolean isJiraWideJalaliEnabled() {
        return this.boolValue(KEY_JIRA_WIDE_JALALI_ENABLED, false);
    }

    @Override
    public boolean isReportComplianceEnabled() {
        return this.boolValue(KEY_REPORT_ENABLED, false);
    }

    @Override
    public String getReportMarker() {
        String value = this.stringValue(KEY_REPORT_MARKER);
        return value == null || value.isEmpty() ? DEFAULT_REPORT_MARKER : value;
    }

    @Override
    public String getReportWindowStart() {
        return this.timeValueOr(KEY_REPORT_WINDOW_START, DEFAULT_REPORT_WINDOW_START);
    }

    @Override
    public String getReportWindowEnd() {
        return this.timeValueOr(KEY_REPORT_WINDOW_END, DEFAULT_REPORT_WINDOW_END);
    }

    @Override
    public String getReportLevelFieldId() {
        return this.fieldIdValueOr(KEY_REPORT_LEVEL_FIELD, DEFAULT_REPORT_LEVEL_FIELD);
    }

    @Override
    public String getReportValidCountFieldId() {
        return this.fieldIdValueOr(KEY_REPORT_VALID_FIELD, DEFAULT_REPORT_VALID_FIELD);
    }

    @Override
    public String getReportRequiredCountFieldId() {
        return this.fieldIdValueOr(KEY_REPORT_REQUIRED_FIELD, DEFAULT_REPORT_REQUIRED_FIELD);
    }

    @Override
    public boolean isReportAllowWithoutConfig() {
        return this.boolValue(KEY_REPORT_ALLOW_WITHOUT_CONFIG, false);
    }

    @Override
    public boolean isReportReopenReactivation() {
        return this.boolValue(KEY_REPORT_REOPEN_REACTIVATION, false);
    }

    @Override
    public String getReportTimezone() {
        String v = this.stringValue(KEY_REPORT_TIMEZONE);
        return v != null ? v : "";
    }

    @Override
    public boolean isReportWorkingDaysEnabled() {
        return this.boolValue(KEY_REPORT_WORKING_DAYS_ENABLED, true);
    }

    @Override
    public String getReportWorkingDaysCsv() {
        String v = this.stringValue(KEY_REPORT_WORKING_DAYS_CSV);
        return v != null ? v : "";
    }

    @Override
    public String getReportHolidaysCsv() {
        String v = this.stringValue(KEY_REPORT_HOLIDAYS_CSV);
        return v != null ? v : "";
    }

    @Override
    public String getReportWorkingCalendarSource() {
        return this.enumLowerValue(KEY_REPORT_WORKING_CALENDAR_SOURCE,
                WORKING_CALENDAR_SOURCES, DEFAULT_WORKING_CALENDAR_SOURCE);
    }

    @Override
    public long getReportDefaultBoardId() {
        String v = this.stringValue(KEY_REPORT_DEFAULT_BOARD_ID);
        if (v == null || v.trim().isEmpty()) {
            return 0L;
        }
        try {
            long b = Long.parseLong(v.trim());
            return b > 0 ? b : 0L;
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    @Override
    public String getReportProjectBoardMapping() {
        String v = this.stringValue(KEY_REPORT_PROJECT_BOARD_MAPPING);
        return v != null ? v : "";
    }

    @Override
    public boolean isReportReceiverPauseEnabled() {
        return this.boolValue(KEY_REPORT_RECEIVER_PAUSE_ENABLED, true);
    }

    @Override
    public boolean isReportApplicabilityEnabled() {
        return this.boolValue(KEY_REPORT_APPLICABILITY_ENABLED, false);
    }

    @Override
    public String getReportEffectiveFromDate() {
        String v = this.stringValue(KEY_REPORT_EFFECTIVE_FROM_DATE);
        return v != null && v.matches("\\d{4}-\\d{2}-\\d{2}") ? v : "";
    }

    @Override
    public String getReportApplicabilityDateMode() {
        return this.enumLowerValue(KEY_REPORT_APPLICABILITY_DATE_MODE,
                APPLICABILITY_DATE_MODES, DEFAULT_APPLICABILITY_DATE_MODE);
    }

    @Override
    public boolean isReportApplicabilityNonDoneOnly() {
        return this.boolValue(KEY_REPORT_APPLICABILITY_NON_DONE_ONLY, true);
    }

    @Override
    public String getReportApplicabilityMissingDuePolicy() {
        return this.enumLowerValue(KEY_REPORT_APPLICABILITY_MISSING_DUE_POLICY,
                APPLICABILITY_MISSING_DUE_POLICIES, DEFAULT_APPLICABILITY_MISSING_DUE_POLICY);
    }

    @Override
    public String getApprovalStatusFieldId() {
        return this.approvalFieldIdValue(KEY_APPROVAL_STATUS_FIELD);
    }

    @Override
    public String getApprovalApproverFieldId() {
        return this.approvalFieldIdValue(KEY_APPROVAL_APPROVER_FIELD);
    }

    @Override
    public String getApprovalProposedDateFieldId() {
        return this.approvalFieldIdValue(KEY_APPROVAL_PROPOSED_DATE_FIELD);
    }

    @Override
    public String getApprovalApprovedDateFieldId() {
        return this.approvalFieldIdValue(KEY_APPROVAL_APPROVED_DATE_FIELD);
    }

    @Override
    public String getApprovalRejectionReasonFieldId() {
        return this.approvalFieldIdValue(KEY_APPROVAL_REASON_FIELD);
    }

    /** Raw stored approval field id ("" when unset — resolver falls back to production id). */
    private String approvalFieldIdValue(String key) {
        String value = this.stringValue(key);
        return value != null && value.matches("customfield_\\d+") ? value : "";
    }

    @Override
    public Map<String, String> getReportFieldMappings() {
        LinkedHashMap<String, String> out = new LinkedHashMap<String, String>();
        for (com.corbitlogic.jira.internalmessenger.report.JimReportMetrics.Def d
                : com.corbitlogic.jira.internalmessenger.report.JimReportMetrics.defs()) {
            String v = this.stringValue(REPORT_MAP_PREFIX + d.key);
            out.put(d.key, v != null && v.matches("customfield_\\d+") ? v : "");
        }
        return out;
    }

    @Override
    public Map<String, Map<String, Object>> getReportLevelSchedules() {
        LinkedHashMap<String, Map<String, Object>> out = new LinkedHashMap<String, Map<String, Object>>();
        for (String code : REPORT_LEVEL_CODES) {
            LinkedHashMap<String, Object> level = new LinkedHashMap<String, Object>();
            level.put("interval", this.intValue("reportInterval_" + code, 0, 0, 100000));
            level.put("unit", this.unitValue("reportIntervalUnit_" + code));
            level.put("start", this.timeOverrideValue("reportStart_" + code));
            level.put("end", this.timeOverrideValue("reportEnd_" + code));
            level.put("activeUntil", this.enumLowerValue("reportActiveUntil_" + code, REPORT_ACTIVE_UNTIL, DEFAULT_ACTIVE_UNTIL));
            level.put("countMode", this.enumLowerValue("reportCountMode_" + code, REPORT_COUNT_MODES, DEFAULT_COUNT_MODE));
            level.put("minCount", this.intValue("reportMinCount_" + code, 1, 1, 1000));
            out.put(code, level);
        }
        return out;
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
        settings.put(KEY_BRANDING_TITLE, this.getBrandingTitle());
        settings.put(KEY_BRANDING_LOGO_URL, this.getBrandingLogoUrl());
        settings.put(KEY_WEB_PERSIAN_UI_DEFAULT, this.isWebPersianUiDefault());
        settings.put(KEY_WEB_JALALI_DATES_DEFAULT, this.isWebJalaliDatesDefault());
        settings.put(KEY_JIRA_WIDE_JALALI_ENABLED, this.isJiraWideJalaliEnabled());
        settings.put(KEY_REPORT_ENABLED, this.isReportComplianceEnabled());
        settings.put(KEY_REPORT_MARKER, this.getReportMarker());
        settings.put(KEY_REPORT_WINDOW_START, this.getReportWindowStart());
        settings.put(KEY_REPORT_WINDOW_END, this.getReportWindowEnd());
        settings.put(KEY_REPORT_LEVEL_FIELD, this.getReportLevelFieldId());
        settings.put(KEY_REPORT_VALID_FIELD, this.getReportValidCountFieldId());
        settings.put(KEY_REPORT_REQUIRED_FIELD, this.getReportRequiredCountFieldId());
        for (String code : REPORT_LEVEL_CODES) {
            Map<String, Object> level = this.getReportLevelSchedules().get(code);
            settings.put("reportInterval_" + code, level.get("interval"));
            settings.put("reportIntervalUnit_" + code, level.get("unit"));
            settings.put("reportStart_" + code, level.get("start"));
            settings.put("reportEnd_" + code, level.get("end"));
            settings.put("reportActiveUntil_" + code, level.get("activeUntil"));
            settings.put("reportCountMode_" + code, level.get("countMode"));
            settings.put("reportMinCount_" + code, level.get("minCount"));
        }
        settings.put(KEY_REPORT_ALLOW_WITHOUT_CONFIG, this.isReportAllowWithoutConfig());
        settings.put(KEY_REPORT_REOPEN_REACTIVATION, this.isReportReopenReactivation());
        settings.put(KEY_REPORT_TIMEZONE, this.getReportTimezone());
        settings.put(KEY_REPORT_WORKING_DAYS_ENABLED, this.isReportWorkingDaysEnabled());
        settings.put(KEY_REPORT_WORKING_DAYS_CSV, this.getReportWorkingDaysCsv());
        settings.put(KEY_REPORT_HOLIDAYS_CSV, this.getReportHolidaysCsv());
        settings.put(KEY_REPORT_WORKING_CALENDAR_SOURCE, this.getReportWorkingCalendarSource());
        settings.put(KEY_REPORT_DEFAULT_BOARD_ID, this.getReportDefaultBoardId());
        settings.put(KEY_REPORT_PROJECT_BOARD_MAPPING, this.getReportProjectBoardMapping());
        settings.put(KEY_REPORT_RECEIVER_PAUSE_ENABLED, this.isReportReceiverPauseEnabled());
        settings.put(KEY_REPORT_APPLICABILITY_ENABLED, this.isReportApplicabilityEnabled());
        settings.put(KEY_REPORT_EFFECTIVE_FROM_DATE, this.getReportEffectiveFromDate());
        settings.put(KEY_REPORT_APPLICABILITY_DATE_MODE, this.getReportApplicabilityDateMode());
        settings.put(KEY_REPORT_APPLICABILITY_NON_DONE_ONLY, this.isReportApplicabilityNonDoneOnly());
        settings.put(KEY_REPORT_APPLICABILITY_MISSING_DUE_POLICY, this.getReportApplicabilityMissingDuePolicy());
        Map<String, String> mappings = this.getReportFieldMappings();
        for (Map.Entry<String, String> e : mappings.entrySet()) {
            settings.put(REPORT_MAP_PREFIX + e.getKey(), e.getValue());
        }
        settings.put(KEY_APPROVAL_STATUS_FIELD, this.getApprovalStatusFieldId());
        settings.put(KEY_APPROVAL_APPROVER_FIELD, this.getApprovalApproverFieldId());
        settings.put(KEY_APPROVAL_PROPOSED_DATE_FIELD, this.getApprovalProposedDateFieldId());
        settings.put(KEY_APPROVAL_APPROVED_DATE_FIELD, this.getApprovalApprovedDateFieldId());
        settings.put(KEY_APPROVAL_REASON_FIELD, this.getApprovalRejectionReasonFieldId());
        return settings;
    }

    @Override
    public List<String> updateSettings(Map<String, Object> changes) {
        if (changes == null || changes.isEmpty()) {
            throw JimMessengerException.badRequest("No settings provided");
        }
        Map<String, Object> before = this.getAllSettings();
        // Validate + normalize ALL entries first so a bad value never leaves a
        // partially-applied change set (important for the multi-field Report
        // Schedule form).
        LinkedHashMap<String, String> normalizedChanges = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            normalizedChanges.put(entry.getKey(), this.normalizeAndValidate(entry.getKey(), entry.getValue()));
        }
        this.validateTimeOrder(normalizedChanges, before);
        ArrayList<String> descriptions = new ArrayList<String>();
        for (Map.Entry<String, String> entry : normalizedChanges.entrySet()) {
            String key = entry.getKey();
            String normalized = entry.getValue();
            Object oldValue = before.get(key);
            this.settings().put(KEY_PREFIX + key, (Object)normalized);
            descriptions.add(key + ": " + String.valueOf(oldValue) + " -> " + normalized);
        }
        return descriptions;
    }

    private String normalizeAndValidate(String key, Object rawValue) {
        String value = rawValue == null ? "" : String.valueOf(rawValue).trim();
        if (key.startsWith("report")) {
            return this.normalizeReportSetting(key, value);
        }
        if (APPROVAL_FIELD_KEYS.contains(key)) {
            // Empty = "use the production fallback id"; else a customfield id.
            if (value.isEmpty()) {
                return "";
            }
            if (!value.matches("customfield_\\d+")) {
                throw JimMessengerException.badRequest(key + " must be a customfield id or empty");
            }
            return value;
        }
        switch (key) {
            case "brandingTitle": {
                if (value.length() > MAX_BRANDING_TITLE_LENGTH) {
                    throw JimMessengerException.badRequest(key + " must be at most " + MAX_BRANDING_TITLE_LENGTH + " characters");
                }
                return value;
            }
            case "brandingLogoUrl": {
                if (value.isEmpty()) {
                    return "";
                }
                if (value.length() > MAX_BRANDING_LOGO_URL_LENGTH) {
                    throw JimMessengerException.badRequest(key + " must be at most " + MAX_BRANDING_LOGO_URL_LENGTH + " characters");
                }
                String lower = value.toLowerCase(Locale.ROOT);
                if (lower.startsWith("javascript:") || lower.startsWith("vbscript:") || lower.startsWith("file:")) {
                    throw JimMessengerException.badRequest(key + " uses an unsupported URL scheme");
                }
                boolean ok = value.startsWith("/") || lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:image/");
                if (!ok) {
                    throw JimMessengerException.badRequest(key + " must start with '/', 'http://', 'https://' or 'data:image/'");
                }
                return value;
            }
            case "chatMode": {
                return JimAdminSettingsServiceImpl.requireOneOf(key, value, CHAT_MODES);
            }
            case "notificationDetailLevel": {
                return JimAdminSettingsServiceImpl.requireOneOf(key, value, DETAIL_LEVELS);
            }
            case "webPersianUiDefault": 
            case "webJalaliDatesDefault": 
            case "jiraWideJalaliEnabled": 
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
                if (size < 1 || size > 200) {
                    throw JimMessengerException.badRequest(key + " must be between 1 and 200");
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

    /**
     * Validate/normalize the Report-Schedule settings. Times are {@code HH:mm};
     * per-level start/end may be empty ({@code ""} = "use global window"). Field
     * ids must look like {@code customfield_<digits>} so an unconfigured/typo'd
     * value can't silently target the wrong Jira field.
     */
    private String normalizeReportSetting(String key, String value) {
        if (KEY_REPORT_ENABLED.equals(key) || KEY_REPORT_ALLOW_WITHOUT_CONFIG.equals(key)
                || KEY_REPORT_REOPEN_REACTIVATION.equals(key)
                || KEY_REPORT_APPLICABILITY_ENABLED.equals(key)
                || KEY_REPORT_APPLICABILITY_NON_DONE_ONLY.equals(key)
                || KEY_REPORT_WORKING_DAYS_ENABLED.equals(key)
                || KEY_REPORT_RECEIVER_PAUSE_ENABLED.equals(key)) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw JimMessengerException.badRequest(key + " must be true or false");
            }
            return value.toLowerCase(Locale.ROOT);
        }
        if (KEY_REPORT_WORKING_DAYS_CSV.equals(key)) {
            if (value.isEmpty()) {
                return ""; // derive from Jira time-tracking days-per-week
            }
            StringBuilder norm = new StringBuilder();
            for (String part : value.split(",")) {
                String t = part.trim();
                if (t.isEmpty()) {
                    continue;
                }
                int d;
                try {
                    d = Integer.parseInt(t);
                } catch (NumberFormatException ex) {
                    throw JimMessengerException.badRequest(key + " must be a CSV of weekday numbers 1-7");
                }
                if (d < 1 || d > 7) {
                    throw JimMessengerException.badRequest(key + " weekday numbers must be 1(Sun)-7(Sat)");
                }
                if (norm.length() > 0) {
                    norm.append(',');
                }
                norm.append(d);
            }
            return norm.toString();
        }
        if (KEY_REPORT_WORKING_CALENDAR_SOURCE.equals(key)) {
            if (value.isEmpty()) {
                return DEFAULT_WORKING_CALENDAR_SOURCE;
            }
            String lower = value.toLowerCase(Locale.ROOT);
            if (!WORKING_CALENDAR_SOURCES.contains(lower)) {
                throw JimMessengerException.badRequest(key + " must be one of " + String.valueOf(WORKING_CALENDAR_SOURCES));
            }
            return lower;
        }
        if (KEY_REPORT_DEFAULT_BOARD_ID.equals(key)) {
            if (value.isEmpty() || "0".equals(value)) {
                return "";
            }
            long b;
            try {
                b = Long.parseLong(value);
            } catch (NumberFormatException ex) {
                throw JimMessengerException.badRequest(key + " must be a positive board (rapidView) id or empty");
            }
            if (b <= 0) {
                throw JimMessengerException.badRequest(key + " must be a positive board id or empty");
            }
            return String.valueOf(b);
        }
        if (KEY_REPORT_PROJECT_BOARD_MAPPING.equals(key)) {
            if (value.isEmpty()) {
                return "";
            }
            StringBuilder norm = new StringBuilder();
            for (String part : value.split(",")) {
                String t = part.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (t.indexOf('=') < 0) {
                    throw JimMessengerException.badRequest(key + " must be CSV of PROJKEY=boardId pairs");
                }
                String[] kv = t.split("=", 2);
                String pk = kv[0].trim().toUpperCase(Locale.ROOT);
                String bid = kv[1].trim();
                if (!pk.matches("[A-Z0-9_]{1,30}")) {
                    throw JimMessengerException.badRequest(key + ": invalid project key '" + kv[0].trim() + "'");
                }
                long b;
                try {
                    b = Long.parseLong(bid);
                } catch (NumberFormatException ex) {
                    throw JimMessengerException.badRequest(key + ": board id for " + pk + " must be a number");
                }
                if (b <= 0) {
                    throw JimMessengerException.badRequest(key + ": board id for " + pk + " must be positive");
                }
                if (norm.length() > 0) {
                    norm.append(',');
                }
                norm.append(pk).append('=').append(b);
            }
            return norm.toString();
        }
        if (KEY_REPORT_HOLIDAYS_CSV.equals(key)) {
            if (value.isEmpty()) {
                return "";
            }
            StringBuilder norm = new StringBuilder();
            for (String part : value.split(",")) {
                String t = part.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (!t.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    throw JimMessengerException.badRequest(key + " must be a CSV of YYYY-MM-DD dates");
                }
                if (norm.length() > 0) {
                    norm.append(',');
                }
                norm.append(t);
            }
            return norm.toString();
        }
        if (KEY_REPORT_EFFECTIVE_FROM_DATE.equals(key)) {
            if (value.isEmpty()) {
                return ""; // policy date unset
            }
            if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                throw JimMessengerException.badRequest(key + " must be an ISO date YYYY-MM-DD or empty");
            }
            // Sanity-parse the calendar components so 2026-13-40 is rejected.
            String[] p = value.split("-");
            int mo = Integer.parseInt(p[1]);
            int da = Integer.parseInt(p[2]);
            if (mo < 1 || mo > 12 || da < 1 || da > 31) {
                throw JimMessengerException.badRequest(key + " is not a valid date");
            }
            return value;
        }
        if (KEY_REPORT_APPLICABILITY_DATE_MODE.equals(key)) {
            String lower = value.toLowerCase(Locale.ROOT);
            if (!APPLICABILITY_DATE_MODES.contains(lower)) {
                throw JimMessengerException.badRequest(key + " must be one of " + APPLICABILITY_DATE_MODES);
            }
            return lower;
        }
        if (KEY_REPORT_APPLICABILITY_MISSING_DUE_POLICY.equals(key)) {
            String lower = value.toLowerCase(Locale.ROOT);
            if (!APPLICABILITY_MISSING_DUE_POLICIES.contains(lower)) {
                throw JimMessengerException.badRequest(key + " must be one of " + APPLICABILITY_MISSING_DUE_POLICIES);
            }
            return lower;
        }
        if (KEY_REPORT_TIMEZONE.equals(key)) {
            if (value.length() > 64) {
                throw JimMessengerException.badRequest(key + " is too long");
            }
            return value; // empty = use viewer tz; else a tz id / GMT offset
        }
        if (key.startsWith(REPORT_MAP_PREFIX)) {
            if (value.isEmpty()) {
                return ""; // disabled / not mapped
            }
            if (!value.matches("customfield_\\d+")) {
                throw JimMessengerException.badRequest(key + " must be a customfield id or empty");
            }
            String metricKey = key.substring(REPORT_MAP_PREFIX.length());
            com.corbitlogic.jira.internalmessenger.report.JimReportMetrics.Def def =
                    com.corbitlogic.jira.internalmessenger.report.JimReportMetrics.byKey(metricKey);
            if (def == null) {
                throw JimMessengerException.badRequest("Unknown report metric: " + metricKey);
            }
            // Type-compatibility check when the field exists on THIS instance. If
            // the field is absent (different environment) we allow the mapping to
            // be saved and skip it at write time (logged) — never crash.
            try {
                com.atlassian.jira.issue.CustomFieldManager cfm =
                        com.atlassian.jira.component.ComponentAccessor.getCustomFieldManager();
                com.atlassian.jira.issue.fields.CustomField cf =
                        cfm != null ? cfm.getCustomFieldObject(value) : null;
                if (cf != null && cf.getCustomFieldType() != null) {
                    String typeKey = cf.getCustomFieldType().getKey();
                    if (!com.corbitlogic.jira.internalmessenger.report.JimReportMetrics
                            .isCompatible(def.category, typeKey)) {
                        throw JimMessengerException.badRequest("Field " + value
                                + " is not compatible with metric " + metricKey
                                + " (expected " + def.category + ")");
                    }
                }
            } catch (JimMessengerException je) {
                throw je;
            } catch (Throwable ignore) {
                // resolution failure → allow save, validate at write time
            }
            return value;
        }
        if (KEY_REPORT_MARKER.equals(key)) {
            if (value.isEmpty()) {
                throw JimMessengerException.badRequest(key + " must not be empty");
            }
            if (value.length() > 40 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw JimMessengerException.badRequest(key + " must be a short single-line marker");
            }
            return value;
        }
        if (KEY_REPORT_WINDOW_START.equals(key) || KEY_REPORT_WINDOW_END.equals(key)) {
            if (!isValidHHmm(value)) {
                throw JimMessengerException.badRequest(key + " must be a valid HH:mm time");
            }
            return value;
        }
        if (KEY_REPORT_LEVEL_FIELD.equals(key) || KEY_REPORT_VALID_FIELD.equals(key)
                || KEY_REPORT_REQUIRED_FIELD.equals(key)) {
            if (value.isEmpty() || !value.matches("customfield_\\d+")) {
                throw JimMessengerException.badRequest(key + " must look like customfield_12345");
            }
            return value;
        }
        if (key.startsWith("reportInterval_") && !key.startsWith("reportIntervalUnit_")) {
            int minutesOrValue;
            try {
                minutesOrValue = Integer.parseInt(value.isEmpty() ? "0" : value);
            } catch (NumberFormatException ex) {
                throw JimMessengerException.badRequest(key + " must be a whole number");
            }
            if (minutesOrValue < 0 || minutesOrValue > 100000) {
                throw JimMessengerException.badRequest(key + " is out of range");
            }
            return String.valueOf(minutesOrValue);
        }
        if (key.startsWith("reportMinCount_")) {
            int min;
            try {
                min = Integer.parseInt(value.isEmpty() ? "1" : value);
            } catch (NumberFormatException ex) {
                throw JimMessengerException.badRequest(key + " must be a whole number");
            }
            if (min < 1 || min > 1000) {
                throw JimMessengerException.badRequest(key + " must be between 1 and 1000");
            }
            return String.valueOf(min);
        }
        if (key.startsWith("reportIntervalUnit_")) {
            String lower = value.toLowerCase(Locale.ROOT);
            if (!INTERVAL_UNITS.contains(lower)) {
                throw JimMessengerException.badRequest(key + " must be minutes or hours");
            }
            return lower;
        }
        if (key.startsWith("reportActiveUntil_")) {
            String lower = value.toLowerCase(Locale.ROOT);
            if (!REPORT_ACTIVE_UNTIL.contains(lower)) {
                throw JimMessengerException.badRequest(key + " must be one of " + REPORT_ACTIVE_UNTIL);
            }
            return lower;
        }
        if (key.startsWith("reportCountMode_")) {
            String lower = value.toLowerCase(Locale.ROOT);
            if (!REPORT_COUNT_MODES.contains(lower)) {
                throw JimMessengerException.badRequest(key + " must be one of " + REPORT_COUNT_MODES);
            }
            return lower;
        }
        if (key.startsWith("reportStart_") || key.startsWith("reportEnd_")) {
            if (value.isEmpty()) {
                return ""; // use global window
            }
            if (!isValidHHmm(value)) {
                throw JimMessengerException.badRequest(key + " must be empty or a valid HH:mm time");
            }
            return value;
        }
        throw JimMessengerException.badRequest("Unknown setting: " + key);
    }

    /**
     * Cross-field check: reporting-window start must be before end, for the
     * global window and each per-level override (only when both ends are set;
     * an empty per-level override means "use global"). Effective values merge
     * the incoming change set over the currently-stored values.
     */
    private void validateTimeOrder(Map<String, String> changes, Map<String, Object> before) {
        requireStartBeforeEnd("reportWindowStart", "reportWindowEnd", changes, before, false);
        for (String code : REPORT_LEVEL_CODES) {
            requireStartBeforeEnd("reportStart_" + code, "reportEnd_" + code, changes, before, true);
        }
    }

    private void requireStartBeforeEnd(String startKey, String endKey,
                                       Map<String, String> changes, Map<String, Object> before,
                                       boolean allowEmpty) {
        String start = changes.containsKey(startKey) ? changes.get(startKey) : String.valueOf(before.get(startKey));
        String end = changes.containsKey(endKey) ? changes.get(endKey) : String.valueOf(before.get(endKey));
        boolean startBlank = start == null || start.isEmpty() || "null".equals(start);
        boolean endBlank = end == null || end.isEmpty() || "null".equals(end);
        if (allowEmpty && (startBlank || endBlank)) {
            return; // per-level override uses the global window
        }
        if (startBlank || endBlank || !isValidHHmm(start) || !isValidHHmm(end)) {
            return; // individual format already validated elsewhere
        }
        if (toMinutes(start) >= toMinutes(end)) {
            throw JimMessengerException.badRequest(startKey + " must be before " + endKey);
        }
    }

    private static int toMinutes(String hhmm) {
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    private static boolean isValidHHmm(String value) {
        if (value == null || !value.matches("\\d{1,2}:\\d{2}")) {
            return false;
        }
        String[] parts = value.split(":");
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        return h >= 0 && h <= 23 && m >= 0 && m <= 59;
    }

    private String timeValueOr(String key, String defaultValue) {
        String value = this.stringValue(key);
        return value != null && isValidHHmm(value) ? value : defaultValue;
    }

    private String timeOverrideValue(String key) {
        String value = this.stringValue(key);
        return value != null && isValidHHmm(value) ? value : "";
    }

    private String fieldIdValueOr(String key, String defaultValue) {
        String value = this.stringValue(key);
        return value != null && value.matches("customfield_\\d+") ? value : defaultValue;
    }

    private String unitValue(String key) {
        String value = this.stringValue(key);
        if (value == null) {
            return "hours";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return INTERVAL_UNITS.contains(lower) ? lower : "hours";
    }

    private String enumLowerValue(String key, List<String> allowed, String defaultValue) {
        String value = this.stringValue(key);
        if (value == null) {
            return defaultValue;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return allowed.contains(lower) ? lower : defaultValue;
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

