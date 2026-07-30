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
 * Issue-view "Report / گزارش" tab (Sprint 12 — Phase C). Renders a mount point
 * for the report web-resource; all data + actions are driven client-side via
 * {@code /rest/jim/1.0/report/*}. The tab is shown only when the report feature
 * is enabled globally; when the custom fields are absent on this instance the
 * client renders a clear "not configured" notice (graceful disable).
 */
public class JimReportTabPanel extends AbstractIssueTabPanel3 {

    private final WebResourceManager webResourceManager;
    private final JimWebLocaleService webLocaleService;

    public JimReportTabPanel(WebResourceManager webResourceManager, JimWebLocaleService webLocaleService) {
        this.webResourceManager = webResourceManager;
        this.webLocaleService = webLocaleService;
    }

    @Override
    public boolean showPanel(ShowPanelRequest request) {
        // Only surface the tab when the feature is enabled globally; Jira already
        // restricts this to users who can browse the issue.
        return JimReportConfig.isFeatureEnabled();
    }

    @Override
    public List<IssueAction> getActions(GetActionsRequest request) {
        try {
            String key = this.descriptor != null ? this.descriptor.getPluginKey() : null;
            if (key != null) {
                this.webResourceManager.requireResource(key + ":jim-report-resources");
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
            // The Report tab lives in the Jira issue view. When the admin enabled
            // Jira-wide Jalali display (the "plugin is configured for the Jalali
            // calendar" toggle), the whole issue view shows Jalali — so the Report
            // tab follows suit even if the viewer's per-chat locale is Gregorian/EN.
            if (JimReportConfig.jiraWideJalali()) {
                cal = "jalali";
            }
            // A Jalali calendar implies a Persian Report interface (RTL + Persian
            // labels): a Jalali date with English labels reads inconsistently, so
            // the Report tab couples them even if the user's chat language is EN.
            if ("jalali".equals(cal)) {
                lang = "fa";
            }
        } catch (Throwable ignored) {
        }
        return Collections.<IssueAction>singletonList(new ReportAction(issueKey, lang, cal));
    }

    /** Single action = the report app mount point. Rendering is done by the JS. */
    private static final class ReportAction implements IssueAction {
        private final String issueKey;
        private final String lang;
        private final String cal;

        ReportAction(String issueKey, String lang, String cal) {
            this.issueKey = issueKey;
            this.lang = lang;
            this.cal = cal;
        }

        @Override
        public String getHtml() {
            String faClass = "fa".equals(lang) ? " jim-lang-fa" : "";
            String dir = "fa".equals(lang) ? "rtl" : "ltr";
            return "<div id=\"jim-report-app\" class=\"jim-report-app" + faClass + "\""
                    + " dir=\"" + dir + "\""
                    + " data-issue-key=\"" + esc(issueKey) + "\""
                    + " data-effective-lang=\"" + esc(lang) + "\""
                    + " data-effective-calendar=\"" + esc(cal) + "\">"
                    + "<div class=\"jim-report-loading\">\u2026</div></div>";
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
