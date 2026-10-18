package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimAttachment;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.model.JimAttachmentFileKind;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.java.ao.Query;

/**
 * Bounded shared Files / Media / Links listing for one conversation.
 * Permission is always {@link JimConversationService#getConversationForUser}.
 */
public class JimSharedContentService {
    public static final int DEFAULT_LIMIT = 25;
    public static final int MAX_LIMIT = 50;
    private static final int LINK_SCAN_BATCH = 80;
    private static final int LINK_SCAN_MAX_BATCHES = 8;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b((?:https?://|www\\.)[^\\s<>\\]\"']+)");

    private final ActiveObjects activeObjects;
    private final JimConversationService conversationService;
    private final JimMessageUserStateService userStateService;
    private final JimReviewerService reviewerService;

    public JimSharedContentService(ActiveObjects activeObjects,
                                   JimConversationService conversationService,
                                   JimMessageUserStateService userStateService,
                                   JimReviewerService reviewerService) {
        this.activeObjects = activeObjects;
        this.conversationService = conversationService;
        this.userStateService = userStateService;
        this.reviewerService = reviewerService;
    }

    public Map<String, Object> listShared(int conversationId, String userKey, String type,
                                          Integer cursor, Integer limit) {
        JimValidation.requirePositiveId(conversationId, "conversationId");
        String viewer = JimValidation.requireNonBlank(userKey, "userKey");
        JimConversation conversation = this.conversationService.getConversationForUser(conversationId, viewer);
        String normalized = normalizeType(type);
        int capped = capLimit(limit);
        Set<Integer> hidden = this.userStateService.hiddenMessageIds(viewer, conversation.getID());

        if ("links".equals(normalized)) {
            return listLinks(conversation, viewer, hidden, cursor, capped);
        }
        return listAttachments(conversation, viewer, hidden, normalized, cursor, capped);
    }

    private Map<String, Object> listAttachments(JimConversation conversation, String viewer,
                                                Set<Integer> hidden, String type,
                                                Integer cursor, int limit) {
        String kindClause = "files".equals(type)
                ? "FILE_KIND IN ('FILE','AUDIO')"
                : "FILE_KIND IN ('IMAGE','VIDEO')";
        StringBuilder where = new StringBuilder(
                "CONVERSATION_ID = ? AND (DELETED IS NULL OR DELETED = 0) AND ");
        where.append(kindClause);
        List<Object> params = new ArrayList<Object>();
        params.add(conversation.getID());
        if (cursor != null && cursor.intValue() > 0) {
            where.append(" AND ID < ?");
            params.add(cursor);
        }
        JimAttachment[] rows = (JimAttachment[]) this.activeObjects.find(
                JimAttachment.class,
                Query.select().where(where.toString(), params.toArray())
                        .order("ID DESC")
                        .limit(limit + 1));

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        Integer nextCursor = null;
        boolean hasMore = false;
        for (JimAttachment attachment : rows) {
            if (hidden.contains(Integer.valueOf(attachment.getMessageId()))) {
                continue;
            }
            if (items.size() >= limit) {
                hasMore = true;
                nextCursor = Integer.valueOf(attachment.getID());
                break;
            }
            items.add(toAttachmentItem(conversation.getID(), attachment, type, viewer));
            nextCursor = Integer.valueOf(attachment.getID());
        }
        if (!hasMore && items.size() <= limit) {
            nextCursor = items.isEmpty() ? null : nextCursor;
            if (rows.length <= limit) {
                nextCursor = null;
            }
        }

        LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("conversationId", conversation.getID());
        body.put("type", type);
        body.put("items", items);
        body.put("nextCursor", hasMore ? nextCursor : null);
        body.put("hasMore", Boolean.valueOf(hasMore));
        return body;
    }

    private Map<String, Object> listLinks(JimConversation conversation, String viewer,
                                          Set<Integer> hidden, Integer cursor, int limit) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        Integer scanBefore = cursor;
        boolean exhausted = false;
        Integer lastSeenId = cursor;
        for (int batch = 0; batch < LINK_SCAN_MAX_BATCHES && items.size() <= limit; batch++) {
            StringBuilder where = new StringBuilder(
                    "CONVERSATION_ID = ? AND (DELETED IS NULL OR DELETED = 0)");
            List<Object> params = new ArrayList<Object>();
            params.add(conversation.getID());
            if (scanBefore != null && scanBefore.intValue() > 0) {
                where.append(" AND ID < ?");
                params.add(scanBefore);
            }
            JimMessage[] messages = (JimMessage[]) this.activeObjects.find(
                    JimMessage.class,
                    Query.select().where(where.toString(), params.toArray())
                            .order("ID DESC")
                            .limit(LINK_SCAN_BATCH));
            if (messages.length == 0) {
                exhausted = true;
                break;
            }
            for (JimMessage message : messages) {
                lastSeenId = Integer.valueOf(message.getID());
                if (hidden.contains(Integer.valueOf(message.getID()))) {
                    continue;
                }
                items.addAll(extractLinkItems(conversation.getID(), message, viewer));
                if (items.size() > limit) {
                    break;
                }
            }
            scanBefore = Integer.valueOf(messages[messages.length - 1].getID());
            if (messages.length < LINK_SCAN_BATCH) {
                exhausted = true;
                break;
            }
        }
        boolean hasMore = items.size() > limit || !exhausted;
        if (items.size() > limit) {
            items = new ArrayList<Map<String, Object>>(items.subList(0, limit));
        }
        LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("conversationId", conversation.getID());
        body.put("type", "links");
        body.put("items", items);
        body.put("nextCursor", hasMore ? lastSeenId : null);
        body.put("hasMore", Boolean.valueOf(hasMore && lastSeenId != null));
        return body;
    }

    private List<Map<String, Object>> extractLinkItems(int conversationId, JimMessage message, String viewer) {
        LinkedHashSet<String> urls = new LinkedHashSet<String>();
        if (message.getIssueUrl() != null && !message.getIssueUrl().trim().isEmpty()) {
            urls.add(normalizeUrl(message.getIssueUrl().trim()));
        }
        String body = message.getBody() == null ? "" : message.getBody();
        Matcher matcher = URL_PATTERN.matcher(body);
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (raw.endsWith(".") || raw.endsWith(",") || raw.endsWith(")") || raw.endsWith("؛")) {
                raw = raw.substring(0, raw.length() - 1);
            }
            String normalized = normalizeUrl(raw);
            if (normalized != null) {
                urls.add(normalized);
            }
        }
        if (urls.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        int index = 0;
        for (String url : urls) {
            LinkedHashMap<String, Object> item = baseItem(conversationId, message, viewer, "links");
            item.put("itemId", "link-" + message.getID() + "-" + index);
            item.put("url", url);
            item.put("title", titleFromUrl(url, message));
            item.put("filename", null);
            item.put("fileKind", null);
            item.put("mimeType", null);
            item.put("size", null);
            item.put("previewUrl", null);
            item.put("downloadUrl", null);
            items.add(item);
            index++;
        }
        return items;
    }

    private Map<String, Object> toAttachmentItem(int conversationId, JimAttachment attachment,
                                                 String type, String viewer) {
        JimMessage message = (JimMessage) this.activeObjects.get(JimMessage.class, attachment.getMessageId());
        LinkedHashMap<String, Object> item = baseItem(conversationId, message, viewer, type);
        item.put("itemId", "att-" + attachment.getID());
        item.put("messageId", Integer.valueOf(attachment.getMessageId()));
        item.put("filename", attachment.getOriginalFilename());
        item.put("title", attachment.getOriginalFilename());
        item.put("url", null);
        item.put("fileKind", attachment.getFileKind());
        item.put("mimeType", attachment.getContentType());
        item.put("size", attachment.getFileSize());
        item.put("previewUrl", "/rest/jim/1.0/attachments/" + attachment.getID() + "/preview");
        item.put("downloadUrl", "/rest/jim/1.0/attachments/" + attachment.getID() + "/download");
        return item;
    }

    private LinkedHashMap<String, Object> baseItem(int conversationId, JimMessage message,
                                                   String viewer, String type) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("conversationId", Integer.valueOf(conversationId));
        item.put("messageId", message == null ? null : Integer.valueOf(message.getID()));
        item.put("createdAt", message == null ? null : message.getCreatedAt());
        item.put("type", type);
        String senderKey = message == null ? null : message.getSenderUserKey();
        if (senderKey != null && this.reviewerService != null
                && this.reviewerService.isHiddenSystemAccountUserKey(senderKey)) {
            item.put("senderUserKey", null);
            item.put("senderDisplayName", "System");
        } else {
            item.put("senderUserKey", senderKey);
            item.put("senderDisplayName", displayName(senderKey));
        }
        return item;
    }

