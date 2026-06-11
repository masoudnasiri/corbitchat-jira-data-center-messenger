/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.servlet.ServletOutputStream
 *  javax.servlet.http.HttpServlet
 *  javax.servlet.http.HttpServletRequest
 *  javax.servlet.http.HttpServletResponse
 */
package com.corbitlogic.jira.internalmessenger.web;

import java.io.IOException;
import java.io.InputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class JimServiceWorkerServlet
extends HttpServlet {
    private static final String RESOURCE_PATH = "/js/jim-sw.js";

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        InputStream resource = ((Object)((Object)this)).getClass().getResourceAsStream(RESOURCE_PATH);
        if (resource == null) {
            response.sendError(404);
            return;
        }
        response.setContentType("application/javascript;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, max-age=0");
        response.setHeader("Service-Worker-Allowed", "/");
        try (InputStream in = resource;
             ServletOutputStream out = response.getOutputStream();){
            int read;
            byte[] buffer = new byte[8192];
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}

