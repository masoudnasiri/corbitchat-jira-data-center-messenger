/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.avatar.Avatar$Size
 *  com.atlassian.jira.avatar.AvatarService
 *  com.atlassian.jira.component.ComponentAccessor
 *  com.atlassian.jira.permission.ProjectPermissions
 *  com.atlassian.jira.project.Project
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.plugin.webresource.WebResourceManager
 *  com.atlassian.templaterenderer.TemplateRenderer
 *  javax.servlet.http.HttpServlet
 *  javax.servlet.http.HttpServletRequest
 *  javax.servlet.http.HttpServletResponse
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.web;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimProjectChatService;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.HashMap;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimProjectChatServlet
extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(JimProjectChatServlet.class);
    private static final String TEMPLATE_PATH = "templates/messenger.vm";
    private static final String PLUGIN_KEY = "com.corbitlogic.corbitchat.jira.dc";
    private static final String WEB_RESOURCE_KEY = "com.corbitlogic.corbitchat.jira.dc:jim-messenger-resources";
    private static final String LOGO_RESOURCE_NAME = "corbit-app-logo.png";
    private static final String LOGO_CLASSPATH = "images/corbit-app-logo.png";
    private final JiraAuthenticationContext authenticationContext;
    private final TemplateRenderer templateRenderer;
    private final WebResourceManager webResourceManager;
    private final AvatarService avatarService;
    private final JimProjectChatService projectChatService;
    private final JimPermissionService permissionService;
    private final JimAdminSettingsService adminSettingsService;

    public JimProjectChatServlet(JiraAuthenticationContext authenticationContext, TemplateRenderer templateRenderer, WebResourceManager webResourceManager, AvatarService avatarService, JimProjectChatService projectChatService, JimPermissionService permissionService, JimAdminSettingsService adminSettingsService) {
        this.authenticationContext = authenticationContext;
        this.templateRenderer = templateRenderer;
        this.webResourceManager = webResourceManager;
        this.avatarService = avatarService;
        this.projectChatService = projectChatService;
        this.permissionService = permissionService;
        this.adminSettingsService = adminSettingsService;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Project project;
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }
        String projectKey = request.getParameter("projectKey");
        Project project2 = project = projectKey != null ? ComponentAccessor.getProjectManager().getProjectObjByKey(projectKey.trim()) : null;
        if (project == null || !ComponentAccessor.getPermissionManager().hasPermission(ProjectPermissions.BROWSE_PROJECTS, project, user)) {
            response.sendError(404, "Project not found");
            return;
        }
        JimConversation conversation = null;
        try {
            conversation = this.projectChatService.ensureProjectConversation(project);
        }
        catch (RuntimeException ex) {
            log.error("event=project_chat stage=servlet outcome=error projectKey={} message={}", new Object[]{project.getKey(), ex.getMessage(), ex});
        }
        boolean isMember = conversation != null && this.permissionService.isParticipant(conversation, user.getKey());
        ApplicationUser lead = project.getProjectLead();
        response.setContentType("text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        boolean logoAvailable = ((Object)((Object)this)).getClass().getClassLoader().getResource(LOGO_CLASSPATH) != null;
        String logoUrl = null;
        if (logoAvailable) {
            logoUrl = request.getContextPath() + "/download/resources/com.corbitlogic.corbitchat.jira.dc:jim-messenger-resources/corbit-app-logo.png";
        }
        HashMap<String, Object> context = new HashMap<String, Object>();
        context.put("currentUserKey", user.getKey());
        context.put("currentUserDisplayName", user.getDisplayName());
        context.put("currentUserAvatarUrl", this.resolveAvatarUrl(user));
        context.put("webResourceManager", this.webResourceManager);
        context.put("i18n", this.authenticationContext.getI18nHelper());
        context.put("logoAvailable", logoAvailable);
        context.put("logoUrl", logoUrl);
        context.put("brandTitle", this.resolveBrandTitle());
        context.put("brandLogoUrl", this.resolveBrandLogoUrl(logoAvailable, logoUrl));
        context.put("projectChatMode", true);
        context.put("projectKey", project.getKey());
        context.put("projectName", project.getName());
        context.put("projectConversationId", conversation != null ? Integer.valueOf(conversation.getID()) : null);
        context.put("projectIsMember", isMember);
        context.put("projectLeadDisplayName", lead != null ? lead.getDisplayName() : null);
        this.templateRenderer.render(TEMPLATE_PATH, context, (Writer)response.getWriter());
    }

    private String resolveBrandTitle() {
        try {
            String title = this.adminSettingsService.getBrandingTitle();
            return title != null && !title.isEmpty() ? title : "CorbitChat";
        }
        catch (RuntimeException ex) {
            return "CorbitChat";
        }
    }

    private String resolveBrandLogoUrl(boolean logoAvailable, String defaultLogoUrl) {
        try {
            String url = this.adminSettingsService.getBrandingLogoUrl();
            if (url != null && !url.isEmpty()) {
                return url;
            }
        }
        catch (RuntimeException ex) {
            // fall back to bundled logo
        }
        return logoAvailable ? defaultLogoUrl : null;
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

