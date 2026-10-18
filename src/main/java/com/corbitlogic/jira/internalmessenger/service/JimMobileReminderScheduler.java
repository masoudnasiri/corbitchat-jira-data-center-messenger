package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.query.Query;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileIssues;
import com.corbitlogic.jira.internalmessenger.mobile.push.MobilePushEvent;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Daily overdue/today reminder push sweep (Sprint 05).
 *
 * <p>Runs a lightweight hourly tick and, once per calendar day at
 * {@link #REMINDER_HOUR} (server time), sends at most one overdue and one
 * today reminder per user who has a registered device. It reuses the exact
 * effective-due-date JQL from {@link JimMobileIssues} by impersonating each
 * device owner, so counts respect Jira permissions and the Sprint 04E/04F
 * "proposed completion date" fallback. Feature access (TASKS) is rechecked by
 * {@link JimMobilePushService} before any delivery.</p>
 *
 * <p>Uses a plain single-thread scheduler (no cluster coordination) which is
 * appropriate for this single-node internal deployment. The sweep is a fast
 * no-op whenever push is not configured or no devices are registered.</p>
 */
public class JimMobileReminderScheduler implements LifecycleAware {

    private static final Logger log = LoggerFactory.getLogger(JimMobileReminderScheduler.class);

    /** Server-local hour (0-23) at which the daily sweep fires. */
    private static final int REMINDER_HOUR = 8;
    private static final long MAX_USERS_PER_SWEEP = 2000L;

    private final JimMobileDeviceService deviceService;
    private final JimMobilePushService pushService;
    private final JimMobileFeatureService featureService;
    private final SearchService searchService;
    private final JiraAuthenticationContext authenticationContext;
    private final UserManager userManager;

    private volatile ScheduledExecutorService scheduler;
    private volatile int lastRunDayOfYear = -1;

    public JimMobileReminderScheduler(JimMobileDeviceService deviceService,
                                      JimMobilePushService pushService,
                                      JimMobileFeatureService featureService,
                                      SearchService searchService,
                                      JiraAuthenticationContext authenticationContext,
                                      UserManager userManager) {
        this.deviceService = deviceService;
        this.pushService = pushService;
        this.featureService = featureService;
        this.searchService = searchService;
        this.authenticationContext = authenticationContext;
        this.userManager = userManager;
    }

    @Override
    public void onStart() {
        start();
    }

    @Override
    public void onStop() {
        stop();
    }

    private synchronized void start() {
        if (this.scheduler != null) {
            return;
        }
        try {
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, "jim-mobile-reminder");
                thread.setDaemon(true);
                return thread;
            };
            this.scheduler = Executors.newSingleThreadScheduledExecutor(factory);
            // Hourly tick; the daily guard inside tick() ensures a single sweep per day.
            this.scheduler.scheduleAtFixedRate(this::tick, 5, 60, TimeUnit.MINUTES);
            log.info("event=mobilepush stage=reminder_scheduler outcome=started hour={}", REMINDER_HOUR);
        } catch (RuntimeException ex) {
            // Never let scheduler setup block plugin startup.
            log.warn("event=mobilepush stage=reminder_scheduler outcome=start_failed message={}", ex.getMessage());
        }
    }

    private synchronized void stop() {
        if (this.scheduler != null) {
            this.scheduler.shutdownNow();
            this.scheduler = null;
            log.info("event=mobilepush stage=reminder_scheduler outcome=stopped");
        }
    }

    private void tick() {
        try {
            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY);
            int dayOfYear = now.get(Calendar.DAY_OF_YEAR);
            if (hour != REMINDER_HOUR || dayOfYear == this.lastRunDayOfYear) {
                return;
            }
            if (!this.pushService.isConfigured()) {
                return;
            }
            this.lastRunDayOfYear = dayOfYear;
            runSweep(dayStamp(now));
        } catch (RuntimeException ex) {
            log.warn("event=mobilepush stage=reminder_tick outcome=error message={}", ex.getMessage());
        }
    }

    private void runSweep(String dayStamp) {
        List<String> userKeys = this.deviceService.distinctActiveDeviceUserKeys();
        if (userKeys.isEmpty()) {
            return;
        }
        int processed = 0;
        for (String userKey : userKeys) {
            if (processed++ >= MAX_USERS_PER_SWEEP) {
                break;
            }
            try {
                remindUser(userKey, dayStamp);
            } catch (RuntimeException ex) {
                log.debug("event=mobilepush stage=reminder_user outcome=error message={}", ex.getMessage());
            }
        }
        log.info("event=mobilepush stage=reminder_sweep outcome=done users={}", processed);
    }

    private void remindUser(String userKey, String dayStamp) {
        ApplicationUser user = this.userManager.getUserByKey(userKey);
        if (user == null || !user.isActive()) {
            return;
        }
        // Cheap pre-check; the sender rechecks again before delivery.
        if (!this.featureService.isAllowed(user, JimMobileFeatures.TASKS)) {
            return;
        }
        int overdue = countFilter(user, "overdue");
        int today = countFilter(user, "today");
        if (overdue > 0) {
            String summary = overdue == 1 ? "You have 1 overdue task" : "You have " + overdue + " overdue tasks";
            this.pushService.sendToUser(userKey, MobilePushEvent
                    .builder(MobilePushEvent.REMINDER_OVERDUE, JimMobileFeatures.TASKS)
                    .category(MobilePushEvent.CAT_REMINDER)
                    .entityId("overdue")
                    .deepLink("corbithub://tasks/overdue")
                    .summaryText(summary)
                    .genericBody("Task reminder")
                    .dedupeKey("reminder|overdue|" + userKey + "|" + dayStamp)
                    .extra("taskFilter", "overdue")
                    .build());
        }
        if (today > 0) {
            String summary = today == 1 ? "You have 1 task due today" : "You have " + today + " tasks due today";
            this.pushService.sendToUser(userKey, MobilePushEvent
                    .builder(MobilePushEvent.REMINDER_TODAY, JimMobileFeatures.TASKS)
                    .category(MobilePushEvent.CAT_REMINDER)
                    .entityId("today")
                    .deepLink("corbithub://tasks/today")
                    .summaryText(summary)
                    .genericBody("Task reminder")
                    .dedupeKey("reminder|today|" + userKey + "|" + dayStamp)
                    .extra("taskFilter", "today")
                    .build());
        }
    }

    /**
     * Count matching issues for {@code filter} as {@code user}, impersonating so
     * the search enforces the user's own permissions. Only the total is read.
     */
    private int countFilter(ApplicationUser user, String filter) {
        ApplicationUser previous = this.authenticationContext.getLoggedInUser();
        try {
            this.authenticationContext.setLoggedInUser(user);
            Query query = JimMobileIssues.buildQuery(user, this.searchService, filter);
            PagerFilter<Issue> pager = new PagerFilter<>(1);
            pager.setStart(0);
            SearchResults<Issue> results = this.searchService.search(user, query, pager);
            return results.getTotal();
        } catch (Exception ex) {
            log.debug("event=mobilepush stage=reminder_count outcome=error filter={} message={}", filter, ex.getMessage());
            return 0;
        } finally {
            this.authenticationContext.setLoggedInUser(previous);
        }
    }

    private static String dayStamp(Calendar cal) {
        return cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH);
    }
}
