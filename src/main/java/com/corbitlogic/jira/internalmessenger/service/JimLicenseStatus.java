package com.corbitlogic.jira.internalmessenger.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable snapshot of the plugin license state.
 *
 * The raw license key is intentionally never stored, logged or exposed here.
 */
public final class JimLicenseStatus {

    /** Overall state label: VALID, MISSING, EXPIRED or INVALID. */
    private final String state;
    private final boolean licensed;
    private final boolean present;
    private final boolean valid;
    /** null = unknown (e.g. license API unavailable). */
    private final Boolean dataCenter;
    private final Boolean evaluation;
    private final String errorKey;
    private final String errorMessage;
    private final String pluginKey;
    private final String expiryDate;

    private JimLicenseStatus(String state, boolean licensed, boolean present, boolean valid,
                             Boolean dataCenter, Boolean evaluation,
                             String errorKey, String errorMessage,
                             String pluginKey, String expiryDate) {
        this.state = state;
        this.licensed = licensed;
        this.present = present;
        this.valid = valid;
        this.dataCenter = dataCenter;
        this.evaluation = evaluation;
        this.errorKey = errorKey;
        this.errorMessage = errorMessage;
        this.pluginKey = pluginKey;
        this.expiryDate = expiryDate;
    }

    public static JimLicenseStatus valid(String pluginKey, Boolean dataCenter, Boolean evaluation, String expiryDate) {
        return new JimLicenseStatus("VALID", true, true, true, dataCenter, evaluation, null, null, pluginKey, expiryDate);
    }

    public static JimLicenseStatus missing(String pluginKey) {
        return new JimLicenseStatus("MISSING", false, false, false, null, null,
                null, "No license installed for this app.", pluginKey, null);
    }

    public static JimLicenseStatus invalid(String pluginKey, String errorKey, String errorMessage,
                                           Boolean dataCenter, Boolean evaluation, String expiryDate) {
        String state = "EXPIRED".equals(errorKey) ? "EXPIRED" : "INVALID";
        return new JimLicenseStatus(state, false, true, false, dataCenter, evaluation,
                errorKey, errorMessage, pluginKey, expiryDate);
    }

    public static JimLicenseStatus unavailable(String pluginKey, String errorMessage) {
        return new JimLicenseStatus("INVALID", false, false, false, null, null,
                "LICENSE_CHECK_UNAVAILABLE", errorMessage, pluginKey, null);
    }

    public String getState() {
        return state;
    }

    public boolean isLicensed() {
        return licensed;
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isValid() {
        return valid;
    }

    public Boolean getDataCenter() {
        return dataCenter;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getPluginKey() {
        return pluginKey;
    }

    /** Full status for Jira System Administrators. Never contains the raw license. */
    public Map<String, Object> toAdminMap() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("state", state);
        map.put("licensed", licensed);
        map.put("present", present);
        map.put("valid", valid);
        map.put("dataCenter", dataCenter);
        map.put("evaluation", evaluation);
        map.put("errorKey", errorKey);
        map.put("errorMessage", errorMessage);
        map.put("pluginKey", pluginKey);
        map.put("expiryDate", expiryDate);
        return map;
    }

    /** Minimal status for regular chat users. */
    public Map<String, Object> toMinimalMap() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("licensed", licensed);
        return map;
    }
}
