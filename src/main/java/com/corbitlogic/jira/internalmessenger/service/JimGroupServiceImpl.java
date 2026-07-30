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
import com.corbitlogic.jira.internalmessenger.attachment.JimAvatarStorageService;
import com.corbitlogic.jira.internalmessenger.model.JimBodyFormat;
import com.corbitlogic.jira.internalmessenger.model.JimConversationType;
import com.corbitlogic.jira.internalmessenger.model.JimEventType;
import com.corbitlogic.jira.internalmessenger.model.JimSenderType;
import com.corbitlogic.jira.internalmessenger.service.JimConversationService;
import com.corbitlogic.jira.internalmessenger.service.JimGroupService;
import com.corbitlogic.jira.internalmessenger.service.JimMessengerException;
import com.corbitlogic.jira.internalmessenger.service.JimPermissionService;
import com.corbitlogic.jira.internalmessenger.util.JimSanitizer;
import com.corbitlogic.jira.internalmessenger.util.JimValidation;
import com.corbitlogic.jira.internalmessenger.attachment.JimAvatarStorageService.StoredAvatar;
import java.io.File;
import java.io.IOException;
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
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MEMBER = "MEMBER";
    public static final int MAX_GROUP_NAME_LENGTH = 120;
    public static final int MAX_GROUP_MEMBERS = 100;
    private final ActiveObjects activeObjects;
    private final JimPermissionService permissionService;
    private final JimConversationService conversationService;
    private final JimAttachmentStorageService attachmentStorageService;
    private final JimAvatarStorageService avatarStorageService;
    private final UserManager userManager;
    private final JimChatAuditService auditService;
    private final JimReviewerService reviewerService;

    public JimGroupServiceImpl(ActiveObjects activeObjects, JimPermissionService permissionService, JimConversationService conversationService, JimAttachmentStorageService attachmentStorageService, JimAvatarStorageService avatarStorageService, UserManager userManager, JimChatAuditService auditService, JimReviewerService reviewerService) {
        this.activeObjects = activeObjects;
        this.permissionService = permissionService;
        this.conversationService = conversationService;
        this.attachmentStorageService = attachmentStorageService;
        this.avatarStorageService = avatarStorageService;
        this.userManager = userManager;
        this.auditService = auditService;
        this.reviewerService = reviewerService;
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
        // Sprint 12 governance: SOFT-ARCHIVE instead of hard delete. Nothing is
        // removed — messages, members, attachments (files + rows), reactions,
        // read-state and audit are all retained for complaint review. The group
        // is flagged archived so it disappears from normal users' lists and is
        // unavailable to them, while the hidden reviewer can still inspect it
        // and its (soft-deleted / hidden) messages.
        this.activeObjects.executeInTransaction(() -> {
            long now = System.currentTimeMillis();
            conversation.setArchived(1);
            conversation.setArchivedAt(now);
            conversation.setArchivedByUserKey(userKey);
            conversation.save();
            // Immutable in-chat audit trail entry.
            this.addGroupEventMessage(conversationId,
                    this.displayName(userKey) + " deleted the group");
            return null;
        });
        this.auditService.record(userKey, "group.archive", null, conversationId,
                null, "group soft-archived (history retained)");
        log.info("event=group stage=archive outcome=success conversationId={} owner={}", (Object)conversationId, (Object)userKey);
    }

    @Override
    public void addMember(int conversationId, String actorUserKey, String targetUserKey) {
        JimConversation conversation = this.requireGroupConversation(conversationId);
        this.requireAuthenticatedSameUser(actorUserKey);
        this.requireNotArchived(conversation);
        this.requireCanManageMembers(conversation, actorUserKey);
        String normalizedTarget = JimValidation.requireNonBlank(targetUserKey, "userKey").trim();
        this.requireNotReviewer(normalizedTarget);
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
            this.requireNotArchived(conversation);
            this.requireCanManageMembers(conversation, actorUserKey);
            if (this.isOwner(conversation, normalizedTarget)) {
                throw JimMessengerException.badRequest("The group owner cannot be removed");
            }
            // An ADMIN (non-owner) may only remove normal MEMBERS, never other
            // admins — only the OWNER can manage admins.
            if (!this.isOwner(conversation, actorUserKey)
                    && ROLE_ADMIN.equals(this.getRole(conversation, normalizedTarget))) {
                throw JimMessengerException.forbidden("Only the group owner can remove an administrator");
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

    @Override
    public String getRole(JimConversation conversation, String userKey) {
        if (conversation == null || userKey == null) {
            return null;
        }
        // The permanent owner (createdByUserKey / project lead) is always OWNER,
        // even if their member row is missing or its role column is null (legacy).
        if (this.isOwner(conversation, userKey)) {
            return ROLE_OWNER;
        }
        JimGroupMember member = this.findMember(conversation.getID(), userKey.trim());
        if (member == null) {
            return null;
        }
        String role = member.getRole();
        if (role == null || role.trim().isEmpty()) {
            return ROLE_MEMBER;
        }
        return role.trim();
    }

    @Override
    public boolean isAdmin(JimConversation conversation, String userKey) {
        return ROLE_ADMIN.equals(this.getRole(conversation, userKey));
    }

    @Override
    public boolean canManageMembers(JimConversation conversation, String userKey) {
        String role = this.getRole(conversation, userKey);
        return ROLE_OWNER.equals(role) || ROLE_ADMIN.equals(role);
    }

    @Override
    public boolean canManageAdmins(JimConversation conversation, String userKey) {
        return this.isOwner(conversation, userKey);
    }

    private void requireCanManageMembers(JimConversation conversation, String userKey) {
        if (!this.canManageMembers(conversation, userKey)) {
            throw JimMessengerException.forbidden("Only the group owner or an administrator can perform this action");
        }
    }

    private void requireNotArchived(JimConversation conversation) {
        if (conversation != null && conversation.getArchived() != null && conversation.getArchived() != 0) {
            throw JimMessengerException.badRequest("This group has been archived and can no longer be modified");
        }
    }

    private void requireNotReviewer(String userKey) {
        // Only the dedicated hidden system account (pluginuser) is blocked from
        // being added to conversations. The configured reviewer user (m.nasiri)
        // is an ordinary user and may be added/promoted like anyone else.
        if (this.reviewerService != null && this.reviewerService.isHiddenSystemAccountUserKey(userKey)) {
            throw JimMessengerException.forbidden("This user cannot be added to a conversation");
        }
    }

    @Override
    public void promoteToAdmin(int conversationId, String actorUserKey, String targetUserKey) {
        JimConversation conversation = this.requireGroupConversation(conversationId);
        this.requireAuthenticatedSameUser(actorUserKey);
        this.requireNotArchived(conversation);
        // Only the OWNER (createdByUserKey / project lead) may manage admins.
        this.requireOwner(conversation, actorUserKey);
        String normalizedTarget = JimValidation.requireNonBlank(targetUserKey, "userKey").trim();
        this.requireNotReviewer(normalizedTarget);
        if (this.isOwner(conversation, normalizedTarget)) {
            throw JimMessengerException.badRequest("The group owner is already an administrator");
        }
        JimGroupMember member = this.findMember(conversationId, normalizedTarget);
        if (member == null) {
            throw JimMessengerException.notFound("User is not a member of this group");
        }
        if (ROLE_ADMIN.equals(this.getRole(conversation, normalizedTarget))) {
            throw JimMessengerException.badRequest("User is already an administrator");
        }
        this.activeObjects.executeInTransaction(() -> {
            member.setRole(ROLE_ADMIN);
            member.save();
            this.addGroupEventMessage(conversationId,
                    this.displayName(actorUserKey) + " made " + this.displayName(normalizedTarget) + " an admin");
            return null;
        });
        this.auditService.record(actorUserKey,
                this.isProjectConversation(conversation) ? "projectChat.admin.add" : "group.admin.add",
                null, conversationId, normalizedTarget, "role=ADMIN");
        log.info("event=group stage=promote outcome=success conversationId={} actor={} target={}",
                new Object[]{conversationId, actorUserKey, normalizedTarget});
    }

    @Override
    public void demoteToMember(int conversationId, String actorUserKey, String targetUserKey) {
        JimConversation conversation = this.requireGroupConversation(conversationId);
        this.requireAuthenticatedSameUser(actorUserKey);
        this.requireNotArchived(conversation);
        this.requireOwner(conversation, actorUserKey);
        String normalizedTarget = JimValidation.requireNonBlank(targetUserKey, "userKey").trim();
        if (this.isOwner(conversation, normalizedTarget)) {
            // The permanent owner can never be demoted — guarantees >= 1 admin.
            throw JimMessengerException.badRequest("The group owner cannot be demoted");
        }
        JimGroupMember member = this.findMember(conversationId, normalizedTarget);
        if (member == null) {
            throw JimMessengerException.notFound("User is not a member of this group");
        }
        if (!ROLE_ADMIN.equals(this.getRole(conversation, normalizedTarget))) {
            throw JimMessengerException.badRequest("User is not an administrator");
        }
        this.activeObjects.executeInTransaction(() -> {
            member.setRole(ROLE_MEMBER);
            member.save();
            this.addGroupEventMessage(conversationId,
                    this.displayName(actorUserKey) + " removed " + this.displayName(normalizedTarget) + " as admin");
            return null;
        });
        this.auditService.record(actorUserKey,
                this.isProjectConversation(conversation) ? "projectChat.admin.remove" : "group.admin.remove",
                null, conversationId, normalizedTarget, "role=MEMBER");
        log.info("event=group stage=demote outcome=success conversationId={} actor={} target={}",
                new Object[]{conversationId, actorUserKey, normalizedTarget});
    }

    @Override
    public JimConversation renameGroup(int conversationId, String actorUserKey, String newName) {
        JimConversation conversation = this.requireGroupConversation(conversationId);
        this.requireAuthenticatedSameUser(actorUserKey);
        this.requireNotArchived(conversation);
        this.requireCanManageMembers(conversation, actorUserKey);
        String normalizedName = this.normalizeGroupName(newName);
        String sanitized = JimSanitizer.sanitizeText(normalizedName);
        this.activeObjects.executeInTransaction(() -> {
            conversation.setGroupName(normalizedName);
            conversation.setUpdatedAt(System.currentTimeMillis());
            conversation.save();
            this.addGroupEventMessage(conversationId,
                    this.displayName(actorUserKey) + " renamed the group to " + sanitized);
            return null;
        });
        this.auditService.record(actorUserKey, "group.rename", null, conversationId, null,
                "name length=" + normalizedName.length());
        log.info("event=group stage=rename outcome=success conversationId={} actor={}",
                (Object) conversationId, (Object) actorUserKey);
        return conversation;
    }

    @Override
    public JimConversation uploadAvatar(int conversationId, String actorUserKey, File sourceFile,
                                        String contentType, String originalFilename) {
        JimConversation conversation = this.requireGroupConversation(conversationId);
        this.requireAuthenticatedSameUser(actorUserKey);
        this.requireNotArchived(conversation);
        this.requireCanManageMembers(conversation, actorUserKey);
        StoredAvatar stored;
        try {
            stored = this.avatarStorageService.storeAvatar(conversationId, sourceFile, contentType);
        } catch (IOException ex) {
            throw JimMessengerException.internalError("Unable to store the avatar image");
        }
        long now = System.currentTimeMillis();
        this.activeObjects.executeInTransaction(() -> {
            conversation.setAvatarStoragePath(stored.getStoragePath());
            conversation.setAvatarContentType(stored.getContentType());
            conversation.setAvatarSize(stored.getSize());
            conversation.setAvatarChecksum(stored.getChecksum());
            conversation.setAvatarFilename(truncate(JimSanitizer.sanitizeText(originalFilename), 255));
            conversation.setAvatarUpdatedAt(now);
            conversation.setAvatarUpdatedByUserKey(actorUserKey);
            conversation.setAvatarVersion(nextVersion(conversation));
            conversation.setUpdatedAt(now);
            conversation.save();
            this.addGroupEventMessage(conversationId, this.displayName(actorUserKey) + " changed the group photo");
            return null;
        });
        // Never place binary data in the audit trail — metadata only.
        this.auditService.record(actorUserKey, "conversation.avatar.upload", null, conversationId, null,
                "contentType=" + stored.getContentType() + " size=" + stored.getSize());
        log.info("event=avatar stage=upload outcome=success conversationId={} actor={} size={}",
                new Object[]{conversationId, actorUserKey, stored.getSize()});
        return conversation;
    }

    @Override
    public JimConversation resetAvatar(int conversationId, String actorUserKey) {
        JimConversation conversation = this.requireGroupConversation(conversationId);
        this.requireAuthenticatedSameUser(actorUserKey);
        this.requireNotArchived(conversation);
        this.requireCanManageMembers(conversation, actorUserKey);
        long now = System.currentTimeMillis();
        this.activeObjects.executeInTransaction(() -> {
            conversation.setAvatarStoragePath(null);
            conversation.setAvatarContentType(null);
            conversation.setAvatarSize(null);
            conversation.setAvatarChecksum(null);
            conversation.setAvatarFilename(null);
            conversation.setAvatarUpdatedAt(now);
            conversation.setAvatarUpdatedByUserKey(actorUserKey);
            conversation.setAvatarVersion(nextVersion(conversation));
            conversation.setUpdatedAt(now);
            conversation.save();
            this.addGroupEventMessage(conversationId, this.displayName(actorUserKey) + " reset the group photo");
            return null;
        });
        this.avatarStorageService.deleteAvatar(conversationId);
        this.auditService.record(actorUserKey, "conversation.avatar.reset", null, conversationId, null, "reset");
        log.info("event=avatar stage=reset outcome=success conversationId={} actor={}",
                (Object) conversationId, (Object) actorUserKey);
        return conversation;
    }

    private static long nextVersion(JimConversation conversation) {
        Long v = conversation.getAvatarVersion();
        return (v == null ? 0L : v) + 1L;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
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

