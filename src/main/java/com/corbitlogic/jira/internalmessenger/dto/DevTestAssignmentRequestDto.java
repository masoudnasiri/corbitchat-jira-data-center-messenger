/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.dto;

public class DevTestAssignmentRequestDto {
    private String targetUserKey;
    private String actorDisplayName;
    private String issueKey;
    private String issueSummary;
    private String issueUrl;

    public String getTargetUserKey() {
        return this.targetUserKey;
    }

    public void setTargetUserKey(String targetUserKey) {
        this.targetUserKey = targetUserKey;
    }

    public String getActorDisplayName() {
        return this.actorDisplayName;
    }

    public void setActorDisplayName(String actorDisplayName) {
        this.actorDisplayName = actorDisplayName;
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
}

