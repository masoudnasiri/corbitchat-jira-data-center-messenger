/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.dto;

public class HealthResponseDto {
    private boolean ok;
    private String plugin;

    public HealthResponseDto() {
    }

    public HealthResponseDto(boolean ok, String plugin) {
        this.ok = ok;
        this.plugin = plugin;
    }

    public boolean getOk() {
        return this.ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getPlugin() {
        return this.plugin;
    }

    public void setPlugin(String plugin) {
        this.plugin = plugin;
    }
}

