package com.corbitlogic.jira.internalmessenger.web;

import com.atlassian.jira.plugin.issuetabpanel.AbstractIssueTabPanel3;
import com.atlassian.jira.plugin.issuetabpanel.GetActionsRequest;
import com.atlassian.jira.plugin.issuetabpanel.IssueAction;
import com.atlassian.jira.plugin.issuetabpanel.ShowPanelRequest;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.webresource.WebResourceManager;
import com.corbitlogic.jira.internalmessenger.report.JimReportConfig;
import com.corbitlogic.jira.internalmessenger.service.JimWebLocaleService;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Phase 8D — issue-view "فرآیند / Workflow" tab. Renders a mount point for the
 * workflow-viewer web-resource; all data is client-rendered via
 * {@code /rest/jim/1.0/issues/{key}/workflow-view}. Read-only; the viewer never
 * transitions the issue and never calls Jira's native Workflow Designer. Jira
 * already restricts this tab to users who can browse the issue.
 */
public class JimWorkflowTabPanel extends AbstractIssueTabPanel3 {

    private final WebResourceManager webResourceManager;
    private final JimWebLocaleService webLocaleService;

    public JimWorkflowTabPanel(WebResourceManager webResourceManager, JimWebLocaleService webLocaleService) {
        this.webResourceManager = webResourceManager;
        this.webLocaleService = webLocaleService;
    }

    @Override
    public boolean showPanel(ShowPanelRequest request) {
        return true; // every browsable issue has a workflow; Jira gates visibility by Browse
    }

    @Override
    public List<IssueAction> getActions(GetActionsRequest request) {
        try {
            String key = this.descriptor != null ? this.descriptor.getPluginKey() : null;
            if (key != null) {
                this.webResourceManager.requireResource(key + ":jim-workflow-resources");
            }
        } catch (Throwable ignored) {
        }
        String issueKey = request.issue() != null ? request.issue().getKey() : "";
        ApplicationUser viewer = request.remoteUser();
        String lang = "en";
        String cal = "gregorian";
        try {
            String uk = viewer != null ? viewer.getKey() : null;
            lang = this.webLocaleService.effectiveLanguage(uk);
            cal = this.webLocaleService.effectiveCalendar(uk);
            if (JimReportConfig.jiraWideJalali()) {
                cal = "jalali";
            }
            if ("jalali".equals(cal)) {
                lang = "fa";
            }
        } catch (Throwable ignored) {
        }
        return Collections.<IssueAction>singletonList(new WorkflowAction(issueKey, lang, cal));
    }

    private static final class WorkflowAction implements IssueAction {
        private final String issueKey;
        private final String lang;
        private final String cal;

        WorkflowAction(String issueKey, String lang, String cal) {
            this.issueKey = issueKey;
            this.lang = lang;
            this.cal = cal;
        }

        @Override
        public String getHtml() {
            String faClass = "fa".equals(lang) ? " jim-lang-fa" : "";
            String dir = "fa".equals(lang) ? "rtl" : "ltr";
            return "<div id=\"jim-workflow-app\" class=\"jim-workflow-app" + faClass + "\""
                    + " dir=\"" + dir + "\""
                    + " data-issue-key=\"" + esc(issueKey) + "\""
                    + " data-effective-lang=\"" + esc(lang) + "\">"
                    + "<div class=\"jim-workflow-loading\">\u2026</div></div>";
        }

        @Override
        public Date getTimePerformed() {
            return null;
        }

        @Override
        public boolean isDisplayActionAllTab() {
            return false;
        }

        private static String esc(String s) {
            if (s == null) {
                return "";
            }
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }
    }
}
