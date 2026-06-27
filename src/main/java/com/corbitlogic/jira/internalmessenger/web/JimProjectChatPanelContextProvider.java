/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.avatar.Avatar$Size
 *  com.atlassian.jira.avatar.AvatarService
 *  com.atlassian.jira.project.Project
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.plugin.PluginParseException
 *  com.atlassian.plugin.web.ContextProvider
 *  com.atlassian.plugin.webresource.WebResourceManager
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.web;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.ContextProvider;
import com.atlassian.plugin.webresource.WebResourceManager;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimProjectChatService;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimProjectChatPanelContextProvider
implements ContextProvider {
    private static final Logger log = LoggerFactory.getLogger(JimProjectChatPanelContextProvider.class);
    private static final String WEB_RESOURCE_KEY = "com.corbitlogic.corbitchat.jira.dc:jim-messenger-resources";
    private final JiraAuthenticationContext authenticationContext;
    private final AvatarService avatarService;
    private final WebResourceManager webResourceManager;
    private final JimProjectChatService projectChatService;
    private final JimPermissionService permissionService;
    private final JimAdminSettingsService adminSettingsService;

    public JimProjectChatPanelContextProvider(JiraAuthenticationContext authenticationContext, AvatarService avatarService, WebResourceManager webResourceManager, JimProjectChatService projectChatService, JimPermissionService permissionService, JimAdminSettingsService adminSettingsService) {
        this.authenticationContext = authenticationContext;
        this.avatarService = avatarService;
        this.webResourceManager = webResourceManager;
        this.projectChatService = projectChatService;
        this.permissionService = permissionService;
        this.adminSettingsService = adminSettingsService;
    }

    public void init(Map<String, String> params) throws PluginParseException {
    }

    public Map<String, Object> getContextMap(Map<String, Object> context) {
        Project project;
        HashMap<String, Object> ctx = new HashMap<String, Object>(context != null ? context : new HashMap<String, Object>());
        this.webResourceManager.requireResource(WEB_RESOURCE_KEY);
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user != null) {
            ctx.put("currentUserKey", user.getKey());
            ctx.put("currentUserDisplayName", user.getDisplayName());
            ctx.put("currentUserAvatarUrl", this.resolveAvatarUrl(user));
        }
        try {
            String title = this.adminSettingsService.getBrandingTitle();
            ctx.put("brandTitle", title != null && !title.isEmpty() ? title : "CorbitChat");
            String logoUrl = this.adminSettingsService.getBrandingLogoUrl();
            if (logoUrl != null && !logoUrl.isEmpty()) {
                ctx.put("brandLogoUrl", logoUrl);
            }
        }
        catch (RuntimeException ex) {
            ctx.put("brandTitle", "CorbitChat");
        }
        if ((project = this.resolveProject(context)) != null) {
            ctx.put("projectChatMode", true);
            ctx.put("projectKey", project.getKey());
            ctx.put("projectName", project.getName());
            try {
                JimConversation conversation = this.projectChatService.ensureProjectConversation(project);
                if (conversation != null) {
                    ctx.put("projectConversationId", conversation.getID());
                    ctx.put("projectIsMember", user != null && this.permissionService.isParticipant(conversation, user.getKey()));
                }
            }
            catch (RuntimeException ex) {
                log.error("event=project_chat stage=panel_context outcome=error projectKey={} message={}", new Object[]{project.getKey(), ex.getMessage(), ex});
            }
            ApplicationUser lead = project.getProjectLead();
            if (lead != null) {
                ctx.put("projectLeadDisplayName", lead.getDisplayName());
            }
        }
        return ctx;
    }

    private Project resolveProject(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object project = context.get("project");
        if (project instanceof Project) {
            return (Project)project;
        }
        return null;
    }

    private String resolveAvatarUrl(ApplicationUser user) {
        try {
            URI uri = this.avatarService.getAvatarURL(user, user, Avatar.Size.XXLARGE);
            return uri == null ? null : uri.toString();
        }
        catch (RuntimeException ex) {
            return null;
        }
    }
}

