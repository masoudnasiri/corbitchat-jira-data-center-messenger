/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.activeobjects.external.ActiveObjects
 *  com.atlassian.jira.user.ApplicationUser
 *  com.atlassian.jira.user.util.UserManager
 *  net.java.ao.DBParam
 *  net.java.ao.Query
 *  net.java.ao.RawEntity
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.corbitlogic.jira.internalmessenger.ao.JimAttachment;
import com.corbitlogic.jira.internalmessenger.ao.JimConversation;
import com.corbitlogic.jira.internalmessenger.ao.JimGroupMember;
import com.corbitlogic.jira.internalmessenger.ao.JimMessage;
import com.corbitlogic.jira.internalmessenger.ao.JimReaction;
import com.corbitlogic.jira.internalmessenger.ao.JimReadState;
import com.corbitlogic.jira.internalmessenger.attachment.JimAttachmentStorageService;
import com.corbitlogic.jira.internalmessenger.model.JimBodyFormat;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;
import com.corbitlogic.jira.internalmessenger.model.JimEventType;
import com.corbitlogic.jira.internalmessenger.model.JimSenderType;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimGroupService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.java.ao.DBParam;
import net.java.ao.Query;
import net.java.ao.RawEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JimGroupServiceImpl
implements JimGroupService {
    private static final Logger log = LoggerFactory.getLogger(JimGroupServiceImpl.class);
    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MEMBER = "MEMBER";
    public static final int MAX_GROUP_NAME_LENGTH = 120;
    public static final int MAX_GROUP_MEMBERS = 100;
    private final ActiveObjects activeObjects;
    private final JimPermissionService permissionService;
    private final JimConversationService conversationService;
    private final JimAttachmentStorageService attachmentStorageService;
    private final UserManager userManager;

    public JimGroupServiceImpl(ActiveObjects activeObjects, JimPermissionService permissionService, JimConversationService conversationService, JimAttachmentStorageService attachmentStorageService, UserManager userManager) {
        this.activeObjects = activeObjects;
        this.permissionService = permissionService;
        this.conversationService = conversationService;
        this.attachmentStorageService = attachmentStorageService;
        this.userManager = userManager;
    }

    @Override
    public JimConversation createGroup(String creatorUserKey, String name, List<String> memberUserKeys) {
        JimValidation.requireNonBlank(creatorUserKey, "creatorUserKey");
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, creatorUserKey);
        this.permissionService.requireActiveUser(creatorUserKey);
        String normalizedName = this.normalizeGroupName(name);
        Set<String> normalizedMembers = this.normalizeMemberKeys(creatorUserKey, memberUserKeys);
        long now = System.currentTimeMillis();
        return (JimConversation)this.activeObjects.executeInTransaction(() -> {
            JimConversation conversation = (JimConversation)this.activeObjects.create(JimConversation.class, new DBParam[0]);
            conversation.setConversationType(JimConversationType.GROUP.name());
            conversation.setGroupName(normalizedName);
            conversation.setCreatedByUserKey(creatorUserKey);
            conversation.setUserAKey(creatorUserKey);
            conversation.setCreatedAt(now);
            conversation.setUpdatedAt(now);
            conversation.setLastMessageAt(now);
            conversation.save();
            this.createMemberRow(conversation.getID(), creatorUserKey, ROLE_OWNER, now);
            for (String memberKey : normalizedMembers) {
                this.createMemberRow(conversation.getID(), memberKey, ROLE_MEMBER, now);
            }
            this.addGroupEventMessage(conversation.getID(), this.displayName(creatorUserKey) + " created the group");
            log.info("event=group stage=create outcome=success conversationId={} owner={} members={}", new Object[]{conversation.getID(), creatorUserKey, normalizedMembers.size() + 1});
            return conversation;
        });
    }

    @Override
    public void deleteGroup(int conversationId, String userKey) {
        JimConversation conversation = this.requireGroupConversation(conversationId);
        if (this.isProjectConversation(conversation)) {
            throw JimMessengerException.badRequest("Project chats cannot be deleted");
        }
        this.requireAuthenticatedSameUser(userKey);
        this.requireOwner(conversation, userKey);
        this.activeObjects.executeInTransaction(() -> {
            JimGroupMember[] members;
            JimReadState[] readStates;
            JimMessage[] messages;
            for (JimMessage message : messages = (JimMessage[])this.activeObjects.find(JimMessage.class, Query.select().where("CONVERSATION_ID = ?", new Object[]{conversationId}))) {
                JimAttachment[] attachments;
                JimReaction[] reactions = (JimReaction[])this.activeObjects.find(JimReaction.class, Query.select().where("MESSAGE_ID = ?", new Object[]{message.getID()}));
                if (reactions.length > 0) {
                    this.activeObjects.delete((RawEntity[])reactions);
                }
                for (JimAttachment attachment : attachments = (JimAttachment[])this.activeObjects.find(JimAttachment.class, Query.select().where("MESSAGE_ID = ?", new Object[]{message.getID()}))) {
                    this.deleteAttachmentFileQuietly(attachment);
                }
                if (attachments.length <= 0) continue;
                this.activeObjects.delete((RawEntity[])attachments);
            }
            if (messages.length > 0) {
                this.activeObjects.delete((RawEntity[])messages);
            }
            if ((readStates = (JimReadState[])this.activeObjects.find(JimReadState.class, Query.select().where("CONVERSATION_ID = ?", new Object[]{conversationId}))).length > 0) {
                this.activeObjects.delete((RawEntity[])readStates);
            }
            if ((members = (JimGroupMember[])this.activeObjects.find(JimGroupMember.class, Query.select().where("CONVERSATION_ID = ?", new Object[]{conversationId}))).length > 0) {
                this.activeObjects.delete((RawEntity[])members);
            }
            this.activeObjects.delete(new RawEntity[]{conversation});
            log.info("event=group stage=delete outcome=success conversationId={} owner={}", (Object)conversationId, (Object)userKey);
            return null;
        });
    }

    @Override
    public void addMember(int conversationId, String actorUserKey, String targetUserKey) {
        JimConversation conversation = this.requireGroupConversation(conversationId);
        this.requireAuthenticatedSameUser(actorUserKey);
        this.requireOwner(conversation, actorUserKey);
        String normalizedTarget = JimValidation.requireNonBlank(targetUserKey, "userKey").trim();
        this.permissionService.requireActiveUser(normalizedTarget);
        if (this.findMember(conversationId, normalizedTarget) != null) {
            throw JimMessengerException.badRequest("User is already a member of this group");
        }
        if (this.countMembers(conversationId) >= 100) {
            throw JimMessengerException.badRequest("Group member limit reached");
        }
        long now = System.currentTimeMillis();
        this.activeObjects.executeInTransaction(() -> {
            this.createMemberRow(conversationId, normalizedTarget, ROLE_MEMBER, now);
            this.addGroupEventMessage(conversationId, this.displayName(actorUserKey) + " added " + this.displayName(normalizedTarget));
            return null;
        });
    }

    @Override
    public void removeMember(int conversationId, String actorUserKey, String targetUserKey) {
        JimGroupMember member;
        JimConversation conversation = this.requireGroupConversation(conversationId);
        this.requireAuthenticatedSameUser(actorUserKey);
        String normalizedTarget = JimValidation.requireNonBlank(targetUserKey, "userKey").trim();
        boolean selfRemoval = actorUserKey.equals(normalizedTarget);
        if (selfRemoval) {
            if (this.isProjectConversation(conversation)) {
                throw JimMessengerException.badRequest("You cannot leave a project chat. Only the project lead can manage members.");
            }
            if (this.isOwner(conversation, actorUserKey)) {
                throw JimMessengerException.badRequest("The group owner cannot leave the group. Delete the group instead.");
            }
        } else {
            this.requireOwner(conversation, actorUserKey);
            if (this.isOwner(conversation, normalizedTarget)) {
                throw JimMessengerException.badRequest("The group owner cannot be removed");
            }
        }
        if ((member = this.findMember(conversationId, normalizedTarget)) == null) {
            throw JimMessengerException.notFound("User is not a member of this group");
        }
        this.activeObjects.executeInTransaction(() -> {
            this.activeObjects.delete(new RawEntity[]{member});
            String body = selfRemoval ? this.displayName(normalizedTarget) + " left the group" : this.displayName(actorUserKey) + " removed " + this.displayName(normalizedTarget);
            this.addGroupEventMessage(conversationId, body);
            return null;
        });
    }

    @Override
    public List<JimGroupMember> listMembers(int conversationId, String userKey) {
        JimConversation conversation = this.requireGroupConversation(conversationId);
        this.permissionService.requireParticipant(conversation, userKey);
        JimGroupMember[] members = (JimGroupMember[])this.activeObjects.find(JimGroupMember.class, Query.select().where("CONVERSATION_ID = ?", new Object[]{conversationId}).order("ID ASC"));
        return new ArrayList<JimGroupMember>(Arrays.asList(members));
    }

    @Override
    public int countMembers(int conversationId) {
        return this.activeObjects.count(JimGroupMember.class, Query.select().where("CONVERSATION_ID = ?", new Object[]{conversationId}));
    }

    @Override
    public boolean isOwner(JimConversation conversation, String userKey) {
        if (conversation == null || userKey == null) {
            return false;
        }
        String owner = conversation.getCreatedByUserKey();
        return owner != null && userKey.equals(owner.trim());
    }

    private void requireOwner(JimConversation conversation, String userKey) {
        if (!this.isOwner(conversation, userKey)) {
            throw JimMessengerException.forbidden("Only the group owner can perform this action");
        }
    }

    private void requireAuthenticatedSameUser(String userKey) {
        JimValidation.requireNonBlank(userKey, "userKey");
        String authenticatedUserKey = this.permissionService.requireAuthenticatedUserKey();
        this.permissionService.requireSameUser(authenticatedUserKey, userKey);
    }

    private JimConversation requireGroupConversation(int conversationId) {
        JimConversation conversation = this.conversationService.getConversation(conversationId);
        if (!JimConversationType.GROUP.name().equals(conversation.getConversationType()) && !this.isProjectConversation(conversation)) {
            throw JimMessengerException.badRequest("Conversation is not a group");
        }
        return conversation;
    }

    private boolean isProjectConversation(JimConversation conversation) {
        return conversation != null && JimConversationType.PROJECT.name().equals(conversation.getConversationType());
    }

    private JimGroupMember findMember(int conversationId, String userKey) {
        JimGroupMember[] members = (JimGroupMember[])this.activeObjects.find(JimGroupMember.class, Query.select().where("CONVERSATION_ID = ? AND USER_KEY = ?", new Object[]{conversationId, userKey}).limit(1));
        return members.length > 0 ? members[0] : null;
    }

    private void createMemberRow(int conversationId, String userKey, String role, long now) {
        JimGroupMember member = (JimGroupMember)this.activeObjects.create(JimGroupMember.class, new DBParam[0]);
        member.setConversationId(conversationId);
        member.setUserKey(userKey);
        member.setRole(role);
        member.setJoinedAt(now);
        member.save();
    }

    private void addGroupEventMessage(int conversationId, String body) {
        long now = System.currentTimeMillis();
        JimMessage message = (JimMessage)this.activeObjects.create(JimMessage.class, new DBParam[0]);
        message.setConversationId(conversationId);
        message.setSenderType(JimSenderType.SYSTEM.name());
        message.setBody(body);
        message.setBodyFormat(JimBodyFormat.TEXT.name());
        message.setEventType(JimEventType.GROUP_EVENT.name());
        message.setCreatedAt(now);
        message.setEdited(0);
        message.setDeleted(0);
        message.save();
        this.conversationService.touchConversation(conversationId, body);
    }

    private String displayName(String userKey) {
        ApplicationUser user = this.userManager.getUserByKey(userKey);
        return user != null ? user.getDisplayName() : userKey;
    }

    private String normalizeGroupName(String name) {
        String normalized = JimValidation.requireNonBlank(name, "name").trim();
        if (normalized.length() > 120) {
            throw JimMessengerException.badRequest("Group name is too long (max 120 characters)");
        }
        return normalized;
    }

    private Set<String> normalizeMemberKeys(String creatorUserKey, List<String> memberUserKeys) {
        LinkedHashSet<String> normalized = new LinkedHashSet<String>();
        if (memberUserKeys == null) {
            return normalized;
        }
        if (memberUserKeys.size() > 100) {
            throw JimMessengerException.badRequest("Too many group members (max 100)");
        }
        for (String key : memberUserKeys) {
            String trimmed;
            if (key == null || (trimmed = key.trim()).isEmpty() || trimmed.equals(creatorUserKey)) continue;
            this.permissionService.requireActiveUser(trimmed);
            normalized.add(trimmed);
        }
        return normalized;
    }

    private void deleteAttachmentFileQuietly(JimAttachment attachment) {
        try {
            File file = this.attachmentStorageService.resolveAttachmentFile(attachment);
            if (file != null && file.isFile() && !file.delete()) {
                log.warn("event=group stage=delete_attachment outcome=skipped attachmentId={}", (Object)attachment.getID());
            }
        }
        catch (RuntimeException ex) {
            log.warn("event=group stage=delete_attachment outcome=error attachmentId={}", (Object)attachment.getID(), (Object)ex);
        }
    }
}

