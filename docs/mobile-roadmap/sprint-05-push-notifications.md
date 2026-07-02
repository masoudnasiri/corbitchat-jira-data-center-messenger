# Sprint 05 — Push Notifications (FCM / APNs)

**Phase:** 1 — MVP Core · **Duration:** 2 weeks · **Goal:** native mobile
push for messages, mentions, assignments, status changes, replies and
overdue reminders, with deep links — closing Phase 1.

> Spec: PDF §7 (push + event model), §6.3 (`JimMobileDevice`), §7.1
> (preferences). Handover gotchas #8, #10. Architecture: `docs/architecture.md`
> §2.4 (notifications), §9.5 (Web Push), §5.1 (`JimPushService`).

## Objective

Add a **new mobile push channel** to the plugin (do not reuse browser
Web Push / `jim-sw.js` — gotcha #10), register device tokens, deliver
minimal safe payloads, and route deep links to the right screen in both
foreground/background/terminated states.

## In scope

- New push channel on the server: `MOBILE_FCM` (Android) + `MOBILE_APNS`
  (iOS, or FCM for both).
- `JimMobileDevice` AO table + device register/unregister endpoints.
- Client FCM/APNs integration; permission prompt; token lifecycle.
- Notification model → deep links (PDF §7 table); tap routes correctly.
- Minimal preferences: enable/disable per category (full prefs in
  Sprint 12).
- Logout revokes device token (wire the hook stubbed in Sprint 01).

## Backend track (plugin BFF + services)

1. AO `JimMobileDevice` (`userKey, platform, deviceId, pushTokenHash,
   enabled, lastSeenAt`) — store token as hash/encrypted, never log raw
   (PDF §6.4).
2. `POST /rest/corbit-mobile/1.0/devices` (register/update) and
   `DELETE /rest/corbit-mobile/1.0/devices/{deviceId}` (revoke).
3. Extend `JimPushService` with a mobile sender: on the same events that
   trigger Web Push today (message, mention, assignment, status change,
   comment reply, overdue reminder — `docs/architecture.md` §2.4, §9.5)
   also fan out to registered mobile devices via FCM/APNs.
4. Payload = title + eventType + safe identifier + deep link only; **no**
   sensitive field content (PDF §6.4, §7).
5. Cluster-safety: dedupe pushes via fingerprint/lock so a DC cluster
   doesn't double-send (PDF §8, Appendix B). (Hardened in Sprint 13.)

## Mobile track (Flutter)

1. `firebase_messaging` (+ APNs config); request notification
   permission; obtain token; register via `POST /devices` after login;
   refresh on token rotation.
2. Handle foreground / background / terminated delivery; local
   notification display in foreground.
3. Deep-link router for the PDF §7 schemes:
   `corbitchat://conversation/{id}`,
   `.../conversation/{id}?message={mid}`,
   `corbitchat://issue/{key}`, `.../issue/{key}?comment={id}`,
   `corbitchat://issues?filter=overdue`. Load full detail from API after
   open (payloads are minimal).
4. Global + per-tab badge counts (`docs/components.md` Bottom Navigation).
5. Notifications tab list (`docs/components.md` Notification List Item) —
   mark seen; ties into Assistant feed (Sprint 08).
6. Logout → `DELETE /devices/{id}`.

## Environment note

FCM/APNs require **outbound internet** from the plugin/host. If Jira is
in a closed network, decide Push Relay vs MDM (PDF Appendix B) — resolve
before this sprint or fall back to in-app polling.

## Design references

- `docs/components.md` — Notification List Item, Bottom Nav badges.
- `docs/layout.md` — Deep Link Handling table.
- PDF §7 event → recipient → deep link table.

## API contract

```
POST   /rest/corbit-mobile/1.0/devices        { platform, deviceId, pushToken }
DELETE /rest/corbit-mobile/1.0/devices/{deviceId}
```

## Dependencies

- Sprint 00 (Firebase/Apple provisioning), Sprint 01 (auth/logout hook),
  Sprint 04 (issue deep-link target), Sprint 02 (conversation target).

## Acceptance criteria (PDF §9 Phase 1, §9.1)

- [ ] Device registers on login; token stored hashed server-side.
- [ ] Push received on Android + iOS in foreground, background, and
      terminated states.
- [ ] Each event type deep-links to the correct screen (both locales).
- [ ] Payloads contain no sensitive field content.
- [ ] Logout revokes the device token.
- [ ] No duplicate pushes in a 2-node test (basic dedupe).

## Definition of Done

- Raw tokens never logged; register/revoke audited (minimal record).
- Falls back gracefully to polling if push unavailable in the network.
