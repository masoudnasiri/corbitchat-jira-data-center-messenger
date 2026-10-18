package com.corbitlogic.jira.internalmessenger.mobile;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.layout.field.FieldLayout;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Colored-pill metadata for the "task type" custom field
 * ({@value #FIELD_ID}, "نوع تسک (طبق دستورالعمل)") — Sprint 04B.
 *
 * <p>The field's stored values use Persian text, but in practice the data mixes
 * Arabic and Persian letter forms (e.g. {@code فوري} with an Arabic yeh) and
 * ZWNJ/space variants (e.g. {@code پروژه ای} vs {@code پروژه‌ای}). We normalize
 * all of those to a single canonical key before mapping to a pill so the UI is
 * consistent regardless of how the value was entered.</p>
 *
 * <p>The mobile app renders the returned metadata generically (a pill), so this
 * mapping is not duplicated per screen. Unknown values return {@code null} and
 * the client falls back to plain text.</p>
 */
public final class JimMobileTaskType {

    /** Jira id of the task-type custom field. Keyed by id, never by name. */
    public static final String FIELD_ID = "customfield_10903";

    private JimMobileTaskType() {
    }

    private static final class Spec {
        final String key;
        final String labelEn;
        final String labelFa;
        final String textColor;
        final String backgroundColor;

        Spec(String key, String labelEn, String labelFa, String textColor, String backgroundColor) {
            this.key = key;
            this.labelEn = labelEn;
            this.labelFa = labelFa;
            this.textColor = textColor;
            this.backgroundColor = backgroundColor;
        }
    }

    /** Canonical(normalized) value -> pill spec. */
    private static final Map<String, Spec> MAP = new LinkedHashMap<>();

    private static void register(Spec spec, String... values) {
        for (String v : values) {
            MAP.put(normalize(v), spec);
        }
    }

    static {
        register(new Spec("critical", "Critical", "بحرانی", "#DE350B", "#FFEBE6"),
                "بحرانی", "critical");
        register(new Spec("urgent", "Urgent", "فوری", "#FF8B00", "#FFF0B3"),
                "فوری", "urgent");
        register(new Spec("committed", "Committed", "تعهدی", "#FFAB00", "#FFF7D6"),
                "تعهدی", "committed");
        register(new Spec("current", "Current", "جاری", "#0052CC", "#DEEBFF"),
                "جاری", "current");
        register(new Spec("project", "Project-Based", "پروژه‌ای", "#6554C0", "#EAE6FF"),
                "پروژه‌ای", "پروژه ای", "project-based", "project");
        register(new Spec("development", "Development", "توسعه‌ای", "#00875A", "#E3FCEF"),
                "توسعه‌ای", "توسعه ای", "development");
        register(new Spec("strategic", "Strategic", "راهبردی", "#172B4D", "#DFE1E6"),
                "راهبردی", "strategic");
    }

    /**
     * Read {@value #FIELD_ID} from an issue (respecting field visibility) and
     * return its pill metadata, or {@code null} when the field is absent,
     * hidden, empty or holds an unknown value.
     */
    public static Map<String, Object> pillForIssue(Issue issue) {
        try {
            CustomField cf = ComponentAccessor.getCustomFieldManager().getCustomFieldObject(FIELD_ID);
            if (cf == null) {
                return null;
            }
            FieldLayout layout = ComponentAccessor.getFieldLayoutManager().getFieldLayout(issue);
            if (layout != null) {
                FieldLayoutItem item = layout.getFieldLayoutItem(cf);
                if (item != null && item.isHidden()) {
                    return null;
                }
            }
            return resolve(rawValue(cf.getValue(issue)));
        } catch (Exception ex) {
            return null;
        }
    }

    /** Extract a plain display string from a custom-field value (handles Option). */
    public static String rawValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Option) {
            return ((Option) value).getValue();
        }
        return value.toString();
    }

    /**
     * Map a raw value to pill metadata, or {@code null} if not a known task type.
     * Returned map: {@code value, key, cssClass, labelEn, labelFa, textColor,
     * backgroundColor, render}.
     */
    public static Map<String, Object> resolve(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Spec spec = MAP.get(normalize(trimmed));
        if (spec == null) {
            return null;
        }
        Map<String, Object> pill = new LinkedHashMap<>();
        pill.put("value", trimmed);
        pill.put("key", spec.key);
        pill.put("cssClass", spec.key);
        pill.put("labelEn", spec.labelEn);
        pill.put("labelFa", spec.labelFa);
        pill.put("textColor", spec.textColor);
        pill.put("backgroundColor", spec.backgroundColor);
        pill.put("render", "pill");
        return pill;
    }

    /**
     * Canonicalize a value for matching: lower-case latin, unify Arabic/Persian
     * letter forms, and drop ZWNJ / whitespace so spacing/spelling variants
     * collapse to one key.
     */
    static String normalize(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '\u064A': // Arabic yeh
                case '\u0649': // Arabic alef maksura
                    sb.append('\u06CC'); // Persian yeh
                    break;
                case '\u0643': // Arabic kaf
                    sb.append('\u06A9'); // Persian kaf
                    break;
                case '\u200C': // ZWNJ
                case '\u200D': // ZWJ
                case '\u200E': // LRM
                case '\u200F': // RLM
                case ' ':
                case '\t':
                case '\u00A0': // NBSP
                    break; // drop separators
                default:
                    sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
