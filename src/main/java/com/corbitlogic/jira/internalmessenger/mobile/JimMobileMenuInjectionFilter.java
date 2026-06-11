/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  javax.inject.Inject
 *  javax.servlet.Filter
 *  javax.servlet.FilterChain
 *  javax.servlet.FilterConfig
 *  javax.servlet.ServletException
 *  javax.servlet.ServletRequest
 *  javax.servlet.ServletResponse
 *  javax.servlet.http.HttpServletRequest
 *  javax.servlet.http.HttpServletResponse
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.mobile;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.bootstrap.JimPluginBootstrap;
import com.corbitlogic.jira.internalmessenger.mobile.JimCapturingHttpServletResponse;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileMenuHtmlInjector;
import java.io.IOException;
import java.nio.charset.Charset;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimMobileMenuInjectionFilter
implements Filter {
    private static final Logger log = LoggerFactory.getLogger(JimMobileMenuInjectionFilter.class);
    private final JiraAuthenticationContext authenticationContext;

    @Inject
    public JimMobileMenuInjectionFilter(JiraAuthenticationContext authenticationContext, JimPluginBootstrap pluginBootstrap) {
        this.authenticationContext = authenticationContext;
    }

    public void init(FilterConfig filterConfig) {
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        HttpServletResponse httpResponse = (HttpServletResponse)response;
        if (!this.shouldAttemptInjection(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }
        JimCapturingHttpServletResponse capturingResponse = new JimCapturingHttpServletResponse(httpResponse);
        chain.doFilter(request, (ServletResponse)capturingResponse);
        byte[] body = capturingResponse.getCapturedBody();
        if (body.length == 0) {
            return;
        }
        if (!this.isHtmlResponse((HttpServletResponse)capturingResponse)) {
            this.writeBody(httpResponse, body);
            return;
        }
        Charset charset = capturingResponse.getCaptureCharset();
        String html = new String(body, charset);
        if (!JimMobileMenuHtmlInjector.shouldInject(html)) {
            this.writeBody(httpResponse, body);
            return;
        }
        String modifiedHtml = JimMobileMenuHtmlInjector.inject(html, httpRequest.getContextPath());
        byte[] modifiedBody = modifiedHtml.getBytes(charset);
        this.writeBody(httpResponse, modifiedBody);
        log.debug("Injected CorbitChat item into Jira mobile menu for path={}", (Object)httpRequest.getRequestURI());
    }

    public void destroy() {
    }

    private boolean shouldAttemptInjection(HttpServletRequest request) {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        if (path.contains("/login.jsp") || path.contains("/plugins/servlet/jim/chat")) {
            return false;
        }
        return path.contains("/plugins/servlet/mobile");
    }

    private boolean isHtmlResponse(HttpServletResponse response) {
        String contentType = response.getContentType();
        return contentType != null && contentType.toLowerCase().contains("text/html");
    }

    private void writeBody(HttpServletResponse response, byte[] body) throws IOException {
        if (body.length == 0) {
            return;
        }
        if (!response.isCommitted()) {
            response.setContentLength(body.length);
        }
        response.getOutputStream().write(body);
        response.getOutputStream().flush();
    }
}

