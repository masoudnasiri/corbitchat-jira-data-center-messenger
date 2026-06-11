/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  net.java.ao.DBParam
 *  net.java.ao.Query
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.corbitlogic.jira.internalmessenger.ao.JimAdminAudit;
import com.corbitlogic.jira.internalmessenger.service.JimAdminAuditService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.java.ao.DBParam;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimAdminAuditServiceImpl
implements JimAdminAuditService {
    private static final Logger log = LoggerFactory.getLogger(JimAdminAuditServiceImpl.class);
    private static final int MAX_DETAILS_LENGTH = 4000;
    private final ActiveObjects activeObjects;

    public JimAdminAuditServiceImpl(ActiveObjects activeObjects) {
        this.activeObjects = activeObjects;
    }

    @Override
    public void record(String userKey, String action, String details) {
        String truncatedDetails = details != null && details.length() > 4000 ? details.substring(0, 4000) : details;
        try {
            this.activeObjects.executeInTransaction(() -> {
                JimAdminAudit entry = (JimAdminAudit)this.activeObjects.create(JimAdminAudit.class, new DBParam[0]);
                entry.setUserKey(userKey);
                entry.setAction(action);
                entry.setDetails(truncatedDetails);
                entry.setCreatedAt(System.currentTimeMillis());
                entry.save();
                return null;
            });
        }
        catch (RuntimeException ex) {
            log.warn("event=admin_audit outcome=error action={} message={}", (Object)action, (Object)ex.getMessage());
        }
        log.info("event=admin_audit userKey={} action={} details={}", new Object[]{userKey, action, truncatedDetails});
    }

    @Override
    public List<JimAdminAudit> listRecent(int limit) {
        int normalizedLimit = Math.max(1, Math.min(200, limit));
        JimAdminAudit[] rows = (JimAdminAudit[])this.activeObjects.find(JimAdminAudit.class, Query.select().order("CREATED_AT DESC, ID DESC").limit(normalizedLimit));
        return new ArrayList<JimAdminAudit>(Arrays.asList(rows));
    }
}

