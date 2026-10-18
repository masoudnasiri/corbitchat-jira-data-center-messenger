package com.corbitlogic.jira.internalmessenger.schedule;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.fields.screen.FieldScreenLayoutItem;
import com.atlassian.jira.issue.fields.screen.FieldScreenManager;
import com.atlassian.jira.issue.fields.screen.FieldScreenTab;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Environment-independent resolver for the schedule-approval custom fields.
 *
 * <p>Historically the approval workflow hardcoded the PRODUCTION custom-field
 * ids ({@code customfield_11503} etc.). Those ids do not exist on DEV/Server-2,
 * and any field re-created there gets a different id, so the hardcoded approach
 * could never work off the reference instance.</p>
 *
 * <p>This class introduces stable <em>logical keys</em> and resolves each to a
 * concrete Jira {@link CustomField} using a three-step policy:</p>
 * <ol>
 *   <li>the admin-configured field id for this environment (plugin settings), then</li>
 *   <li>the PRODUCTION fallback id (so existing production behaviour never breaks), then</li>
 *   <li>{@code null} — the field is <em>not configured</em> on this instance.</li>
 * </ol>
 *
 * <p>Every resolution is type-guarded: an id that resolves to a field of the
 * wrong type is treated as unresolved (never silently used). This class never
 * logs field values and never mutates configuration — provisioning lives in
 * {@link JimApprovalFieldProvisioning}.</p>
 */
public final class JimApprovalFields {

    /** Stable logical keys, independent of any Jira instance's numeric ids. */
    public enum Key {
        STATUS("corbit.approval.status", "Approval Status", "\u0648\u0636\u0639\u06cc\u062a \u062a\u0627\u06cc\u06cc\u062f"),
        APPROVER("corbit.approval.approver", "Approver", "\u062a\u0627\u06cc\u06cc\u062f\u200c\u06a9\u0646\u0646\u062f\u0647"),
        PROPOSED_DATE("corbit.approval.proposedDate", "Proposed Completion Date", "\u062a\u0627\u0631\u06cc\u062e \u067e\u06cc\u0634\u0646\u0647\u0627\u062f\u06cc"),
        APPROVED_DATE("corbit.approval.approvedDate", "Approved Completion Date", "\u062a\u0627\u0631\u06cc\u062e \u062a\u0627\u06cc\u06cc\u062f\u200c\u0634\u062f\u0647"),
        REJECTION_REASON("corbit.approval.rejectionReason", "Rejection Reason", "\u062f\u0644\u06cc\u0644 \u0631\u062f");

        public final String logicalKey;
        public final String nameEn;
        public final String nameFa;

        Key(String logicalKey, String nameEn, String nameFa) {
            this.logicalKey = logicalKey;
            this.nameEn = nameEn;
            this.nameFa = nameFa;
        }

        public static Key byLogicalKey(String logicalKey) {
            if (logicalKey == null) {
                return null;
            }
            for (Key k : values()) {
                if (k.logicalKey.equals(logicalKey)) {
                    return k;
                }
            }
            return null;
        }
    }

    /**
     * PRODUCTION fallback ids — the confirmed ids on the reference production
     * instance. Kept ONLY as a fallback so an un-migrated production keeps
     * working; never used as the long-term source of truth.
     */
    public static final Map<Key, String> PRODUCTION_FALLBACK;
    static {
        Map<Key, String> m = new LinkedHashMap<>();
        m.put(Key.STATUS, "customfield_11503");
        m.put(Key.APPROVER, "customfield_11305");
        m.put(Key.PROPOSED_DATE, "customfield_11309");
        m.put(Key.APPROVED_DATE, "customfield_11501");
        m.put(Key.REJECTION_REASON, "customfield_11502");
        PRODUCTION_FALLBACK = Collections.unmodifiableMap(m);
    }

    /** Accepted custom-field type-key substrings per logical key (type guard). */
    private static final Map<Key, String[]> EXPECTED_TYPES;
    static {
        Map<Key, String[]> m = new LinkedHashMap<>();
        m.put(Key.STATUS, new String[]{"select", "radiobuttons"});
        m.put(Key.APPROVER, new String[]{"userpicker"});
        m.put(Key.PROPOSED_DATE, new String[]{"datepicker", "datetime"});
        m.put(Key.APPROVED_DATE, new String[]{"datepicker", "datetime"});
        m.put(Key.REJECTION_REASON, new String[]{"textarea", "textfield"});
        EXPECTED_TYPES = Collections.unmodifiableMap(m);
    }

    /** Required select options for the status field (exact display values). */
    public static final List<String> REQUIRED_STATUS_OPTIONS =
            Collections.unmodifiableList(Arrays.asList("Pending", "Approved", "Rejected"));

    private JimApprovalFields() {
    }

    private static JimAdminSettingsService admin() {
        return ComponentAccessor.getOSGiComponentInstanceOfType(JimAdminSettingsService.class);
    }

