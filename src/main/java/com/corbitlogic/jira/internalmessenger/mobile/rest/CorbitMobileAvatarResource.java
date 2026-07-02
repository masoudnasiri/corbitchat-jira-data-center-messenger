package com.corbitlogic.jira.internalmessenger.mobile.rest;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarManager;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.avatar.AvatarsDisabledException;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.util.Consumer;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Response;

/**
 * Mobile BFF avatar proxy (Sprint 04C). Mounted at
 * {@code /rest/corbit-mobile/1.0/avatar/*}.
 *
 * <p>Streams a user's or project's Jira avatar bytes over the same authenticated
 * mobile connection, so the client never has to reach Jira's configured
 * (possibly internal/broken) base URL or an external gravatar host. Permissions
 * are enforced by {@link AvatarService#getAvatar} (users) and a BROWSE check
 * (projects); the viewer is the authenticated mobile user. No token appears in
 * the URL and nothing sensitive is logged.</p>
 */
@Path("/avatar")
@AnonymousAllowed
public class CorbitMobileAvatarResource {

    /** Hard cap so a bad/huge file can never blow up mobile memory. */
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    @GET
    @Path("/user/{userKey}")
    public Response userAvatar(@PathParam("userKey") String userKey,
                               @QueryParam("size") String size) {
        ApplicationUser viewer = authContext().getLoggedInUser();
        if (viewer == null) {
            return status(401);
        }
        ApplicationUser target = resolveUser(userKey);
        if (target == null) {
            return status(404);
        }
        try {
            Avatar avatar = avatarService().getAvatar(viewer, target);
            return stream(avatar, size);
        } catch (AvatarsDisabledException ex) {
            return status(404);
        } catch (Exception ex) {
            return status(404);
        }
    }

    @GET
    @Path("/project/{projectKey}")
    public Response projectAvatar(@PathParam("projectKey") String projectKey,
                                  @QueryParam("size") String size) {
        ApplicationUser viewer = authContext().getLoggedInUser();
        if (viewer == null) {
            return status(401);
        }
        Project project = projectManager().getProjectObjByKeyIgnoreCase(projectKey);
        if (project == null) {
            return status(404);
        }
        // Same visibility rule the rest of the mobile surface uses.
        if (!permissionManager().hasPermission(ProjectPermissions.BROWSE_PROJECTS, project, viewer)) {
            return status(404);
        }
        try {
            Avatar avatar = project.getAvatar();
            return stream(avatar, size);
        } catch (Exception ex) {
            return status(404);
        }
    }

    // --- helpers --------------------------------------------------------------

    private Response stream(Avatar avatar, String sizeParam) {
        if (avatar == null) {
            return status(404);
        }
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            avatarManager().readAvatarData(avatar, parseSize(sizeParam), new Consumer<InputStream>() {
                @Override
                public void consume(InputStream in) {
                    try {
                        byte[] chunk = new byte[8192];
                        int read;
                        while ((read = in.read(chunk)) != -1) {
                            if (buffer.size() + read > MAX_BYTES) {
                                break;
                            }
                            buffer.write(chunk, 0, read);
                        }
                    } catch (IOException io) {
                        throw new RuntimeException(io);
                    }
                }
            });
        } catch (Exception ex) {
            return status(404);
        }
        byte[] bytes = buffer.toByteArray();
        if (bytes.length == 0) {
            return status(404);
        }
        String contentType = avatar.getContentType();
        if (contentType == null || contentType.trim().isEmpty()) {
            contentType = "image/png";
        }
        CacheControl cc = new CacheControl();
        cc.setPrivate(true);
        cc.setMaxAge(86400);
        return Response.ok(bytes).type(contentType).cacheControl(cc).build();
    }

    private static Avatar.Size parseSize(String size) {
        if (size == null) {
            return Avatar.Size.LARGE;
        }
        switch (size.trim().toLowerCase()) {
            case "small":
                return Avatar.Size.SMALL;
            case "medium":
                return Avatar.Size.MEDIUM;
            case "large":
                return Avatar.Size.LARGE;
            case "xlarge":
                return Avatar.Size.XLARGE;
            case "xxlarge":
                return Avatar.Size.XXLARGE;
            default:
                return Avatar.Size.LARGE;
        }
    }

    private ApplicationUser resolveUser(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        String k = key.trim();
        UserManager um = ComponentAccessor.getUserManager();
        ApplicationUser user = um.getUserByKey(k);
        if (user == null) {
            user = um.getUserByName(k);
        }
        return user;
    }

    private static Response status(int code) {
        return Response.status(code).build();
    }

    private static JiraAuthenticationContext authContext() {
        return ComponentAccessor.getJiraAuthenticationContext();
    }

    private static AvatarService avatarService() {
        return ComponentAccessor.getComponent(AvatarService.class);
    }

    private static AvatarManager avatarManager() {
        return ComponentAccessor.getComponent(AvatarManager.class);
    }

    private static ProjectManager projectManager() {
        return ComponentAccessor.getProjectManager();
    }

    private static PermissionManager permissionManager() {
        return ComponentAccessor.getPermissionManager();
    }
}
