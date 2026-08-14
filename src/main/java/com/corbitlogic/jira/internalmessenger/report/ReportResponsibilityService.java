package com.corbitlogic.jira.internalmessenger.report;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.changehistory.ChangeHistory;
import com.atlassian.jira.issue.history.ChangeItemBean;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.schedule.JimScheduleApprovalConfig;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import java.util.ArrayList;
import java.util.List;

/**
 * Report-counter pause rule (Report Replies &amp; Working-Days feature).
 *
 * <p>The <b>report receiver / team lead</b> is the schedule-approval approver
 * ({@link JimScheduleApprovalConfig#getApprover(Issue)}). While an issue is
 * assigned to that user, report counting <b>pauses</b>: no required windows are
 * generated for the assignment interval, so missed/streak counters do not grow.
 * The pause is per assignment interval and reversible — reassigning to a normal
 * responsible user resumes counting from the reassignment time with no backfill.</p>
 *
 * <p>Assignment intervals are reconstructed from the Jira assignee change-history
 * ({@code ChangeHistoryManager}) — never from the current assignee alone — so the
 * pause is historically accurate. Static like {@link JimReportConfig} to match the
 * report package style.</p>
 */
public final class ReportResponsibilityService {

    public static final String PAUSE_REASON_ASSIGNED_TO_RECEIVER = "ASSIGNED_TO_REPORT_RECEIVER";

    /** Guard against pathological change histories. */
    private static final int MAX_HISTORY_ITEMS = 5000;

    private ReportResponsibilityService() {
    }

    private static JimAdminSettingsService admin() {
        return ComponentAccessor.getOSGiComponentInstanceOfType(JimAdminSettingsService.class);
    }

    /** Master toggle: pause report counting while assigned to the report receiver. Default on. */
    public static boolean isPauseEnabled() {
        JimAdminSettingsService a = admin();
        return a == null || a.isReportReceiverPauseEnabled();
    }