    /** Raw admin-configured field id for a logical key, or empty when unset. */
    public static String configuredId(Key key) {
        JimAdminSettingsService a = admin();
        if (a == null || key == null) {
            return "";
        }
        String v;
        switch (key) {
            case STATUS: v = a.getApprovalStatusFieldId(); break;
            case APPROVER: v = a.getApprovalApproverFieldId(); break;
            case PROPOSED_DATE: v = a.getApprovalProposedDateFieldId(); break;
            case APPROVED_DATE: v = a.getApprovalApprovedDateFieldId(); break;
            case REJECTION_REASON: v = a.getApprovalRejectionReasonFieldId(); break;
            default: v = "";
        }
        return v != null ? v.trim() : "";
    }

    /** PRODUCTION fallback id for a logical key. */
    public static String fallbackId(Key key) {
        return PRODUCTION_FALLBACK.get(key);
    }

    /** Admin-settings storage key for a logical key (used to persist a mapping). */
    public static String settingsKey(Key key) {
        switch (key) {
            case STATUS: return "approvalStatusFieldId";
            case APPROVER: return "approvalApproverFieldId";
            case PROPOSED_DATE: return "approvalProposedDateFieldId";
            case APPROVED_DATE: return "approvalApprovedDateFieldId";
            case REJECTION_REASON: return "approvalRejectionReasonFieldId";
            default: return null;
        }
    }

    /** Accepted custom-field type-key substrings for a logical key (type guard). */
    public static String[] expectedTypeSubstrings(Key key) {
        return EXPECTED_TYPES.get(key);
    }

    /**
     * Resolve the concrete Jira field for a logical key using the
     * configured-id → production-fallback → null policy, type-guarded.
     */
    public static CustomField resolve(Key key) {
        if (key == null) {
            return null;
        }
        CustomField cf = fieldOfType(configuredId(key), EXPECTED_TYPES.get(key));
        if (cf != null) {
            return cf;
        }
        return fieldOfType(fallbackId(key), EXPECTED_TYPES.get(key));
    }

    /** The id that {@link #resolve(Key)} actually used, or null when notConfigured. */
    public static String effectiveId(Key key) {
        CustomField cf = resolve(key);
        return cf != null ? cf.getId() : null;
    }

    /** True when resolution fell back to the production id (configured id absent/unresolved). */
    public static boolean usingFallback(Key key) {
        CustomField configured = fieldOfType(configuredId(key), EXPECTED_TYPES.get(key));
        return configured == null && resolve(key) != null;
    }

