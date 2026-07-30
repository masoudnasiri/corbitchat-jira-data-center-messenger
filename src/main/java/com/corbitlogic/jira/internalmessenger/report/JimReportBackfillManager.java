package com.corbitlogic.jira.internalmessenger.report;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.thread.JiraThreadLocalUtil;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.query.Query;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Admin-triggered, rate-limited report backfill (Phase 7). There is <b>no</b>
 * automatic backfill on startup — heavy recompute only runs when an admin asks
 * for it. Work is paced (bounded page size + inter-page sleep + a hard cap) and
 * enqueued through {@link JimReportRecalcCoordinator}, whose single maintenance
 * lane and per-issue coalescing provide the actual back-pressure so a burst can
 * never overwhelm Jira or the SLA/Assistant lanes.
 */
public class JimReportBackfillManager {

    private static final Logger log = LoggerFactory.getLogger(JimReportBackfillManager.class);

    private static final int PAGE_SIZE = 50;
    private static final long PAGE_PAUSE_MS = 750L;
    private static final int HARD_CAP = 5000;

    private final SearchService searchService;
    private final JimReportRecalcCoordinator coordinator;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<String> state = new AtomicReference<String>("IDLE");
    private final AtomicInteger total = new AtomicInteger();
    private final AtomicInteger enqueued = new AtomicInteger();
    private final AtomicLong startedAt = new AtomicLong();
    private final AtomicLong finishedAt = new AtomicLong();
    private final AtomicReference<String> message = new AtomicReference<String>("");

    @Inject
    public JimReportBackfillManager(SearchService searchService, JimReportRecalcCoordinator coordinator) {
        this.searchService = searchService;
        this.coordinator = coordinator;
    }

    /**
     * Start a backfill for issues matching {@code jql} (or, when blank, all
     * issues visible to {@code admin} ordered by most-recently-updated), up to
     * {@code maxIssues} (hard-capped). Returns immediately; poll {@link #status()}.
     *
     * @return false when a backfill is already running (rejected).
     */
    public boolean start(final ApplicationUser admin, final String jql, final int maxIssues) {
        if (!JimReportConfig.isFeatureEnabled()) {
            message.set("Report compliance feature is disabled.");
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            return false; // already running
        }
        final int cap = maxIssues <= 0 ? 500 : Math.min(maxIssues, HARD_CAP);
        state.set("RUNNING");
        total.set(0);
        enqueued.set(0);
        startedAt.set(System.currentTimeMillis());
        finishedAt.set(0);
        message.set("");
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                runBackfill(admin, jql, cap);
            }
        }, "jim-report-backfill");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private void runBackfill(ApplicationUser admin, String jql, int cap) {
        JiraThreadLocalUtil tlu = null;
        try {
            tlu = ComponentAccessor.getComponent(JiraThreadLocalUtil.class);
        } catch (Throwable ignore) {
        }
        if (tlu != null) {
            tlu.preCall(); // background thread needs a Jira thread-local context for search
        }
        try {
            Query query = buildQuery(admin, jql);
            int startIndex = 0;
            int enq = 0;
            while (enq < cap) {
                int pageSize = Math.min(PAGE_SIZE, cap - enq);
                PagerFilter<?> pager = PagerFilter.newPageAlignedFilter(startIndex, pageSize);
                SearchResults<?> results = searchService.search(admin, query, pager);
                List<?> issues = results.getResults();
                if (issues == null || issues.isEmpty()) {
                    break;
                }
                for (Object o : issues) {
                    if (o instanceof com.atlassian.jira.issue.Issue) {
                        com.atlassian.jira.issue.Issue issue = (com.atlassian.jira.issue.Issue) o;
                        coordinator.enqueue(issue, admin, "admin_backfill");
                        enq++;
                        enqueued.set(enq);
                        if (enq >= cap) {
                            break;
                        }
                    }
                }
                total.addAndGet(issues.size());
                startIndex += issues.size();
                if (issues.size() < pageSize) {
                    break; // last page
                }
                try {
                    Thread.sleep(PAGE_PAUSE_MS); // rate-limit / back-pressure
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            state.set("DONE");
            message.set("Enqueued " + enq + " issue(s) for recalculation.");
            log.info("event=report stage=backfill outcome=done enqueued={} scanned={}", enq, total.get());
        } catch (Throwable ex) {
            state.set("ERROR");
            message.set(String.valueOf(ex.getMessage()));
            log.warn("event=report stage=backfill outcome=error message={}", ex.getMessage());
        } finally {
            if (tlu != null) {
                try {
                    tlu.postCall(log);
                } catch (Throwable ignore) {
                }
            }
            finishedAt.set(System.currentTimeMillis());
            running.set(false);
        }
    }

    private Query buildQuery(ApplicationUser admin, String jql) {
        if (jql != null && !jql.trim().isEmpty()) {
            SearchService.ParseResult pr = searchService.parseQuery(admin, jql.trim());
            if (pr.isValid()) {
                return pr.getQuery();
            }
            throw new IllegalArgumentException("Invalid JQL: " + pr.getErrors().getErrorMessages());
        }
        // Default: all visible issues, most-recently-updated first.
        return JqlQueryBuilder.newBuilder().orderBy().updatedDate(
                com.atlassian.query.order.SortOrder.DESC).buildQuery();
    }

    /** Current backfill status (safe to call any time). */
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("state", state.get());
        m.put("running", running.get());
        m.put("scanned", total.get());
        m.put("enqueued", enqueued.get());
        m.put("startedAt", startedAt.get());
        m.put("finishedAt", finishedAt.get());
        m.put("message", message.get());
        return m;
    }
}
