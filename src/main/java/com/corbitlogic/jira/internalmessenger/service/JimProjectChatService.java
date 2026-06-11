/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.project.Project
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.jira.project.Project;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;

public interface JimProjectChatService {
    public JimConversation ensureProjectConversation(Project var1);

    public void ensureConversationsForAllProjects();

    public boolean isProjectLead(JimConversation var1, String var2);
}

