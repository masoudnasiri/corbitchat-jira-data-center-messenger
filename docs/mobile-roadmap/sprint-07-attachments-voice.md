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
