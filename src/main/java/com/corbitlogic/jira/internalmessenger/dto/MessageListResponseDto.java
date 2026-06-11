/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.dto;

import com.corbitlogic.jira.internalmessenger.dto.MessageDto;
import java.util.ArrayList;
import java.util.List;

public class MessageListResponseDto {
    private List<MessageDto> messages;

    public MessageListResponseDto() {
        this.messages = new ArrayList<MessageDto>();
    }

    public MessageListResponseDto(List<MessageDto> messages) {
        this.messages = messages != null ? messages : new ArrayList();
    }

    public List<MessageDto> getMessages() {
        return this.messages;
    }

    public void setMessages(List<MessageDto> messages) {
        this.messages = messages != null ? messages : new ArrayList();
    }
}

