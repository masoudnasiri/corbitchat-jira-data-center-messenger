package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.query.Query;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileIssues;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimMobileFeatureService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mobile BFF dashboard (Sprint 03). Mounted under
 * {@code /rest/corbit-mobile/1.0/dashboard}.
 *
 * <p>One aggregated, permission-aware, bounded payload for the home screen:
 * unread chat totals (reusing the chat read-state) plus counts for the
 * whitelisted task filters and a small "today" preview. Counts come from
 * {@code SearchService.searchCount(user, ...)} and the preview from a bounded
 * {@code SearchService.search(...)}, so Jira issue security is enforced per
 * user. Every sub-query is best-effort: a failure degrades that section to a
 * safe default rather than failing the whole dashboard.</p>
 */
@Path("/dashboard")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobileDashboardResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileDashboardResource.class);
    private static final int PREVIEW_SIZE = 5;

    private final JiraAuthenticationContext authenticationContext;
    private final SearchService searchService;
    private final AvatarService avatarService;
    private final ApplicationProperties applicationProperties;
    private final JimConversationService conversationService;
    private final JimReadStateService readStateService;
    private final JimMobileFeatureService featureService;

    @Inject
    public CorbitMobileDashboardResource(JiraAuthenticationContext authenticationContext,
                                         SearchService searchService,
                                         AvatarService avatarService,
                                         ApplicationProperties applicationProperties,
                                         JimConversationService conversationService,
                                         JimReadStateService readStateService,
                                         JimMobileFeatureService featureService) {
        this.authenticationContext = authenticationContext;
        this.searchService = searchService;
        this.avatarService = avatarService;
        this.applicationProperties = applicationProperties;
        this.conversationService = conversationService;
        this.readStateService = readStateService;
        this.featureService = featureService;
    }

    @GET
    public Response dashboard() {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                    "You must be signed in to use CorbitChat Mobile.");
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.DASHBOARD)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.DASHBOARD);
        }
        try {
            Map<String, Object> tasks = new LinkedHashMap<>();
            for (String filter : JimMobileIssues.FILTERS) {
                tasks.put(filter, count(viewer, filter));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("serverTime", System.currentTimeMillis());
            body.put("unread", computeUnread(viewer.getKey()));
            body.put("tasks", tasks);
            body.put("todayPreview", preview(viewer, "today"));
            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/dashboard", viewer.getKey(), ex,
                    "internal_error", "An internal error occurred while loading the dashboard.");
        }
    }

    private int count(ApplicationUser viewer, String filter) {
        try {
            Query query = JimMobileIssues.buildQuery(viewer, this.searchService, filter);
            return (int) this.searchService.searchCount(viewer, query);
        } catch (Exception ex) {
            log.warn("endpoint=GET /rest/corbit-mobile/1.0/dashboard stage=count filter={} outcome=degraded message={}",
                    filter, ex.getMessage());
            return 0;
        }
    }

    private List<Map<String, Object>> preview(ApplicationUser viewer, String filter) {
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            Query query = JimMobileIssues.buildQuery(viewer, this.searchService, filter);
            PagerFilter<Issue> pager = new PagerFilter<>(PREVIEW_SIZE);
            SearchResults<Issue> results = this.searchService.search(viewer, query, pager);
            for (Issue issue : results.getResults()) {
                items.add(JimMobileIssues.toIssueMap(issue, viewer, this.avatarService, this.applicationProperties));
            }
        } catch (Exception ex) {
            log.warn("endpoint=GET /rest/corbit-mobile/1.0/dashboard stage=preview filter={} outcome=degraded message={}",
                    filter, ex.getMessage());
        }
        return items;
    }

    /**
     * Aggregate unread across the user's conversations, grouped by type — same
     * shape as bootstrap's {@code unread}. Best-effort: degrades to zero.
     */
    private Map<String, Object> computeUnread(String userKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> byType = new LinkedHashMap<>();
        int total = 0;
        try {
            List<JimConversation> conversations =
                    this.conversationService.listConversationsForUser(userKey);
            for (JimConversation conversation : conversations) {
                int unread = this.readStateService.getUnreadCount(conversation.getID(), userKey);
                if (unread <= 0) {
                    continue;
                }
                total += unread;
                String type = conversation.getConversationType();
                String bucket = (type == null || type.isEmpty()) ? "OTHER" : type;
                byType.merge(bucket, unread, Integer::sum);
            }
        } catch (Exception ex) {
            log.warn("endpoint=GET /rest/corbit-mobile/1.0/dashboard stage=unread outcome=degraded message={}",
                    ex.getMessage());
        }
        result.put("total", total);
        result.put("byType", byType);
        return result;
    }
}
