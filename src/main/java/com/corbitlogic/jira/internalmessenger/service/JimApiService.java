/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.corbitlogic.jira.internalmessenger.dto.ConversationSummaryDto;
import com.corbitlogic.jira.internalmessenger.dto.MessageDto;
import com.corbitlogic.jira.internalmessenger.dto.UserSearchResultDto;
import java.util.List;

public interface JimApiService {
    public List<ConversationSummaryDto> listConversations();

    public ConversationSummaryDto createOrGetDirectConversation(String var1);

    public List<MessageDto> listMessages(int var1, int var2, Integer var3);

    public MessageDto sendMessage(int var1, String var2);

    public void markConversationRead(int var1);

    public List<UserSearchResultDto> searchUsers(String var1);
}

