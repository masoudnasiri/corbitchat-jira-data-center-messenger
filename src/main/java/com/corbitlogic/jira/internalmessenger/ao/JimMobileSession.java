package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Accessor;
import net.java.ao.Entity;
import net.java.ao.Mutator;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * A plugin-issued mobile session for CorbitChat Mobile (Sprint 01B).
 *
 * <p>Created when a user authenticates with username/password (validated against
 * Jira via {@code CrowdService}). Only the SHA-256 <em>hash</em> of the opaque
 * session token is persisted — the raw token is returned to the client once and
 * never stored server-side. Later requests present the raw token in the
 * {@code X-CorbitChat-Session} header; the filter hashes it, looks up this row,
 * and impersonates {@code USER_KEY} for the request.</p>
 *
 * <p>Short {@code @Table} name: AO prefixes tables with {@code AO_xxxxxx_} and
 * caps the total length at 30 chars.</p>
 */
@Preload
@Table("JimMobileSess")
public interface JimMobileSession extends Entity {

    @Indexed
    @StringLength(255)
    @Accessor("USER_KEY")
    String getUserKey();

    @Mutator("USER_KEY")
    void setUserKey(String userKey);

    /** SHA-256 hex of the raw token (64 chars). Never store the raw token. */
    @Indexed
    @StringLength(64)
    @Accessor("TOKEN_HASH")
    String getTokenHash();

    @Mutator("TOKEN_HASH")
    void setTokenHash(String tokenHash);

    @Accessor("CREATED_AT")
    Long getCreatedAt();

    @Mutator("CREATED_AT")
    void setCreatedAt(Long createdAt);

    @Accessor("EXPIRES_AT")
    Long getExpiresAt();

    @Mutator("EXPIRES_AT")
    void setExpiresAt(Long expiresAt);

    @Accessor("LAST_USED_AT")
    Long getLastUsedAt();

    @Mutator("LAST_USED_AT")
    void setLastUsedAt(Long lastUsedAt);

    @Accessor("REVOKED")
    Boolean getRevoked();

    @Mutator("REVOKED")
    void setRevoked(Boolean revoked);

    /** Optional device/client label for user-visible session management. */
    @StringLength(255)
    @Accessor("DEVICE")
    String getDevice();

    @Mutator("DEVICE")
    void setDevice(String device);
}
