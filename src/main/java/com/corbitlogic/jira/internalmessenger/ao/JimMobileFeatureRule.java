package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Entity;
import net.java.ao.Accessor;
import net.java.ao.Mutator;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * A mobile feature-access rule (Sprint 04G).
 *
 * <p>Each row grants a Jira user or group a specific set of CorbitHub mobile
 * feature keys (e.g. {@code chat,boards}). This is a product-level gate layered
 * <em>on top of</em> Jira permissions &mdash; it never replaces them. When no
 * rule matches a user, that user keeps full access to every mobile feature
 * (backward compatible default).</p>
 *
 * <p>An explicit short {@code @Table} name is required: AO prefixes the table
 * with {@code AO_xxxxxx_} and caps the total at 30 chars, which the full class
 * name would exceed. {@code AO_xxxxxx_JIMMOBILEFEATRULE} is 27 chars.</p>
 */
@Preload
@Table("JimMobileFeatRule")
public interface JimMobileFeatureRule extends Entity {

    /** {@code USER} or {@code GROUP}. */
    @Indexed
    @StringLength(20)
    @Accessor("SUBJECT_TYPE")
    String getSubjectType();

    @Mutator("SUBJECT_TYPE")
    void setSubjectType(String subjectType);

    /** A Jira username / user key, or a group name. */
    @StringLength(255)
    @Accessor("SUBJECT_VALUE")
    String getSubjectValue();

    @Mutator("SUBJECT_VALUE")
    void setSubjectValue(String subjectValue);

    /** Comma-separated allowed mobile feature keys, e.g. {@code chat,boards}. */
    @StringLength(255)
    @Accessor("FEATURES")
    String getFeatures();

    @Mutator("FEATURES")
    void setFeatures(String features);

    @Accessor("ENABLED")
    Boolean getEnabled();

    @Mutator("ENABLED")
    void setEnabled(Boolean enabled);

    @Accessor("PRIORITY")
    Integer getPriority();

    @Mutator("PRIORITY")
    void setPriority(Integer priority);

    @StringLength(255)
    @Accessor("CREATED_BY")
    String getCreatedBy();

    @Mutator("CREATED_BY")
    void setCreatedBy(String createdBy);

    @Accessor("CREATED_AT")
    Long getCreatedAt();

    @Mutator("CREATED_AT")
    void setCreatedAt(Long createdAt);

    @Accessor("UPDATED_AT")
    Long getUpdatedAt();

    @Mutator("UPDATED_AT")
    void setUpdatedAt(Long updatedAt);
}
