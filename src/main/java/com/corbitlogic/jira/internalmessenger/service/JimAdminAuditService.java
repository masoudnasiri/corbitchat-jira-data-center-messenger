/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.ao.JimAdminAudit;
import java.util.List;

public interface JimAdminAuditService {
    public void record(String var1, String var2, String var3);

    public List<JimAdminAudit> listRecent(int var1);
}

