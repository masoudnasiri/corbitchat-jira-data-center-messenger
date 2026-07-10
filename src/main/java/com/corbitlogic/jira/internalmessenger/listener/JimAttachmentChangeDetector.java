package com.corbitlogic.jira.internalmessenger.listener;

import com.atlassian.jira.event.issue.IssueEvent;
import java.util.ArrayList;
import java.util.List;
import org.ofbiz.core.entity.GenericValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects Jira issue attachment additions from an {@link IssueEvent} changelog
 * (Sprint 12 — Assistant notification enrichment). An attachment add produces a
 * change item with {@code field="Attachment"}, {@code newvalue}=the attachment
 * id and {@code newstring}=the file name (and empty {@code oldvalue}). Deletes
 * populate {@code oldvalue} instead and are ignored here.
 */
public final class JimAttachmentChangeDetector {
    private static final Logger log = LoggerFactory.getLogger(JimAttachmentChangeDetector.class);
    private static final String ATTACHMENT_FIELD = "Attachment";

    private JimAttachmentChangeDetector() {
    }

    /** A single added attachment extracted from the changelog. */
    public static final class AddedAttachment {
        private final long id;
        private final String fileName;

        AddedAttachment(long id, String fileName) {
            this.id = id;
            this.fileName = fileName;
        }

        public long getId() {
            return this.id;
        }

        public String getFileName() {
            return this.fileName;
        }
    }

    public static boolean hasAttachmentAdd(IssueEvent issueEvent) {
        return !extractAddedAttachments(issueEvent).isEmpty();
    }

    /**
     * The attachments ADDED in this event (newvalue present). Never null.
     */
    public static List<AddedAttachment> extractAddedAttachments(IssueEvent issueEvent) {
        ArrayList<AddedAttachment> added = new ArrayList<AddedAttachment>();
        if (issueEvent == null) {
            return added;
        }
        GenericValue changeLog = issueEvent.getChangeLog();
        if (changeLog == null) {
            return added;
        }
        try {
            List<GenericValue> changeItems = changeLog.getRelated("ChildChangeItem");
            if (changeItems == null || changeItems.isEmpty()) {
                return added;
            }
            for (GenericValue item : changeItems) {
                String field = item.getString("field");
                if (field == null || !ATTACHMENT_FIELD.equalsIgnoreCase(field)) {
                    continue;
                }
                String newValue = item.getString("newvalue");
                if (newValue == null || newValue.trim().isEmpty()) {
                    // No new value → this is a delete/clear, not an add.
                    continue;
                }
                long id = parseLong(newValue.trim());
                if (id <= 0L) {
                    continue;
                }
                String fileName = item.getString("newstring");
                added.add(new AddedAttachment(id, fileName != null ? fileName.trim() : null));
            }
        } catch (Exception ex) {
            log.debug("event=attachment stage=detect outcome=error message={}", (Object) ex.getMessage());
        }
        return added;
    }

    public static String resolveChangeGroupSuffix(IssueEvent issueEvent) {
        if (issueEvent == null) {
            return "no-event";
        }
        GenericValue changeLog = issueEvent.getChangeLog();
        if (changeLog == null) {
            return "no-changelog";
        }
        Object rawId = changeLog.get("id");
        return rawId != null ? String.valueOf(rawId) : "no-changelog";
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }
}
