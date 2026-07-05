package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileBoards;
import com.corbitlogic.jira.internalmessenger.mobile.JimMobileFeatures;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimMobileFeatureService;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mobile BFF Board Gallery (Sprint 04D). Mounted at
 * {@code /rest/corbit-mobile/1.0/boards}.
 *
 * <p>Returns the Jira boards the caller can see, as cards mirroring the existing
 * web Board Gallery (name, type, project chip, lead, "my tasks" count). Boards
 * come from {@code RapidViewService}, which already permission-filters per user,
 * so this works identically for PAT and mobile-session callers.</p>
 *
 * <p>All Jira Software / GreenHopper access is isolated in {@link JimMobileBoards}
 * and wrapped in {@code try/catch(Throwable)} here: if Jira Software is missing
 * or its API is incompatible, the endpoint returns
 * {@code {agileAvailable:false, boards:[]}} instead of failing — the app then
 * shows a friendly "boards unavailable" state and every other mobile endpoint
 * keeps working.</p>
 */
@Path("/boards")
@Produces({"application/json"})
@AnonymousAllowed
public class CorbitMobileBoardResource {

    private static final Logger log = LoggerFactory.getLogger(CorbitMobileBoardResource.class);

    private final JiraAuthenticationContext authenticationContext;
    private final SearchService searchService;
    private final AvatarService avatarService;
    private final ApplicationProperties applicationProperties;
    private final JimMobileFeatureService featureService;

    @Inject
    public CorbitMobileBoardResource(JiraAuthenticationContext authenticationContext,
                                     SearchService searchService,
                                     AvatarService avatarService,
                                     ApplicationProperties applicationProperties,
                                     JimMobileFeatureService featureService) {
        this.authenticationContext = authenticationContext;
        this.searchService = searchService;
        this.avatarService = avatarService;
        this.applicationProperties = applicationProperties;
        this.featureService = featureService;
    }

    @GET
    public Response list() {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                    "You must be signed in to use CorbitChat Mobile.");
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.BOARDS)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.BOARDS);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            if (!JimMobileBoards.isAvailable()) {
                body.put("agileAvailable", false);
                body.put("total", 0);
                body.put("boards", java.util.Collections.emptyList());
                return JimRestResponses.okJson(body);
            }
            List<Map<String, Object>> boards = JimMobileBoards.listBoards(
                    viewer, this.searchService, this.applicationProperties);
            body.put("agileAvailable", true);
            body.put("total", boards.size());
            body.put("boards", boards);
            return JimRestResponses.okJson(body);
        } catch (Throwable t) {
            // Contain any Jira Software linkage/API error to this endpoint.
            log.warn("Board gallery unavailable for {}: {}", viewer.getKey(), t.toString());
            body.put("agileAvailable", false);
            body.put("total", 0);
            body.put("boards", java.util.Collections.emptyList());
            return JimRestResponses.okJson(body);
        }
    }

    /**
     * Board header/meta — {@code GET /boards/{id}}. Returns board name, type,
     * project chip and (Scrum boards only) the usable sprint scopes. A missing
     * or non-visible board returns 404 so the endpoint never reveals board ids.
     */
    @GET
    @Path("/{id}")
    public Response board(@PathParam("id") long id) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.BOARDS)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.BOARDS);
        }
        try {
            if (!JimMobileBoards.isAvailable()) {
                return boardsUnavailable();
            }
            Map<String, Object> meta = JimMobileBoards.boardMeta(
                    id, viewer, this.searchService, this.applicationProperties);
            if (meta == null) {
                return notFound();
            }
            return JimRestResponses.okJson(meta);
        } catch (Throwable t) {
            log.warn("Board {} meta unavailable for {}: {}", id, viewer.getKey(), t.toString());
            return notFound();
        }
    }

    /**
     * Board issues grouped by status — {@code GET /boards/{id}/issues?sprint=}.
     * The {@code sprint} scope is one of {@code active|future|closed|all}
     * (default {@code all}); unknown/unusable scopes fall back to {@code all}.
     */
    @GET
    @Path("/{id}/issues")
    public Response boardIssues(@PathParam("id") long id,
                                @QueryParam("sprint") @DefaultValue("all") String sprint,
                                @QueryParam("mine") @DefaultValue("false") boolean mine) {
        ApplicationUser viewer = this.authenticationContext.getLoggedInUser();
        if (viewer == null) {
            return unauthenticated();
        }
        if (!this.featureService.isAllowed(viewer, JimMobileFeatures.BOARDS)) {
            return JimRestResponses.featureDisabled(JimMobileFeatures.BOARDS);
        }
        try {
            if (!JimMobileBoards.isAvailable()) {
                return boardsUnavailable();
            }
            Map<String, Object> body = JimMobileBoards.boardColumns(
                    id, sprint, mine, viewer, this.searchService, this.avatarService,
                    this.applicationProperties);
            if (body == null) {
                return notFound();
            }
            return JimRestResponses.okJson(body);
        } catch (Throwable t) {
            log.warn("Board {} issues unavailable for {}: {}", id, viewer.getKey(), t.toString());
            return notFound();
        }
    }

    private static Response unauthenticated() {
        return JimRestResponses.errorJson(401, "NOT_AUTHENTICATED",
                "You must be signed in to use CorbitChat Mobile.");
    }

    private static Response notFound() {
        return JimRestResponses.errorJson(404, "not_found",
                "Board not found or you do not have permission to view it.");
    }

    private static Response boardsUnavailable() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agileAvailable", false);
        return JimRestResponses.errorJson(404, "agile_unavailable",
                "Jira Software boards are not available on this server.");
    }
}
