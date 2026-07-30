package com.corbitlogic.jira.internalmessenger.schedule;

import com.atlassian.beehive.ClusterLock;
import com.atlassian.beehive.ClusterLockService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.context.GlobalIssueContext;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.customfields.CustomFieldSearcher;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.fields.screen.FieldScreenLayoutItem;
import com.atlassian.jira.issue.fields.screen.FieldScreenManager;
import com.atlassian.jira.issue.fields.screen.FieldScreenTab;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.service.JimAdminAuditService;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Admin-controlled, cluster-safe, idempotent provisioning of the schedule-approval
 * custom fields on an environment that does not have them (DEV / Server-2).
 *
 * <p>Provisioning NEVER runs on plugin startup — only from the admin REST action.
 * For each logical field it: (1) resolves the existing field (idempotent skip),
 * else (2) adopts an existing correctly-typed same-named field (map, no create),
 * else (3) creates a new field with the correct type + searcher + a global
 * context and, for the status field, the required options. Resulting ids are
 * stored in plugin settings so the resolver uses the environment-local id from
 * then on. Optionally adds the fields to explicit screens.</p>
 *
 * <p>All mutation is guarded by a beehive {@link ClusterLock} so two Data Center
 * nodes can never create duplicate fields. Every action returns a structured
 * record and is audited by the caller. Never logs field values.</p>
 */
public final class JimApprovalFieldProvisioning {
    private static final Logger log = LoggerFactory.getLogger(JimApprovalFieldProvisioning.class);

    private static final String LOCK_NAME = "com.corbitlogic.corbitchat.approvalFieldProvisioning";
    private static final String TYPE_PREFIX = "com.atlassian.jira.plugin.system.customfieldtypes:";

    private JimApprovalFieldProvisioning() {
    }

    /** Jira type key + searcher key to create for a logical key. */
    private static String typeKey(JimApprovalFields.Key key) {
        switch (key) {
            case STATUS: return TYPE_PREFIX + "select";
            case APPROVER: return TYPE_PREFIX + "userpicker";
            case PROPOSED_DATE: return TYPE_PREFIX + "datepicker";
            case APPROVED_DATE: return TYPE_PREFIX + "datepicker";
            case REJECTION_REASON: return TYPE_PREFIX + "textarea";
            default: return null;
        }
    }

    private static String searcherKey(JimApprovalFields.Key key) {
        switch (key) {
            case STATUS: return TYPE_PREFIX + "multiselectsearcher";
            case APPROVER: return TYPE_PREFIX + "userpickergroupsearcher";
            case PROPOSED_DATE: return TYPE_PREFIX + "daterange";
            case APPROVED_DATE: return TYPE_PREFIX + "daterange";
            case REJECTION_REASON: return TYPE_PREFIX + "textsearcher";
            default: return null;
        }
    }

    /**
     * Provision all missing approval fields. Cluster-locked, idempotent.
     *
     * @param addToScreens  when true, add created/mapped fields to {@code screenIds}
     * @param screenIds     optional explicit Jira screen ids to add the fields to
     * @param settings      settings service (to persist resulting ids)
     * @param audit         audit service (per-action records)
     * @param admin         acting admin (audit attribution)
     * @return structured result: {@code {locked, actions:[...], summary}}
     */
    public static Map<String, Object> provision(boolean addToScreens, List<Long> screenIds,
                                                 JimAdminSettingsService settings,
                                                 JimAdminAuditService audit, ApplicationUser admin) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> actions = new ArrayList<>();
        result.put("actions", actions);

