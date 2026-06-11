/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimPushSubscription;
import java.util.List;

public interface JimPushService {
    public String getVapidPublicKey();

    public JimPushSubscription subscribe(String var1, String var2, String var3, String var4);

    public void unsubscribe(String var1, String var2);

    public List<JimPushSubscription> listSubscriptions(String var1);

    public void pushToUserAsync(String var1);

    public void pushToUserAsync(String var1, String var2, String var3, String var4);

    public int countSubscriptions();

    public long getFailedPushCount();
}

