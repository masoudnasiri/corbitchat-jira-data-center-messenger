/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.atlassian.sal.api.rdbms.ConnectionCallback
 *  com.atlassian.sal.api.rdbms.TransactionalExecutorFactory
 */
package com.corbitlogic.jira.internalmessenger.service;

import com.atlassian.sal.api.rdbms.ConnectionCallback;
import com.atlassian.sal.api.rdbms.TransactionalExecutorFactory;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class JimAoSchemaDiagnostics {
    public static final String PLUGIN_KEY = "com.corbitlogic.jira.internalmessenger.jira-internal-messenger";
    private final TransactionalExecutorFactory transactionalExecutorFactory;

    public JimAoSchemaDiagnostics(TransactionalExecutorFactory transactionalExecutorFactory) {
        this.transactionalExecutorFactory = transactionalExecutorFactory;
    }

    public Map<String, Object> buildSchemaReport() {
        LinkedHashMap<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("ok", true);
        body.put("pluginKey", PLUGIN_KEY);
        body.put("entities", this.expectedEntities());
        try {
            body.put("databaseTables", this.inspectAoTables());
        }
        catch (Exception ex) {
            body.put("databaseInspectionWarning", "Could not read database metadata: " + ex.getMessage());
        }
        return body;
    }

    private List<Map<String, Object>> expectedEntities() {
        ArrayList<Map<String, Object>> entities = new ArrayList<Map<String, Object>>();
        entities.add(this.entityDefinition("JimConversation", "ID", "CONVERSATION_TYPE", "USER_A_KEY", "USER_B_KEY", "SYSTEM_KEY", "CREATED_AT", "UPDATED_AT", "LAST_MESSAGE_AT", "LAST_MESSAGE_PREVIEW"));
        entities.add(this.entityDefinition("JimMessage", "ID", "CONVERSATION_ID", "SENDER_TYPE", "SENDER_USER_KEY", "BODY", "BODY_FORMAT", "EVENT_TYPE", "ISSUE_KEY", "ISSUE_SUMMARY", "ISSUE_URL", "ACTOR_USER_KEY", "ACTOR_DISPLAY_NAME", "CREATED_AT", "REPLY_TO_MESSAGE_ID", "EDITED", "EDITED_AT", "DELETED", "DELETED_AT", "DELETED_BY_USER_KEY"));
        entities.add(this.entityDefinition("JimReadState", "ID", "CONVERSATION_ID", "USER_KEY", "LAST_READ_MESSAGE_ID", "LAST_READ_AT"));
        entities.add(this.entityDefinition("JimEventLog", "ID", "EVENT_FINGERPRINT", "EVENT_TYPE", "ISSUE_KEY", "TARGET_USER_KEY", "CREATED_AT"));
        entities.add(this.entityDefinition("JimAttachment", "ID", "MESSAGE_ID", "CONVERSATION_ID", "UPLOADER_USER_KEY", "ORIGINAL_FILENAME", "STORED_FILENAME", "STORAGE_PATH", "CONTENT_TYPE", "FILE_SIZE", "FILE_KIND", "CREATED_AT", "DELETED", "THUMBNAIL_PATH", "WIDTH", "HEIGHT"));
        return entities;
    }

    private Map<String, Object> entityDefinition(String entityName, String ... expectedColumns) {
        LinkedHashMap<String, Object> entity = new LinkedHashMap<String, Object>();
        entity.put("entity", entityName);
        entity.put("expectedColumns", Arrays.asList(expectedColumns));
        return entity;
    }

    private List<Map<String, Object>> inspectAoTables() {
        ConnectionCallback callback = connection -> {
            try {
                return this.readAoTableMetadata(connection);
            }
            catch (SQLException ex) {
                throw new IllegalStateException("Failed to read AO table metadata", ex);
            }
        };
        return (List)this.transactionalExecutorFactory.createReadOnly().execute(callback);
    }

    private List<Map<String, Object>> readAoTableMetadata(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = this.resolveSchema(connection, metaData);
        TreeSet<String> tableNames = new TreeSet<String>();
        try (ResultSet tableResult = metaData.getTables(catalog, schema, "AO_%", new String[]{"TABLE"})) {
            while (tableResult.next()) {
                tableNames.add(tableResult.getString("TABLE_NAME"));
            }
        }
        List<Map<String, Object>> tables = new ArrayList<Map<String, Object>>();
        for (String tableName : tableNames) {
            TreeSet<String> columns = new TreeSet<String>();
            try (ResultSet columnResult = metaData.getColumns(catalog, schema, tableName, "%");){
                while (columnResult.next()) {
                    columns.add(columnResult.getString("COLUMN_NAME"));
                }
            }
            if (columns.isEmpty()) continue;
            LinkedHashMap<String, Object> table = new LinkedHashMap<String, Object>();
            table.put("tableName", tableName);
            table.put("columns", new ArrayList(columns));
            if (columns.contains("CONVERSATION_TYPE")) {
                table.put("likelyEntity", "JimConversation");
                table.put("hasUserAKey", columns.contains("USER_A_KEY"));
                if (!columns.contains("USER_A_KEY")) {
                    table.put("schemaMismatchWarning", "Table has CONVERSATION_TYPE but is missing USER_A_KEY. This usually indicates a stale AO table from an older plugin version.");
                }
            } else if (columns.contains("EVENT_FINGERPRINT")) {
                table.put("likelyEntity", "JimEventLog");
            } else if (columns.contains("LAST_READ_MESSAGE_ID")) {
                table.put("likelyEntity", "JimReadState");
            } else if (columns.contains("BODY") && columns.contains("CONVERSATION_ID")) {
                table.put("likelyEntity", "JimMessage");
            }
            tables.add(table);
        }
        return tables;
    }

    private String resolveSchema(Connection connection, DatabaseMetaData metaData) throws SQLException {
        String schema = connection.getSchema();
        if (schema != null && !schema.isEmpty()) {
            return schema;
        }
        try (ResultSet schemas = metaData.getSchemas();){
            LinkedHashSet<String> schemaNames = new LinkedHashSet<String>();
            while (schemas.next()) {
                schemaNames.add(schemas.getString("TABLE_SCHEM"));
            }
            if (schemaNames.size() == 1) {
                String string = (String)schemaNames.iterator().next();
                return string;
            }
        }
        return null;
    }
}

