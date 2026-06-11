# CorbitChat Direct Message Attachments

## Architecture Overview

CorbitChat stores direct-chat attachments outside Jira issue attachments:

1. **Binary files** live on disk under Jira home.
2. **Metadata** lives in Active Objects (`JimAttachment`).
3. **Messages** (`JimMessage`) remain the primary chat row and reference attachments by `MESSAGE_ID`.
4. **Download/preview** is served through authenticated REST endpoints.

This keeps direct chat independent from Jira issues and issue permissions.

## Storage Location

Relative to Jira home:

```
<jira-home>/data/corbitchat/attachments/
```

In the current Docker dev container this is typically:

```
/var/jira/data/corbitchat/attachments/
```

The plugin resolves Jira home through Jira's `JiraHome` component. Do not hardcode container paths in production code.

Stored files use a date-based relative path:

```
YYYY/MM/DD/<uuid><extension>
```

## Data Center Note

In a real Jira Data Center cluster, attachment storage must live on **shared Jira home** so every node can read uploaded files. The current single-node Docker environment is acceptable for development only.

## Security Rules

- Upload requires an authenticated conversation participant.
- Download/preview requires the same participant check.
- Original filenames are sanitized; stored filenames are UUID-based.
- Path traversal is blocked; files are never written outside the attachment root.
- Max file size: **10 MB** per upload.
- Dangerous extensions are rejected.
- Storage paths are never returned in REST JSON.
- Files are not exposed through Jira Web Resources.

## Allowed MIME Types

Images:

- `image/png`
- `image/jpeg`
- `image/gif`
- `image/webp`

Documents/files:

- `application/pdf`
- `text/plain`
- `application/zip`
- `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `application/vnd.openxmlformats-officedocument.presentationml.presentation`
- `application/msword`
- `application/vnd.ms-excel`
- `application/vnd.ms-powerpoint`

## REST Endpoints

- `POST /rest/jim/1.0/conversations/{conversationId}/attachments`
- `GET /rest/jim/1.0/attachments/{attachmentId}/download`
- `GET /rest/jim/1.0/attachments/{attachmentId}/preview`

Message list responses include an `attachments` array for each message.

## Dev Reset Implications

Adding `JimAttachment` is an AO schema change. If AO migration fails in dev, follow the documented dev reset procedure:

1. Uninstall the plugin.
2. Drop stale `AO_%` tables for this plugin if needed.
3. Reinstall the rebuilt plugin JAR.

Do not drop AO tables automatically from plugin code.

## Production Hardening TODOs

- Antivirus/malware scanning on upload
- Thumbnail generation for large images
- Admin settings for size/type limits
- Per-user or per-conversation quota management
- Cleanup job for deleted attachment binaries
- Shared-home health checks in Data Center
