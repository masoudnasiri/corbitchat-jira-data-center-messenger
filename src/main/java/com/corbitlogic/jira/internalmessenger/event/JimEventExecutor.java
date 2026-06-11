/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.event;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimEventExecutor {
    private static final Logger log = LoggerFactory.getLogger(JimEventExecutor.class);
    private final ExecutorService executorService;

    public JimEventExecutor() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "jim-assistant-event-processor");
            thread.setDaemon(true);
            return thread;
        };
        this.executorService = Executors.newSingleThreadExecutor(threadFactory);
    }

    public void submit(String eventName, Runnable task) {
        this.executorService.execute(() -> {
            try {
                task.run();
            }
            catch (Exception ex) {
                log.error("event={} stage=async outcome=error message={}", new Object[]{eventName, ex.getMessage(), ex});
            }
        });
    }
}

