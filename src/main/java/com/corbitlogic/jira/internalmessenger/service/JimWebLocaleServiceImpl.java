package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Web-only per-user localization prefs, stored in plugin settings keyed by
 * user (additive; no AO change, no mobile impact). Effective values resolve
 * user choice → admin default → legacy (English / Gregorian).
 */
public class JimWebLocaleServiceImpl implements JimWebLocaleService {
    private static final String KEY_PREFIX = "com.corbitlogic.jim.web.locale.";

    private final PluginSettingsFactory pluginSettingsFactory;
    private final JimAdminSettingsService adminSettingsService;

    public JimWebLocaleServiceImpl(PluginSettingsFactory pluginSettingsFactory,
                                   JimAdminSettingsService adminSettingsService) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.adminSettingsService = adminSettingsService;
    }

    @Override
    public String getUserLanguage(String userKey) {
        String v = read(userKey, "language");
        if (LANG_FA.equals(v) || LANG_EN.equals(v)) {
            return v;
        }
        return LANG_DEFAULT;
    }

    @Override
    public String getUserCalendar(String userKey) {
        String v = read(userKey, "calendar");
        if (CAL_JALALI.equals(v) || CAL_GREGORIAN.equals(v)) {
            return v;
        }
        return CAL_DEFAULT;
    }

    @Override
    public void saveUserPreferences(String userKey, String language, String calendar) {
        if (userKey == null || userKey.trim().isEmpty()) {
            throw JimMessengerException.badRequest("Missing user");
        }
        String lang = normalize(language, LANG_DEFAULT, LANG_FA, LANG_EN);
        String cal = normalize(calendar, CAL_DEFAULT, CAL_JALALI, CAL_GREGORIAN);
        PluginSettings settings = this.pluginSettingsFactory.createGlobalSettings();
        settings.put(KEY_PREFIX + userKey.trim() + ".language", lang);
        settings.put(KEY_PREFIX + userKey.trim() + ".calendar", cal);
    }

    @Override
    public String effectiveLanguage(String userKey) {
        String user = getUserLanguage(userKey);
        if (LANG_FA.equals(user)) {
            return LANG_FA;
        }
        if (LANG_EN.equals(user)) {
            return LANG_EN;
        }
        // default → admin default → legacy English
        return this.adminSettingsService.isWebPersianUiDefault() ? LANG_FA : LANG_EN;
    }

    @Override
    public String effectiveCalendar(String userKey) {
        String user = getUserCalendar(userKey);
        if (CAL_JALALI.equals(user)) {
            return CAL_JALALI;
        }
        if (CAL_GREGORIAN.equals(user)) {
            return CAL_GREGORIAN;
        }
        return this.adminSettingsService.isWebJalaliDatesDefault() ? CAL_JALALI : CAL_GREGORIAN;
    }

    @Override
    public Map<String, Object> bootstrap(String userKey) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
        LinkedHashMap<String, Object> adminDefaults = new LinkedHashMap<String, Object>();
        adminDefaults.put("persianUi", this.adminSettingsService.isWebPersianUiDefault());
        adminDefaults.put("jalaliDates", this.adminSettingsService.isWebJalaliDatesDefault());
        out.put("adminDefaults", adminDefaults);
        LinkedHashMap<String, Object> userPrefs = new LinkedHashMap<String, Object>();
        userPrefs.put("language", getUserLanguage(userKey));
        userPrefs.put("calendar", getUserCalendar(userKey));
        out.put("userPreferences", userPrefs);
        out.put("effectiveLanguage", effectiveLanguage(userKey));
        out.put("effectiveCalendar", effectiveCalendar(userKey));
        return out;
    }

    private String read(String userKey, String field) {
        if (userKey == null || userKey.trim().isEmpty()) {
            return null;
        }
        Object v = this.pluginSettingsFactory.createGlobalSettings()
                .get(KEY_PREFIX + userKey.trim() + "." + field);
        return v instanceof String ? (String) v : null;
    }

    private static String normalize(String value, String def, String a, String b) {
        String v = value == null ? def : value.trim().toLowerCase(Locale.ROOT);
        if (a.equals(v) || b.equals(v) || def.equals(v)) {
            return v;
        }
        return def;
    }
}
