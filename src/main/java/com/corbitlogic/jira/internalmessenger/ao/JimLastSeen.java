package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Minimal persisted last-seen presence (Sprint 12), cluster-safe via AO. One row
 * per user, holding only the last-active epoch millis — no more than needed for
 * an "online / last seen …" label. The hidden reviewer never writes a row here.
 */
@Preload
@Table("JimLastSeen")
public interface JimLastSeen extends Entity {

    @Indexed
    @StringLength(255)
    String getUserKey();
    void setUserKey(String userKey);

    @Indexed
    Long getLastSeenAt();
    void setLastSeenAt(Long lastSeenAt);
}
