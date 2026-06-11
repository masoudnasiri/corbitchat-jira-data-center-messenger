/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.dto;

public class MessageDto {
    private int id;
    private int conversationId;
    private String senderType;
    private String senderUserKey;
    private String senderDisplayName;
    private String body;
    private String bodyFormat;
    private String eventType;
    private String issueKey;
    private String issueSummary;
    private String issueUrl;
    private String actorUserKey;
    private String actorDisplayName;
    private Long createdAt;

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getConversationId() {
        return this.conversationId;
    }

    public void setConversationId(int conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderType() {
        return this.senderType;
    }

    public void setSenderType(String senderType) {
        this.senderType = senderType;
    }

    public String getSenderUserKey() {
        return this.senderUserKey;
    }

    public void setSenderUserKey(String senderUserKey) {
        this.senderUserKey = senderUserKey;
    }

    public String getSenderDisplayName() {
        return this.senderDisplayName;
    }

    public void setSenderDisplayName(String senderDisplayName) {
        this.senderDisplayName = senderDisplayName;
    }

    public String getBody() {
        return this.body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getBodyFormat() {
        return this.bodyFormat;
    }

    public void setBodyFormat(String bodyFormat) {
        this.bodyFormat = bodyFormat;
    }

    public String getEventType() {
        return this.eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getIssueKey() {
        return this.issueKey;
    }

    public void setIssueKey(String issueKey) {
        this.issueKey = issueKey;
    }

    public String getIssueSummary() {
        return this.issueSummary;
    }

    public void setIssueSummary(String issueSummary) {
        this.issueSummary = issueSummary;
    }

    public String getIssueUrl() {
        return this.issueUrl;
    }

    public void setIssueUrl(String issueUrl) {
        this.issueUrl = issueUrl;
    }

    public String getActorUserKey() {
        return this.actorUserKey;
    }

    public void setActorUserKey(String actorUserKey) {
        this.actorUserKey = actorUserKey;
    }

    public String getActorDisplayName() {
        return this.actorDisplayName;
    }

    public void setActorDisplayName(String actorDisplayName) {
        this.actorDisplayName = actorDisplayName;
    }

    public Long getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}

