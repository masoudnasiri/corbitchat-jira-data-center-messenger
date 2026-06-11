/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.dto;

public class DevTestStatusRequestDto {
    private String targetUserKey;
    private String actorDisplayName;
    private String issueKey;
    private String issueSummary;
    private String issueUrl;
    private String fromStatus;
    private String toStatus;

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

    public String getFromStatus() {
        return this.fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return this.toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }
}

