/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.dto;

import java.util.List;

public class CreateGroupRequestDto {
    private String name;
    private List<String> memberUserKeys;

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getMemberUserKeys() {
        return this.memberUserKeys;
    }

    public void setMemberUserKeys(List<String> memberUserKeys) {
        this.memberUserKeys = memberUserKeys;
    }
}

