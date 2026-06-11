/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  com.atlassian.jira.component.ComponentAccessor
 *  com.atlassian.jira.project.Project
 *  com.atlassian.jira.user.ApplicationUser
 *  net.java.ao.DBParam
 *  net.java.ao.Query
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimGroupMember;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;
import com.corbitlogic.jira.internalmessenger.service.JimProjectChatService;
import java.util.List;
import net.java.ao.DBParam;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimProjectChatServiceImpl
implements JimProjectChatService {
    private static final Logger log = LoggerFactory.getLogger(JimProjectChatServiceImpl.class);
    private static final String ROLE_OWNER = "OWNER";
    private final ActiveObjects activeObjects;

    public JimProjectChatServiceImpl(ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    @Override
    public JimConversation ensureProjectConversation(Project project) {
        if (project == null || project.getKey() == null) {
            return null;
        }
        String projectKey = project.getKey().trim();
        String projectName = project.getName() != null ? project.getName() : projectKey;
        ApplicationUser lead = project.getProjectLead();
        String leadUserKey = lead != null ? lead.getKey() : null;
        return (JimConversation)this.activeObjects.executeInTransaction(() -> {
            JimConversation conversation = this.findProjectConversation(projectKey);
            long now = System.currentTimeMillis();
            if (conversation == null) {
                conversation = (JimConversation)this.activeObjects.create(JimConversation.class, new DBParam[0]);
                conversation.setConversationType(JimConversationType.PROJECT.name());
                conversation.setProjectKey(projectKey);
                conversation.setGroupName(projectName);
                conversation.setCreatedByUserKey(leadUserKey);
                conversation.setUserAKey(leadUserKey);
                conversation.setCreatedAt(now);
                conversation.setUpdatedAt(now);
                conversation.setLastMessageAt(now);
                conversation.setLastMessagePreview("Project chat created");
                conversation.save();
                log.info("event=project_chat stage=create outcome=success projectKey={} conversationId={} lead={}", new Object[]{projectKey, conversation.getID(), leadUserKey});
            } else {
                boolean dirty = false;
                if (!projectName.equals(conversation.getGroupName())) {
                    conversation.setGroupName(projectName);
                    dirty = true;
                }
                if (leadUserKey != null && !leadUserKey.equals(conversation.getCreatedByUserKey())) {
                    conversation.setCreatedByUserKey(leadUserKey);
                    dirty = true;
                }
                if (dirty) {
                    conversation.setUpdatedAt(now);
                    conversation.save();
                }
            }
            if (leadUserKey != null) {
                this.ensureMember(conversation.getID(), leadUserKey, now);
            }
            return conversation;
        });
    }

    @Override
    public void ensureConversationsForAllProjects() {
        List<Project> projects = ComponentAccessor.getProjectManager().getProjects();
        if (projects == null) {
            return;
        }
        int ensured = 0;
        for (Project project : projects) {
            try {
                this.ensureProjectConversation(project);
                ++ensured;
            }
            catch (Exception ex) {
                log.error("event=project_chat stage=sync outcome=error projectKey={} message={}", new Object[]{project != null ? project.getKey() : "null", ex.getMessage(), ex});
            }
        }
        log.info("event=project_chat stage=sync outcome=done projects={}", (Object)ensured);
    }

    @Override
    public boolean isProjectLead(JimConversation conversation, String userKey) {
        if (conversation == null || userKey == null || conversation.getProjectKey() == null) {
            return false;
        }
        Project project = ComponentAccessor.getProjectManager().getProjectObjByKey(conversation.getProjectKey());
        if (project == null) {
            return false;
        }
        ApplicationUser lead = project.getProjectLead();
        return lead != null && userKey.equals(lead.getKey());
    }

    private JimConversation findProjectConversation(String projectKey) {
        JimConversation[] found = (JimConversation[])this.activeObjects.find(JimConversation.class, Query.select().where("CONVERSATION_TYPE = ? AND PROJECT_KEY = ?", new Object[]{JimConversationType.PROJECT.name(), projectKey}).limit(1));
        return found.length > 0 ? found[0] : null;
    }

    private void ensureMember(int conversationId, String userKey, long now) {
        JimGroupMember[] existing = (JimGroupMember[])this.activeObjects.find(JimGroupMember.class, Query.select().where("CONVERSATION_ID = ? AND USER_KEY = ?", new Object[]{conversationId, userKey}).limit(1));
        if (existing.length > 0) {
            if (!ROLE_OWNER.equals(existing[0].getRole())) {
                existing[0].setRole(ROLE_OWNER);
                existing[0].save();
            }
            return;
        }
        JimGroupMember member = (JimGroupMember)this.activeObjects.create(JimGroupMember.class, new DBParam[0]);
        member.setConversationId(conversationId);
        member.setUserKey(userKey);
        member.setRole(ROLE_OWNER);
        member.setJoinedAt(now);
        member.save();
    }
}

