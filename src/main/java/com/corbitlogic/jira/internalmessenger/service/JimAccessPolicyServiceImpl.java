/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  com.atlassian.jira.component.ComponentAccessor
 *  com.atlassian.jira.security.groups.GroupManager
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.jira.user.util.UserManager
 *  net.java.ao.DBParam
 *  net.java.ao.Query
 *  net.java.ao.RawEntity
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.corbitlogic.jira.internalmessenger.ao.JimAccessPolicy;
import com.corbitlogic.jira.internalmessenger.service.JimAccessPolicyService;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.java.ao.DBParam;
import net.java.ao.Query;
import net.java.ao.RawEntity;

public class JimAccessPolicyServiceImpl
implements JimAccessPolicyService {
    private static final List<String> SOURCE_TYPES = Arrays.asList("USER", "GROUP");
    private static final List<String> TARGET_TYPES = Arrays.asList("USER", "GROUP", "ANY");
    private static final List<String> ACTIONS = Arrays.asList("ALLOW", "DENY");
    private final ActiveObjects activeObjects;
    private final JimAdminSettingsService adminSettingsService;

    public JimAccessPolicyServiceImpl(ActiveObjects activeObjects, JimAdminSettingsService adminSettingsService) {
        this.activeObjects = activeObjects;
        this.adminSettingsService = adminSettingsService;
    }

    @Override
    public List<JimAccessPolicy> listPolicies() {
        JimAccessPolicy[] rows = (JimAccessPolicy[])this.activeObjects.find(JimAccessPolicy.class, Query.select().order("PRIORITY DESC, ID ASC"));
        return new ArrayList<JimAccessPolicy>(Arrays.asList(rows));
    }

    @Override
    public JimAccessPolicy createPolicy(String sourceType, String sourceValue, String targetType, String targetValue, String action, boolean canSearch, boolean canStartChat, boolean canReceiveChat, boolean enabled, int priority, String actorUserKey) {
        String normalizedSourceType = JimAccessPolicyServiceImpl.requireOneOf("sourceType", sourceType, SOURCE_TYPES);
        String normalizedTargetType = JimAccessPolicyServiceImpl.requireOneOf("targetType", targetType, TARGET_TYPES);
        String normalizedAction = JimAccessPolicyServiceImpl.requireOneOf("action", action, ACTIONS);
        String normalizedSourceValue = JimValidation.requireNonBlank(sourceValue, "sourceValue").trim();
        String normalizedTargetValue = "ANY".equals(normalizedTargetType) ? "" : JimValidation.requireNonBlank(targetValue, "targetValue").trim();
        long now = System.currentTimeMillis();
        return (JimAccessPolicy)this.activeObjects.executeInTransaction(() -> {
            JimAccessPolicy policy = (JimAccessPolicy)this.activeObjects.create(JimAccessPolicy.class, new DBParam[0]);
            policy.setSourceType(normalizedSourceType);
            policy.setSourceValue(normalizedSourceValue);
            policy.setTargetType(normalizedTargetType);
            policy.setTargetValue(normalizedTargetValue);
            policy.setAction(normalizedAction);
            policy.setCanSearch(canSearch);
            policy.setCanStartChat(canStartChat);
            policy.setCanReceiveChat(canReceiveChat);
            policy.setEnabled(enabled);
            policy.setPriority(priority);
            policy.setCreatedBy(actorUserKey);
            policy.setCreatedAt(now);
            policy.setUpdatedAt(now);
            policy.save();
            return policy;
        });
    }

    @Override
    public JimAccessPolicy updatePolicy(int policyId, String sourceType, String sourceValue, String targetType, String targetValue, String action, boolean canSearch, boolean canStartChat, boolean canReceiveChat, boolean enabled, int priority) {
        String normalizedSourceType = JimAccessPolicyServiceImpl.requireOneOf("sourceType", sourceType, SOURCE_TYPES);
        String normalizedTargetType = JimAccessPolicyServiceImpl.requireOneOf("targetType", targetType, TARGET_TYPES);
        String normalizedAction = JimAccessPolicyServiceImpl.requireOneOf("action", action, ACTIONS);
        String normalizedSourceValue = JimValidation.requireNonBlank(sourceValue, "sourceValue").trim();
        String normalizedTargetValue = "ANY".equals(normalizedTargetType) ? "" : JimValidation.requireNonBlank(targetValue, "targetValue").trim();
        return (JimAccessPolicy)this.activeObjects.executeInTransaction(() -> {
            JimAccessPolicy policy = (JimAccessPolicy)this.activeObjects.get(JimAccessPolicy.class, policyId);
            if (policy == null) {
                throw JimMessengerException.notFound("Policy not found");
            }
            policy.setSourceType(normalizedSourceType);
            policy.setSourceValue(normalizedSourceValue);
            policy.setTargetType(normalizedTargetType);
            policy.setTargetValue(normalizedTargetValue);
            policy.setAction(normalizedAction);
            policy.setCanSearch(canSearch);
            policy.setCanStartChat(canStartChat);
            policy.setCanReceiveChat(canReceiveChat);
            policy.setEnabled(enabled);
            policy.setPriority(priority);
            policy.setUpdatedAt(System.currentTimeMillis());
            policy.save();
            return policy;
        });
    }

    @Override
    public void deletePolicy(int policyId) {
        this.activeObjects.executeInTransaction(() -> {
            JimAccessPolicy policy = (JimAccessPolicy)this.activeObjects.get(JimAccessPolicy.class, policyId);
            if (policy == null) {
                throw JimMessengerException.notFound("Policy not found");
            }
            this.activeObjects.delete(new RawEntity[]{policy});
            return null;
        });
    }

    @Override
    public boolean isChatEnabled() {
        return !"DISABLED".equals(this.adminSettingsService.getChatMode());
    }

    @Override
    public boolean canSearch(String viewerUserKey, String targetUserKey) {
        String mode = this.adminSettingsService.getChatMode();
        if ("DISABLED".equals(mode)) {
            return false;
        }
        if ("ALLOW_ALL".equals(mode)) {
            return true;
        }
        return this.evaluate(viewerUserKey, targetUserKey, Capability.SEARCH);
    }

    @Override
    public boolean canChatWith(String initiatorUserKey, String targetUserKey) {
        String mode = this.adminSettingsService.getChatMode();
        if ("DISABLED".equals(mode)) {
            return false;
        }
        if ("ALLOW_ALL".equals(mode)) {
            return true;
        }
        return this.evaluate(initiatorUserKey, targetUserKey, Capability.START_CHAT) && this.evaluate(targetUserKey, initiatorUserKey, Capability.RECEIVE_CHAT);
    }

    @Override
    public void requireCanChatWith(String initiatorUserKey, String targetUserKey) {
        String mode = this.adminSettingsService.getChatMode();
        if ("DISABLED".equals(mode)) {
            throw JimMessengerException.forbidden("Chat has been disabled by the administrator");
        }
        if (!this.canChatWith(initiatorUserKey, targetUserKey)) {
            throw JimMessengerException.forbidden("Chat with this user is not allowed by policy");
        }
    }

    private boolean evaluate(String actorUserKey, String subjectUserKey, Capability capability) {
        if (actorUserKey == null || subjectUserKey == null) {
            return false;
        }
        ApplicationUser actor = JimAccessPolicyServiceImpl.userManager().getUserByKey(actorUserKey);
        ApplicationUser subject = JimAccessPolicyServiceImpl.userManager().getUserByKey(subjectUserKey);
        if (actor == null || subject == null) {
            return false;
        }
        Integer bestPriority = null;
        boolean bestAllowed = false;
        for (JimAccessPolicy policy : this.listPolicies()) {
            if (!Boolean.TRUE.equals(policy.getEnabled()) || !JimAccessPolicyServiceImpl.capabilityApplies(policy, capability) || !this.sourceMatches(policy, actor) || !this.targetMatches(policy, subject)) continue;
            int priority = policy.getPriority() != null ? policy.getPriority() : 0;
            boolean deny = "DENY".equals(policy.getAction());
            if (bestPriority == null || priority > bestPriority) {
                bestPriority = priority;
                bestAllowed = !deny;
                continue;
            }
            if (priority != bestPriority || !deny) continue;
            bestAllowed = false;
        }
        return bestPriority != null && bestAllowed;
    }

    private static boolean capabilityApplies(JimAccessPolicy policy, Capability capability) {
        switch (capability) {
            case SEARCH: {
                return Boolean.TRUE.equals(policy.getCanSearch());
            }
            case START_CHAT: {
                return Boolean.TRUE.equals(policy.getCanStartChat());
            }
            case RECEIVE_CHAT: {
                return Boolean.TRUE.equals(policy.getCanReceiveChat());
            }
        }
        return false;
    }

    private boolean sourceMatches(JimAccessPolicy policy, ApplicationUser actor) {
        if ("USER".equals(policy.getSourceType())) {
            return JimAccessPolicyServiceImpl.userMatches(policy.getSourceValue(), actor);
        }
        if ("GROUP".equals(policy.getSourceType())) {
            return this.isInGroup(actor, policy.getSourceValue());
        }
        return false;
    }

    private boolean targetMatches(JimAccessPolicy policy, ApplicationUser subject) {
        if ("ANY".equals(policy.getTargetType())) {
            return true;
        }
        if ("USER".equals(policy.getTargetType())) {
            return JimAccessPolicyServiceImpl.userMatches(policy.getTargetValue(), subject);
        }
        if ("GROUP".equals(policy.getTargetType())) {
            return this.isInGroup(subject, policy.getTargetValue());
        }
        return false;
    }

    private static boolean userMatches(String policyValue, ApplicationUser user) {
        if (policyValue == null || user == null) {
            return false;
        }
        String value = policyValue.trim().toLowerCase(Locale.ROOT);
        String username = user.getUsername() != null ? user.getUsername().toLowerCase(Locale.ROOT) : "";
        String key = user.getKey() != null ? user.getKey().toLowerCase(Locale.ROOT) : "";
        return value.equals(username) || value.equals(key);
    }

    private boolean isInGroup(ApplicationUser user, String groupName) {
        if (user == null || groupName == null || groupName.trim().isEmpty()) {
            return false;
        }
        try {
            return JimAccessPolicyServiceImpl.groupManager().isUserInGroup(user, groupName.trim());
        }
        catch (RuntimeException ex) {
            return false;
        }
    }

    private static String requireOneOf(String field, String value, List<String> allowed) {
        if (value == null) {
            throw JimMessengerException.badRequest(field + " is required");
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(upper)) {
            throw JimMessengerException.badRequest(field + " must be one of " + String.valueOf(allowed));
        }
        return upper;
    }

    private static UserManager userManager() {
        return ComponentAccessor.getUserManager();
    }

    private static GroupManager groupManager() {
        return ComponentAccessor.getGroupManager();
    }

    private static enum Capability {
        SEARCH,
        START_CHAT,
        RECEIVE_CHAT;

    }
}

