package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Plugin-owned hidden chat-reviewer account (Sprint 12). This is NOT a Jira
 * ApplicationUser: it exists only in plugin storage and authenticates only
 * through the app/PWA auth path, so it never appears in Jira user directories,
 * chat search, mentions, members or presence. Only a salted password HASH is
 * stored — never the raw password.
 */
@Preload
@Table("JimReviewer")
public interface JimReviewer extends Entity {

    @Indexed
    @StringLength(255)
    String getUsername();
    void setUsername(String username);

    /** PBKDF2/hash of the password. Never the raw password. */
    @StringLength(255)
    String getPasswordHash();
    void setPasswordHash(String passwordHash);

    @StringLength(255)
    String getSalt();
    void setSalt(String salt);

    Long getCreatedAt();
    void setCreatedAt(Long createdAt);

    Long getUpdatedAt();
    void setUpdatedAt(Long updatedAt);
}
