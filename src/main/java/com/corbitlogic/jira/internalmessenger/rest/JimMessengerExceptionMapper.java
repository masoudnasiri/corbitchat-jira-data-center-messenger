/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.ws.rs.core.Response
 *  javax.ws.rs.ext.ExceptionMapper
 *  javax.ws.rs.ext.Provider
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class JimMessengerExceptionMapper
implements ExceptionMapper<JimMessengerException> {
    public Response toResponse(JimMessengerException exception) {
        return JimRestResponses.errorJson(exception.getStatusCode(), "request_failed", exception.getMessage());
    }
}

