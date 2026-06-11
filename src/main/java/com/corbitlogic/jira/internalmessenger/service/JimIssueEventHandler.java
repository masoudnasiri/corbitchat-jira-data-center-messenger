/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.event.AssignmentEventPayload;
import com.corbitlogic.jira.internalmessenger.event.CommentEventPayload;
import com.corbitlogic.jira.internalmessenger.event.StatusChangeEventPayload;

public interface JimIssueEventHandler {
    public void handleAssignment(AssignmentEventPayload var1);

    public void handleComment(CommentEventPayload var1);

    public void handleStatusChange(StatusChangeEventPayload var1);
}

