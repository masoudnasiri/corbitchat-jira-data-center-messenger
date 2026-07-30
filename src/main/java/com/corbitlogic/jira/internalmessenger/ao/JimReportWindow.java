package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Materialized per-window report ledger row (Phase 3). Bounded materialization:
 * only recent/relevant windows are stored; the full history is recomputed
 * deterministically from the schedule for the API/export. Idempotent upsert
 * keyed by (issueId, windowStart) — historical rows are never destroyed and no
 * missed window is ever created after the issue's reporting was closed.
 */
@Preload
@Table("JimReportWin")
public interface JimReportWindow extends Entity {

    @Indexed
    Long getIssueId();
    void setIssueId(Long issueId);

    @Indexed
    @StringLength(64)
    String getIssueKey();
    void setIssueKey(String issueKey);

    /** Window start epoch ms (part of the idempotent upsert key). */
    @Indexed
    Long getWindowStart();
    void setWindowStart(Long windowStart);

    Long getWindowEnd();
    void setWindowEnd(Long windowEnd);

    /** Responsible user (assignee) key captured at window materialization. */
    @StringLength(255)
    String getResponsibleUserKey();
    void setResponsibleUserKey(String responsibleUserKey);

    @StringLength(32)
    String getLevelCode();
    void setLevelCode(String levelCode);

    Integer getIntervalMinutes();
    void setIntervalMinutes(Integer intervalMinutes);

    Long getDueAt();
    void setDueAt(Long dueAt);

    /** FUTURE | OPEN | SATISFIED | MISSED | LATE | CLOSED_NOT_REQUIRED | EXEMPT */
    @Indexed
    @StringLength(32)
    String getStatus();
    void setStatus(String status);

    Long getFirstValidReportAt();
    void setFirstValidReportAt(Long firstValidReportAt);

    Long getLastReportAt();
    void setLastReportAt(Long lastReportAt);

    Integer getValidCount();
    void setValidCount(Integer validCount);

    Integer getRawCount();
    void setRawCount(Integer rawCount);

    Integer getDuplicateCount();
    void setDuplicateCount(Integer duplicateCount);

    Integer getOutOfWindowCount();
    void setOutOfWindowCount(Integer outOfWindowCount);

    Integer getLateByMinutes();
    void setLateByMinutes(Integer lateByMinutes);

    @StringLength(StringLength.UNLIMITED)
    String getSourceCommentIds();
    void setSourceCommentIds(String sourceCommentIds);

    Long getCalculatedAt();
    void setCalculatedAt(Long calculatedAt);

    Integer getCalculationVersion();
    void setCalculationVersion(Integer calculationVersion);
}
