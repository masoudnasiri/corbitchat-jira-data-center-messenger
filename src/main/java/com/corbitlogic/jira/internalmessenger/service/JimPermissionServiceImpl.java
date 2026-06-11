/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  com.atlassian.jira.component.ComponentAccessor
 *  com.atlassian.jira.project.Project
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.jira.user.util.UserManager
 *  net.java.ao.Query
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimGroupMember;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import net.java.ao.Query;

public class JimPermissionServiceImpl
implements JimPermissionService {
    private final JiraAuthenticationContext authenticationContext;
    private final UserManager userManager;
    private final ActiveObjects activeObjects;

    public JimPermissionServiceImpl(JiraAuthenticationContext authenticationContext, UserManager userManager, ActiveObjects activeObjects) {
        this.authenticationContext = authenticationContext;
        this.userManager = userManager;
        this.activeObjects = activeObjects;
    }

    @Override
    public String requireAuthenticatedUserKey() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            throw JimMessengerException.unauthorized("User is not authenticated");
        }
        return user.getKey();
    }

    @Override
    public void requireSameUser(String authenticatedUserKey, String requestedUserKey) {
        if (!authenticatedUserKey.equals(requestedUserKey)) {
            throw JimMessengerException.forbidden("User is not allowed to access this resource");
        }
    }

    @Override
    public void requireActiveUser(String userKey) {
        if (!this.isActiveUser(userKey)) {
            throw JimMessengerException.badRequest("User is not active or does not exist");
        }
    }

    @Override
    public boolean isActiveUser(String userKey) {
        if (userKey == null || userKey.trim().isEmpty()) {
            return false;
        }
        ApplicationUser user = this.userManager.getUserByKey(userKey.trim());
        return user != null && user.isActive();
    }

    @Override
    public boolean isParticipant(JimConversation conversation, String userKey) {
        if (conversation == null || userKey == null) {
            return false;
        }
        JimConversationType type = this.toConversationType(conversation);
        if (type == JimConversationType.DIRECT) {
            return userKey.equals(conversation.getUserAKey()) || userKey.equals(conversation.getUserBKey());
        }
        if (type == JimConversationType.SYSTEM) {
            String ownerKey = conversation.getUserAKey();
            return ownerKey != null && userKey.equals(ownerKey.trim());
        }
        if (type == JimConversationType.GROUP || type == JimConversationType.PROJECT) {
            JimGroupMember[] members = (JimGroupMember[])this.activeObjects.find(JimGroupMember.class, Query.select().where("CONVERSATION_ID = ? AND USER_KEY = ?", new Object[]{conversation.getID(), userKey.trim()}).limit(1));
            if (members.length > 0) {
                return true;
            }
            if (type == JimConversationType.PROJECT) {
                return this.isCurrentProjectLead(conversation, userKey.trim());
            }
            return false;
        }
        return false;
    }

    private boolean isCurrentProjectLead(JimConversation conversation, String userKey) {
        if (conversation.getProjectKey() == null) {
            return false;
        }
        try {
            Project project = ComponentAccessor.getProjectManager().getProjectObjByKey(conversation.getProjectKey());
            ApplicationUser lead = project != null ? project.getProjectLead() : null;
            return lead != null && userKey.equals(lead.getKey());
        }
        catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public void requireParticipant(JimConversation conversation, String userKey) {
        if (!this.isParticipant(conversation, userKey)) {
            throw JimMessengerException.forbidden("User is not a participant in this conversation");
        }
    }

    @Override
    public boolean isDirectConversation(JimConversation conversation) {
        return this.toConversationType(conversation) == JimConversationType.DIRECT;
    }

    @Override
    public boolean isSystemConversation(JimConversation conversation) {
        return this.toConversationType(conversation) == JimConversationType.SYSTEM;
    }

    @Override
    public JimConversationType toConversationType(JimConversation conversation) {
        if (conversation == null || conversation.getConversationType() == null) {
            throw JimMessengerException.badRequest("Conversation type is missing");
        }
        try {
            return JimConversationType.valueOf(conversation.getConversationType().trim());
        }
        catch (IllegalArgumentException ex) {
            throw JimMessengerException.badRequest("Conversation type is invalid");
        }
    }
}

