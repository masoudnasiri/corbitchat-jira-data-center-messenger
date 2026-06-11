/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.dto;

public class ErrorResponseDto {
    private String error;
    private String message;

    public ErrorResponseDto() {
    }

    public ErrorResponseDto(String error, String message) {
        this.error = error;
        this.message = message;
    }

    public String getError() {
        return this.error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

