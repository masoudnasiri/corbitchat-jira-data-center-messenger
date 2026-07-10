package com.corbitlogic.jira.internalmessenger.event;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.listener.JimAttachmentChangeDetector;
import com.corbitlogic.jira.internalmessenger.util.JimNotificationRecipientResolver;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of a Jira issue attachment-added event (Sprint 12 —
 * Assistant notification enrichment). Carries the added files' metadata
 * (id/name/size/mime) plus the recipients (issue assignee + reporter) so the
 * Assistant can show a meaningful "file attached" card with secure download
 * links. No file bytes are held — only metadata; the download link is resolved
 * per-viewer with a live Jira browse/attachment permission check.
 */
public final class AttachmentEventPayload {
    private final String issueKey;
    private final String issueSummary;
    private final String actorUserKey;
    private final String actorDisplayName;
    private final Long eventTime;
    private final String changeGroupSuffix;
    private final List<FileMeta> files;
    private final List<String> recipientUserKeys;

    private AttachmentEventPayload(String issueKey, String issueSummary, String actorUserKey,
                                   String actorDisplayName, Long eventTime, String changeGroupSuffix,
                                   List<FileMeta> files, List<String> recipientUserKeys) {
        this.issueKey = issueKey;
        this.issueSummary = issueSummary;
        this.actorUserKey = actorUserKey;
        this.actorDisplayName = actorDisplayName;
        this.eventTime = eventTime;
        this.changeGroupSuffix = changeGroupSuffix;
        this.files = files;
        this.recipientUserKeys = recipientUserKeys;
    }

    public static final class FileMeta {
        private final long id;
        private final String fileName;
        private final Long fileSize;
        private final String contentType;

        FileMeta(long id, String fileName, Long fileSize, String contentType) {
            this.id = id;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.contentType = contentType;
        }

        public long getId() {
            return this.id;
        }

        public String getFileName() {
            return this.fileName;
        }

        public Long getFileSize() {
            return this.fileSize;
        }

        public String getContentType() {
            return this.contentType;
        }
    }

    public static AttachmentEventPayload from(IssueEvent issueEvent) {
        if (issueEvent == null) {
            return null;
        }
        Issue issue = issueEvent.getIssue();
        if (issue == null) {
            return null;
        }
        List<JimAttachmentChangeDetector.AddedAttachment> added =
                JimAttachmentChangeDetector.extractAddedAttachments(issueEvent);
        if (added.isEmpty()) {
            return null;
        }
        List<String> recipients = JimNotificationRecipientResolver.resolveStatusChangeRecipients(issue);
        if (recipients.isEmpty()) {
            return null;
        }
        // Resolve live metadata (size/mime) for each added attachment. If the
        // attachment can no longer be resolved (already deleted), fall back to
        // the name captured in the changelog.
        ArrayList<FileMeta> files = new ArrayList<FileMeta>();
        for (JimAttachmentChangeDetector.AddedAttachment a : added) {
            String fileName = a.getFileName();
            Long fileSize = null;
            String contentType = null;
            try {
                Attachment att = ComponentAccessor.getAttachmentManager().getAttachment(a.getId());
                if (att != null) {
                    if (att.getFilename() != null) {
                        fileName = att.getFilename();
                    }
                    fileSize = att.getFilesize();
                    contentType = att.getMimetype();
                }
            } catch (Exception ignored) {
                // Keep changelog name; metadata stays null.
            }
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "attachment";
            }
            files.add(new FileMeta(a.getId(), fileName, fileSize, contentType));
        }
        if (files.isEmpty()) {
            return null;
        }
        ApplicationUser actor = issueEvent.getUser();
        String actorDisplayName = actor != null && actor.getDisplayName() != null
                ? actor.getDisplayName() : "Someone";
        Long eventTime = issueEvent.getTime() != null
                ? issueEvent.getTime().getTime() : System.currentTimeMillis();
        return new AttachmentEventPayload(issue.getKey(), issue.getSummary(),
                actor != null ? actor.getKey() : null, actorDisplayName, eventTime,
                JimAttachmentChangeDetector.resolveChangeGroupSuffix(issueEvent),
                files, recipients);
    }

    public String getIssueKey() {
        return this.issueKey;
    }

    public String getIssueSummary() {
        return this.issueSummary;
    }

    public String getActorUserKey() {
        return this.actorUserKey;
    }

    public String getActorDisplayName() {
        return this.actorDisplayName;
    }

    public Long getEventTime() {
        return this.eventTime;
    }

    public String getChangeGroupSuffix() {
        return this.changeGroupSuffix;
    }

    public List<FileMeta> getFiles() {
        return this.files;
    }

    public List<String> getRecipientUserKeys() {
        return this.recipientUserKeys;
    }

    /**
     * Serialize the added attachments for {@code JimMessage.eventData}: one
     * attachment per line as {@code id<TAB>fileName}. Only an id + a fallback
     * name are stored — size/type/existence and the secure download link are
     * ALWAYS re-derived live per-viewer at read time (JimRestJsonMapper), so a
     * deleted file or a lost permission never leaks stale metadata.
     */
    public String toEventData() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < this.files.size(); i++) {
            FileMeta f = this.files.get(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(f.getId()).append('\t').append(sanitizeLine(f.getFileName()));
        }
        return sb.toString();
    }

    private static String sanitizeLine(String value) {
        if (value == null) {
            return "attachment";
        }
        String cleaned = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.isEmpty() ? "attachment" : cleaned;
    }
}
