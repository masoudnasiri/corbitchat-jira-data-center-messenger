/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.dto;

import com.corbitlogic.jira.internalmessenger.dto.UserSearchResultDto;
import java.util.ArrayList;
import java.util.List;

public class UserSearchResponseDto {
    private List<UserSearchResultDto> users;

    public UserSearchResponseDto() {
        this.users = new ArrayList<UserSearchResultDto>();
    }

    public UserSearchResponseDto(List<UserSearchResultDto> users) {
        this.users = users != null ? users : new ArrayList();
    }

    public List<UserSearchResultDto> getUsers() {
        return this.users;
    }

    public void setUsers(List<UserSearchResultDto> users) {
        this.users = users != null ? users : new ArrayList();
    }
}

