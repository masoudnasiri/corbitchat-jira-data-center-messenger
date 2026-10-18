# Sprint 07 — Attachments & Voice Messages

**Phase:** 2 — Jira Actions · **Duration:** 2 weeks · **Goal:** chat
attachments (image / file / audio) with preview, download, upload, plus
voice messages, camera and OS share-sheet inbound.

> Spec: PDF §4.3 (attachments/voice). Design: `docs/components.md`.
> Reference impl: `jim-api.js` (`uploadAttachment`), `jim-messenger.js`
> (lightbox). Architecture §2.3, §9.4.

## Objective

Match desktop attachment behavior: multipart upload, permission-enforced
preview/download, image lightbox, audio playback, and record-and-send
voice messages — respecting RFC 5987 filenames already handled
server-side (gotcha #3).

## In scope

- Upload image/file/audio from gallery, files, camera.
- Inline previews: image thumbnail + lightbox, audio player, generic
  file card with size/type.
- Download with correct filename (server sets RFC 5987
  `Content-Disposition` — do not reparse, gotcha #3).
- Voice messages: record, waveform/duration, send, playback.
- OS share-sheet + camera integration (inbound share into a chat).
- Permission enforcement on preview/download (403 → no-permission).

## Backend track (plugin BFF)

Existing endpoints (`docs/architecture.md` §8.2, §9.4):

```
POST /conversations/{id}/attachments   (multipart: file, body?)
GET  /attachments/{id}/download        (RFC 5987 Content-Disposition)
GET  /attachments/{id}/preview         (inline image/audio)
```
Expose via `/rest/corbit-mobile/1.0/*` (proxy or thin wrapper). Ensure
auth header (PAT/session) is honored on binary streams; keep
`X-Atlassian-Token: no-check` semantics. Add content-type + size in the
message DTO for client rendering.

## Mobile track (Flutter)

1. Attachment picker sheet: Photo, Camera, File, Voice
   (`docs/components.md` Modal/Bottom Sheet).
2. Upload with progress; optimistic attachment bubble; retry on failure
   (queue). Multipart via `Dio` with auth interceptor.
3. Image thumbnail + full-screen lightbox (pinch-zoom), matching
   `jim-messenger.js` lightbox behavior.
4. Audio player component (play/pause/scrub); voice recorder
   (`record`/`just_audio`) with duration + waveform; send as audio
   attachment.
5. Secure download to app storage using server filename; open/share.
6. Handle OS share-sheet / camera intents → route into conversation
   picker → send.

## Design references

- `docs/components.md` — Chat Bubble (attachments), Modal/Bottom Sheet,
  Toast (upload failed → retry).
- `docs/states.md` — Message Send States for uploads.

## Dependencies

- Sprint 02/06 (thread + composer + message actions).

## Acceptance criteria (PDF §4.3, §6.4)

- [ ] Upload image/file/audio and voice message; peer receives/plays.
- [ ] Image lightbox + audio playback work in-thread.
- [ ] Downloaded file keeps the correct (RFC 5987) filename.
- [ ] Preview/download blocked without permission (no-permission state).
- [ ] Camera capture and OS share into a chat both work.

## Definition of Done

- Uploads queue + retry offline; no partial/broken bubbles on failure.
- Large-file guardrails + rate limiting respected (PDF §6.4; enforced
  Sprint 13).
- RTL layout of attachment bubbles verified.

---

## Implementation addendum (Sprint 07 — delivered)

**Scope delivered:** Android share sheet for images/files, inbound share →
destination selection, in-chat attachment send from a Telegram-inspired
composer, image original-vs-compressed choice, in-thread attachment rendering
(image lightbox + file card with download/open), Saved Messages attachment
support (same backend path). Voice messages, camera capture, audio in-thread
playback, upload queue/retry, and multi-file share are **deferred** (see below).

**Root cause of the gap:** the plugin already had a complete attachment stack
(multipart upload `POST /rest/jim/1.0/conversations/{id}/attachments`,
download/preview with Range, allow-list policy, license gate) and the mobile BFF
message payload already emitted **relative** attachment URLs
(`/rest/jim/1.0/attachments/{id}/download|preview`). The mobile app simply never
(1) modelled/rendered `attachments`, (2) uploaded files, or (3) registered as an
image/file share target. So this was **mobile-only**.

**Backend / plugin changes:** none. Plugin stays `1.0.0-mobile-s06-fix2`; no
redeploy. The web upload endpoint enforces the same participant permission +
direct-chat policy + license the BFF uses, so it is reused directly by the app
over the authenticated connection.

**No new Flutter packages** (none of `image_picker` / `file_picker` /
`flutter_image_compress` / share plugins are in the offline pub cache). All new
device capabilities are native Kotlin over the existing `corbitchat/share`
`MethodChannel`:
- receive shared image/file (`ACTION_SEND` + `EXTRA_STREAM` → copied to app
  cache), `pickAttachment` (system document picker), `compressImage`
  (Bitmap downscale + JPEG), `getCacheDir` + `openFile` (FileProvider
  `ACTION_VIEW`). Text share (Sprint 06) is preserved.
- Upload uses `dio` multipart (`file` + optional `body`); authenticated image
  previews reuse the existing `avatarImage` helper.

**Compression UX:** sending/sharing an image opens a bottom sheet offering
*Compress* (longest edge ≤ 1600px, JPEG q80) or *Original size*. Non-images
upload as-is. Compression failures fall back to the original.

**Deferred (documented):** multi-file share (`ACTION_SEND_MULTIPLE`), voice
messages/recording, in-thread audio playback, camera capture, and offline
upload queue/retry — none are required for usable single image/file workflows
and each adds material native surface area.

**App build:** `1.0.0+7` / label `s07`.
