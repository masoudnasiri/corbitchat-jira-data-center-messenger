package com.corbitlogic.jira.internalmessenger.report;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.CommentManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Links report <b>replies</b> to their parent report comment (Report Replies &amp;
 * Working-Days feature).
 *
 * <p>Replies are ordinary Jira comments (real, audit-compatible) created by the
 * report-reply endpoint, whose wiki body quotes the parent report:
 * {@code [~author]\n\n{quote}<parent excerpt>{quote}\n\n<text>}. A reply is never a
 * report submission (the parent parser rule {@link JimReportConfig#isReportSubmissionBody(String)}
 * returns false because the marker only appears inside the {@code {quote}}), so it
 * never distorts valid/missed counts.</p>
 *
 * <p>Linking is deterministic for endpoint-created replies: the quoted excerpt is a
 * prefix of the parent report body, so a reply is attributed to the report whose
 * normalized body best matches its quoted excerpt.</p>
 */
public final class JimReportReplies {

    private static final int PREVIEW_MAX = 160;
    /** Only match the leading portion of a quote against a report body. */
    private static final int MATCH_MAX = 200;

    private JimReportReplies() {
    }

    /** All comments on the issue, or empty on failure. */
    private static List<Comment> allComments(Issue issue) {
        try {
            CommentManager cm = ComponentAccessor.getCommentManager();
            List<Comment> all = cm != null ? cm.getComments(issue) : null;
            return all != null ? all : new ArrayList<Comment>();
        } catch (Throwable t) {
            return new ArrayList<Comment>();
        }
    }

    /**
     * Map each report comment id → its reply comments (chronological). Only comments
     * that are NOT report submissions and quote a report are considered replies.
     */
    public static Map<Long, List<Comment>> repliesByReport(Issue issue, List<Comment> reportComments) {
        Map<Long, List<Comment>> out = new LinkedHashMap<Long, List<Comment>>();
        if (reportComments == null || reportComments.isEmpty()) {
            return out;
        }
        for (Comment rc : reportComments) {
            out.put(rc.getId(), new ArrayList<Comment>());
        }
        // Pre-normalize report bodies for matching. Whitespace is COLLAPSED because
        // the quoted excerpt joins the marker + text with a single space
        // ("#گزارش jsjsdasd") while the stored report body uses newlines
        // ("#گزارش\n\njsjsdasd"); without collapsing, the substring match fails and
        // replies never link (repliesCount stays 0).
        Map<Long, String> reportBodyNorm = new LinkedHashMap<Long, String>();
        for (Comment rc : reportComments) {
            reportBodyNorm.put(rc.getId(),
                    collapseWs(JimReportConfig.normalizePersian(rc.getBody() == null ? "" : rc.getBody())));
        }
        for (Comment c : allComments(issue)) {
            if (c == null || c.getBody() == null) {
                continue;
            }
            if (JimReportConfig.isReportSubmissionBody(c.getBody())) {
                continue; // a report submission, not a reply
            }
            String quote = extractQuote(c.getBody());
            if (quote == null || quote.isEmpty()) {
                continue; // not a reply (no quoted parent)
            }
            String quoteNorm = collapseWs(JimReportConfig.normalizePersian(quote));
            if (quoteNorm.length() > MATCH_MAX) {
                quoteNorm = quoteNorm.substring(0, MATCH_MAX);
            }
            Long best = bestMatch(quoteNorm, reportBodyNorm);
            if (best != null) {
                out.get(best).add(c);
            }
        }
        return out;
    }

    /** Collapse all whitespace runs to a single space + trim (whitespace-insensitive match). */
    private static String collapseWs(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    /** The report id whose (whitespace-collapsed) body best matches the quoted excerpt, or null. */
    private static Long bestMatch(String quoteNorm, Map<Long, String> reportBodyNorm) {
        if (quoteNorm.length() < 3) {
            return null;
        }
        Long best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (Map.Entry<Long, String> e : reportBodyNorm.entrySet()) {
            String head = e.getValue(); // already collapsed
            if (head.isEmpty()) {
                continue;
            }
            if (head.length() > MATCH_MAX) {
                head = head.substring(0, MATCH_MAX);
            }
            boolean match = head.startsWith(quoteNorm) || quoteNorm.startsWith(head)
                    || head.contains(quoteNorm) || quoteNorm.contains(head);
            // The quoted excerpt is (a prefix of) the true parent body, so prefer the
            // report whose length is closest to the quote — this disambiguates when one
            // report body is a prefix of another.
            int diff = Math.abs(head.length() - quoteNorm.length());
            if (match && diff < bestDiff) {
                best = e.getKey();
                bestDiff = diff;
            }
        }
        return best;
    }

    /** First {@code {quote}...{quote}} content, trimmed of trailing ellipsis, or null. */
    static String extractQuote(String body) {
        if (body == null) {
            return null;
        }
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?is)\\{quote\\}(.*?)\\{quote\\}").matcher(body);
            if (m.find()) {
                String q = m.group(1).trim();
                // Drop a trailing ellipsis added by the excerpt truncation.
                q = q.replaceAll("[\\u2026.]+$", "").trim();
                return q;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * The parent report comment that a given comment replies to, or {@code null} when the
     * comment is not a report reply (it's a report submission, an ordinary comment, or its
     * quote does not match any report on the issue). Used by the Assistant event pipeline to
     * classify report replies and resolve the parent report author.
     */
    public static Comment parentReportFor(Issue issue, long replyCommentId, String replyBody) {
        if (issue == null || replyBody == null) {
            return null;
        }
        if (JimReportConfig.isReportSubmissionBody(replyBody)) {
            return null; // a report submission, not a reply
        }
        if (extractQuote(replyBody) == null) {
            return null; // no quoted parent → not a report reply
        }
        List<Comment> reports = new ArrayList<Comment>();
        for (Comment c : allComments(issue)) {
            if (c != null && JimReportConfig.isReportSubmissionBody(c.getBody())) {
                reports.add(c);
            }
        }
        if (reports.isEmpty()) {
            return null;
        }
        Map<Long, List<Comment>> byReport = repliesByReport(issue, reports);
        for (Comment rc : reports) {
            List<Comment> replies = byReport.get(rc.getId());
            if (replies != null) {
                for (Comment r : replies) {
                    if (r != null && r.getId() != null && r.getId() == replyCommentId) {
                        return rc;
                    }
                }
            }
        }
        return null;
    }

    /** Populate {@code repliesCount} + latest-reply preview on each report item. */
    public static void enrichSummary(Issue issue, JimReportSummary s, List<Comment> reportComments) {
        Map<Long, List<Comment>> byReport = repliesByReport(issue, reportComments);
        for (JimReportSummary.Item it : s.items) {
            List<Comment> replies = byReport.get(it.commentId);
            if (replies == null || replies.isEmpty()) {
                continue;
            }
            it.repliesCount = replies.size();
            Comment latest = replies.get(replies.size() - 1);
            it.latestReplyMs = latest.getCreated() != null ? latest.getCreated().getTime() : null;
            it.latestReplyPreview = preview(latest.getBody());
        }
    }

    /** Plain, quote/mention-stripped preview of a reply body. */
    public static String preview(String body) {
        if (body == null) {
            return null;
        }
        String cleaned = JimReportConfig.stripQuotedSegments(body)
                .replaceAll("\\[~[^\\]]+\\]", "")   // mention tokens
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > PREVIEW_MAX ? cleaned.substring(0, PREVIEW_MAX) + "\u2026" : cleaned;
    }
}
