/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.jira.avatar.Avatar$Size
 *  com.atlassian.jira.avatar.AvatarService
 *  com.atlassian.jira.avatar.AvatarsDisabledException
 *  com.atlassian.jira.bc.user.search.UserSearchParams
 *  com.atlassian.jira.bc.user.search.UserSearchParams$Builder
 *  com.atlassian.jira.bc.user.search.UserSearchService
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.avatar.AvatarsDisabledException;
import com.atlassian.jira.bc.user.search.UserSearchParams;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.dto.UserSearchResultDto;
import com.corbitlogic.jira.internalmessenger.service.JimAccessPolicyService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.service.JimUserSearchService;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class JimUserSearchServiceImpl
implements JimUserSearchService {
    public static final int MIN_QUERY_LENGTH = 2;
    public static final int MAX_RESULTS = 20;
    private final UserSearchService userSearchService;
    private final AvatarService avatarService;
    private final JiraAuthenticationContext authenticationContext;
    private final JimPermissionService permissionService;
    private final JimAccessPolicyService accessPolicyService;

    public JimUserSearchServiceImpl(UserSearchService userSearchService, AvatarService avatarService, JiraAuthenticationContext authenticationContext, JimPermissionService permissionService, JimAccessPolicyService accessPolicyService) {
        this.userSearchService = userSearchService;
        this.avatarService = avatarService;
        this.authenticationContext = authenticationContext;
        this.permissionService = permissionService;
        this.accessPolicyService = accessPolicyService;
    }

    @Override
    public List<UserSearchResultDto> searchActiveUsers(String query) {
        this.validateSearchQuery(query);
        String viewerKey = this.permissionService.requireAuthenticatedUserKey();
        if (!this.accessPolicyService.isChatEnabled()) {
            return new ArrayList<UserSearchResultDto>();
        }
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        UserSearchParams params = new UserSearchParams.Builder().includeActive(true).includeInactive(false).canMatchEmail(false).maxResults(Integer.valueOf(20)).sorted(true).build();
        List<ApplicationUser> users = this.userSearchService.findUsers(query.trim(), params);
        LinkedHashSet<String> seenKeys = new LinkedHashSet<String>();
        ArrayList<UserSearchResultDto> results = new ArrayList<UserSearchResultDto>();
        for (ApplicationUser user : users) {
            if (user == null || !user.isActive() || !seenKeys.add(user.getKey()) || !user.getKey().equals(viewerKey) && !this.accessPolicyService.canSearch(viewerKey, user.getKey())) continue;
            results.add(this.toUserSearchResult(user, viewer));
            if (results.size() < 20) continue;
            break;
        }
        return results;
    }

    void validateSearchQuery(String query) {
        String normalized = JimValidation.requireNonBlank(query, "query");
        if (normalized.length() < 2) {
            throw JimMessengerException.badRequest("query must be at least 2 characters");
        }
        JimValidation.requireMaxLength(normalized, 100, "query");
    }

    private UserSearchResultDto toUserSearchResult(ApplicationUser user, ApplicationUser viewer) {
        UserSearchResultDto dto = new UserSearchResultDto();
        dto.setUserKey(user.getKey());
        dto.setUsername(this.resolveUsername(user));
        dto.setDisplayName(user.getDisplayName());
        dto.setAvatarUrl(this.resolveAvatarUrl(viewer, user));
        return dto;
    }

    private String resolveUsername(ApplicationUser user) {
        if (user == null) {
            return null;
        }
        String username = user.getUsername();
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        return username.trim();
    }

    private String resolveAvatarUrl(ApplicationUser viewer, ApplicationUser target) {
        if (viewer == null || target == null) {
            return null;
        }
        try {
            URI uri = this.avatarService.getAvatarURL(viewer, target, Avatar.Size.XXLARGE);
            return uri == null ? null : uri.toString();
        }
        catch (AvatarsDisabledException ex) {
            return null;
        }
    }
}

