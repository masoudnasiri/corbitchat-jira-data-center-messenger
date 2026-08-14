/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.model;

public enum JimEventType {
    NORMAL,
    MENTION,
    ASSIGNMENT,
    COMMENT,
    STATUS_CHANGE,
    GROUP_EVENT,
    ISSUE_LINK,
    ISSUE_ATTACHMENT,
    SCHEDULE_APPROVAL,
    SCHEDULE_APPROVAL_APPROVED,
    SCHEDULE_APPROVAL_REJECTED,
    // --- Report-specific Assistant events (Report Assistant Notifications) ---
    /** A report was submitted on an issue (general; recipient view is REPORT_SUBMITTED_TO_ME). */
    REPORT_SUBMITTED,
    /** A report was submitted on an issue for which the recipient is the report receiver/team lead. */
    REPORT_SUBMITTED_TO_ME,
    /** Someone replied to a report (recipient = parent report author and/or report receiver). */
    REPORT_REPLY,
    /** The recipient was mentioned inside a report or a report reply. */
    REPORT_MENTION;

}

