/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.servlet.ServletOutputStream
 *  javax.servlet.http.HttpServletResponse
 *  javax.servlet.http.HttpServletResponseWrapper
 */
package com.corbitlogic.jira.internalmessenger.mobile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

class JimCapturingHttpServletResponse
extends HttpServletResponseWrapper {
    private final ByteArrayOutputStream capture = new ByteArrayOutputStream(8192);
    private ServletOutputStream outputStream;
    private PrintWriter writer;
    private Charset charset = StandardCharsets.UTF_8;

    JimCapturingHttpServletResponse(HttpServletResponse response) {
        super(response);
        String encoding = response.getCharacterEncoding();
        if (encoding != null && !encoding.isEmpty()) {
            try {
                this.charset = Charset.forName(encoding);
            }
            catch (RuntimeException ex) {
                this.charset = StandardCharsets.UTF_8;
            }
        }
    }

    public void setCharacterEncoding(String charsetName) {
        if (charsetName != null && !charsetName.isEmpty()) {
            try {
                this.charset = Charset.forName(charsetName);
            }
            catch (RuntimeException ex) {
                this.charset = StandardCharsets.UTF_8;
            }
        }
        super.setCharacterEncoding(charsetName);
    }

    public ServletOutputStream getOutputStream() throws IOException {
        if (this.writer != null) {
            throw new IllegalStateException("getWriter() has already been called");
        }
        if (this.outputStream == null) {
            this.outputStream = new ServletOutputStream(){

                public void write(int value) throws IOException {
                    JimCapturingHttpServletResponse.this.capture.write(value);
                }

                public void write(byte[] buffer, int offset, int length) throws IOException {
                    JimCapturingHttpServletResponse.this.capture.write(buffer, offset, length);
                }
            };
        }
        return this.outputStream;
    }

    public PrintWriter getWriter() throws IOException {
        if (this.outputStream != null) {
            throw new IllegalStateException("getOutputStream() has already been called");
        }
        if (this.writer == null) {
            this.writer = new PrintWriter(new OutputStreamWriter((OutputStream)this.capture, this.charset), true);
        }
        return this.writer;
    }

    byte[] getCapturedBody() throws IOException {
        if (this.writer != null) {
            this.writer.flush();
        }
        return this.capture.toByteArray();
    }

    Charset getCaptureCharset() {
        return this.charset;
    }
}

