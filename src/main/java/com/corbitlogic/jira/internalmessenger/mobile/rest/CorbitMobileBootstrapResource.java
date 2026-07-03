package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.mobile.MobilePreferences;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimAdminSettingsService;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimMobileFeatureService;
import com.corbitlogic.jira.internalmessenger.service.JimMobilePreferenceService;
import com.corbitlogic.jira.internalmessenger.service.JimReadStateService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mobile BFF bootstrap. Mounted under {@code /rest/corbit-mobile/1.0/bootstrap}.
 *
 * <p>Returns a single UI-ready payload for app launch: profile, effective
 * locale, feature flags, persisted preferences, and unread counts. Derived
 * solely from the authenticated user; anonymous requests get HTTP 401.</p>
 */
@Path("/bootstrap")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobileBootstrapResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileBootstrapResource.class);

    private final JiraAuthenticationContext authenticationContext;
    private final AvatarService avatarService;
    private final JimMobilePreferenceService preferenceService;
    private final JimConversationService conversationService;
    private final JimReadStateService readStateService;
    private final JimMobileFeatureService featureService;
    private final JimAdminSettingsService adminSettingsService;

    @Inject
    public CorbitMobileBootstrapResource(JiraAuthenticationContext authenticationContext,
                                         AvatarService avatarService,
                                         JimMobilePreferenceService preferenceService,
                                         JimConversationService conversationService,
                                         JimReadStateService readStateService,
                                         JimMobileFeatureService featureService,
                                         JimAdminSettingsService adminSettingsService) {
        this.authenticationContext = authenticationContext;
        this.avatarService = avatarService;
        this.preferenceService = preferenceService;
        this.conversationService = conversationService;
        this.readStateService = readStateService;
        this.featureService = featureService;
        this.adminSettingsService = adminSettingsService;
    }

    @GET
    public Response bootstrap() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                    "You must be signed in to use CorbitChat Mobile.");
        }

        try {
            MobilePreferences prefs = this.preferenceService.getForUser(user.getKey());

            Locale jiraLocale = this.authenticationContext.getLocale();
            String jiraLocaleTag = jiraLocale != null ? jiraLocale.toLanguageTag() : "en-US";
            // The app's own preference wins; Jira locale is a hint only.
            String effectiveLocale = prefs.getLanguage();

            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("key", user.getKey());
            profile.put("name", user.getName());
            profile.put("displayName", user.getDisplayName());
            profile.put("email", user.getEmailAddress());
            profile.put("avatarUrl", com.corbitlogic.jira.internalmessenger.mobile.JimMobileAvatars.userPath(user));
            profile.put("active", user.isActive());
            // The current user is, by definition, present while bootstrapping.
            profile.put("presence", "ONLINE");

            Map<String, Object> featureFlags = new LinkedHashMap<>();
            featureFlags.put("chat", true);
            featureFlags.put("issues", true);
            featureFlags.put("projects", true);
            featureFlags.put("boards", true);
            featureFlags.put("mobilePush", false);

            // Sprint 04G: the admin-configured per-user allowed mobile features.
            // Absent rules => full access (see JimMobileFeatureService). The app
            // builds its navigation and default landing from this.
            Set<String> allowed = this.featureService.allowedFeatures(user);
            Map<String, Object> mobileFeatures = new LinkedHashMap<>();
            for (String key : JimMobileFeatures.all()) {
                mobileFeatures.put(key, allowed.contains(key));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("module", "corbit-mobile");
            body.put("apiVersion", "1.0");
            body.put("user", profile);
            body.put("locale", effectiveLocale);
            body.put("jiraLocale", jiraLocaleTag);
            body.put("calendar", prefs.getCalendar());
            body.put("serverTime", System.currentTimeMillis());
            body.put("featureFlags", featureFlags);
            body.put("mobileFeatures", mobileFeatures);
            body.put("mobileFeatureOrder", JimMobileFeatures.all());
            body.put("preferences", prefs.toMap());
            body.put("unread", computeUnread(user.getKey()));

            // Branding polish: the admin-configured branding (same values the
            // web chat header uses) so the app can brand its own surfaces.
            Map<String, Object> branding = new LinkedHashMap<>();
            branding.put("title", this.adminSettingsService.getBrandingTitle());
            branding.put("logoUrl", this.adminSettingsService.getBrandingLogoUrl());
            body.put("branding", branding);

            return JimRestResponses.okJson(body);
        } catch (Exception ex) {
            return JimRestResponses.internalError(log,
                    "GET /rest/corbit-mobile/1.0/bootstrap",
                    user.getKey(), ex, "BOOTSTRAP_ERROR",
                    "Could not load your CorbitChat Mobile session.");
        }
    }

    /**
     * Aggregate unread across the user's conversations, grouped by type. Chat is
     * best-effort here: any failure degrades to zero rather than failing launch.
     */
    private Map<String, Object> computeUnread(String userKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> byType = new LinkedHashMap<>();
        int total = 0;
        try {
            List<JimConversation> conversations =
                    this.conversationService.listConversationsForUser(userKey);
            for (JimConversation conversation : conversations) {
                int unread = this.readStateService.getUnreadCount(conversation.getID(), userKey);
                if (unread <= 0) {
                    continue;
                }
                total += unread;
                String type = conversation.getConversationType();
                String bucket = (type == null || type.isEmpty()) ? "OTHER" : type;
                byType.merge(bucket, unread, Integer::sum);
            }
        } catch (Exception ex) {
            log.warn("endpoint=GET /rest/corbit-mobile/1.0/bootstrap stage=unread outcome=degraded message={}",
                    ex.getMessage());
        }
        result.put("total", total);
        result.put("byType", byType);
        return result;
    }
}
