/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  com.atlassian.jira.permission.GlobalPermissionKey
 *  com.atlassian.jira.security.GlobalPermissionManager
 *  com.atlassian.jira.security.JiraAuthenticationContext
 *  com.atlassian.jira.user.ApplicationUser
 *  javax.inject.Inject
 *  javax.ws.rs.GET
 *  javax.ws.rs.Path
 *  javax.ws.rs.Produces
 *  javax.ws.rs.core.Response
 *  net.java.ao.Query
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.rest;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.corbitlogic.jira.internalmessenger.ao.JimAttachment;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentPolicy;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentStorageService;
import com.corbitlogic.jira.internalmessenger.bootstrap.JimPluginBootstrap;
import com.corbitlogic.jira.internalmessenger.rest.JimRestResponses;
import com.corbitlogic.jira.internalmessenger.service.JimAoSchemaDiagnostics;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import net.java.ao.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(value="/dev/debug")
@Produces(value={"application/json"})
public class JimDevDebugResource {
    private static final Logger log = LoggerFactory.getLogger(JimDevDebugResource.class);
    private static final List<String> EXPECTED_CONVERSATION_COLUMNS = Arrays.asList("ID", "CONVERSATION_TYPE", "USER_A_KEY", "USER_B_KEY", "SYSTEM_KEY", "CREATED_AT", "UPDATED_AT", "LAST_MESSAGE_AT", "LAST_MESSAGE_PREVIEW");
    private final JiraAuthenticationContext authenticationContext;
    private final GlobalPermissionManager globalPermissionManager;
    private final ActiveObjects activeObjects;
    private final JimAoSchemaDiagnostics aoSchemaDiagnostics;
    private final JimPluginBootstrap pluginBootstrap;
    private final JimAttachmentStorageService attachmentStorageService;

    @Inject
    public JimDevDebugResource(JiraAuthenticationContext authenticationContext, GlobalPermissionManager globalPermissionManager, ActiveObjects activeObjects, JimAoSchemaDiagnostics aoSchemaDiagnostics, JimPluginBootstrap pluginBootstrap, JimAttachmentStorageService attachmentStorageService) {
        this.authenticationContext = authenticationContext;
        this.globalPermissionManager = globalPermissionManager;
        this.activeObjects = activeObjects;
        this.aoSchemaDiagnostics = aoSchemaDiagnostics;
        this.pluginBootstrap = pluginBootstrap;
        this.attachmentStorageService = attachmentStorageService;
    }

    @GET
    public Response debug() {
        ApplicationUser user = this.authenticationContext.getLoggedInUser();
        if (user == null) {
            return JimRestResponses.errorJson(401, "unauthorized", "User is not authenticated");
        }
        if (!this.globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, user)) {
            return JimRestResponses.errorJson(403, "forbidden", "Jira administrator permission is required");
        }
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("ok", true);
            body.put("plugin", "Jira Internal Messenger");
            body.put("pluginKey", "com.corbitlogic.jira.internalmessenger.jira-internal-messenger");
            body.put("currentUserKey", user.getKey());
            body.put("assignmentListenerRegistered", this.pluginBootstrap.isAssignmentListenerRegistered());
            body.put("mentionListenerRegistered", this.pluginBootstrap.isMentionListenerRegistered());
            body.put("statusChangeListenerRegistered", this.pluginBootstrap.isStatusChangeListenerRegistered());
            LinkedHashMap<String, Object> attachments = new LinkedHashMap<String, Object>();
            attachments.put("attachmentAoEntityIncluded", true);
            attachments.put("maxUploadSizeBytes", 0xA00000L);
            attachments.put("allowedMimeTypesCount", JimAttachmentPolicy.allowedMimeTypeCount());
            try {
                attachments.put("attachmentStorageRootExists", this.attachmentStorageService.storageRootExists());
                attachments.put("attachmentStorageRoot", this.attachmentStorageService.getStorageRoot().getAbsolutePath());
            }
            catch (Exception ex) {
                attachments.put("attachmentStorageRootExists", false);
                attachments.put("attachmentStorageRootError", ex.getMessage());
            }
            body.put("attachments", attachments);
            LinkedHashMap<String, Object> ao = new LinkedHashMap<String, Object>();
            try {
                this.activeObjects.find(JimConversation.class, Query.select().limit(1));
                this.activeObjects.find(JimAttachment.class, Query.select().limit(1));
                ao.put("initialized", true);
            }
            catch (Exception ex) {
                ao.put("initialized", false);
                ao.put("error", ex.getMessage());
            }
            body.put("ao", ao);
            body.put("expectedConversationColumns", EXPECTED_CONVERSATION_COLUMNS);
            Map<String, Object> schemaReport = this.aoSchemaDiagnostics.buildSchemaReport();
            if (schemaReport.containsKey("databaseTables")) {
                body.put("databaseTables", schemaReport.get("databaseTables"));
            }
            if (schemaReport.containsKey("databaseInspectionWarning")) {
                body.put("databaseInspectionWarning", schemaReport.get("databaseInspectionWarning"));
            }
            return JimRestResponses.okJson(body);
        }
        catch (Exception ex) {
            return JimRestResponses.internalError(log, "GET /rest/jim/1.0/dev/debug", user.getKey(), ex, "internal_error", "An internal error occurred while building the debug report.");
        }
    }
}

