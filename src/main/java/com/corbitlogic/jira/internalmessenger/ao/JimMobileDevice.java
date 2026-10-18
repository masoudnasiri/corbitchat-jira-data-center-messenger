package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Accessor;
import net.java.ao.Entity;
import net.java.ao.Mutator;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * A registered mobile device that can receive native push notifications
 * (Sprint 05). One row per (user, device-token). Distinct from
 * {@link JimPushSubscription}, which is the browser Web Push (VAPID) channel:
 * mobile push is a separate native FCM/APNs channel and must not reuse the web
 * subscription table.
 *
 * <p>Privacy: the raw FCM/APNs token is never stored in the clear and never
 * logged. {@code TOKEN_ENC} holds an AES-GCM encrypted copy (needed to actually
 * send), and {@code TOKEN_HASH} holds a SHA-256 hash used for idempotent
 * lookup/dedupe so we can find an existing row without handling the raw token.</p>
 *
 * <p>Short {@code @Table} name: AO prefixes tables with {@code AO_xxxxxx_} and
 * the total generated name must stay within DB identifier limits, so we keep the
 * logical name short and explicit.</p>
 */
@Preload
@Table("JimMobileDevice")
public interface JimMobileDevice extends Entity {

    @Indexed
    @StringLength(255)
    @Accessor("USER_KEY")
    String getUserKey();

    @Mutator("USER_KEY")
    void setUserKey(String userKey);

    /** SHA-256 hex of the raw push token; used for lookup/dedupe, safe to index. */
    @Indexed
    @StringLength(64)
    @Accessor("TOKEN_HASH")
    String getTokenHash();

    @Mutator("TOKEN_HASH")
    void setTokenHash(String tokenHash);

    /** AES-GCM encrypted push token (base64). Never logged. */
    @StringLength(StringLength.UNLIMITED)
    @Accessor("TOKEN_ENC")
    String getTokenEnc();

    @Mutator("TOKEN_ENC")
    void setTokenEnc(String tokenEnc);

    /** {@code android} or {@code ios}. */
    @StringLength(20)
    @Accessor("PLATFORM")
    String getPlatform();

    @Mutator("PLATFORM")
    void setPlatform(String platform);

    @StringLength(40)
    @Accessor("APP_VERSION")
    String getAppVersion();

    @Mutator("APP_VERSION")
    void setAppVersion(String appVersion);

    @StringLength(120)
    @Accessor("DEVICE_MODEL")
    String getDeviceModel();

    @Mutator("DEVICE_MODEL")
    void setDeviceModel(String deviceModel);

    @StringLength(20)
    @Accessor("LOCALE")
    String getLocale();

    @Mutator("LOCALE")
    void setLocale(String locale);

    @Accessor("ENABLED")
    Boolean getEnabled();

    @Mutator("ENABLED")
    void setEnabled(Boolean enabled);

    @Accessor("CREATED_AT")
    Long getCreatedAt();

    @Mutator("CREATED_AT")
    void setCreatedAt(Long createdAt);

    @Accessor("UPDATED_AT")
    Long getUpdatedAt();

    @Mutator("UPDATED_AT")
    void setUpdatedAt(Long updatedAt);

    @Accessor("LAST_SEEN_AT")
    Long getLastSeenAt();

    @Mutator("LAST_SEEN_AT")
    void setLastSeenAt(Long lastSeenAt);

    /** Consecutive send failures; used to prune permanently-dead tokens. */
    @Accessor("FAIL_COUNT")
    Integer getFailCount();

    @Mutator("FAIL_COUNT")
    void setFailCount(Integer failCount);
}