        ClusterLock lock = null;
        boolean locked = false;
        try {
            ClusterLockService cls = ComponentAccessor.getComponent(ClusterLockService.class);
            if (cls != null) {
                lock = cls.getLockForName(LOCK_NAME);
                try {
                    locked = lock.tryLock(30, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    result.put("locked", false);
                    result.put("error", "interrupted_acquiring_lock");
                    return result;
                }
                if (!locked) {
                    result.put("locked", false);
                    result.put("error", "provisioning_in_progress_on_another_node");
                    return result;
                }
            }
            result.put("locked", locked);

            for (JimApprovalFields.Key key : JimApprovalFields.Key.values()) {
                actions.add(provisionOne(key, addToScreens, screenIds, settings, audit, admin));
            }
            result.put("summary", summarize(actions));
            return result;
        } finally {
            if (lock != null && locked) {
                try {
                    lock.unlock();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Map<String, Object> provisionOne(JimApprovalFields.Key key, boolean addToScreens,
                                                    List<Long> screenIds, JimAdminSettingsService settings,
                                                    JimAdminAuditService audit, ApplicationUser admin) {
        LinkedHashMap<String, Object> a = new LinkedHashMap<>();
        a.put("logicalKey", key.logicalKey);
        try {
            // (1) Already resolves (configured or production fallback) → idempotent.
            CustomField existing = JimApprovalFields.resolve(key);
            if (existing != null) {
                ensureStatusOptions(key, existing);
                if (addToScreens) {
                    addToScreens(existing, screenIds, a);
                }
                a.put("action", "exists");
                a.put("fieldId", existing.getId());
                a.put("message", "Field already resolves; no change.");
                return a;
            }

            // (2) Adopt an existing correctly-typed, same-named field (no duplicate).
            CustomField adoptable = findAdoptable(key);
            if (adoptable != null) {
                ensureStatusOptions(key, adoptable);
                persistMapping(key, adoptable.getId(), settings);
                if (addToScreens) {
                    addToScreens(adoptable, screenIds, a);
                }
                audit(audit, admin, "approvalField.map", key.logicalKey + " -> " + adoptable.getId() + " (adopted existing)");
                a.put("action", "mapped");
                a.put("fieldId", adoptable.getId());
                a.put("message", "Adopted existing field '" + safeName(adoptable) + "'.");
                return a;
            }

            // (3) Create a new field.
            CustomField created = createField(key);
            if (created == null) {
                a.put("action", "failed");
                a.put("message", "Could not create field (type/searcher unavailable).");
                return a;
            }
            ensureStatusOptions(key, created);
            persistMapping(key, created.getId(), settings);
            if (addToScreens) {
                addToScreens(created, screenIds, a);
            }
            audit(audit, admin, "approvalField.create", key.logicalKey + " -> " + created.getId()
                    + " type=" + typeKey(key));
            a.put("action", "created");
            a.put("fieldId", created.getId());
            a.put("message", "Created field '" + safeName(created) + "'.");
            return a;
        } catch (Throwable ex) {
            log.error("event=approvalProvision stage=field outcome=error logicalKey={} message={}",
                    new Object[]{key.logicalKey, ex.getMessage(), ex});
            a.put("action", "failed");
            a.put("message", "Error: " + ex.getMessage());
            return a;
        }
    }

    private static CustomField createField(JimApprovalFields.Key key) throws Exception {
        CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
        if (cfm == null) {
            return null;
        }
        CustomFieldType<?, ?> type = cfm.getCustomFieldType(typeKey(key));
        if (type == null) {
            log.warn("event=approvalProvision stage=create outcome=error reason=type_unavailable typeKey={}", typeKey(key));
            return null;
        }
        CustomFieldSearcher searcher = null;
        try {
            searcher = cfm.getCustomFieldSearcher(searcherKey(key));
        } catch (Throwable ignored) {
        }
        // Global context applying to ALL issue types. The issue-type list must
        // contain a single null element to mean "all issue types"; passing a
        // null/empty list creates the field WITHOUT a config scheme, so it has
        // no context and cannot hold values or options.
        List<JiraContextNode> contexts = new ArrayList<>();
        contexts.add(GlobalIssueContext.getInstance());
        List<IssueType> issueTypes = new ArrayList<>();
        issueTypes.add((IssueType) null);
        String name = key.nameEn;
        String description = "CorbitChat schedule-approval field (" + key.logicalKey + ").";
        CustomField cf = cfm.createCustomField(name, description, type, searcher, contexts, issueTypes);
        // Re-fetch so the returned object carries the freshly-created config scheme.
        if (cf != null) {
            CustomField fresh = cfm.getCustomFieldObject(cf.getId());
            if (fresh != null) {
                cf = fresh;
            }
        }
        return cf;
    }

    /** Ensure Pending/Approved/Rejected exist on the status field (idempotent). */
    private static void ensureStatusOptions(JimApprovalFields.Key key, CustomField cf) {
        if (key != JimApprovalFields.Key.STATUS || cf == null) {
            return;
        }
        try {
            OptionsManager om = ComponentAccessor.getComponent(OptionsManager.class);
            if (om == null) {
                return;
            }
            FieldConfig config = null;
            List<FieldConfigScheme> schemes = cf.getConfigurationSchemes();
            if (schemes != null && !schemes.isEmpty() && schemes.get(0) != null) {
                config = schemes.get(0).getOneAndOnlyConfig();
            }
            if (config == null) {
                // Fall back to the global-context relevant config.
                try {
                    config = cf.getRelevantConfig(GlobalIssueContext.getInstance());
                } catch (Throwable ignored) {
                }
            }
            if (config == null) {
                log.warn("event=approvalProvision stage=options outcome=error reason=no_config fieldId={}", cf.getId());
                return;
            }
            List<String> present = new ArrayList<>();
            com.atlassian.jira.issue.customfields.option.Options current = om.getOptions(config);
            if (current != null) {
                for (Option o : current) {
                    if (o != null && o.getValue() != null) {
                        present.add(o.getValue());
                    }
                }
            }
            long seq = present.size();
            for (String value : JimApprovalFields.REQUIRED_STATUS_OPTIONS) {
                boolean found = false;
                for (String p : present) {
                    if (value.equalsIgnoreCase(p)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    om.createOption(config, null, seq++, value);
                }
            }
        } catch (Throwable ex) {
            log.warn("event=approvalProvision stage=options outcome=error fieldId={} message={}",
                    cf.getId(), ex.getMessage());
        }
    }

    /** Add the field to explicit screens' first tab (idempotent). */
    private static void addToScreens(CustomField cf, List<Long> screenIds, Map<String, Object> action) {
        List<Long> targets = new ArrayList<>();
        if (screenIds != null && !screenIds.isEmpty()) {
            targets.addAll(screenIds);
        } else {
            targets.add(1L); // Jira "Default Screen"
        }
        List<Long> added = new ArrayList<>();
        try {
            FieldScreenManager fsm = ComponentAccessor.getFieldScreenManager();
            if (fsm == null) {
                return;
            }
            for (Long sid : targets) {
                FieldScreen screen = fsm.getFieldScreen(sid);
                if (screen == null || screen.getTabs() == null || screen.getTabs().isEmpty()) {
                    continue;
                }
                FieldScreenTab tab = screen.getTab(0);
                boolean alreadyThere = false;
                for (FieldScreenTab t : screen.getTabs()) {
                    for (FieldScreenLayoutItem item : t.getFieldScreenLayoutItems()) {
                        if (item != null && cf.getId().equals(item.getFieldId())) {
                            alreadyThere = true;
                            break;
                        }
                    }
                }
                if (!alreadyThere) {
                    tab.addFieldScreenLayoutItem(cf.getId());
                    added.add(sid);
                }
            }
        } catch (Throwable ex) {
            log.warn("event=approvalProvision stage=screen outcome=error fieldId={} message={}",
                    cf.getId(), ex.getMessage());
        }
        action.put("screensAdded", added);
    }

    /**
     * Find an existing custom field of the correct type whose name matches the
     * logical field's English or Persian name — adopt instead of duplicating.
     */
    private static CustomField findAdoptable(JimApprovalFields.Key key) {
        try {
            CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
            if (cfm == null) {
                return null;
            }
            String[] types = JimApprovalFields.expectedTypeSubstrings(key);
            String wantEn = normalize(key.nameEn);
            String wantFa = normalize(key.nameFa);
            List<CustomField> all = cfm.getCustomFieldObjects();
            if (all == null) {
                return null;
            }
            for (CustomField cf : all) {
                if (cf == null || cf.getCustomFieldType() == null) {
                    continue;
                }
                String typeKey = cf.getCustomFieldType().getKey();
                String lower = typeKey != null ? typeKey.toLowerCase() : "";
                boolean typeOk = false;
                for (String sub : types) {
                    if (lower.contains(sub)) {
                        typeOk = true;
                        break;
                    }
                }
                if (!typeOk) {
                    continue;
                }
                String n = normalize(safeName(cf));
                if (n.equals(wantEn) || (!wantFa.isEmpty() && n.equals(wantFa))) {
                    // Status field must also carry (or be able to carry) the options.
                    return cf;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void persistMapping(JimApprovalFields.Key key, String fieldId, JimAdminSettingsService settings) {
        if (settings == null || fieldId == null) {
            return;
        }
        try {
            Map<String, Object> change = Collections.singletonMap(JimApprovalFields.settingsKey(key), fieldId);
            settings.updateSettings(change);
        } catch (Throwable ex) {
            log.warn("event=approvalProvision stage=persist outcome=error logicalKey={} fieldId={} message={}",
                    new Object[]{key.logicalKey, fieldId, ex.getMessage()});
        }
    }

    private static void audit(JimAdminAuditService audit, ApplicationUser admin, String action, String details) {
        try {
            if (audit != null && admin != null) {
                audit.record(admin.getKey(), action, details);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Map<String, Object> summarize(List<Map<String, Object>> actions) {
        int created = 0, mapped = 0, exists = 0, failed = 0;
        for (Map<String, Object> a : actions) {
            String act = String.valueOf(a.get("action"));
            if ("created".equals(act)) created++;
            else if ("mapped".equals(act)) mapped++;
            else if ("exists".equals(act)) exists++;
            else if ("failed".equals(act)) failed++;
        }
        LinkedHashMap<String, Object> s = new LinkedHashMap<>();
        s.put("created", created);
        s.put("mapped", mapped);
        s.put("exists", exists);
        s.put("failed", failed);
        return s;
    }

    private static String safeName(CustomField cf) {
        try {
            return cf.getFieldName();
        } catch (Throwable t) {
            return "";
        }
    }

    /** Normalize a name for matching: Arabic→Persian letter unification, collapse ws, lowercase. */
    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\u064A':
                case '\u0649':
                    c = '\u06CC';
                    break;
                case '\u0643':
                    c = '\u06A9';
                    break;
                case '\u200C':
                case '\u200D':
                case '\u200E':
                case '\u200F':
                    continue;
                default:
                    break;
            }
            if (Character.isWhitespace(c)) {
                if (b.length() > 0 && b.charAt(b.length() - 1) != ' ') {
                    b.append(' ');
                }
            } else {
                b.append(c);
            }
        }
        return b.toString().trim().toLowerCase();
    }
}
