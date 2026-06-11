/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.dto;

public class SendMessageRequestDto {
    private String body;
    private Long replyToMessageId;
    private String issueKey;

    public String getBody() {
        return this.body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Long getReplyToMessageId() {
        return this.replyToMessageId;
    }

    public void setReplyToMessageId(Long replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public String getIssueKey() {
        return this.issueKey;
    }

    public void setIssueKey(String issueKey) {
        this.issueKey = issueKey;
    }
}