    /** The report receiver (schedule-approval approver) for this issue, or null. */
    public static ApplicationUser receiver(Issue issue) {
        try {
            return JimScheduleApprovalConfig.getApprover(issue);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Current-state pause info: whether counting is paused right now and why. */
    public static final class PauseState {
        public boolean pausedNow;
        public String pauseReason;      // ASSIGNED_TO_REPORT_RECEIVER | null
        public String receiverKey;
        public String receiverName;
        public boolean enabled;
        public boolean receiverConfigured;
    }

    /** Resolve the current pause state (for the summary DTO). */
    public static PauseState pauseState(Issue issue) {
        PauseState ps = new PauseState();
        ps.enabled = isPauseEnabled();
        ApplicationUser rec = receiver(issue);
        ps.receiverConfigured = rec != null;
        if (rec != null) {
            ps.receiverKey = rec.getKey();
            ps.receiverName = rec.getDisplayName();
        }
        if (!ps.enabled || rec == null || issue == null) {
            return ps;
        }
        ApplicationUser assignee = null;
        try {
            assignee = issue.getAssignee();
        } catch (Throwable ignored) {
        }
        if (assignee != null && assignee.getKey() != null && assignee.getKey().equals(rec.getKey())) {
            ps.pausedNow = true;
            ps.pauseReason = PAUSE_REASON_ASSIGNED_TO_RECEIVER;
        }
        return ps;
    }

    /**
     * Half-open [start,end) intervals in {@code [lowerBoundMs, upperBoundMs)} during
     * which the issue was assigned to the report receiver. Buckets whose start falls
     * in these intervals are skipped by {@link JimReportCalculator}. Empty when the
     * pause rule is disabled, no receiver is configured, or the issue was never
     * assigned to the receiver. An ongoing receiver-assignment extends to
     * {@code upperBoundMs} (so future required windows are also suppressed until a
     * reassignment triggers a recompute).
     */
    public static long[][] pausedIntervals(Issue issue, long lowerBoundMs, long upperBoundMs) {
        if (!isPauseEnabled() || issue == null || upperBoundMs <= lowerBoundMs) {
            return new long[0][];
        }
        ApplicationUser rec = receiver(issue);
        if (rec == null || rec.getKey() == null) {
            return new long[0][];
        }
        String receiverKey = rec.getKey();

        // Reconstruct the assignee timeline from the changelog. Each assignee
        // change at time t means: assignee BEFORE t = from, AFTER t = to.
        List<long[]> segments = new ArrayList<long[]>(); // [startMs, endMs) receiver-assigned
        try {
            List<ChangeHistory> histories =
                    ComponentAccessor.getChangeHistoryManager().getChangeHistories(issue);
            // Collect (timeMs, fromKey, toKey) assignee changes in chronological order.
            List<AssigneeChange> assigneeChanges = new ArrayList<AssigneeChange>();
            int seen = 0;
            if (histories != null) {
                for (ChangeHistory h : histories) {
                    if (h == null || h.getTimePerformed() == null) {
                        continue;
                    }
                    for (ChangeItemBean item : h.getChangeItemBeans()) {
                        if (++seen > MAX_HISTORY_ITEMS) {
                            break;
                        }
                        if (item == null || !"assignee".equalsIgnoreCase(item.getField())) {
                            continue;
                        }
                        AssigneeChange ac = new AssigneeChange();
                        ac.timeMs = h.getTimePerformed().getTime();
                        ac.fromKey = resolveUserKey(item.getFrom());
                        ac.toKey = resolveUserKey(item.getTo());
                        assigneeChanges.add(ac);
                    }
                }
            }
            // Sort by time (changelog is usually ordered, but be safe).
            assigneeChanges.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));

            // Initial assignee (at lowerBound) = the "from" of the first change, or,
            // if there were no changes, the current assignee.
            String currentKey;
            if (!assigneeChanges.isEmpty()) {
                currentKey = assigneeChanges.get(0).fromKey;
            } else {
                ApplicationUser cur = issue.getAssignee();
                currentKey = cur != null ? cur.getKey() : null;
            }
            long segStart = lowerBoundMs;
            for (AssigneeChange ac : assigneeChanges) {
                long t = ac.timeMs;
                if (t <= lowerBoundMs) {
                    // Change happened before the window; just adopt the new assignee.
                    currentKey = ac.toKey;
                    continue;
                }
                if (t > upperBoundMs) {
                    break;
                }
                if (isReceiver(currentKey, receiverKey)) {
                    segments.add(new long[]{segStart, t});
                }
                currentKey = ac.toKey;
                segStart = t;
            }
            // Final open segment up to the upper bound.
            if (isReceiver(currentKey, receiverKey) && segStart < upperBoundMs) {
                segments.add(new long[]{segStart, upperBoundMs});
            }
        } catch (Throwable ex) {
            // Change history unavailable/too expensive: fall back to current-assignee
            // only. If currently assigned to the receiver, pause from now to upper.
            try {
                ApplicationUser cur = issue.getAssignee();
                if (cur != null && receiverKey.equals(cur.getKey())) {
                    long start = Math.max(lowerBoundMs, System.currentTimeMillis());
                    if (start < upperBoundMs) {
                        return new long[][]{{start, upperBoundMs}};
                    }
                }
            } catch (Throwable ignored) {
            }
            return new long[0][];
        }
        return segments.toArray(new long[0][]);
    }

    private static boolean isReceiver(String key, String receiverKey) {
        return key != null && key.equals(receiverKey);
    }

    /** Resolve an assignee changelog value (user key or legacy username) to a user key. */
    private static String resolveUserKey(String changelogValue) {
        if (changelogValue == null || changelogValue.trim().isEmpty()) {
            return null;
        }
        String v = changelogValue.trim();
        try {
            ApplicationUser u = ComponentAccessor.getUserManager().getUserByKey(v);
            if (u == null) {
                u = ComponentAccessor.getUserManager().getUserByName(v);
            }
            return u != null ? u.getKey() : v;
        } catch (Throwable t) {
            return v;
        }
    }

    /** Short signature for the ledger fingerprint (receiver + current assignee + pause toggle). */
    public static String fingerprintSignature(Issue issue) {
        if (!isPauseEnabled()) {
            return "rr:off";
        }
        String recKey = null;
        String asgKey = null;
        try {
            ApplicationUser rec = receiver(issue);
            recKey = rec != null ? rec.getKey() : null;
        } catch (Throwable ignored) {
        }
        try {
            ApplicationUser asg = issue != null ? issue.getAssignee() : null;
            asgKey = asg != null ? asg.getKey() : null;
        } catch (Throwable ignored) {
        }
        return "rr:" + recKey + ":" + asgKey;
    }

    private static final class AssigneeChange {
        long timeMs;
        String fromKey;
        String toKey;
    }
}
