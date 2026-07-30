package com.corbitlogic.jira.internalmessenger.event;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background executor for Jira-event-driven work (Assistant cards, report
 * recalculation, project-chat provisioning).
 *
 * <p><b>P0 fix — separate lanes.</b> Previously ALL work ran on a single shared
 * thread. The report feature submits {@code report_recalc} on every comment /
 * create / update; that task writes an issue custom field via
 * {@code IssueManager.updateIssue(..., ISSUE_UPDATED, ...)}, which synchronously
 * dispatches an ISSUE_UPDATED event on the SAME thread. That event then runs
 * third-party listeners (e.g. the Time-to-SLA plugin's expensive SLA
 * recomputation, ~1s per query) and re-enters our own listener. Latency-sensitive
 * Assistant notifications (mention / assignment / status / attachment / schedule)
 * were queued FIFO behind all of that on the one thread, so under production load
 * they were created minutes-to-hours late.
 *
 * <p>We now route the heavy / best-effort work ({@code report_recalc},
 * {@code project_chat_sync}, {@code project_chat_create}) onto its own dedicated
 * lane, while Assistant notifications run on a separate small pool. Even if the
 * maintenance lane backs up for a long time, Assistant cards are still created
 * promptly. Each event is independent and Assistant writes are fingerprint-deduped,
 * so the notification pool is safe to run with more than one thread.
 */
public class JimEventExecutor {
    private static final Logger log = LoggerFactory.getLogger(JimEventExecutor.class);

    /** Latency-sensitive Assistant/notification cards. */
    private final ExecutorService notificationExecutor;
    /** Heavy, best-effort maintenance work that must never block notifications. */
    private final ExecutorService maintenanceExecutor;

    public JimEventExecutor() {
        this.notificationExecutor = Executors.newFixedThreadPool(2, namedFactory("jim-assistant-event-processor"));
        this.maintenanceExecutor = Executors.newSingleThreadExecutor(namedFactory("jim-maintenance-processor"));
    }

    private static ThreadFactory namedFactory(final String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Best-effort work that writes issue fields / iterates projects and can
     * trigger expensive downstream (third-party) event processing. It is kept off
     * the notification lane so it cannot delay Assistant card creation.
     */
    private static boolean isMaintenanceLane(String eventName) {
        return "report_recalc".equals(eventName)
                || "project_chat_sync".equals(eventName)
                || "project_chat_create".equals(eventName);
    }

    public void submit(String eventName, Runnable task) {
        ExecutorService target = isMaintenanceLane(eventName) ? this.maintenanceExecutor : this.notificationExecutor;
        target.execute(() -> {
            long startedAt = System.currentTimeMillis();
            try {
                task.run();
            } catch (Exception ex) {
                log.error("event={} stage=async outcome=error message={}", new Object[]{eventName, ex.getMessage(), ex});
            } finally {
                long elapsed = System.currentTimeMillis() - startedAt;
                if (elapsed > 5000L) {
                    // Surfaces a slow task that could congest its lane (diagnostic).
                    log.warn("event={} stage=async outcome=slow elapsedMs={}", eventName, elapsed);
                }
            }
        });
    }
}
