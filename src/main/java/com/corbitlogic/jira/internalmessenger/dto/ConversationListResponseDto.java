/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.dto;

import com.corbitlogic.jira.internalmessenger.dto.ConversationSummaryDto;
import java.util.ArrayList;
import java.util.List;

public class ConversationListResponseDto {
    private List<ConversationSummaryDto> conversations;

    public ConversationListResponseDto() {
        this.conversations = new ArrayList<ConversationSummaryDto>();
    }

    public ConversationListResponseDto(List<ConversationSummaryDto> conversations) {
        this.conversations = conversations != null ? conversations : new ArrayList();
    }

    public List<ConversationSummaryDto> getConversations() {
        return this.conversations;
    }

    public void setConversations(List<ConversationSummaryDto> conversations) {
        this.conversations = conversations != null ? conversations : new ArrayList();
    }
}

