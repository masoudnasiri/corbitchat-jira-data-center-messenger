/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.dto.UserSearchResultDto;
import java.util.List;

public interface JimUserSearchService {
    public List<UserSearchResultDto> searchActiveUsers(String var1);
}

