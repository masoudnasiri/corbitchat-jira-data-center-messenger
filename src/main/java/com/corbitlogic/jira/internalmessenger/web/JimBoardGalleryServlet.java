package com.corbitlogic.jira.internalmessenger.web;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Board Gallery page.
 *
 * Renders a visual card directory of all Jira boards the currently
 * authenticated user can access. Boards are fetched client-side from
 * Jira's built-in REST API ({@code /rest/agile/1.0/board}), which already
 * applies the appropriate Jira Software permission filters - we do not
 * re-implement permission logic here and we do not expose any board
 * the user couldn't already see through normal Jira navigation.
 *
 * Clicking a card navigates to the real Jira board page; this servlet
 * does NOT render or proxy board contents.
 */
public class JimBoardGalleryServlet extends HttpServlet {

    private static final String TEMPLATE_PATH = "templates/board-gallery.vm";
    private static final String WEB_RESOURCE_KEY =
            "com.corbitlogic.corbitchat.jira.dc:jim-board-gallery-resources";

    private final JiraAuthenticationContext authenticationContext;
    private final TemplateRenderer templateRenderer;
    private final WebResourceManager webResourceManager;

    public JimBoardGalleryServlet(JiraAuthenticationContext authenticationContext,
                                  TemplateRenderer templateRenderer,
                                  WebResourceManager webResourceManager) {
        this.authenticationContext = authenticationContext;
        this.templateRenderer = templateRenderer;
        this.webResourceManager = webResourceManager;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }
        response.setContentType("text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        this.webResourceManager.requireResource(WEB_RESOURCE_KEY);

        HashMap<String, Object> context = new HashMap<String, Object>();
        context.put("currentUserKey", user.getKey());
        context.put("currentUserDisplayName", user.getDisplayName());
        context.put("webResourceManager", this.webResourceManager);
        context.put("i18n", this.authenticationContext.getI18nHelper());

        this.templateRenderer.render(TEMPLATE_PATH, context, (Writer) response.getWriter());
    }
}
