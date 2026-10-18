/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.java.ao.Accessor
 *  net.java.ao.Entity
 *  net.java.ao.Mutator
 *  net.java.ao.Preload
 *  net.java.ao.schema.Indexed
 *  net.java.ao.schema.StringLength
 */
package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Accessor;
import net.java.ao.Entity;
import net.java.ao.Mutator;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;

@Preload
public interface JimAttachment
extends Entity {
    @Indexed
    @Accessor(value="MESSAGE_ID")
    public int getMessageId();

    @Mutator(value="MESSAGE_ID")
    public void setMessageId(int var1);

    @Indexed
    @Accessor(value="CONVERSATION_ID")
    public int getConversationId();

    @Mutator(value="CONVERSATION_ID")
    public void setConversationId(int var1);

    @Indexed
    @StringLength(value=255)
    @Accessor(value="UPLOADER_USER_KEY")
    public String getUploaderUserKey();

    @Mutator(value="UPLOADER_USER_KEY")
    public void setUploaderUserKey(String var1);

    @StringLength(value=450)
    @Accessor(value="ORIGINAL_FILENAME")
    public String getOriginalFilename();

    @Mutator(value="ORIGINAL_FILENAME")
    public void setOriginalFilename(String var1);

    @StringLength(value=450)
    @Accessor(value="STORED_FILENAME")
    public String getStoredFilename();

    @Mutator(value="STORED_FILENAME")
    public void setStoredFilename(String var1);

    @StringLength(value=450)
    @Accessor(value="STORAGE_PATH")
    public String getStoragePath();

    @Mutator(value="STORAGE_PATH")
    public void setStoragePath(String var1);

    @StringLength(value=255)
    @Accessor(value="CONTENT_TYPE")
    public String getContentType();

    @Mutator(value="CONTENT_TYPE")
    public void setContentType(String var1);

    @Accessor(value="FILE_SIZE")
    public Long getFileSize();

    @Mutator(value="FILE_SIZE")
    public void setFileSize(Long var1);

    @StringLength(value=50)
    @Accessor(value="FILE_KIND")
    public String getFileKind();

    @Mutator(value="FILE_KIND")
    public void setFileKind(String var1);

    @Indexed
    @Accessor(value="CREATED_AT")
    public Long getCreatedAt();

    @Mutator(value="CREATED_AT")
    public void setCreatedAt(Long var1);

    @Accessor(value="DELETED")
    public Integer getDeleted();

    @Mutator(value="DELETED")
    public void setDeleted(Integer var1);

    @StringLength(value=450)
    @Accessor(value="THUMBNAIL_PATH")
    public String getThumbnailPath();

    @Mutator(value="THUMBNAIL_PATH")
    public void setThumbnailPath(String var1);

    // Sprint 07 Fix-2: distinguishes a recorded voice note (composer mic /
    // browser MediaRecorder) from a picked/shared audio file. Nullable so the
    // AO upgrade is non-destructive; NULL on legacy rows means "voice" because
    // historically every AUDIO upload came from a voice recorder.
    @Accessor(value="VOICE")
    public Boolean getVoice();

    @Mutator(value="VOICE")
    public void setVoice(Boolean var1);

    @Accessor(value="WIDTH")
    public Integer getWidth();

    @Mutator(value="WIDTH")
    public void setWidth(Integer var1);

    @Accessor(value="HEIGHT")
    public Integer getHeight();

    @Mutator(value="HEIGHT")
    public void setHeight(Integer var1);
}

