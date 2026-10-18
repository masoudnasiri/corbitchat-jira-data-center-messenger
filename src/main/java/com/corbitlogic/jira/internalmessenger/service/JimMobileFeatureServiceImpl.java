package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimMobileFeatureRule;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.java.ao.DBParam;
import net.java.ao.Query;
import net.java.ao.RawEntity;

/**
 * Active Objects backed implementation of {@link JimMobileFeatureService}.
 *
 * <p>Precedence and default behaviour are documented on the interface. This
 * class only persists rules and evaluates them; it deliberately does not know
 * about REST or HTTP so it can be reused by the bootstrap payload, the per
 * endpoint gate and the admin diagnostics identically.</p>
 */
public class JimMobileFeatureServiceImpl implements JimMobileFeatureService {

    private static final List<String> SUBJECT_TYPES = Arrays.asList(SUBJECT_USER, SUBJECT_GROUP);

    private final ActiveObjects activeObjects;

    public JimMobileFeatureServiceImpl(ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    // ----- Admin CRUD ---------------------------------------------------------

    @Override
    public List<JimMobileFeatureRule> listRules() {
        JimMobileFeatureRule[] rows = this.activeObjects.find(JimMobileFeatureRule.class,
                Query.select().order("PRIORITY DESC, ID ASC"));
        return new ArrayList<>(Arrays.asList(rows));
    }

    @Override
    public JimMobileFeatureRule createRule(String subjectType, String subjectValue,
                                           String featuresCsv, boolean enabled, int priority,
                                           String actorUserKey) {
        String type = requireSubjectType(subjectType);
        String value = JimValidation.requireNonBlank(subjectValue, "subjectValue").trim();
        String features = normalizeFeatures(featuresCsv);
        long now = System.currentTimeMillis();
        return this.activeObjects.executeInTransaction(() -> {
            JimMobileFeatureRule rule = this.activeObjects.create(JimMobileFeatureRule.class, new DBParam[0]);
            rule.setSubjectType(type);
            rule.setSubjectValue(value);
            rule.setFeatures(features);
            rule.setEnabled(enabled);
            rule.setPriority(priority);
            rule.setCreatedBy(actorUserKey);
            rule.setCreatedAt(now);
            rule.setUpdatedAt(now);
            rule.save();
            return rule;
        });
    }

    @Override
    public JimMobileFeatureRule updateRule(int ruleId, String subjectType, String subjectValue,
                                           String featuresCsv, boolean enabled, int priority) {
        String type = requireSubjectType(subjectType);
        String value = JimValidation.requireNonBlank(subjectValue, "subjectValue").trim();
        String features = normalizeFeatures(featuresCsv);
        return this.activeObjects.executeInTransaction(() -> {
            JimMobileFeatureRule rule = this.activeObjects.get(JimMobileFeatureRule.class, ruleId);
            if (rule == null) {
                throw JimMessengerException.notFound("Mobile feature rule not found");
            }
            rule.setSubjectType(type);
            rule.setSubjectValue(value);
            rule.setFeatures(features);
            rule.setEnabled(enabled);
            rule.setPriority(priority);
            rule.setUpdatedAt(System.currentTimeMillis());
            rule.save();
            return rule;
        });
    }

    @Override
    public void deleteRule(int ruleId) {
        this.activeObjects.executeInTransaction(() -> {
            JimMobileFeatureRule rule = this.activeObjects.get(JimMobileFeatureRule.class, ruleId);
            if (rule == null) {
                throw JimMessengerException.notFound("Mobile feature rule not found");
            }
            this.activeObjects.delete(new RawEntity[]{rule});
            return null;
        });
    }

    // ----- Runtime resolution -------------------------------------------------

    @Override
    public Set<String> allowedFeatures(ApplicationUser user) {
        JimMobileFeatureRule match = resolveRule(user);
        if (match == null) {
            // No rule matched: full access (backward-compatible default).
            return JimMobileFeatures.allSet();
        }
        return JimMobileFeatures.parse(match.getFeatures());
    }

    @Override
    public boolean isAllowed(ApplicationUser user, String featureKey) {
        String key = JimMobileFeatures.normalize(featureKey);
        if (key == null) {
            // Unknown/ungated key: never blocked by this layer.
            return true;
        }
        return allowedFeatures(user).contains(key);
    }

    @Override
    public Integer matchedRuleId(ApplicationUser user) {
        JimMobileFeatureRule match = resolveRule(user);
        return match != null ? match.getID() : null;
    }

    /**
     * Applies the documented precedence and returns the single winning rule, or
     * null when no enabled rule matches the user.
     */
    private JimMobileFeatureRule resolveRule(ApplicationUser user) {
        if (user == null) {
            return null;
        }
        JimMobileFeatureRule bestUser = null;
        JimMobileFeatureRule bestGroup = null;
        for (JimMobileFeatureRule rule : listRules()) {
            if (!Boolean.TRUE.equals(rule.getEnabled())) {
                continue;
            }
            if (SUBJECT_USER.equals(rule.getSubjectType())) {
                if (userMatches(rule.getSubjectValue(), user) && wins(rule, bestUser)) {
                    bestUser = rule;
                }
            } else if (SUBJECT_GROUP.equals(rule.getSubjectType())) {
                if (isInGroup(user, rule.getSubjectValue()) && wins(rule, bestGroup)) {
                    bestGroup = rule;
                }
            }
        }
        // USER rules are more specific and always beat GROUP rules.
        return bestUser != null ? bestUser : bestGroup;
    }

    /**
     * True when {@code candidate} should replace {@code current}: higher priority
     * wins; on a tie the lower row id wins (deterministic, stable).
     */
    private static boolean wins(JimMobileFeatureRule candidate, JimMobileFeatureRule current) {
        if (current == null) {
            return true;
        }
        int cp = candidate.getPriority() != null ? candidate.getPriority() : 0;
        int op = current.getPriority() != null ? current.getPriority() : 0;
        if (cp != op) {
            return cp > op;
        }
        return candidate.getID() < current.getID();
    }

    private static boolean userMatches(String subjectValue, ApplicationUser user) {
        if (subjectValue == null || user == null) {
            return false;
        }
        String value = subjectValue.trim().toLowerCase(Locale.ROOT);
        String username = user.getUsername() != null ? user.getUsername().toLowerCase(Locale.ROOT) : "";
        String key = user.getKey() != null ? user.getKey().toLowerCase(Locale.ROOT) : "";
        return value.equals(username) || value.equals(key);
    }

    private boolean isInGroup(ApplicationUser user, String groupName) {
        if (user == null || groupName == null || groupName.trim().isEmpty()) {
            return false;
        }
        try {
            return groupManager().isUserInGroup(user, groupName.trim());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String requireSubjectType(String value) {
        if (value == null) {
            throw JimMessengerException.badRequest("subjectType is required");
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (!SUBJECT_TYPES.contains(upper)) {
            throw JimMessengerException.badRequest("subjectType must be one of " + SUBJECT_TYPES);
        }
        return upper;
    }

    /**
     * Normalise the admin-supplied CSV to canonical known keys. An empty result
     * is allowed and meaningful: it grants no features (e.g. a "blocked" user).
     */
    private static String normalizeFeatures(String featuresCsv) {
        return JimMobileFeatures.toCsv(JimMobileFeatures.parse(featuresCsv));
    }

    private static GroupManager groupManager() {
        return ComponentAccessor.getGroupManager();
    }
}
