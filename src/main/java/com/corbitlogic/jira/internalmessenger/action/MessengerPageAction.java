/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.web.action.JiraWebActionSupport
 */
package com.corbitlogic.jira.internalmessenger.action;

import com.atlassian.jira.web.action.JiraWebActionSupport;

public class MessengerPageAction
extends JiraWebActionSupport {
    public String doDefault() throws Exception {
        if (this.getLoggedInApplicationUser() == null) {
            return this.getRedirect("/login.jsp");
        }
        return "success";
    }

    public String getCurrentUserKey() {
        return this.getLoggedInApplicationUser() != null ? this.getLoggedInApplicationUser().getKey() : null;
    }
}

