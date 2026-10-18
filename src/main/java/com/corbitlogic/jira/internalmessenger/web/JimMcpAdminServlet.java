package com.corbitlogic.jira.internalmessenger.web;

import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.atlassian.plugin.webresource.WebResourceManager;

import java.io.IOException;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Admin console page for the MCP Control Plane. System Administrator only. */
public class JimMcpAdminServlet extends HttpServlet {

    private static final String TEMPLATE_PATH = "templates/mcp-admin.vm";
    private static final String WEB_RESOURCE_KEY =
            "com.corbitlogic.corbitchat.jira.dc:jim-mcp-resources";

    private final JiraAuthenticationContext authenticationContext;
    private final GlobalPermissionManager globalPermissionManager;
    private final TemplateRenderer templateRenderer;
    private final WebResourceManager webResourceManager;

    public JimMcpAdminServlet(JiraAuthenticationContext authenticationContext,
                              GlobalPermissionManager globalPermissionManager,
                              TemplateRenderer templateRenderer,
                              WebResourceManager webResourceManager) {
        this.authenticationContext = authenticationContext;
        this.globalPermissionManager = globalPermissionManager;
        this.templateRenderer = templateRenderer;
        this.webResourceManager = webResourceManager;
    }

    @Override
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
        Map<String, Object> context = new HashMap<>();
        context.put("currentUserDisplayName", user.getDisplayName());
        context.put("webResourceManager", this.webResourceManager);
        context.put("i18n", this.authenticationContext.getI18nHelper());
        this.templateRenderer.render(TEMPLATE_PATH, context, (Writer) response.getWriter());
    }
}
