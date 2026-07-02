package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimMobileFeatureRule;
import java.util.List;
import java.util.Set;

/**
 * Mobile feature access-control (Sprint 04G).
 *
 * <p>Resolves which CorbitHub mobile feature keys a given user may use. This is
 * a product-level gate on top of Jira permissions &mdash; it never replaces
 * them. Callers must still perform their normal Jira permission checks after a
 * feature check passes.</p>
 *
 * <h3>Rule precedence (deterministic)</h3>
 * <ol>
 *   <li>Only <em>enabled</em> rules are considered.</li>
 *   <li>A {@code USER} rule is more specific than a {@code GROUP} rule and always
 *       wins: if any enabled USER rule matches the user, the single
 *       highest-priority USER rule applies (ties broken by lowest row id).</li>
 *   <li>Otherwise, if any enabled GROUP rule matches (the user is a member), the
 *       single highest-priority GROUP rule applies (ties broken by lowest id).</li>
 *   <li>If no rule matches, the user gets <strong>all</strong> features (the
 *       backward-compatible default &mdash; no rules means full access).</li>
 * </ol>
 * The winning rule's feature set is authoritative; feature sets are never merged
 * across rules, so an admin can always reason about exactly one applied rule.
 */
public interface JimMobileFeatureService {

    String SUBJECT_USER = "USER";
    String SUBJECT_GROUP = "GROUP";

    // ----- Admin CRUD ---------------------------------------------------------

    List<JimMobileFeatureRule> listRules();

    JimMobileFeatureRule createRule(String subjectType, String subjectValue,
                                    String featuresCsv, boolean enabled, int priority,
                                    String actorUserKey);

    JimMobileFeatureRule updateRule(int ruleId, String subjectType, String subjectValue,
                                    String featuresCsv, boolean enabled, int priority);

    void deleteRule(int ruleId);

    // ----- Runtime resolution / enforcement -----------------------------------

    /** The effective set of allowed feature keys for the user (never null). */
    Set<String> allowedFeatures(ApplicationUser user);

    /** True when the user is allowed to use the given feature key. */
    boolean isAllowed(ApplicationUser user, String featureKey);

    /**
     * The id of the single rule that decided the user's access, or {@code null}
     * when the default (no matching rule → full access) applied. Used for admin
     * diagnostics only.
     */
    Integer matchedRuleId(ApplicationUser user);
}