    private String displayName(String userKey) {
        if (userKey == null || userKey.trim().isEmpty()) {
            return "";
        }
        try {
            ApplicationUser user = ComponentAccessor.getUserManager().getUserByKey(userKey);
            if (user != null && user.getDisplayName() != null) {
                return user.getDisplayName();
            }
        } catch (RuntimeException ignored) {
        }
        return userKey;
    }

    private static String normalizeType(String type) {
        String value = type == null ? "files" : type.trim().toLowerCase(Locale.ROOT);
        if ("files".equals(value) || "media".equals(value) || "links".equals(value)) {
            return value;
        }
        throw JimMessengerException.badRequest("type must be files, media, or links");
    }

    private static int capLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit.intValue();
        if (value < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private static String normalizeUrl(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String value = raw.startsWith("www.") ? "https://" + raw : raw;
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return null;
            }
            String lower = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(lower) && !"https".equals(lower)) {
                return null;
            }
            return uri.toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String titleFromUrl(String url, JimMessage message) {
        if (message != null && message.getIssueKey() != null && !message.getIssueKey().isEmpty()
                && message.getIssueUrl() != null && url.equals(normalizeUrl(message.getIssueUrl()))) {
            return message.getIssueKey();
        }
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host;
        } catch (RuntimeException ex) {
            return url;
        }
    }
}
