/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.service;

public class JimMessengerException
extends RuntimeException {
    private final int statusCode;

    public JimMessengerException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public JimMessengerException(String message) {
        this(message, 400);
    }

    public int getStatusCode() {
        return this.statusCode;
    }

    public static JimMessengerException unauthorized(String message) {
        return new JimMessengerException(message, 401);
    }

    public static JimMessengerException forbidden(String message) {
        return new JimMessengerException(message, 403);
    }

    public static JimMessengerException notFound(String message) {
        return new JimMessengerException(message, 404);
    }

    public static JimMessengerException badRequest(String message) {
        return new JimMessengerException(message, 400);
    }

    public static JimMessengerException internalError(String message) {
        return new JimMessengerException(message, 500);
    }
}

