/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.ws.rs.core.MediaType
 *  javax.ws.rs.core.Response
 *  javax.ws.rs.core.Response$Status
 *  org.slf4j.Logger
 */
package com.corbitlogic.jira.internalmessenger.rest;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;

public final class JimRestResponses {
    public static final boolean INCLUDE_DEV_DEBUG_FIELDS = true;

    private JimRestResponses() {
    }

    public static Response okJson(Map<String, Object> body) {
        return Response.ok(body).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    public static Response errorJson(int statusCode, String error, String message) {
        LinkedHashMap<String, String> body = new LinkedHashMap<String, String>();
        body.put("error", error);
        body.put("message", message);
        return Response.status((int)statusCode).type(MediaType.APPLICATION_JSON_TYPE).entity(body).build();
    }

    public static Response internalError(Logger log, String endpoint, String userKey, Exception exception, String errorCode, String publicMessage) {
        log.error("endpoint={} outcome=error userKey={} exceptionClass={} message={}", new Object[]{endpoint, userKey, exception.getClass().getName(), exception.getMessage(), exception});
        LinkedHashMap<String, String> body = new LinkedHashMap<String, String>();
        body.put("error", errorCode);
        body.put("message", publicMessage);
        body.put("debugExceptionClass", exception.getClass().getName());
        body.put("debugExceptionMessage", JimRestResponses.safeDebugMessage(exception.getMessage()));
        return Response.status((Response.Status)Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON_TYPE).entity(body).build();
    }

    public static Map<String, Object> singleEntry(String key, Object value) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(key, value);
        return result;
    }

    private static String safeDebugMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}

