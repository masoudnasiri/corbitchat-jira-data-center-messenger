package com.corbitlogic.jira.internalmessenger.ao;

import net.java.ao.Accessor;
import net.java.ao.Entity;
import net.java.ao.Mutator;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * Per-tool MCP policy row. One row per upstream Jira MCP tool. The Jira plugin is
 * the administrative source of truth; the Corbit Logic Gateway pulls the effective
 * policy and enforces it. No secrets are stored here.
 */
@Preload
@Table("JimMcpToolPol")
public interface JimMcpToolPolicy extends Entity {

    @Indexed
    @StringLength(200)
    @Accessor("TOOL_NAME")
    String getToolName();

    @Mutator("TOOL_NAME")
    void setToolName(String v);

    @StringLength(200)
    @Accessor("DISPLAY_NAME")
    String getDisplayName();

    @Mutator("DISPLAY_NAME")
    void setDisplayName(String v);

    @StringLength(50)
    @Accessor("TOOLSET")
    String getToolset();

    @Mutator("TOOLSET")
    void setToolset(String v);

    /** read | write | destructive */
    @StringLength(20)
    @Accessor("CLASSIFICATION")
    String getClassification();

    @Mutator("CLASSIFICATION")
    void setClassification(String v);

    /** PASS | BLOCKED | NOT_APPLICABLE */
    @StringLength(30)
    @Accessor("VERIFICATION")
    String getVerification();

    @Mutator("VERIFICATION")
    void setVerification(String v);

    @Accessor("AVAILABLE")
    boolean getAvailable();

    @Mutator("AVAILABLE")
    void setAvailable(boolean v);

    @Accessor("ENABLED")
    boolean getEnabled();

    @Mutator("ENABLED")
    void setEnabled(boolean v);

    @Accessor("CONFIRM_REQ")
    boolean getConfirm();

    @Mutator("CONFIRM_REQ")
    void setConfirm(boolean v);

    @StringLength(450)
    @Accessor("DESCRIPTION")
    String getDescription();

    @Mutator("DESCRIPTION")
    void setDescription(String v);

    @Accessor("UPDATED_AT")
    Long getUpdatedAt();

    @Mutator("UPDATED_AT")
    void setUpdatedAt(Long v);
}
