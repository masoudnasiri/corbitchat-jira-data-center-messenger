/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimPushSubscription;
import java.util.List;
import java.util.Map;

public interface JimPushService {
    public String getVapidPublicKey();

    public JimPushSubscription subscribe(String var1, String var2, String var3, String var4);

    public void unsubscribe(String var1, String var2);

    public List<JimPushSubscription> listSubscriptions(String var1);

    public void pushToUserAsync(String var1);

    public void pushToUserAsync(String var1, String var2, String var3, String var4);

    /**
     * Payload-first push: the service worker reads title/body/tag/url/type/
     * conversationId/messageId straight from the encrypted payload and does
     * not contact the REST API during the push event. The map must contain
     * at least a "title" entry; null or empty maps are dropped.
     */
    public void pushToUserAsync(String var1, Map<String, String> var2);

    public int countSubscriptions();

    public long getFailedPushCount();
}

