package com.corbitlogic.jira.internalmessenger.report;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.thread.JiraThreadLocalUtil;
import com.corbitlogic.jira.internalmessenger.event.JimEventExecutor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-issue coalescing / debounce for {@code report_recalc} (Phase 1 hardening).
 *
 * <p>Before this, every comment / create / update on an issue submitted its own
 * full recalculation to the maintenance lane. A burst of edits produced a burst
 * of expensive (~seconds) recalcs, congesting the single maintenance thread.</p>
 *
 * <p>This coordinator guarantees <b>at most one pending job per issue</b>: an
 * incoming trigger either starts a short debounce timer (first trigger) or is
 * coalesced into the already-pending job. If a trigger arrives <i>while the job
 * is running</i>, the issue is flagged dirty-again so exactly one more recalc
 * runs after the current one finishes. The real work is submitted onto the
 * existing {@link JimEventExecutor} maintenance lane — heavy report work never
 * moves to the latency-sensitive Assistant/notification lane.</p>
 *
 * <p>The recalc body is wrapped in {@link JiraThreadLocalUtil#preCall()} /
 * {@code postCall} so the background thread gets a clean RequestCache that is
 * cleared afterwards, and emits a structured timing log line.</p>
 */
public class JimReportRecalcCoordinator {
    private static final Logger log = LoggerFactory.getLogger(JimReportRecalcCoordinator.class);

    /** Coalescing window: triggers within this window collapse into one recalc. */
    private static final long DEBOUNCE_MS = 3000L;
    /** A recalc taking longer than this is counted as a slow job (diagnostics). */
    private static final long SLOW_JOB_MS = 5000L;

    private final JimReportService reportService;
    private final JimEventExecutor eventExecutor;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<Long, Entry> pending = new ConcurrentHashMap<Long, Entry>();

    // Phase 7 diagnostics (lock-free counters for the admin diagnostics API).
    private final AtomicLong totalRuns = new AtomicLong();
    private final AtomicLong totalSkipped = new AtomicLong();
    private final AtomicLong slowRuns = new AtomicLong();
    private final AtomicLong maxElapsedMs = new AtomicLong();
    private final AtomicLong lastElapsedMs = new AtomicLong();
    private final AtomicLong lastRunAt = new AtomicLong();

    public JimReportRecalcCoordinator(JimReportService reportService, JimEventExecutor eventExecutor) {
        this.reportService = reportService;
        this.eventExecutor = eventExecutor;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemonFactory("jim-report-recalc-debounce"));
    }

    private static ThreadFactory daemonFactory(final String name) {
        return runnable -> {
            Thread t = new Thread(runnable, name);
            t.setDaemon(true);
            return t;
        };
    }

    /** Mutable per-issue coalescing state (guarded by {@link ConcurrentHashMap#compute}). */
    private static final class Entry {
        final long issueId;
        ApplicationUser actor;
        String reason;
        long queuedAt;
        boolean scheduled; // a debounce timer is pending
        boolean running;   // the maintenance task is executing
        boolean dirtyAgain; // a trigger arrived while running → re-run once

        Entry(long issueId) {
            this.issueId = issueId;
        }
    }

    /**
     * Enqueue a recalc for an issue. Coalesces with any pending job for the same
     * issue; if a job is currently running, marks it dirty-again for exactly one
     * follow-up run.
     */
    public void enqueue(final Issue issue, final ApplicationUser actor, final String reason) {
        if (issue == null || issue.getId() == null) {
            return;
        }
        // Belt-and-suspenders: never enqueue from inside our own metric write.
        if (JimReportServiceImpl.isReportWriteInProgress()) {
            return;
        }
        final long issueId = issue.getId();
        pending.compute(issueId, new BiFunction<Long, Entry, Entry>() {
            @Override
            public Entry apply(Long key, Entry existing) {
                if (existing == null) {
                    Entry e = new Entry(issueId);
                    e.actor = actor;
                    e.reason = reason;
                    e.queuedAt = System.currentTimeMillis();
                    e.scheduled = true;
                    scheduleTimer(issueId);
                    return e;
                }
                // Refresh latest actor/reason; keep the earliest queuedAt so the
                // structured log reports true end-to-end coalesced latency.
                existing.actor = actor;
                existing.reason = reason;
                if (existing.running) {
                    existing.dirtyAgain = true;
                } else if (!existing.scheduled) {
                    // Defensive: entry exists but nothing pending → (re)schedule.
                    existing.scheduled = true;
                    existing.queuedAt = System.currentTimeMillis();
                    scheduleTimer(issueId);
                }
                // else: already scheduled → coalesce (do nothing).
                return existing;
            }
        });
    }

    private void scheduleTimer(final long issueId) {
        try {
            scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    onTimerFire(issueId);
                }
            }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable ex) {
            // Scheduler rejected (shutdown): drop the pending entry so it is not stuck.
            pending.remove(issueId);
            log.debug("event=report stage=coalesce outcome=schedule_rejected issueId={} message={}",
                    issueId, ex.getMessage());
        }
    }

    /** Debounce timer elapsed → submit the coalesced job onto the maintenance lane. */
    private void onTimerFire(final long issueId) {
        Entry e = pending.compute(issueId, new BiFunction<Long, Entry, Entry>() {
            @Override
            public Entry apply(Long key, Entry cur) {
                if (cur == null) {
                    return null;
                }
                if (cur.running) {
                    // Already running (should not happen from the timer) → ensure
                    // a follow-up run rather than dropping this trigger.
                    cur.dirtyAgain = true;
                    return cur;
                }
                cur.scheduled = false;
                cur.running = true;
                return cur;
            }
        });
        if (e == null || !e.running) {
            return;
        }
        eventExecutor.submit("report_recalc", new Runnable() {
            @Override
            public void run() {
                runRecalc(issueId);
            }
        });
    }

    /** Runs on the maintenance lane: clean ThreadLocal context, recalc, structured log, re-run if dirty. */
    private void runRecalc(final long issueId) {
        Entry e = pending.get(issueId);
        if (e == null) {
            return;
        }
        final long queuedAt = e.queuedAt;
        final String reason = e.reason;
        final ApplicationUser actor = e.actor;
        final long startedAt = System.currentTimeMillis();

        JiraThreadLocalUtil tlu = null;
        try {
            tlu = ComponentAccessor.getComponent(JiraThreadLocalUtil.class);
        } catch (Throwable ignore) {
        }
        if (tlu != null) {
            tlu.preCall();
        }

        JimReportRecalcOutcome outcome = null;
        String issueKey = null;
        try {
            Issue issue = ComponentAccessor.getIssueManager().getIssueObject(issueId);
            if (issue != null) {
                issueKey = issue.getKey();
                outcome = reportService.recalculateWithOutcome(issue, actor, reason);
            }
        } catch (Throwable ex) {
            log.warn("event=report stage=recalc outcome=error issueId={} message={}", issueId, ex.getMessage());
        } finally {
            if (tlu != null) {
                try {
                    tlu.postCall(log);
                } catch (Throwable ignore) {
                }
            }
        }

        final long finishedAt = System.currentTimeMillis();
        final long elapsed = finishedAt - startedAt;
        totalRuns.incrementAndGet();
        lastElapsedMs.set(elapsed);
        lastRunAt.set(finishedAt);
        if (elapsed > maxElapsedMs.get()) {
            maxElapsedMs.set(elapsed);
        }
        if (elapsed > SLOW_JOB_MS) {
            slowRuns.incrementAndGet();
        }
        if (outcome != null && outcome.skipped) {
            totalSkipped.incrementAndGet();
        }
        boolean skipped = outcome == null || outcome.skipped;
        String skipReason = outcome != null ? outcome.skipReason : "no_issue";
        int valuesChanged = outcome != null ? outcome.valuesChanged : 0;
        int fieldsWritten = outcome != null ? outcome.fieldsWritten : 0;
        boolean dispatched = outcome != null && outcome.dispatched;
        if (outcome != null && outcome.issueKey != null) {
            issueKey = outcome.issueKey;
        }

        // Re-run decision: if a trigger arrived while running, schedule exactly
        // one more coalesced run; otherwise drop the entry.
        final boolean[] reRun = {false};
        pending.compute(issueId, new BiFunction<Long, Entry, Entry>() {
            @Override
            public Entry apply(Long key, Entry cur) {
                if (cur == null) {
                    return null;
                }
                if (cur.dirtyAgain) {
                    cur.dirtyAgain = false;
                    cur.running = false;
                    cur.scheduled = true;
                    cur.queuedAt = System.currentTimeMillis();
                    reRun[0] = true;
                    return cur;
                }
                return null; // no follow-up → remove
            }
        });
        if (reRun[0]) {
            scheduleTimer(issueId);
        }

        log.info("event=report stage=recalc outcome=done issueKey={} reason={} queuedAtMs={} startedAtMs={} finishedAtMs={} elapsedMs={} coalescedLatencyMs={} valuesChanged={} fieldsWritten={} dispatched={} skipped={} skipReason={} reRun={}",
                new Object[]{issueKey, reason, queuedAt, startedAt, finishedAt,
                        (finishedAt - startedAt), (finishedAt - queuedAt),
                        valuesChanged, fieldsWritten, dispatched, skipped, skipReason, reRun[0]});
    }

    /** Current number of issues with a pending/coalesced/running recalc (queue depth). */
    public int pendingCount() {
        return pending.size();
    }

    /**
     * Enqueue a recalc by issue id (used by the admin-triggered backfill). Loads
     * the issue on the caller's thread; no-op when it cannot be resolved.
     */
    public void enqueueById(long issueId, ApplicationUser actor, String reason) {
        try {
            Issue issue = ComponentAccessor.getIssueManager().getIssueObject(issueId);
            if (issue != null) {
                enqueue(issue, actor, reason);
            }
        } catch (Throwable ex) {
            log.debug("event=report stage=coalesce outcome=enqueue_by_id_error issueId={} message={}",
                    issueId, ex.getMessage());
        }
    }

    /** Phase 7 diagnostics: queue depth + slow-job stats for the admin API. */
    public Map<String, Object> diagnostics() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("queueDepth", pending.size());
        m.put("debounceMs", DEBOUNCE_MS);
        m.put("slowJobThresholdMs", SLOW_JOB_MS);
        m.put("totalRuns", totalRuns.get());
        m.put("totalSkipped", totalSkipped.get());
        m.put("slowRuns", slowRuns.get());
        m.put("maxElapsedMs", maxElapsedMs.get());
        m.put("lastElapsedMs", lastElapsedMs.get());
        m.put("lastRunAt", lastRunAt.get());
        return m;
    }

    /** Stop the debounce scheduler (plugin disable/uninstall). */
    public void shutdown() {
        try {
            scheduler.shutdownNow();
        } catch (Throwable ignore) {
        }
    }
}
