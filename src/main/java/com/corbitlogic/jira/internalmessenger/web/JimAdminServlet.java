/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.permission.GlobalPermissionKey
 *  com.atlassian.jira.security.GlobalPermissionManager
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.plugin.webresource.WebResourceManager
 *  com.atlassian.templaterenderer.TemplateRenderer
 *  javax.servlet.http.HttpServlet
 *  javax.servlet.http.HttpServletRequest
 *  javax.servlet.http.HttpServletResponse
 */
package com.corbitlogic.jira.internalmessenger.web;

import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import java.io.IOException;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.HashMap;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class JimAdminServlet
extends HttpServlet {
    private static final String TEMPLATE_PATH = "templates/admin.vm";
    private static final String PLUGIN_KEY = "com.corbitlogic.corbitchat.jira.dc";
    private static final String WEB_RESOURCE_KEY = "com.corbitlogic.corbitchat.jira.dc:jim-admin-resources";
    private final JiraAuthenticationContext authenticationContext;
    private final GlobalPermissionManager globalPermissionManager;
    private final TemplateRenderer templateRenderer;
    private final WebResourceManager webResourceManager;

    public JimAdminServlet(JiraAuthenticationContext authenticationContext, GlobalPermissionManager globalPermissionManager, TemplateRenderer templateRenderer, WebResourceManager webResourceManager) {
        this.authenticationContext = authenticationContext;
        this.globalPermissionManager = globalPermissionManager;
        this.templateRenderer = templateRenderer;
        this.webResourceManager = webResourceManager;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            String destination = URLEncoder.encode(request.getRequestURI(), "UTF-8");
            response.sendRedirect(request.getContextPath() + "/login.jsp?os_destination=" + destination);
            return;
        }
        if (!this.globalPermissionManager.hasPermission(GlobalPermissionKey.SYSTEM_ADMIN, user)) {
            response.sendError(403, "Jira System Administrator permission is required");
            return;
        }
        this.webResourceManager.requireResource(WEB_RESOURCE_KEY);
        response.setContentType("text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        HashMap<String, Object> context = new HashMap<String, Object>();
        context.put("currentUserDisplayName", user.getDisplayName());
        context.put("webResourceManager", this.webResourceManager);
        context.put("i18n", this.authenticationContext.getI18nHelper());
        this.templateRenderer.render(TEMPLATE_PATH, context, (Writer)response.getWriter());
    }
}

