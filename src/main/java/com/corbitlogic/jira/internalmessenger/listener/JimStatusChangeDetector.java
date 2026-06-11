/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.event.issue.IssueEvent
 *  org.ofbiz.core.entity.GenericValue
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.listener;

import com.atlassian.jira.event.issue.IssueEvent;
import java.util.List;
import org.ofbiz.core.entity.GenericValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JimStatusChangeDetector {
    private static final Logger log = LoggerFactory.getLogger(JimStatusChangeDetector.class);
    private static final String STATUS_FIELD = "status";

    private JimStatusChangeDetector() {
    }

    public static boolean isStatusChange(IssueEvent issueEvent) {
        return JimStatusChangeDetector.detect(issueEvent) != null;
    }

    public static StatusChangeDetails detect(IssueEvent issueEvent) {
        if (issueEvent == null) {
            return null;
        }
        GenericValue changeLog = issueEvent.getChangeLog();
        if (changeLog == null) {
            log.debug("event=status stage=detect outcome=skipped reason=no_changelog");
            return null;
        }
        try {
            List<GenericValue> changeItems = changeLog.getRelated("ChildChangeItem");
            if (changeItems == null || changeItems.isEmpty()) {
                log.debug("event=status stage=detect outcome=skipped reason=no_change_items");
                return null;
            }
            for (GenericValue item : changeItems) {
                if (!JimStatusChangeDetector.isStatusField(item)) continue;
                String fromStatus = JimStatusChangeDetector.safeFromStatus(item);
                String toStatus = JimStatusChangeDetector.safeToStatus(item);
                if (fromStatus == null || toStatus == null) {
                    log.debug("event=status stage=detect outcome=skipped reason=incomplete_status_values");
                    continue;
                }
                if (fromStatus.equalsIgnoreCase(toStatus)) {
                    log.debug("event=status stage=detect outcome=skipped reason=unchanged_status");
                    continue;
                }
                log.debug("event=status stage=detect outcome=matched fromStatus={} toStatus={}", (Object)fromStatus, (Object)toStatus);
                return new StatusChangeDetails(fromStatus, toStatus, JimStatusChangeDetector.resolveChangeGroupSuffix(issueEvent));
            }
        }
        catch (Exception ex) {
            log.debug("event=status stage=detect outcome=error message={}", (Object)ex.getMessage());
        }
        return null;
    }

    public static boolean isStatusField(GenericValue changeItem) {
        if (changeItem == null) {
            return false;
        }
        String field = changeItem.getString("field");
        return field != null && STATUS_FIELD.equalsIgnoreCase(field);
    }

    public static String safeFromStatus(GenericValue changeItem) {
        return JimStatusChangeDetector.firstNonBlank(changeItem != null ? changeItem.getString("oldstring") : null, changeItem != null ? changeItem.getString("oldvalue") : null);
    }

    public static String safeToStatus(GenericValue changeItem) {
        return JimStatusChangeDetector.firstNonBlank(changeItem != null ? changeItem.getString("newstring") : null, changeItem != null ? changeItem.getString("newvalue") : null);
    }

    public static String resolveChangeGroupSuffix(IssueEvent issueEvent) {
        if (issueEvent == null) {
            return "no-event";
        }
        GenericValue changeLog = issueEvent.getChangeLog();
        if (changeLog == null) {
            return "no-changelog";
        }
        Long groupId = changeLog.getLong("id");
        if (groupId != null) {
            return String.valueOf(groupId);
        }
        Object rawId = changeLog.get("id");
        return rawId != null ? String.valueOf(rawId) : "no-changelog";
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback.trim();
        }
        return null;
    }

    public static final class StatusChangeDetails {
        private final String fromStatus;
        private final String toStatus;
        private final String changeGroupSuffix;

        public StatusChangeDetails(String fromStatus, String toStatus, String changeGroupSuffix) {
            this.fromStatus = fromStatus;
            this.toStatus = toStatus;
            this.changeGroupSuffix = changeGroupSuffix;
        }

        public String getFromStatus() {
            return this.fromStatus;
        }

        public String getToStatus() {
            return this.toStatus;
        }

        public String getChangeGroupSuffix() {
            return this.changeGroupSuffix;
        }
    }
}

