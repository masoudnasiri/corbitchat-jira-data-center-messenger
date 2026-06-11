package com.corbitlogic.jira.internalmessenger.service;

/**
 * Thrown when an action is blocked because the plugin license is missing or
 * invalid. Mapped to HTTP 402 with error code LICENSE_INVALID.
 */
public class JimLicenseBlockedException extends JimMessengerException {

    public static final int PAYMENT_REQUIRED = 402;

    public JimLicenseBlockedException() {
        super("CorbitChat license is missing or expired.", PAYMENT_REQUIRED);
    }
}
