/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.dto;

public class AoHealthResponseDto {
    private boolean ok;
    private String activeObjects;

    public AoHealthResponseDto() {
    }

    public AoHealthResponseDto(boolean ok, String activeObjects) {
        this.ok = ok;
        this.activeObjects = activeObjects;
    }

    public boolean getOk() {
        return this.ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getActiveObjects() {
        return this.activeObjects;
    }

    public void setActiveObjects(String activeObjects) {
        this.activeObjects = activeObjects;
    }
}