    private static CustomField fieldOfType(String id, String[] typeKeySubstrings) {
        if (id == null || id.isEmpty() || typeKeySubstrings == null) {
            return null;
        }
        try {
            CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
            if (cfm == null) {
                return null;
            }
            CustomField cf = cfm.getCustomFieldObject(id);
            if (cf == null || cf.getCustomFieldType() == null) {
                return null;
            }
            String typeKey = cf.getCustomFieldType().getKey();
            if (typeKey == null) {
                return null;
            }
            String lower = typeKey.toLowerCase();
            for (String sub : typeKeySubstrings) {
                if (lower.contains(sub)) {
                    return cf;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Any custom field for an id, regardless of type (for diagnostics). */
    private static CustomField anyField(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
            return cfm != null ? cfm.getCustomFieldObject(id) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // --- Option / context / screen checks ------------------------------------

    /** The display values of the status field's options on its default config. */
    public static List<String> statusOptionValues(CustomField statusCf) {
        List<String> out = new ArrayList<>();
        if (statusCf == null) {
            return out;
        }
        try {
            OptionsManager om = ComponentAccessor.getComponent(OptionsManager.class);
            List<FieldConfigScheme> schemes = statusCf.getConfigurationSchemes();
            if (om == null || schemes == null) {
                return out;
            }
            for (FieldConfigScheme scheme : schemes) {
                if (scheme == null || scheme.getOneAndOnlyConfig() == null) {
                    continue;
                }
                Options options = om.getOptions(scheme.getOneAndOnlyConfig());
                if (options != null) {
                    for (Option o : options) {
                        if (o != null && o.getValue() != null && !out.contains(o.getValue())) {
                            out.add(o.getValue());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** True when the status field carries all of Pending/Approved/Rejected. */
    public static boolean statusOptionsOk(CustomField statusCf) {
        if (statusCf == null) {
            return false;
        }
        List<String> present = statusOptionValues(statusCf);
        for (String required : REQUIRED_STATUS_OPTIONS) {
            boolean found = false;
            for (String p : present) {
                if (required.equalsIgnoreCase(p)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /** True when the field has at least one configuration scheme (context). */
    public static boolean hasContext(CustomField cf) {
        if (cf == null) {
            return false;
        }
        try {
            List<FieldConfigScheme> schemes = cf.getConfigurationSchemes();
            return schemes != null && !schemes.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Best-effort check that the field appears on at least one Jira screen. */
    public static boolean onAnyScreen(CustomField cf) {
        if (cf == null) {
            return false;
        }
        try {
            FieldScreenManager fsm = ComponentAccessor.getFieldScreenManager();
            if (fsm == null) {
                return false;
            }
            String fieldId = cf.getId();
            for (FieldScreen screen : fsm.getFieldScreens()) {
                if (screen == null) {
                    continue;
                }
                for (FieldScreenTab tab : screen.getTabs()) {
                    if (tab == null) {
                        continue;
                    }
                    for (FieldScreenLayoutItem item : tab.getFieldScreenLayoutItems()) {
                        if (item != null && fieldId.equals(item.getFieldId())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // --- Diagnostics ----------------------------------------------------------

    /**
     * Full diagnostic record for one logical key: configured/effective ids,
     * whether the fallback is in use, and exists/type/options/context/screen
     * health. Never throws.
     */
    public static Map<String, Object> diagnostics(Key key) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        String configured = configuredId(key);
        String fallback = fallbackId(key);
        CustomField resolved = resolve(key);
        String effective = resolved != null ? resolved.getId() : null;
        boolean usingFallback = usingFallback(key);

        // "exists" reflects whether SOME field object exists for the effective/
        // configured/fallback id, even if the type is wrong.
        CustomField existing = resolved;
        if (existing == null) {
            existing = anyField(configured);
        }
        if (existing == null) {
            existing = anyField(fallback);
        }

        m.put("logicalKey", key.logicalKey);
        m.put("nameEn", key.nameEn);
        m.put("nameFa", key.nameFa);
        m.put("expectedType", String.join("|", EXPECTED_TYPES.get(key)));
        m.put("configuredFieldId", configured == null || configured.isEmpty() ? null : configured);
        m.put("fallbackFieldId", fallback);
        m.put("effectiveFieldId", effective);
        m.put("usingFallback", usingFallback);
        m.put("exists", existing != null);
        m.put("fieldName", existing != null ? safeName(existing) : null);
        m.put("fieldType", existing != null && existing.getCustomFieldType() != null
                ? existing.getCustomFieldType().getKey() : null);
        m.put("typeOk", resolved != null);
        boolean optionsRequired = key == Key.STATUS;
        m.put("optionsRequired", optionsRequired);
        m.put("optionsOk", optionsRequired ? statusOptionsOk(resolved) : true);
        if (optionsRequired) {
            m.put("options", statusOptionValues(resolved));
            m.put("requiredOptions", REQUIRED_STATUS_OPTIONS);
        }
        m.put("contextOk", hasContext(resolved));
        m.put("screenOk", onAnyScreen(resolved));
        m.put("notConfigured", resolved == null);
        return m;
    }

    public static List<Map<String, Object>> diagnosticsAll() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Key k : Key.values()) {
            out.add(diagnostics(k));
        }
        return out;
    }

    /**
     * True when the minimum set of approval fields resolve: status + approver +
     * proposed date. Mirrors {@link JimScheduleApprovalConfig#isConfigured()}.
     */
    public static boolean minimallyConfigured() {
        return resolve(Key.STATUS) != null
                && resolve(Key.APPROVER) != null
                && resolve(Key.PROPOSED_DATE) != null;
    }

    private static String safeName(CustomField cf) {
        try {
            return cf.getFieldName();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Validate that a candidate field id is type-compatible with a logical key
     * (used by the admin "map existing field" action). Returns null when OK,
     * otherwise a human-readable reason.
     */
    public static String validateMappingCandidate(Key key, String fieldId) {
        if (key == null) {
            return "Unknown logical key";
        }
        if (fieldId == null || !fieldId.matches("customfield_\\d+")) {
            return "Field id must look like customfield_12345";
        }
        CustomField cf = anyField(fieldId);
        if (cf == null || cf.getCustomFieldType() == null) {
            return "Field " + fieldId + " does not exist on this instance";
        }
        String typeKey = cf.getCustomFieldType().getKey();
        String lower = typeKey != null ? typeKey.toLowerCase() : "";
        boolean typeOk = false;
        for (String sub : EXPECTED_TYPES.get(key)) {
            if (lower.contains(sub)) {
                typeOk = true;
                break;
            }
        }
        if (!typeOk) {
            return "Field " + fieldId + " has type " + typeKey + " but " + key.logicalKey
                    + " requires " + String.join("|", EXPECTED_TYPES.get(key));
        }
        if (key == Key.STATUS && !statusOptionsOk(cf)) {
            return "Field " + fieldId + " is missing required options ("
                    + String.join(", ", REQUIRED_STATUS_OPTIONS) + ")";
        }
        return null;
    }

    /** Read an issue's raw config for a status field (kept for callers). */
    static FieldConfig relevantConfig(CustomField cf, Issue issue) {
        if (cf == null) {
            return null;
        }
        try {
            return issue != null ? cf.getRelevantConfig(issue) : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
