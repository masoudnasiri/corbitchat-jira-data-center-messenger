/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.avatar.Avatar$Size
 *  com.atlassian.jira.avatar.AvatarService
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.plugin.webresource.WebResourceManager
 *  com.atlassian.templaterenderer.TemplateRenderer
 *  javax.servlet.http.HttpServlet
 *  javax.servlet.http.HttpServletRequest
 *  javax.servlet.http.HttpServletResponse
 */
package com.corbitlogic.jira.internalmessenger.web;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.corbitlogic.jira.internalmessenger.bootstrap.JimPluginBootstrap;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.HashMap;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class JimChatServlet
extends HttpServlet {
    private static final String TEMPLATE_PATH = "templates/messenger.vm";
    private static final String PLUGIN_KEY = "com.corbitlogic.corbitchat.jira.dc";
    private static final String WEB_RESOURCE_KEY = "com.corbitlogic.corbitchat.jira.dc:jim-messenger-resources";
    private static final String LOGO_RESOURCE_NAME = "corbit-app-logo.png";
    private static final String LOGO_CLASSPATH = "images/corbit-app-logo.png";
    private final JiraAuthenticationContext authenticationContext;
    private final TemplateRenderer templateRenderer;
    private final WebResourceManager webResourceManager;
    private final AvatarService avatarService;
    private final JimAdminSettingsService adminSettingsService;

    public JimChatServlet(JiraAuthenticationContext authenticationContext, TemplateRenderer templateRenderer, WebResourceManager webResourceManager, AvatarService avatarService, JimPluginBootstrap pluginBootstrap, JimAdminSettingsService adminSettingsService) {
        this.authenticationContext = authenticationContext;
        this.templateRenderer = templateRenderer;
        this.webResourceManager = webResourceManager;
        this.avatarService = avatarService;
        this.adminSettingsService = adminSettingsService;
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }
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

