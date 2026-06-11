# Dev reset: Jira Internal Messenger Active Objects schema

## Symptom

`GET /rest/jim/1.0/conversations` fails with HTTP 500 and Jira logs show:

```
java.sql.SQLSyntaxErrorException: Unknown column 'USER_A_KEY' in 'where clause'
```

Stack trace path:

- `JimConversationResource.listConversations`
- `JimApiServiceImpl.listConversations` (if used)
- `JimConversationServiceImpl.getOrCreateSystemConversation`
- `JimConversationServiceImpl.findSystemConversationForUser`

`GET /rest/jim/1.0/health` and `GET /rest/jim/1.0/ao-health` may still succeed because they do not query `USER_A_KEY`.

## Cause

The plugin was installed earlier with a different `JimConversation` Active Objects schema. Jira/Active Objects kept the old AO table, and the current code expects columns such as `USER_A_KEY` that do not exist in that stale table.

A related issue: without explicit `@Accessor`/`@Mutator` annotations, Active Objects maps `getUserAKey()` to column `USER_AKEY` (not `USER_A_KEY`). The service queries use `USER_A_KEY`, so the entity must declare:

```java
@Accessor("USER_A_KEY")
String getUserAKey();

@Mutator("USER_A_KEY")
void setUserAKey(String userAKey);
```

After fixing the entity, drop stale tables so AO recreates columns with the correct names.

**Important:** Uninstalling a P2 app does not necessarily remove its AO tables. In a dev/test environment, stale AO tables may need to be manually dropped.

## Diagnostic endpoint (development only)

Jira administrators can inspect expected vs actual schema:

```
GET /rest/jim/1.0/dev/ao-schema
```

This returns JSON with expected entity columns and, when database metadata is available, actual `AO_%` table names and column lists. Look for a conversation table with `CONVERSATION_TYPE` but without `USER_A_KEY`.

## Important warnings

- **Back up the database first.**
- **Do not drop AO tables in production.**
- **Do not add code that automatically drops tables.**
- This plugin does not drop tables automatically on startup.
- Only remove AO tables that belong to this plugin.

## Reset procedure (development only)

1. Uninstall **Jira Internal Messenger** from **Manage apps**.
2. Stop/restart Jira if the uninstall does not fully release AO state.
3. Back up the database.
4. Inspect AO tables in MySQL:

```sql
SHOW TABLES LIKE 'AO\\_%';
```

5. Identify only the AO tables belonging to this plugin (`com.corbitlogic.jira.internalmessenger.ao` namespace). Use `/rest/jim/1.0/dev/ao-schema` or column inspection to confirm.
6. Drop only this plugin's stale AO tables.
7. Start Jira.
8. Install the newly built plugin JAR:

```
target/jira-internal-messenger-1.0.0-SNAPSHOT.jar
```

9. Test in this order:

```
GET /rest/jim/1.0/health
GET /rest/jim/1.0/ao-health
GET /rest/jim/1.0/dev/ao-schema
GET /rest/jim/1.0/conversations
```

## JimMessage lifecycle columns (2026-06)

If `JimMessage` was created before message lifecycle features (reply, edit, delete), AO may be missing:

- `REPLY_TO_MESSAGE_ID`
- `EDITED`
- `EDITED_AT`
- `DELETED`
- `DELETED_AT`
- `DELETED_BY_USER_KEY`

Symptoms may include HTTP 500 on message list/send/edit/delete with SQL errors referencing those column names. Use `GET /rest/jim/1.0/dev/ao-schema` to compare expected `JimMessage` columns vs the database. In development, follow the reset procedure above so AO recreates the table with the new columns.

## Expected results after reset

- `/health` returns JSON without touching Active Objects.
- `/ao-health` returns JSON with `"activeObjects": "initialized"` when schema is healthy.
- `/dev/ao-schema` shows `JimConversation` expected columns and database tables that include `USER_A_KEY`.
- `/conversations` returns JSON with the system conversation list. If AO is still broken, it returns JSON (not HTML) with `debugExceptionMessage` during development.
