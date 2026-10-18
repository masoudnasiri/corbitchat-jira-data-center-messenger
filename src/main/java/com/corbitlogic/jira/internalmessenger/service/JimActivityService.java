package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimActivity;
import java.util.List;

/**
 * Ephemeral chat activity (typing / recording / uploading) with a short TTL
 * (Sprint 12 polish). Server-mediated so it works across web, native, and PWA
 * over the existing foreground message polling — no WebSocket/SSE required.
 * Never a message; carries no draft text or file metadata.
 */
public interface JimActivityService {

    String TYPING = "typing";
    String RECORDING = "recording";
    String UPLOADING = "uploading";
    String UPLOADING_MEDIA = "uploading_media";

    /** Upsert the actor's activity in a conversation, refreshing its TTL. */
    void record(String userKey, int conversationId, String activity);

    /** Clear the actor's activity in a conversation immediately (on send/leave). */
    void clear(String userKey, int conversationId);

    /** Non-expired activities in a conversation, excluding the viewer. */
    List<JimActivity> activeFor(int conversationId, String excludeUserKey);
}
